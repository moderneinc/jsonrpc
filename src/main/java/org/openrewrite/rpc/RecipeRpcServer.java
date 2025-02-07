package org.openrewrite.rpc;

import com.fasterxml.jackson.core.type.TypeReference;
import io.moderne.jsonrpc.JsonRpc;
import io.moderne.jsonrpc.JsonRpcRequest;
import org.jspecify.annotations.Nullable;
import org.openrewrite.Cursor;
import org.openrewrite.Tree;
import org.openrewrite.rpc.request.*;
import org.openrewrite.rpc.response.RecipeRpcResponse;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * The recipe RPC server is used by the process controlling the recipe scheduler.
 */
public class RecipeRpcServer {
    private final JsonRpc jsonRpc;
    private final Duration timeout;

    /**
     * Represents the last state of each tree that was sent to (or received back from) the
     * remote process. When the remote process next interacts with the tree, if no changes
     * have been made to it, there will only be one NoChange event sent, and the remote
     * process can proceed with the tree it has locally cached.
     */
    private final Map<UUID, Tree> remoteTrees = new HashMap<>();

    public RecipeRpcServer(JsonRpc jsonRpc, Duration timeout) {
        this.jsonRpc = jsonRpc;
        this.timeout = timeout;
    }

    public <P> Tree visit(Cursor cursor, String visitorName, P p)
            throws InterruptedException {
        assert cursor.getValue() instanceof Tree;

        UUID txId = UUID.randomUUID();
        Tree tree = cursor.getValue();
        Language language = Language.fromCursor(cursor);

        send("startTreeTransaction", new StartTreeTransactionRequest(txId, tree.getId(), language));
        sendTree(txId, tree, language, remoteTrees.get(tree.getId()));
        send("visit", new VisitRequest(txId, visitorName, p));
        Tree after = receiveTree(txId, tree, language);
        send("endTreeTransaction", new EndTransaction(txId));

        // Update with the latest state of the tree.
        return remoteTrees.compute(tree.getId(), (id, before) -> after);
    }

    private void sendTree(UUID txId, Tree tree, Language language, @Nullable Tree remoteState) throws InterruptedException {
        TreeSender sender = language.getSender(remoteState, tree);

        List<TreeDatum> batch = new ArrayList<>(10);
        while (!sender.isDone()) {
            batch.add(sender.getData().take());
            if (sender.isDone() || batch.size() == 10) {
                send("setTreeData", new SetTreeDataRequest(txId, new TreeData(batch, sender.isDone())));
                batch.clear();
            }
        }
    }

    Tree receiveTree(UUID txId, Tree tree, Language language) throws InterruptedException {
        TreeReceiver receiver = language.getReceiver(tree);

        for (boolean done = false; !done; ) {
            TreeData data = send("getTreeData", new GetTreeDataRequest(txId));
            receiver.putAll(data.getData());
            done = data.isEndOfData();
        }

        return receiver.getTree();
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
