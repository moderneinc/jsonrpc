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
package io.moderne.jsonrpc.handler;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.moderne.jsonrpc.JsonRpcError;
import io.moderne.jsonrpc.JsonRpcMessage;
import io.moderne.jsonrpc.JsonRpcRequest;
import io.moderne.jsonrpc.JsonRpcSuccess;
import io.moderne.jsonrpc.formatter.MessageFormatter;

import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;

public class MeteredMessageHandler implements MessageHandler {
    /**
     * Cap on distinct error-code Timer entries per direction. Beyond this,
     * unrecognized codes share a single {@code error="other"} bucket — the
     * library is published and consumed by external clients whose error
     * codes are not bounded by spec, so an unbounded tag set could explode
     * cardinality in the consumer's metrics backend.
     */
    private static final int ERROR_TIMER_CAP = 64;

    private final MessageHandler delegate;
    private final MeterRegistry meterRegistry;

    // The four fixed-tag Timers covering every non-error message: pre-built
    // once at construction so the hot path does no Timer.Builder allocation
    // and no per-call tag-string assembly.
    private final Timer receivedRequest;
    private final Timer receivedResponse;
    private final Timer sentRequest;
    private final Timer sentResponse;

    // Error Timers are keyed (direction, code). Lazy-built up to
    // ERROR_TIMER_CAP entries; further codes fold into a single "other"
    // bucket per direction.
    private final ConcurrentHashMap<Long, Timer> errorTimers = new ConcurrentHashMap<>();

    public MeteredMessageHandler(MessageHandler delegate, MeterRegistry meterRegistry) {
        this.delegate = delegate;
        this.meterRegistry = meterRegistry;
        String handler = delegate.getClass().getSimpleName();
        this.receivedRequest = buildTimer("jsonrpc.receive", "received", "request", "none", handler);
        this.receivedResponse = buildTimer("jsonrpc.receive", "received", "response", "none", handler);
        this.sentRequest = buildTimer("jsonrpc.send", "sent", "request", "none", handler);
        this.sentResponse = buildTimer("jsonrpc.send", "sent", "response", "none", handler);
    }

    @Override
    public JsonRpcMessage receive(MessageFormatter formatter) throws IOException {
        Timer.Sample sample = Timer.start(meterRegistry);
        JsonRpcMessage msg = delegate.receive(formatter);
        Timer timer = timerFor(true, msg);
        if (timer != null) {
            sample.stop(timer);
        }
        return msg;
    }

    @Override
    public void send(JsonRpcMessage msg, MessageFormatter formatter) {
        Timer.Sample sample = Timer.start(meterRegistry);
        delegate.send(msg, formatter);
        Timer timer = timerFor(false, msg);
        if (timer != null) {
            sample.stop(timer);
        }
    }

    private Timer timerFor(boolean received, JsonRpcMessage msg) {
        if (msg instanceof JsonRpcSuccess) {
            return received ? receivedResponse : sentResponse;
        }
        if (msg instanceof JsonRpcRequest) {
            return received ? receivedRequest : sentRequest;
        }
        if (msg instanceof JsonRpcError) {
            return errorTimer(received, ((JsonRpcError) msg).getError().getCode());
        }
        return null;
    }

    private Timer errorTimer(boolean received, int code) {
        // Pack (direction, code) into a single long key — avoids allocating
        // a String key on every call (this method is on the receive/send hot
        // path and ConcurrentHashMap.get does no work for primitive-Long
        // boxes pulled from the Long cache for small values).
        long key = ((long) (received ? 1 : 0) << 32) | (code & 0xFFFFFFFFL);
        Timer cached = errorTimers.get(key);
        if (cached != null) {
            return cached;
        }
        // Cap reached — fold this code into the "other" bucket for this
        // direction (key uses code=Integer.MIN_VALUE as the sentinel so it
        // can't collide with a legitimate code).
        if (errorTimers.size() >= ERROR_TIMER_CAP) {
            long otherKey = ((long) (received ? 1 : 0) << 32) | (Integer.MIN_VALUE & 0xFFFFFFFFL);
            return errorTimers.computeIfAbsent(otherKey, k ->
                    buildTimer(received ? "jsonrpc.receive" : "jsonrpc.send",
                            received ? "received" : "sent",
                            "error",
                            "other",
                            delegate.getClass().getSimpleName()));
        }
        return errorTimers.computeIfAbsent(key, k ->
                buildTimer(received ? "jsonrpc.receive" : "jsonrpc.send",
                        received ? "received" : "sent",
                        "error",
                        Integer.toString(code),
                        delegate.getClass().getSimpleName()));
    }

    private Timer buildTimer(String name, String direction, String type, String errorTag, String handler) {
        String description = "jsonrpc.receive".equals(name)
                ? "Time taken to receive a JSON-RPC message"
                : "Time taken to send a JSON-RPC message";
        return Timer.builder(name)
                .description(description)
                .tag("direction", direction)
                .tag("type", type)
                .tag("error", errorTag)
                .tag("handler", handler)
                .register(meterRegistry);
    }
}
