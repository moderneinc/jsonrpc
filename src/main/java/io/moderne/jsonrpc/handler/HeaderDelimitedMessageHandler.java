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
import io.moderne.jsonrpc.JsonRpcReceiveException;
import io.moderne.jsonrpc.formatter.MessageFormatter;
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

    private final InputStream inputStream;
    private final OutputStream outputStream;

    /**
     * Formatter stored for backwards compatibility with deprecated methods.
     */
    @Deprecated
    @SuppressWarnings("DeprecatedIsStillUsed")
    private @Nullable MessageFormatter formatter;

    /**
     * @param formatter    the formatter to use for serialization/deserialization
     * @param inputStream  the input stream to read messages from
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
    public JsonRpcMessage receive(MessageFormatter formatter) throws IOException {
        MessageFormatter effectiveFormatter = this.formatter != null ? this.formatter : formatter;
        byte[] content = null;
        try {
            // readLineFromInputStream throws EOFException when the peer has closed
            // the stream cleanly between messages; let that propagate so the reader
            // loop can exit instead of treating EOF as a malformed message and
            // spinning at full CPU constructing exceptions for every empty read.
            String contentLength = readLineFromInputStream();
            Matcher contentLengthMatcher = CONTENT_LENGTH.matcher(contentLength);
            if (!contentLengthMatcher.matches()) {
                throw new JsonRpcReceiveException(null, JsonRpcReceiveException.invalidRequestDetail(
                        "Expected Content-Length header but received '" + contentLength + "'"));
            }

            String contentType = readLineFromInputStream();
            if (!contentType.isEmpty()) {
                if (!contentType.startsWith("Content-Type")) {
                    throw new JsonRpcReceiveException(null, JsonRpcReceiveException.invalidRequestDetail(
                            "Expected Content-Type header but received '" + contentType + "'"));
                }
                // now the next line should be an empty line
                if (!readLineFromInputStream().isEmpty()) {
                    throw new JsonRpcReceiveException(null,
                            JsonRpcReceiveException.invalidRequestDetail("Expected empty line after headers"));
                }
            }

            content = new byte[Integer.parseInt(contentLengthMatcher.group(1))];
            for (int totalRead = 0; totalRead < content.length; ) {
                int bytesRead = inputStream.read(content, totalRead, content.length - totalRead);
                if (bytesRead == -1) {
                    // Mid-message EOF — treat as a closed stream rather than a
                    // recoverable parse error, otherwise the loop spins on the
                    // already-closed pipe.
                    throw new EOFException("Stream closed mid-message after " + totalRead +
                            " of " + content.length + " bytes");
                }
                totalRead += bytesRead;
            }

            ByteArrayInputStream bis = new ByteArrayInputStream(content);
            return effectiveFormatter.deserialize(bis);
        } catch (EOFException | JsonRpcReceiveException e) {
            throw e;
        } catch (IOException e) {
            // Frame- or parse-level failure on an inbound message. Surface it
            // as JsonRpcReceiveException so JsonRpc.bind() routes it back to
            // the peer rather than completing an unrelated open client future
            // (whose id might collide with the extracted id, or trigger the
            // null-id "fail all open requests" branch).
            throw new JsonRpcReceiveException(IdExtractor.extractId(content),
                    JsonRpcReceiveException.invalidRequestDetail(e.getMessage()));
        }
    }

    private String readLineFromInputStream() throws IOException {
        StringBuilder sb = new StringBuilder();
        int c = inputStream.read();
        if (c == -1) {
            // EOF before any byte was read: peer closed the stream between
            // messages. Surface as EOFException so the reader loop can shut
            // down instead of returning an empty string that the caller would
            // misinterpret as a malformed header.
            throw new EOFException("Stream closed");
        }
        do {
            if (c == '\n') {
                break;
            } else if (c != '\r') {
                sb.append((char) c);
            }
        } while ((c = inputStream.read()) != -1);
        return sb.toString();
    }

    @Override
    public void send(JsonRpcMessage msg, MessageFormatter formatter) {
        MessageFormatter effectiveFormatter = this.formatter != null ? this.formatter : formatter;
        try {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            effectiveFormatter.serialize(msg, bos);
            byte[] content = bos.toByteArray();
            // Synchronize writes so concurrent sends (e.g. from callback handlers
            // and the main thread) don't interleave headers and content.
            synchronized (outputStream) {
                outputStream.write(("Content-Length: " + content.length + "\r\n").getBytes());
                if (effectiveFormatter.getEncoding() != StandardCharsets.UTF_8) {
                    outputStream.write(("Content-Type: application/vscode-jsonrpc;charset=" + effectiveFormatter.getEncoding().name() + "\r\n").getBytes());
                }
                outputStream.write('\r');
                outputStream.write('\n');
                outputStream.write(content);
                outputStream.flush();
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
