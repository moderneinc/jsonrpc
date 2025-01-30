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
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class MeteredMessageHandler implements MessageHandler {
    private final MessageHandler delegate;
    private final MeterRegistry meterRegistry;

    @Override
    public JsonRpcMessage receive() {
        Timer.Sample sample = Timer.start(meterRegistry);
        Timer.Builder timer = Timer.builder("jsonrpc.receive")
                .description("Time taken to receive a JSON-RPC message")
                .tag("direction", "received");
        JsonRpcMessage msg = delegate.receive();
        finishTimer(msg, sample, timer);
        return msg;
    }

    @Override
    public void send(JsonRpcMessage msg) {
        Timer.Sample sample = Timer.start(meterRegistry);
        Timer.Builder timer = Timer.builder("jsonrpc.send")
                .description("Time taken to send a JSON-RPC message")
                .tag("direction", "sent");
        delegate.send(msg);
        finishTimer(msg, sample, timer);
    }

    private void finishTimer(JsonRpcMessage msg, Timer.Sample sample, Timer.Builder timer) {
        timer = timer.tag("handler", delegate.getClass().getSimpleName());
        if (msg instanceof JsonRpcSuccess) {
            sample.stop(timer
                    .tag("type", "response")
                    .tags("error", "none")
                    .register(meterRegistry));
        } else if (msg instanceof JsonRpcRequest) {
            sample.stop(timer
                    .tag("type", "request")
                    .tag("error", "none")
                    .register(meterRegistry));
        } else if (msg instanceof JsonRpcError) {
            sample.stop(timer
                    .tag("type", "error")
                    .tag("error", Integer.toString(((JsonRpcError) msg).getError().getCode()))
                    .register(meterRegistry));
        }
    }
}
