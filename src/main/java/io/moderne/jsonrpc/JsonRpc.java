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

import io.moderne.jsonrpc.formatter.JsonMessageFormatter;
import io.moderne.jsonrpc.formatter.MessageFormatter;
import io.moderne.jsonrpc.handler.MessageHandler;
import lombok.RequiredArgsConstructor;

import java.util.Map;
import java.util.concurrent.*;

@RequiredArgsConstructor
public class JsonRpc {
    private final ForkJoinPool forkJoin = new ForkJoinPool(
            4, ForkJoinPool.defaultForkJoinWorkerThreadFactory, null, true);

    private final Map<String, JsonRpcMethod<?>> methods = new ConcurrentHashMap<>();

    private volatile boolean shutdown = false;

    private final MessageHandler messageHandler;
    private final MessageFormatter formatter;
    private final Map<Object, CompletableFuture<JsonRpcSuccess>> openRequests = new ConcurrentHashMap<>();

    /**
     * @deprecated Use {@link #JsonRpc(MessageHandler, MessageFormatter)} instead.
     */
    @Deprecated
    public JsonRpc(MessageHandler messageHandler) {
        this(messageHandler, new JsonMessageFormatter());
    }

    public <P> JsonRpc rpc(String name, JsonRpcMethod<P> method) {
        methods.put(name, method);
        return this;
    }

    public CompletableFuture<JsonRpcSuccess> send(JsonRpcRequest request) {
        CompletableFuture<JsonRpcSuccess> response = new CompletableFuture<>();
        openRequests.put(request.getId(), response);
        messageHandler.send(request, formatter);
        return response;
    }

    public void notify(JsonRpcRequest request) {
        messageHandler.send(request, formatter);
    }

    public JsonRpc bind() {
        shutdown = false;
        forkJoin.submit(new RecursiveAction() {
            @Override
            protected void compute() {
                while (!shutdown) {
                    Object requestId = null;
                    try {
                        JsonRpcMessage msg = messageHandler.receive(formatter);
                        if (msg instanceof JsonRpcResponse) {
                            JsonRpcResponse response = (JsonRpcResponse) msg;
                            Object id = response.getId();
                            if (id != null) {
                                CompletableFuture<JsonRpcSuccess> responseFuture = openRequests.remove(id);
                                if (response instanceof JsonRpcError) {
                                    responseFuture.completeExceptionally(new JsonRpcException((JsonRpcError) response));
                                } else if (response instanceof JsonRpcSuccess) {
                                    responseFuture.complete((JsonRpcSuccess) response);
                                }
                            } else if (response instanceof JsonRpcError) {
                                // Error with no id — fail all open requests since we
                                // can't correlate this error to a specific one
                                JsonRpcException exception = new JsonRpcException((JsonRpcError) response);
                                for (CompletableFuture<JsonRpcSuccess> future : openRequests.values()) {
                                    future.completeExceptionally(exception);
                                }
                            }
                        } else if (msg instanceof JsonRpcRequest) {
                            JsonRpcRequest request = (JsonRpcRequest) msg;
                            requestId = request.getId();
                            JsonRpcMethod<?> method = methods.get(request.getMethod());
                            if (method == null) {
                                messageHandler.send(JsonRpcError.methodNotFound(request.getId(), request.getMethod()), formatter);
                            } else {
                                ForkJoinTask.adapt(() -> {
                                    try {
                                        Object response = method.convertAndHandle(request.getParams(), formatter);
                                        if (response != null) {
                                            messageHandler.send(new JsonRpcSuccess(request.getId(), response), formatter);
                                        } else {
                                            messageHandler.send(JsonRpcError.internalError(request.getId(), "Method returned null"), formatter);
                                        }
                                    } catch (Exception e) {
                                        messageHandler.send(JsonRpcError.internalError(request.getId(), e), formatter);
                                    }
                                }).fork();
                            }
                        }
                    } catch (Throwable t) {
                        messageHandler.send(JsonRpcError.internalError(requestId, t), formatter);
                    }
                }
            }
        });
        return this;
    }

    public void shutdown() {
        shutdown = true;
        forkJoin.shutdownNow();
    }
}
