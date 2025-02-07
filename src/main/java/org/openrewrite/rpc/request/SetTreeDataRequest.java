package org.openrewrite.rpc.request;

import lombok.Value;
import org.openrewrite.rpc.TreeData;

import java.util.UUID;

@Value
public class SetTreeDataRequest implements TransactionalRecipeRpcRequest {
    UUID txId;
    TreeData treeData;
}
