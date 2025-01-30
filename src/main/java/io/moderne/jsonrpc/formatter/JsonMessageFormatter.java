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
