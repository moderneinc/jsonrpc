package org.openrewrite.rpc.request;

import lombok.Value;

import java.util.UUID;

@Value
public class VisitRequest implements TransactionalRecipeRpcRequest {
    UUID txId;
    String visitor;
    Object p;
}
