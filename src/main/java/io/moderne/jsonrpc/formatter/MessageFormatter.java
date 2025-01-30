package io.moderne.jsonrpc.formatter;

import io.moderne.jsonrpc.JsonRpcMessage;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

public interface MessageFormatter {
    JsonRpcMessage deserialize(InputStream in) throws IOException;

    void serialize(JsonRpcMessage message, OutputStream out) throws IOException;

    default Charset getEncoding() {
        return StandardCharsets.UTF_8;
    }
}
