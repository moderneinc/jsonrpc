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

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.cfg.ConstructorDetector;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.util.TokenBuffer;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.module.paramnames.ParameterNamesModule;
import io.moderne.jsonrpc.JsonRpcError;
import io.moderne.jsonrpc.JsonRpcMessage;
import io.moderne.jsonrpc.JsonRpcRequest;
import io.moderne.jsonrpc.JsonRpcSuccess;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Type;

public class JsonMessageFormatter implements MessageFormatter {
    private final ObjectMapper mapper;
    private final ClassValue<ObjectWriter> writerCache = new ClassValue<ObjectWriter>() {
        @Override
        protected ObjectWriter computeValue(Class<?> type) {
            return mapper.writerFor(type);
        }
    };

    public JsonMessageFormatter() {
        this(JsonMapper.builder()
                // to be able to construct classes that have @Data and a single field
                // see https://cowtowncoder.medium.com/jackson-2-12-most-wanted-3-5-246624e2d3d0
                .constructorDetector(ConstructorDetector.USE_PROPERTIES_BASED)
                .build()
                .registerModules(new ParameterNamesModule(), new JavaTimeModule())
                .disable(SerializationFeature.FAIL_ON_EMPTY_BEANS)
                .setSerializationInclusion(JsonInclude.Include.NON_NULL));
        mapper.setVisibility(mapper.getSerializationConfig().getDefaultVisibilityChecker()
                .withCreatorVisibility(JsonAutoDetect.Visibility.PUBLIC_ONLY)
                .withGetterVisibility(JsonAutoDetect.Visibility.NONE)
                .withIsGetterVisibility(JsonAutoDetect.Visibility.NONE)
                .withFieldVisibility(JsonAutoDetect.Visibility.ANY));
    }

    public JsonMessageFormatter(com.fasterxml.jackson.databind.Module... modules) {
        this(JsonMapper.builder()
                // to be able to construct classes that have @Data and a single field
                // see https://cowtowncoder.medium.com/jackson-2-12-most-wanted-3-5-246624e2d3d0
                .constructorDetector(ConstructorDetector.USE_PROPERTIES_BASED)
                .build()
                .registerModules(new ParameterNamesModule(), new JavaTimeModule())
                .registerModules(modules)
                .disable(SerializationFeature.FAIL_ON_EMPTY_BEANS)
                .setSerializationInclusion(JsonInclude.Include.NON_NULL));
        mapper.setVisibility(mapper.getSerializationConfig().getDefaultVisibilityChecker()
                .withCreatorVisibility(JsonAutoDetect.Visibility.PUBLIC_ONLY)
                .withGetterVisibility(JsonAutoDetect.Visibility.NONE)
                .withIsGetterVisibility(JsonAutoDetect.Visibility.NONE)
                .withFieldVisibility(JsonAutoDetect.Visibility.ANY));
    }

    public JsonMessageFormatter(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public JsonRpcMessage deserialize(InputStream in) throws IOException {
        JsonParser parser = mapper.getFactory().createParser(in);

        Object id = null;
        String method = null;
        TokenBuffer params = null;
        TokenBuffer errorBuffer = null;
        Object result = null;

        if (parser.nextToken() != JsonToken.START_OBJECT) {
            return JsonRpcError.invalidRequest(null, "Expected JSON object");
        }

        while (parser.nextToken() != JsonToken.END_OBJECT) {
            String fieldName = parser.currentName();
            parser.nextToken();

            switch (fieldName) {
                case "jsonrpc":
                    parser.skipChildren();
                    break;
                case "id":
                    id = normalizeId(parser.readValueAs(Object.class));
                    break;
                case "method":
                    method = parser.getValueAsString();
                    break;
                case "params":
                    params = captureValue(parser);
                    break;
                case "error":
                    errorBuffer = captureValue(parser);
                    break;
                case "result":
                    JsonToken token = parser.currentToken();
                    if (token == JsonToken.START_OBJECT || token == JsonToken.START_ARRAY) {
                        result = captureValue(parser);
                    } else {
                        result = parser.readValueAs(Object.class);
                    }
                    break;
                default:
                    parser.skipChildren();
                    break;
            }
        }

        if (method != null) {
            return new JsonRpcRequest(id, method, params);
        } else if (errorBuffer != null) {
            JsonRpcError.Detail detail = convertValue(errorBuffer, JsonRpcError.Detail.class);
            return new JsonRpcError(id, detail);
        }
        return JsonRpcSuccess.fromPayload(id, result, this);
    }

    private TokenBuffer captureValue(JsonParser parser) throws IOException {
        TokenBuffer buffer = new TokenBuffer(parser);
        buffer.copyCurrentStructure(parser);
        return buffer;
    }

    private Object normalizeId(Object id) {
        if (id instanceof Number) {
            return ((Number) id).intValue();
        }
        return id;
    }

    @Override
    public void serialize(JsonRpcMessage message, OutputStream out) throws IOException {
        writerCache.get(message.getClass()).writeValue(out, message);
    }

    @Override
    public <T> T convertValue(Object value, Type type) {
        if (value instanceof TokenBuffer) {
            try {
                JsonParser bufferParser = ((TokenBuffer) value).asParser();
                bufferParser.nextToken();
                return mapper.readValue(bufferParser, mapper.getTypeFactory().constructType(type));
            } catch (IOException e) {
                throw new RuntimeException("Failed to convert TokenBuffer", e);
            }
        }
        return mapper.convertValue(value, mapper.getTypeFactory().constructType(type));
    }
}
