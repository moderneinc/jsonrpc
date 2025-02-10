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

import io.moderne.jsonrpc.handler.MessageHandler;
import lombok.RequiredArgsConstructor;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@RequiredArgsConstructor
public class JsonRpc {
    private final Map<String, JsonRpcMethod> methods = new ConcurrentHashMap<>();

    private volatile boolean shutdown = false;

    private final MessageHandler messageHandler;
    private final Map<String, CompletableFuture<JsonRpcSuccess>> openRequests = new ConcurrentHashMap<>();
    private ExecutorService executorService = Executors.newSingleThreadExecutor();

    public JsonRpc method(String name, JsonRpcMethod method) {
        methods.put(name, method);
        return this;
    }

    public JsonRpc executor(ExecutorService executorService) {
        this.executorService = executorService;
        return this;
    }

    public CompletableFuture<JsonRpcSuccess> send(JsonRpcRequest request) {
        CompletableFuture<JsonRpcSuccess> response = new CompletableFuture<>();
        openRequests.put(request.getId(), response);
        messageHandler.send(request);
        return response;
    }

    public void notification(JsonRpcRequest request) {
        messageHandler.send(request);
    }

    public JsonRpc bind() {
        shutdown = false;
        executorService.submit(() -> {
            while (!shutdown) {
                JsonRpcMessage msg = messageHandler.receive();
                if (msg instanceof JsonRpcResponse) {
                    JsonRpcResponse response = (JsonRpcResponse) msg;
                    CompletableFuture<JsonRpcSuccess> responseFuture = openRequests.remove(response.getId());
                    if (response instanceof JsonRpcError) {
                        responseFuture.completeExceptionally(new JsonRpcException((JsonRpcError) response));
                    } else if (response instanceof JsonRpcSuccess) {
                        responseFuture.complete((JsonRpcSuccess) response);
                    }
                } else if (msg instanceof JsonRpcRequest) {
                    JsonRpcRequest request = (JsonRpcRequest) msg;
                    JsonRpcMethod method = methods.get(request.getMethod());
                    if (method == null) {
                        messageHandler.send(JsonRpcError.methodNotFound(request.getId(), request.getMethod()));
                    } else {
                        try {
                            Object response = method.handle(request.getParams());
                            if (response != null) {
                                messageHandler.send(new JsonRpcSuccess(request.getId(), response));
                            }
                        } catch (Exception e) {
                            messageHandler.send(JsonRpcError.internalError(request.getId(), e.getMessage()));
                        }
                    }
                }
            }
        });
        return this;
    }

    public void shutdown() {
        shutdown = true;
    }
}
