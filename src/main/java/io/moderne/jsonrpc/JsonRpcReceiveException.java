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

import org.jspecify.annotations.Nullable;

import java.io.IOException;

/**
 * Thrown by a {@link io.moderne.jsonrpc.handler.MessageHandler} when an inbound
 * message cannot be parsed or framed. Distinct from a {@link JsonRpcError}
 * returned over the wire by a peer: this represents a local frame-level failure
 * that should be reported back to the peer as an error response, not dispatched
 * through the open-request correlation map. Mixing the two paths can complete
 * an unrelated open client future on a malformed inbound request.
 */
public class JsonRpcReceiveException extends IOException {
    private final @Nullable Object id;
    private final JsonRpcError.Detail detail;

    public JsonRpcReceiveException(@Nullable Object id, JsonRpcError.Detail detail) {
        super(detail.getMessage());
        this.id = id;
        this.detail = detail;
    }

    public JsonRpcError toError() {
        return new JsonRpcError(id, detail);
    }

    public static JsonRpcError.Detail invalidRequestDetail(@Nullable String message) {
        return new JsonRpcError.Detail(-32600, "Invalid Request: " + message, null);
    }
}
