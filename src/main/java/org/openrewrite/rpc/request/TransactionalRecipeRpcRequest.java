package org.openrewrite.rpc.request;

import java.util.UUID;

public interface TransactionalRecipeRpcRequest extends RecipeRpcRequest {
    UUID getTxId();
}
