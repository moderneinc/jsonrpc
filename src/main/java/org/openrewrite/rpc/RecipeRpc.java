package org.openrewrite.rpc;

import io.moderne.jsonrpc.JsonRpc;
import io.moderne.jsonrpc.JsonRpcRequest;
import org.openrewrite.SourceFile;
import org.openrewrite.Tree;
import org.openrewrite.TreeVisitor;
import org.openrewrite.rpc.request.GetTreeDataRequest;
import org.openrewrite.rpc.request.RecipeRpcRequest;
import org.openrewrite.rpc.request.VisitRequest;
import org.openrewrite.rpc.request.VisitResponse;

import java.lang.reflect.Constructor;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;

import static io.moderne.jsonrpc.JsonRpcMethod.typed;
import static org.openrewrite.rpc.TreeDatum.State.END_OF_TREE;

public class RecipeRpc {
    private final JsonRpc jsonRpc;
    private final Duration timeout;

    /**
     * Keeps track of the local and remote state of trees that are used in
     * visits.
     */
    private final Map<UUID, SourceFile> remoteTrees = new HashMap<>();
    private final Map<UUID, SourceFile> localTrees = new HashMap<>();

    /**
     * Keeps track of objects that need to be referentially deduplicated, and
     * the ref IDs to look them up by on the remote.
     */
    private final Map<Object, Integer> localRefs = new IdentityHashMap<>();
    private final Map<Integer, Object> remoteRefs = new IdentityHashMap<>();

    // TODO This should be keyed on both the visit (transaction) ID in addition to the tree ID
    private final Map<UUID, BlockingQueue<TreeData>> inProgressGetTreeDatas = new ConcurrentHashMap<>();

    private static final ExecutorService forkJoin = ForkJoinPool.commonPool();

    public RecipeRpc(JsonRpc jsonRpc, Duration timeout) {
        this.jsonRpc = jsonRpc;
        this.timeout = timeout;

        jsonRpc.method("visit", typed(VisitRequest.class, request -> {
            Constructor<?> ctor = Class.forName(request.getVisitor()).getDeclaredConstructor();
            ctor.setAccessible(true);

            //noinspection unchecked
            TreeVisitor<Tree, Object> visitor = (TreeVisitor<Tree, Object>) ctor.newInstance();
            SourceFile before = getTree(request.getTreeId(), request.getLanguage());

            localTrees.put(before.getId(), before);

            SourceFile after = (SourceFile) visitor.visit(before, request.getP());
            if (after == null) {
                localTrees.remove(before.getId());
            } else {
                localTrees.put(after.getId(), after);
            }
            return new VisitResponse(before != after);
        }));

        jsonRpc.method("getTree", typed(GetTreeDataRequest.class, request -> {
            BlockingQueue<TreeData> q = inProgressGetTreeDatas.computeIfAbsent(request.getTreeId(), id -> {
                BlockingQueue<TreeData> batch = new ArrayBlockingQueue<>(1);
                SourceFile before = remoteTrees.get(id);
                TreeDataSendQueue sendQueue = new TreeDataSendQueue(10, batch::put, localRefs);
                forkJoin.submit(() -> {
                    try {
                        SourceFile after = localTrees.get(id);
                        sendQueue.send(after, before, () -> Language.fromSourceFile(after)
                                .getSender().visit(after, sendQueue));

                        // All the data has been sent, and the remote should have received
                        // the full tree, so update our understanding of the remote state
                        // of this tree.
                        remoteTrees.put(id, after);
                    } catch (Throwable ignored) {
                        // TODO do something with this exception
                    } finally {
                        sendQueue.put(new TreeDatum(END_OF_TREE, null, null, null));
                        sendQueue.flush();
                    }
                    return 0;
                });
                return batch;
            });

            TreeData batch = q.take();
            List<TreeDatum> data = batch.getData();
            if (data.get(data.size() - 1).getState() == END_OF_TREE) {
                inProgressGetTreeDatas.remove(request.getTreeId());
            }
            return batch;
        }));
        jsonRpc.bind();
    }

    public void shutdown() {
        jsonRpc.shutdown();
    }

    public <P> Tree visit(SourceFile sourceFile, String visitorName, P p) {
        VisitResponse response = scan(sourceFile, visitorName, p);
        return response.isModified() ?
                getTree(sourceFile.getId(), Language.fromSourceFile(sourceFile)) :
                sourceFile;
    }

    public <P> VisitResponse scan(SourceFile sourceFile, String visitorName, P p) {
        // Set the local state of this tree, so that when the remote
        // asks for it, we know what to send.
        localTrees.put(sourceFile.getId(), sourceFile);
        Language language = Language.fromSourceFile(sourceFile);
        return send("visit", new VisitRequest(visitorName, sourceFile.getId(), language, p),
                VisitResponse.class);
    }

    private SourceFile getTree(UUID treeId, Language language) {
        TreeDataReceiveQueue q = new TreeDataReceiveQueue(remoteRefs, () -> send("getTree",
                new GetTreeDataRequest(treeId), TreeData.class));
        SourceFile remoteTree = q.receive(localTrees.get(treeId), before -> (SourceFile) language
                .getReceiver().visit(before, q));
        if (!q.take().getState().equals(END_OF_TREE)) {
            throw new IllegalStateException("Expected END_OF_TREE");
        }
        // We are now in sync with the remote state of the tree.
        remoteTrees.put(treeId, remoteTree);
        return remoteTree;
    }

    private <P> P send(String method, RecipeRpcRequest body, Class<P> responseType) {
        try {
            // TODO handle error
            return jsonRpc
                    .send(JsonRpcRequest.newRequest(method)
                            .namedParameters(body)
                            .build())
                    .get(timeout.getSeconds(), TimeUnit.SECONDS)
                    .getResult(responseType);
        } catch (ExecutionException | TimeoutException | InterruptedException e) {
            throw new RuntimeException(e);
        }
    }
}
