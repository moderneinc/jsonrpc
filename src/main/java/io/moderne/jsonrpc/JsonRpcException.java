package io.moderne.jsonrpc;

import lombok.Getter;

@Getter
public class JsonRpcException extends Throwable {
    private final JsonRpcError error;

    public JsonRpcException(JsonRpcError error) {
        super("{code=" + error.getError().getCode() +
              ", message='" + error.getError().getMessage() + "'}");
        this.error = error;
    }
}
