package org.openrewrite.rpc.request;

import lombok.Value;

import java.util.UUID;

@Value
public class GetTreeDataRequest implements RecipeRpcRequest {
    UUID treeId;
}
