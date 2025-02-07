package org.openrewrite.rpc.request;

import lombok.Value;
import org.openrewrite.rpc.Language;

import java.util.UUID;

@Value
public class StartTreeTransactionRequest implements TransactionalRecipeRpcRequest {
    UUID txId;
    UUID treeId;
    Language language;
}
