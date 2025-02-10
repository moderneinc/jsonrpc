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

import io.moderne.jsonrpc.JsonRpcMessage;
import io.moderne.jsonrpc.JsonRpcRequest;
import io.moderne.jsonrpc.JsonRpcResponse;
import lombok.RequiredArgsConstructor;

import java.io.PrintStream;

@RequiredArgsConstructor
public class TraceMessageHandler implements MessageHandler {
    private final String name;
    private final MessageHandler delegate;
    private final PrintStream out;

    public TraceMessageHandler(String name, MessageHandler delegate) {
        this(name, delegate, System.out);
    }

    @Override
    public JsonRpcMessage receive() {
        JsonRpcMessage message = delegate.receive();
        if (message instanceof JsonRpcResponse) {
            out.printf("<-(%s)- %s%n", name, message);
        }
        return message;
    }

    @Override
    public void send(JsonRpcMessage message) {
        if (message instanceof JsonRpcRequest) {
            out.printf("-(%s)-> %s%n", name, message);
        }
        delegate.send(message);
    }
}
