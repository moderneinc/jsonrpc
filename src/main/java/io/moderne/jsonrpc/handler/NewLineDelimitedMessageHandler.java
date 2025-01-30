package io.moderne.jsonrpc.handler;

import io.moderne.jsonrpc.formatter.JsonMessageFormatter;
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
    private final MessageFormatter formatter = new JsonMessageFormatter();
    private final InputStream inputStream;
    private final OutputStream outputStream;

    @Override
    public JsonRpcMessage receive() {
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
    public void send(JsonRpcMessage msg) {
        try {
            formatter.serialize(msg, outputStream);
            outputStream.write(new byte[]{'\n'});
            outputStream.flush();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
