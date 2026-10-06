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
public class HeaderDelimitedMessageHandler implements MessageHandler {
    private static final Pattern CONTENT_LENGTH = Pattern.compile("Content-Length: (\\d+)");

    private final InputStream inputStream;
    private final OutputStream outputStream;

    /**
     * Reused across sends, trading per-message garbage for retaining roughly the largest
     * message this handler has sent. Guarded by the {@code outputStream} monitor.
     */
    private final ByteArrayOutputStream sendBuffer = new ByteArrayOutputStream();

    /** Reused across receives. Unguarded, unlike {@code sendBuffer}: only the reader loop calls {@link #receive}. */
    private byte[] receiveBuffer = new byte[0];

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

    public HeaderDelimitedMessageHandler(InputStream inputStream, OutputStream outputStream) {
        // Wrap so byte-by-byte header reads (`readLineFromInputStream`) don't
        // hit a syscall per byte. Skip re-wrapping a stream the caller has
        // already buffered — double-buffering wastes a copy with no benefit.
        this.inputStream = inputStream instanceof BufferedInputStream
                ? inputStream
                : new BufferedInputStream(inputStream);
        this.outputStream = outputStream;
    }

    @Override
    public JsonRpcMessage receive(MessageFormatter formatter) throws IOException {
        MessageFormatter effectiveFormatter = this.formatter != null ? this.formatter : formatter;
        byte[] content = null;
        int length = 0;
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

            length = Integer.parseInt(contentLengthMatcher.group(1));
            if (receiveBuffer.length < length) {
                receiveBuffer = new byte[length];
            }
            content = receiveBuffer;
            for (int totalRead = 0; totalRead < length; ) {
                int bytesRead = inputStream.read(content, totalRead, length - totalRead);
                if (bytesRead == -1) {
                    // Mid-message EOF — treat as a closed stream rather than a
                    // recoverable parse error, otherwise the loop spins on the
                    // already-closed pipe.
                    throw new EOFException("Stream closed mid-message after " + totalRead +
                            " of " + length + " bytes");
                }
                totalRead += bytesRead;
            }

            return effectiveFormatter.deserialize(content, 0, length);
        } catch (EOFException | JsonRpcReceiveException e) {
            throw e;
        } catch (IOException e) {
            // Frame- or parse-level failure on an inbound message. Surface it
            // as JsonRpcReceiveException so JsonRpc.bind() routes it back to
            // the peer rather than completing an unrelated open client future
            // (whose id might collide with the extracted id, or trigger the
            // null-id "fail all open requests" branch).
            throw new JsonRpcReceiveException(IdExtractor.extractId(content, length),
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
            // Serialization stays inside the monitor because it guards sendBuffer, not just the
            // header-and-body write; a send must therefore not nest, since the inner one would
            // reset the buffer the outer is still filling.
            synchronized (outputStream) {
                sendBuffer.reset();
                effectiveFormatter.serialize(msg, sendBuffer);
                outputStream.write(("Content-Length: " + sendBuffer.size() + "\r\n").getBytes());
                if (effectiveFormatter.getEncoding() != StandardCharsets.UTF_8) {
                    outputStream.write(("Content-Type: application/vscode-jsonrpc;charset=" + effectiveFormatter.getEncoding().name() + "\r\n").getBytes());
                }
                outputStream.write('\r');
                outputStream.write('\n');
                sendBuffer.writeTo(outputStream);
                outputStream.flush();
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
