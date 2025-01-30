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

import lombok.EqualsAndHashCode;
import lombok.Value;

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
