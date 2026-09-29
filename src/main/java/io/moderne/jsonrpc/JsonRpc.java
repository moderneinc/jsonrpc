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

import io.micrometer.core.instrument.Metrics;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.Timer;
import io.moderne.jsonrpc.formatter.JsonMessageFormatter;
import io.moderne.jsonrpc.formatter.MessageFormatter;
import io.moderne.jsonrpc.handler.MessageHandler;
import org.jspecify.annotations.Nullable;

import java.io.EOFException;
import java.io.IOException;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;

public class JsonRpc {
    private final ForkJoinPool forkJoin = new ForkJoinPool(
            4, ForkJoinPool.defaultForkJoinWorkerThreadFactory, null, true);

    private final Map<String, JsonRpcMethod<?>> methods = new ConcurrentHashMap<>();

    private volatile boolean shutdown;

    /**
     * Why the reader loop stopped, which every later {@link #send} fails with.
     */
    private volatile @Nullable Throwable failure;

    private final MessageHandler messageHandler;
    private final MessageFormatter formatter;
    private final Map<Object, OpenRequest> openRequests = new ConcurrentHashMap<>();

    private volatile Iterable<Tag> tags = Tags.empty();

    /**
     * @deprecated Use {@link #JsonRpc(MessageHandler, MessageFormatter)} instead.
     */
    @Deprecated
    public JsonRpc(MessageHandler messageHandler) {
        this(messageHandler, new JsonMessageFormatter());
    }

    public JsonRpc(MessageHandler messageHandler, MessageFormatter formatter) {
        this.messageHandler = messageHandler;
        this.formatter = formatter;
    }

    public <P> JsonRpc rpc(String name, JsonRpcMethod<P> method) {
        methods.put(name, method);
        return this;
    }

    /**
     * Added to the {@code method} and {@code outcome} tags of the timers this records for the
     * requests it sends ({@code jsonrpc.client.requests}) and handles
     * ({@code jsonrpc.server.requests}).
     */
    public JsonRpc tags(Iterable<Tag> tags) {
        this.tags = tags;
        return this;
    }

    public CompletableFuture<JsonRpcSuccess> send(JsonRpcRequest request) {
        OpenRequest open = new OpenRequest(request.getMethod(), tags);
        openRequests.put(request.getId(), open);
        if (shutdown) {
            // Reader loop already exited (peer EOF or explicit shutdown) and
            // may have drained openRequests before our put. Fail the future
            // ourselves; completeExceptionally is a no-op if the reader did
            // observe our entry and failed it first.
            openRequests.remove(request.getId());
            Throwable cause = failure;
            open.fail(cause != null ? cause : peerClosed(), Outcome.CLOSED);
            return open.response;
        }
        messageHandler.send(request, formatter);
        return open.response;
    }

    public void notify(JsonRpcRequest request) {
        messageHandler.send(request, formatter);
    }

    public JsonRpc bind() {
        failure = null;
        shutdown = false;
        forkJoin.submit(new RecursiveAction() {
            @Override
            protected void compute() {
                try {
                    readUntilShutdown();
                } catch (Throwable t) {
                    // Failing inside a catch below (an allocation under memory pressure) must
                    // still release the requests waiting on this loop.
                    fail(t);
                }
            }
        });
        return this;
    }

    private void readUntilShutdown() {
        while (!shutdown) {
            Object requestId = null;
            try {
                JsonRpcMessage msg = messageHandler.receive(formatter);
                if (msg instanceof JsonRpcResponse) {
                    JsonRpcResponse response = (JsonRpcResponse) msg;
                    Object id = response.getId();
                    if (id != null) {
                        OpenRequest open = openRequests.remove(id);
                        if (response instanceof JsonRpcError) {
                            open.fail(new JsonRpcException((JsonRpcError) response), Outcome.ERROR);
                        } else if (response instanceof JsonRpcSuccess) {
                            open.succeed((JsonRpcSuccess) response);
                        }
                    } else if (response instanceof JsonRpcError && !openRequests.isEmpty()) {
                        // Error with no id — fail all open requests since we
                        // can't correlate this error to a specific one. Skip
                        // when there's nothing to fail; allocating a Throwable
                        // (and filling its stack) per malformed message is
                        // expensive enough to peg a CPU when an upstream peer
                        // emits non-RPC noise on the wire.
                        JsonRpcException exception = new JsonRpcException((JsonRpcError) response);
                        for (OpenRequest open : openRequests.values()) {
                            open.fail(exception, Outcome.ERROR);
                        }
                    }
                } else if (msg instanceof JsonRpcRequest) {
                    JsonRpcRequest request = (JsonRpcRequest) msg;
                    requestId = request.getId();
                    JsonRpcMethod<?> method = methods.get(request.getMethod());
                    if (method == null) {
                        // Fork error sends off the reader thread to avoid
                        // deadlock with synchronized send()
                        Object errorId = request.getId();
                        String errorMethod = request.getMethod();
                        ForkJoinTask.adapt(() ->
                                messageHandler.send(JsonRpcError.methodNotFound(errorId, errorMethod), formatter)
                        ).fork();
                    } else {
                        ForkJoinTask.adapt(() -> dispatch(request, method)).fork();
                    }
                }
            } catch (EOFException e) {
                // Peer closed the stream — there's nothing more to read.
                fail(peerClosed());
            } catch (JsonRpcReceiveException e) {
                // Frame- or parse-level failure on an inbound message.
                // Send the error back to the peer; do NOT touch
                // openRequests — those track responses we're waiting
                // for from the peer, and the peer's malformed message
                // is not one of them. Treating it as one would either
                // complete an unrelated future on id collision, or
                // (worse, on null id) fail every open request at once.
                JsonRpcError errorToPeer = e.toError();
                ForkJoinTask.adapt(() ->
                        messageHandler.send(errorToPeer, formatter)
                ).fork();
            } catch (IOException | Error e) {
                // A broken transport, or this JVM out of memory mid-read, leaves the
                // stream unreadable: the requests waiting on it would wait forever.
                fail(e);
            } catch (Throwable t) {
                // Fork error sends off the reader thread to avoid
                // deadlock with synchronized send()
                Object errorReqId = requestId;
                Throwable errorT = t;
                ForkJoinTask.adapt(() ->
                        messageHandler.send(JsonRpcError.internalError(errorReqId, errorT), formatter)
                ).fork();
            }
        }
    }

