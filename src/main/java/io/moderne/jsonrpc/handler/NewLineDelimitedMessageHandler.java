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

import java.io.*;

/**
 * This appends each JSON-RPC message with \n. It should only be used with UTF-8 text-based
 * formatters that do not emit new line characters as part of the JSON.
 */
public class NewLineDelimitedMessageHandler implements MessageHandler {
    private final InputStream inputStream;
    private final OutputStream outputStream;

    public NewLineDelimitedMessageHandler(InputStream inputStream, OutputStream outputStream) {
        // Same buffering policy as HeaderDelimitedMessageHandler: read-loop is
        // byte-by-byte until newline; buffer once at construction so we don't
        // syscall per byte. Skip re-wrapping a pre-buffered stream.
        this.inputStream = inputStream instanceof BufferedInputStream
                ? inputStream
                : new BufferedInputStream(inputStream);
        this.outputStream = outputStream;
    }

    @Override
    public JsonRpcMessage receive(MessageFormatter formatter) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        int b = inputStream.read();
        if (b == -1) {
            // Stream closed cleanly between messages — let the reader loop
            // shut down rather than spin treating EOF as a parse failure.
            throw new EOFException("Stream closed");
        }
        boolean foundNewline = false;
        do {
            buffer.write(b);
            if (b == '\n') {
                foundNewline = true;
                break;
            }
        } while ((b = inputStream.read()) != -1);
        if (!foundNewline) {
            // Bytes read but no terminating newline before EOF — peer died
            // mid-message. Match HeaderDelimitedMessageHandler's mid-message
            // EOF behavior so the reader loop exits instead of treating
            // partial bytes as a recoverable parse failure.
            throw new EOFException("Stream closed mid-message after " + buffer.size() + " bytes");
        }
        byte[] content = buffer.toByteArray();
        try {
            return formatter.deserialize(new ByteArrayInputStream(content));
        } catch (IOException e) {
            // Parse failure on a complete frame. Surface as JsonRpcReceiveException
            // so JsonRpc.bind() routes the error back to the peer rather than
            // letting it fall through to the generic Throwable catch (which
            // would lose the extracted id and the proper Invalid Request code).
            throw new JsonRpcReceiveException(IdExtractor.extractId(content),
                    JsonRpcReceiveException.invalidRequestDetail(e.getMessage()));
        }
    }

    @Override
    public void send(JsonRpcMessage msg, MessageFormatter formatter) {
        try {
            synchronized (outputStream) {
                formatter.serialize(msg, outputStream);
                outputStream.write(new byte[]{'\n'});
                outputStream.flush();
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
