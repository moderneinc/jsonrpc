package org.openrewrite.rpc;

public class RecipeRpcException extends RuntimeException {
    public RecipeRpcException(String message) {
        super(message);
    }
}
