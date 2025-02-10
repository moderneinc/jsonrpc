package org.openrewrite.rpc;

import com.fasterxml.jackson.core.type.TypeReference;
import io.moderne.jsonrpc.JsonRpc;
import io.moderne.jsonrpc.JsonRpcRequest;
import org.openrewrite.SourceFile;
import org.openrewrite.Tree;
import org.openrewrite.TreeVisitor;
import org.openrewrite.rpc.request.GetTreeDataRequest;
import org.openrewrite.rpc.request.RecipeRpcRequest;
import org.openrewrite.rpc.request.VisitRequest;
import org.openrewrite.rpc.response.RecipeRpcResponse;

import java.lang.reflect.Constructor;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.*;

import static io.moderne.jsonrpc.JsonRpcMethod.typed;

public class RecipeRpc {
    ExecutorService executorService = ForkJoinPool.commonPool();
    private final JsonRpc jsonRpc;
    private final Duration timeout;

    private final Map<UUID, SourceFile> localTrees = new HashMap<>();

    // TODO This should be keyed on both the visit (transaction) ID in addition to the tree ID
    private final Map<UUID, BlockingQueue<List<TreeDatum>>> inProgressGetTreeDatas = new HashMap<>();

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
            } finally {
                inProgressGetTreeDatas.remove(request.getTreeId());
            }

            return RecipeRpcResponse.ok();
        }));

        jsonRpc.method("getTree", typed(GetTreeDataRequest.class, request -> {
            BlockingQueue<List<TreeDatum>> q = inProgressGetTreeDatas.computeIfAbsent(request.getTreeId(), id -> {
                BlockingQueue<List<TreeDatum>> batch = new ArrayBlockingQueue<>(1);
                SourceFile before = localTrees.get(id);
                TreeDataSendQueue sendQueue = new TreeDataSendQueue(10, before, batch::put);
                executorService.submit(() -> {
                    Language.fromSourceFile(before).getSender().visit(before, sendQueue);
                    return 0;
                });
                return batch;
            });
            TreeData body = new TreeData(q.take());
            return RecipeRpcResponse.ok(body);
        }));
    }

    public RecipeRpc executor(ExecutorService executorService) {
        jsonRpc.executor(executorService);
        return this;
    }

    public RecipeRpc bind() {
        jsonRpc.bind();
        return this;
    }

    public void shutdown() {
        jsonRpc.shutdown();
    }

    public <P> Tree visit(SourceFile sourceFile, String visitorName, P p) {
        scan(sourceFile, visitorName, p);
        return getTree(sourceFile.getId(), Language.fromSourceFile(sourceFile));
    }

    public <P> void scan(SourceFile sourceFile, String visitorName, P p) {
        localTrees.put(sourceFile.getId(), sourceFile);
        Language language = Language.fromSourceFile(sourceFile);
        send("visit", new VisitRequest(visitorName, sourceFile.getId(), language, p));
    }

    private SourceFile getTree(UUID treeId, Language language) {
        TreeDataReceiveQueue q = new TreeDataReceiveQueue(() -> send("getTree",
                new GetTreeDataRequest(treeId)));
        return q.tree(language.getReceiver(), localTrees.get(treeId));
    }

    private <P> P send(String method, RecipeRpcRequest body) {
        RecipeRpcResponse<P> response;
        try {
            response = jsonRpc.send(JsonRpcRequest.newRequest(method)
                            .namedParameters(body)
                            .build())
                    .get(timeout.getSeconds(), TimeUnit.SECONDS)
                    .getResult(new TypeReference<RecipeRpcResponse<P>>() {
                    });
        } catch (ExecutionException | TimeoutException | InterruptedException e) {
            throw new RuntimeException(e);
        }

        if (!response.isSuccessful()) {
            throw new RecipeRpcException(response.getError());
        }

        return response.getBody();
    }
}
