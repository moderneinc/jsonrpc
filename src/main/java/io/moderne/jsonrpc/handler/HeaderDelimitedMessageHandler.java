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

import io.moderne.jsonrpc.JsonRpcError;
import io.moderne.jsonrpc.JsonRpcMessage;
import io.moderne.jsonrpc.formatter.JsonMessageFormatter;
import io.moderne.jsonrpc.formatter.MessageFormatter;
import lombok.RequiredArgsConstructor;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * This handler is compatible with the
 * <a href="https://www.npmjs.com/package/vscode-jsonrpc">vscode-jsonrpc</a> NPM package.
 * It utilizes HTTP-like headers to introduce each JSON-RPC message by describing its
 * length and (optionally) its text encoding.
 */
@RequiredArgsConstructor
public class HeaderDelimitedMessageHandler implements MessageHandler {
    private static final Pattern CONTENT_LENGTH = Pattern.compile("Content-Length: (\\d+)");

    private final MessageFormatter formatter;
    private final InputStream inputStream;
    private final OutputStream outputStream;

    public HeaderDelimitedMessageHandler(InputStream inputStream, OutputStream outputStream) {
        this.formatter = new JsonMessageFormatter();
        this.inputStream = inputStream;
        this.outputStream = outputStream;
    }

    @Override
    public JsonRpcMessage receive() {
        try {
            String contentLength = readLineFromInputStream();
            Matcher contentLengthMatcher = CONTENT_LENGTH.matcher(contentLength);
            if (!contentLengthMatcher.matches()) {
                return JsonRpcError.invalidRequest(null,
                        "Expected Content-Length header but received '" + contentLength + "'");
            }

            String contentType = readLineFromInputStream();
            if (!contentType.isEmpty()) {
                if (!contentType.startsWith("Content-Type")) {
                    return JsonRpcError.invalidRequest(null,
                            "Expected Content-Type header but received '" + contentType + "'");
                }
                // now the next line should be an empty line
                if (!readLineFromInputStream().isEmpty()) {
                    return JsonRpcError.invalidRequest(null, "Expected empty line after headers");
                }
            }

            byte[] content = new byte[Integer.parseInt(contentLengthMatcher.group(1))];
            ByteArrayInputStream bis = new ByteArrayInputStream(content);
            if (inputStream.read(content) != content.length) {
                return JsonRpcError.invalidRequest(null, "Content length mismatch");
            }
            return formatter.deserialize(bis);
        } catch (IOException e) {
            return JsonRpcError.invalidRequest(null, e.getMessage());
        }
    }

    private String readLineFromInputStream() throws IOException {
        StringBuilder sb = new StringBuilder();
        int c;
        while ((c = inputStream.read()) != -1) {
            if (c == '\n') {
                break;
            }
            sb.append((char) c);
        }
        return sb.toString();
    }

    @Override
    public void send(JsonRpcMessage msg) {
        try {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            formatter.serialize(msg, bos);
            byte[] content = bos.toByteArray();
            outputStream.write(("Content-Length: " + content.length + "\n").getBytes());
            if (formatter.getEncoding() != StandardCharsets.UTF_8) {
                outputStream.write(("Content-Type: application/vscode-jsonrpc;charset=" + formatter.getEncoding().name() + "\n").getBytes());
            }
            outputStream.write('\n');
            outputStream.write(content);
            outputStream.flush();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
