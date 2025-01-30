package io.moderne.jsonrpc.formatter;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.module.paramnames.ParameterNamesModule;
import io.moderne.jsonrpc.JsonRpcError;
import io.moderne.jsonrpc.JsonRpcMessage;
import io.moderne.jsonrpc.JsonRpcRequest;
import io.moderne.jsonrpc.JsonRpcSuccess;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Map;

public class JsonMessageFormatter implements MessageFormatter {
    ObjectMapper mapper = new ObjectMapper()
            .registerModule(new ParameterNamesModule());

    @Override
    public JsonRpcMessage deserialize(InputStream in) throws IOException {
        Map<String, Object> payload = mapper.readValue(in, new TypeReference<Map<String, Object>>() {
        });
        if (payload.containsKey("method")) {
            return mapper.convertValue(payload, JsonRpcRequest.class);
        } else if (payload.containsKey("error")) {
            return mapper.convertValue(payload, JsonRpcError.class);
        }
        return mapper.convertValue(payload, JsonRpcSuccess.class);
    }

    @Override
    public void serialize(JsonRpcMessage message, OutputStream out) throws IOException {
        mapper.writeValue(out, message);
    }
}
