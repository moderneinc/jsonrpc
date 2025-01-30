package io.moderne.jsonrpc;

import lombok.EqualsAndHashCode;
import lombok.Value;

import javax.annotation.Nullable;

@Value
@EqualsAndHashCode(callSuper = false)
public class JsonRpcError extends JsonRpcResponse {
    String id;
    Detail error;

    @Value
    public static class Detail {
        int code;
        String message;
    }

    public static JsonRpcError parseError(String id) {
        return new JsonRpcError(id, new Detail(-32700, "Parse error"));
    }

    public static JsonRpcError invalidRequest(String id, String message) {
        return new JsonRpcError(id, new Detail(-32600, "Invalid Request: " + message));
    }

    public static JsonRpcError methodNotFound(String id, String method) {
        return new JsonRpcError(id, new Detail(-32601, "Method not found: " + method));
    }

    public static JsonRpcError invalidParams(String id) {
        return new JsonRpcError(id, new Detail(-32602, "Invalid params"));
    }

    public static JsonRpcError internalError(String id, String message) {
        return new JsonRpcError(id, new Detail(-32603, "Internal error: " + message));
    }
}
