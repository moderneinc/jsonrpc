package org.openrewrite.rpc.request;

import lombok.Value;

import java.util.UUID;

@Value
public class GetTreeDataRequest implements TransactionalRecipeRpcRequest {
    UUID txId;
}
