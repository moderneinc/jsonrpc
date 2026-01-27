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
import io.moderne.jsonrpc.formatter.MessageFormatter;
import lombok.RequiredArgsConstructor;

import java.io.*;

/**
 * This appends each JSON-RPC message with \n. It should only be used with UTF-8 text-based
 * formatters that do not emit new line characters as part of the JSON.
 */
@RequiredArgsConstructor
public class NewLineDelimitedMessageHandler implements MessageHandler {
    private final InputStream inputStream;
    private final OutputStream outputStream;

    @Override
    public JsonRpcMessage receive(MessageFormatter formatter) {
        try {
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            int b;
            while ((b = inputStream.read()) != -1) {
                buffer.write(b);
                if (b == '\n') {
                    break;
                }
            }
            return formatter.deserialize(new ByteArrayInputStream(buffer.toByteArray()));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public void send(JsonRpcMessage msg, MessageFormatter formatter) {
        try {
            formatter.serialize(msg, outputStream);
            outputStream.write(new byte[]{'\n'});
            outputStream.flush();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