    /**
     * Stop reading and fail every request still waiting on a response. Shutdown is set
     * first so a concurrent {@link #send} observes it and fails its own future after the
     * put; otherwise a request registered after this drain would be stranded.
     */
    private void fail(Throwable cause) {
        failure = cause;
        shutdown = true;
        for (OpenRequest open : openRequests.values()) {
            open.fail(cause, Outcome.CLOSED);
        }
        openRequests.clear();
    }

    private static JsonRpcException peerClosed() {
        return new JsonRpcException(JsonRpcError.internalError(null, "JSON-RPC peer closed the stream"));
    }

    private void dispatch(JsonRpcRequest request, JsonRpcMethod<?> method) {
        Timer.Sample sample = Timer.start(Metrics.globalRegistry);
        Outcome outcome = Outcome.ERROR;
        JsonRpcMessage outbound;
        try {
            Object result = method.convertAndHandle(request.getParams(), formatter);
            // Wrap the handler's return value so the on-wire representation
            // goes through the same RawJson + Jackson serializer pipeline
            // as inbound-converted values.
            if (result != null) {
                outbound = new JsonRpcSuccess(request.getId(), RawJson.of(result));
                outcome = Outcome.SUCCESS;
            } else {
                outbound = JsonRpcError.internalError(request.getId(), "Method returned null");
            }
        } catch (Throwable t) {
            // Errors included: the peer is blocked on this reply.
            outbound = JsonRpcError.internalError(request.getId(), t);
        }
        try {
            messageHandler.send(outbound, formatter);
        } finally {
            record(sample, "jsonrpc.server.requests", tags, request.getMethod(), outcome);
        }
    }

    public void shutdown() {
        shutdown = true;
        forkJoin.shutdownNow();
    }

    /**
     * A request awaiting its response, which records its outcome once. The reader and
     * {@link #fail(Throwable)} say how it ended; the response completing any other way is the
     * caller giving up on it.
     */
    private static final class OpenRequest {
        private static final AtomicIntegerFieldUpdater<OpenRequest> REPORTED =
                AtomicIntegerFieldUpdater.newUpdater(OpenRequest.class, "reported");

        final CompletableFuture<JsonRpcSuccess> response = new CompletableFuture<>();
        private final String method;
        private final Iterable<Tag> tags;
        private final Timer.Sample sample = Timer.start(Metrics.globalRegistry);
        private volatile int reported;

        OpenRequest(String method, Iterable<Tag> tags) {
            this.method = method;
            this.tags = tags;
            response.whenComplete((result, t) -> report(
                    t == null ? Outcome.SUCCESS :
                            t instanceof TimeoutException ? Outcome.TIMEOUT :
                                    t instanceof CancellationException ? Outcome.CANCELLED :
                                            Outcome.ERROR));
        }

        void succeed(JsonRpcSuccess success) {
            report(Outcome.SUCCESS);
            response.complete(success);
        }

        void fail(Throwable cause, Outcome outcome) {
            report(outcome);
            response.completeExceptionally(cause);
        }

        private void report(Outcome outcome) {
            if (REPORTED.compareAndSet(this, 0, 1)) {
                record(sample, "jsonrpc.client.requests", tags, method, outcome);
            }
        }
    }

    private static void record(Timer.Sample sample, String name, Iterable<Tag> tags, String method, Outcome outcome) {
        sample.stop(Timer.builder(name)
                .tags(tags)
                .tag("method", method)
                .tag("outcome", outcome.tag)
                .register(Metrics.globalRegistry));
    }

    private enum Outcome {
        SUCCESS, ERROR, CLOSED, TIMEOUT, CANCELLED;

        private final String tag = name().toLowerCase(Locale.ROOT);
    }
}
