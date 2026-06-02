/*
 * Copyright 2025 the original author or authors.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * https://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.moderne.jsonrpc;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import lombok.EqualsAndHashCode;
import lombok.Value;
import org.jspecify.annotations.Nullable;

import java.io.PrintWriter;
import java.io.StringWriter;

@Value
@EqualsAndHashCode(callSuper = false)
public class JsonRpcError extends JsonRpcResponse {
    // JSON-RPC 2.0 requires error responses to carry an "id" (null when the error
    // can't be correlated to a request, e.g. an internal error on a background
    // thread). Without ALWAYS, a global NON_NULL inclusion omits a null id, and
    // strict peers (e.g. StreamJsonRpc on the .NET side) reject the frame with
    // "id property missing" and abort — masking the real error.
    @JsonInclude(JsonInclude.Include.ALWAYS)
    @JsonDeserialize(using = JsonRpcIdDeserializer.class)
    Object id;

    Detail error;

    @Value
    public static class Detail {
        int code;
        String message;

        @Nullable
        String data;
    }

    public static JsonRpcError parseError(Object id) {
        return new JsonRpcError(id, new Detail(-32700, "Parse error", null));
    }

    public static JsonRpcError invalidRequest(Object id, String message) {
        return new JsonRpcError(id, new Detail(-32600, "Invalid Request: " + message, null));
    }

    public static JsonRpcError methodNotFound(Object id, String method) {
        return new JsonRpcError(id, new Detail(-32601, "Method not found: " + method, null));
    }

    public static JsonRpcError invalidParams(Object id) {
        return new JsonRpcError(id, new Detail(-32602, "Invalid params", null));
    }

    public static JsonRpcError internalError(Object id, String message) {
        return new JsonRpcError(id, new Detail(-32603, "Internal error: " + message, null));
    }

    public static JsonRpcError internalError(Object id, Throwable t) {
        StringWriter sw = new StringWriter();
        t.printStackTrace(new PrintWriter(sw));
        return new JsonRpcError(id, new Detail(-32603, "Internal error: " + t.getMessage(), sw.toString()));
    }
}
