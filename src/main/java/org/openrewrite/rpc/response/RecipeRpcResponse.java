package org.openrewrite.rpc.response;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;

import static java.util.Objects.requireNonNull;

@RequiredArgsConstructor
@Getter
public class RecipeRpcResponse<P> {
    private final P body;

    @Nullable
    private final String error;

    public boolean isSuccessful() {
        return error == null;
    }

    public String getError() {
        return requireNonNull(error);
    }

    public static RecipeRpcResponse<Integer> ok() {
        return new RecipeRpcResponse<>(0, null);
    }
}
