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
import io.moderne.jsonrpc.formatter.MessageFormatter;
import io.moderne.jsonrpc.internal.LimitedInputStream;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;

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
    private static final ThreadLocal<ByteArrayOutputStream> SEND_BUFFER =
            ThreadLocal.withInitial(() -> new ByteArrayOutputStream(8192));

    private final InputStream inputStream;
    private final OutputStream outputStream;

    /**
     * Formatter stored for backwards compatibility with deprecated methods.
     */
    @Deprecated
    @SuppressWarnings("DeprecatedIsStillUsed")
    private @Nullable MessageFormatter formatter;

    /**
     * @param formatter the formatter to use for serialization/deserialization
     * @param inputStream the input stream to read messages from
     * @param outputStream the output stream to write messages to
     * @deprecated The formatter is now passed to individual receive/send calls.
     * Use the two-argument constructor instead.
     */
    @Deprecated
    public HeaderDelimitedMessageHandler(MessageFormatter formatter, InputStream inputStream, OutputStream outputStream) {
        this(inputStream, outputStream);
        this.formatter = formatter;
    }

    @Override
    public JsonRpcMessage receive(MessageFormatter formatter) {
        MessageFormatter effectiveFormatter = this.formatter != null ? this.formatter : formatter;
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

            int length = Integer.parseInt(contentLengthMatcher.group(1));
            LimitedInputStream limited = new LimitedInputStream(inputStream, length);
            JsonRpcMessage msg = effectiveFormatter.deserialize(limited);
            limited.skipRemaining();
            return msg;
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
            } else if (c == '\r') {
                continue;
            }
            sb.append((char) c);
        }
        return sb.toString();
    }

    @Override
    public void send(JsonRpcMessage msg, MessageFormatter formatter) {
        MessageFormatter effectiveFormatter = this.formatter != null ? this.formatter : formatter;
        try {
            ByteArrayOutputStream buffer = SEND_BUFFER.get();
            buffer.reset();
            effectiveFormatter.serialize(msg, buffer);
            outputStream.write(("Content-Length: " + buffer.size() + "\r\n").getBytes());
            if (effectiveFormatter.getEncoding() != StandardCharsets.UTF_8) {
                outputStream.write(("Content-Type: application/vscode-jsonrpc;charset=" + effectiveFormatter.getEncoding().name() + "\r\n").getBytes());
            }
            outputStream.write('\r');
            outputStream.write('\n');
            buffer.writeTo(outputStream);
            outputStream.flush();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
