package org.openrewrite.rpc;

import io.moderne.jsonrpc.JsonRpc;
import lombok.Getter;
import org.jspecify.annotations.Nullable;
import org.openrewrite.Tree;
import org.openrewrite.TreeVisitor;
import org.openrewrite.rpc.request.*;
import org.openrewrite.rpc.response.GetTreeDataResponse;
import org.openrewrite.rpc.response.RecipeRpcResponse;

import java.lang.reflect.Constructor;
import java.util.*;

import static io.moderne.jsonrpc.JsonRpcMethod.typed;
import static java.util.Objects.requireNonNull;

@Getter
public class RecipeRpcClient {
    private final JsonRpc jsonRpc;
    private final Map<UUID, Tree> remoteTrees = new HashMap<>();
    private final Map<UUID, TreeTransaction> transactions = new HashMap<>();

    public RecipeRpcClient(JsonRpc jsonRpc) {
        this.jsonRpc = jsonRpc;

        jsonRpc.method("startTreeTransaction", typed(StartTreeTransactionRequest.class, request -> {
            transactions.put(request.getTxId(), new TreeTransaction(request.getLanguage(),
                    remoteTrees.get(request.getTreeId())));
            return RecipeRpcResponse.ok();
        }));

        jsonRpc.method("setTreeData", typed(SetTreeDataRequest.class, request -> {
            TreeTransaction tx = transactions.get(request.getTxId());
            tx.receiveData(request.getTreeData());
            return RecipeRpcResponse.ok();
        }));

        jsonRpc.method("visit", typed(VisitRequest.class, request -> {
            TreeTransaction tx = transactions.get(request.getTxId());

            Constructor<?> ctor = Class.forName(request.getVisitor()).getDeclaredConstructor();
            ctor.setAccessible(true);

            //noinspection unchecked
            TreeVisitor<Tree, Object> visitor = (TreeVisitor<Tree, Object>) ctor.newInstance();
            Tree before = tx.getRemoteState();
            Tree after = visitor.visit(before, request.getP());
            if (after == null) {
                remoteTrees.remove(before.getId());
            } else {
                remoteTrees.put(after.getId(), after);
            }
            tx.after(after);
            return RecipeRpcResponse.ok();
        }));

        jsonRpc.method("getTreeData", typed(GetTreeDataRequest.class, request -> {
            TreeTransaction tx = transactions.get(request.getTxId());
            return new GetTreeDataResponse(tx.sendData(), null);
        }));

        jsonRpc.method("endTransaction", typed(EndTransaction.class, request -> {
            transactions.remove(request.getTxId());
            return RecipeRpcResponse.ok();
        }));
    }

    public RecipeRpcClient start() {
        jsonRpc.start();
        return this;
    }

    /**
     * A transaction state machine for an action on a {@link Tree}.
     * <ol>
     *     <li>The transaction is initialized with the local state of the tree (if any).</li>
     *     <li>The remote state from the server is received.<li>
     *     <li>An action is taken and the sender is initialized to send the new state back to the remote process.</li>
     *     <li>At the remote process' request, the new state is sent back.</li>
     * </ol>
     */
    public static class TreeTransaction {
        private final Language language;

        @Getter
        private final TreeReceiver receiver;

        private @Nullable Tree remoteState;
        private @Nullable TreeSender sender;

        public TreeTransaction(Language language, @Nullable Tree localState) {
            this.language = language;
            this.receiver = language.getReceiver(localState);
        }

        public void receiveData(TreeData treeData) throws Exception {
            receiver.putAll(treeData.getData());
            if (treeData.isEndOfData()) {
                remoteState = receiver.getTree();
            }
        }

        public TreeData sendData() throws InterruptedException {
            if (sender == null) {
                throw new IllegalStateException("After state must be set before sending data");
            }
            List<TreeDatum> batch = new ArrayList<>(10);
            while (!sender.isDone()) {
                batch.add(sender.getData().take());
                if (sender.isDone() || batch.size() == 10) {
                    return new TreeData(batch, sender.isDone());
                }
            }
            throw new IllegalStateException("No data to send because sender is already done");
        }

        public Tree getRemoteState() {
            return requireNonNull(remoteState);
        }

        public void after(@Nullable Tree after) {
            if (remoteState == null) {
                throw new IllegalStateException("Remote state must be fully received before " +
                                                "taking an action on the tree");
            }
            this.sender = language.getSender(remoteState, after);
        }
    }
}
