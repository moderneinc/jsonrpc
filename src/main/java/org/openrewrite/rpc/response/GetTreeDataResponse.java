package org.openrewrite.rpc.response;

import org.jspecify.annotations.Nullable;
import org.openrewrite.rpc.TreeData;

public class GetTreeDataResponse extends RecipeRpcResponse<TreeData> {
    public GetTreeDataResponse(TreeData body, @Nullable String error) {
        super(body, error);
    }
}
