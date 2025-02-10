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
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.*;

import static io.moderne.jsonrpc.JsonRpcMethod.typed;

public class RecipeRpc {
    ExecutorService executorService = ForkJoinPool.commonPool();
    private final JsonRpc jsonRpc;
    private final Duration timeout;

    private final Map<UUID, SourceFile> remoteTrees = new HashMap<>();
    private final Map<UUID, SourceFile> localTrees = new HashMap<>();

    // TODO This should be keyed on both the visit (transaction) ID in addition to the tree ID
    private final Map<UUID, BlockingQueue<TreeData>> inProgressGetTreeDatas = new HashMap<>();

    public RecipeRpc(JsonRpc jsonRpc, Duration timeout) {
        this.jsonRpc = jsonRpc;
        this.timeout = timeout;

        jsonRpc.method("visit", typed(VisitRequest.class, request -> {
            Constructor<?> ctor = Class.forName(request.getVisitor()).getDeclaredConstructor();
            ctor.setAccessible(true);

            //noinspection unchecked
            TreeVisitor<Tree, Object> visitor = (TreeVisitor<Tree, Object>) ctor.newInstance();
            try {
                SourceFile before = getTree(request.getTreeId(), request.getLanguage());
                SourceFile after = (SourceFile) visitor.visit(before, request.getP());
                if (after == null) {
                    localTrees.remove(before.getId());
                } else {
                    localTrees.put(after.getId(), after);
                }
                return new VisitResponse(before != after);
            } finally {
                inProgressGetTreeDatas.remove(request.getTreeId());
            }
        }));

        jsonRpc.method("getTree", typed(GetTreeDataRequest.class, request -> {
            BlockingQueue<TreeData> q = inProgressGetTreeDatas.computeIfAbsent(request.getTreeId(), id -> {
                BlockingQueue<TreeData> batch = new ArrayBlockingQueue<>(1);
                SourceFile before = remoteTrees.get(id);
                TreeDataSendQueue sendQueue = new TreeDataSendQueue(10, before, batch::put);
                executorService.submit(() -> {
                    try {
                        SourceFile after = localTrees.get(id);
                        Language.fromSourceFile(after).getSender().visit(after, sendQueue);
                        remoteTrees.put(id, after);
                    } catch (Throwable t) {
                        // TODO what to do here?
                        t.printStackTrace();
                    }
                    return 0;
                });
                return batch;
            });
            return q.take();
        }));
    }

    public RecipeRpc bind() {
        jsonRpc.bind();
        return this;
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
        localTrees.put(sourceFile.getId(), sourceFile);
        Language language = Language.fromSourceFile(sourceFile);
        return send("visit", new VisitRequest(visitorName, sourceFile.getId(), language, p),
                VisitResponse.class);
    }

    private SourceFile getTree(UUID treeId, Language language) {
        TreeDataReceiveQueue q = new TreeDataReceiveQueue(() -> send("getTree",
                new GetTreeDataRequest(treeId), TreeData.class));
        return q.tree(language.getReceiver(), localTrees.get(treeId));
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
