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
import com.fasterxml.jackson.annotation.JsonValue;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.cfg.ConstructorDetector;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.type.TypeFactory;
import com.fasterxml.jackson.databind.util.TokenBuffer;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.module.paramnames.ParameterNamesModule;
import io.moderne.jsonrpc.JsonRpcError;
import io.moderne.jsonrpc.JsonRpcMessage;
import io.moderne.jsonrpc.JsonRpcRequest;
import io.moderne.jsonrpc.JsonRpcSuccess;
import io.moderne.jsonrpc.RawJson;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Type;
import java.util.function.Function;

public class JsonMessageFormatter implements MessageFormatter {
    private final ObjectMapper mapper;

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
        // Not closed: Jackson would close the caller's stream along with it.
        return read(mapper.getFactory().createParser(in), id -> null);
    }

    @Override
    public JsonRpcMessage deserialize(byte[] content, int offset, int length) throws IOException {
        return deserialize(content, offset, length, id -> null);
    }

    @Override
    public JsonRpcMessage deserialize(byte[] content, int offset, int length,
                                      Function<Object, @Nullable Type> resultTypes) throws IOException {
        // Closed, unlike the stream overload: there is no caller-owned source, and closing is what
        // returns the parser's text and name buffers to Jackson's recycler.
        try (JsonParser parser = mapper.getFactory().createParser(content, offset, length)) {
            return read(parser, resultTypes);
        }
    }

    private JsonRpcMessage read(JsonParser parser, Function<Object, @Nullable Type> resultTypes) throws IOException {
        Object id = null;
        String method = null;
        TokenBuffer params = null;
        TokenBuffer errorBuffer = null;
        Object resultScalar = null;
        Object resultBuffer = null;
        boolean haveResultField = false;

        if (parser.nextToken() != JsonToken.START_OBJECT) {
            throw new IOException("Expected JSON object");
        }

        while (parser.nextToken() != JsonToken.END_OBJECT) {
            String fieldName = parser.currentName();
            parser.nextToken();
            if (fieldName == null) {
                parser.skipChildren();
                continue;
            }
            switch (fieldName) {
                case "jsonrpc":
                    parser.skipChildren();
                    break;
                case "id":
                    id = normalizeId(parser);
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
                    haveResultField = true;
                    // Only possible when the id precedes the result, as vscode-jsonrpc and this library write it.
                    Type resultType = id == null ? null : resultTypes.apply(id);
                    JsonToken t = parser.currentToken();
                    if (resultType != null && t != JsonToken.VALUE_NULL) {
                        Decoded decoded = decode(parser, resultType);
                        resultBuffer = decoded;
                        if (decoded.failure != null) {
                            // The parser stopped somewhere inside the result.
                            return JsonRpcSuccess.fromPayload(id, RawJson.of(decoded), this);
                        }
                    } else if (t == JsonToken.START_OBJECT || t == JsonToken.START_ARRAY) {
                        resultBuffer = captureValue(parser);
                    } else {
                        // Primitive — read directly without buffering.
                        resultScalar = parser.readValueAs(Object.class);
                    }
                    break;
                default:
                    parser.skipChildren();
                    break;
            }
        }

        if (method != null) {
            return new JsonRpcRequest(id, method, params == null ? null : RawJson.of(params));
        }
        if (errorBuffer != null) {
            JsonRpcError.Detail detail = convertValue(RawJson.of(errorBuffer), JsonRpcError.Detail.class);
            return new JsonRpcError(id, detail);
        }
        if (haveResultField) {
            RawJson result = resultBuffer != null ? RawJson.of(resultBuffer) : RawJson.of(resultScalar);
            return JsonRpcSuccess.fromPayload(id, result, this);
        }
        // No method, no error, no result — treat as a success with null result
        // (matches the prior {@code mapper.convertValue} fallback behavior).
        return JsonRpcSuccess.fromPayload(id, null, this);
    }

    /**
     * A failure to map the result to its type fails only that result, as it would on conversion.
     * Malformed JSON fails the whole message.
     */
    private Decoded decode(JsonParser parser, Type type) throws IOException {
        try {
            return new Decoded(mapper.readValue(parser, mapper.getTypeFactory().constructType(type)), type, null);
        } catch (JsonMappingException | RuntimeException | StackOverflowError | LinkageError e) {
            return new Decoded(null, type, e);
        }
    }

    private TokenBuffer captureValue(JsonParser parser) throws IOException {
        TokenBuffer buffer = new TokenBuffer(parser);
        buffer.copyCurrentStructure(parser);
        return buffer;
    }

    private @Nullable Object normalizeId(JsonParser parser) throws IOException {
        // Match the legacy JsonRpcIdDeserializer contract: int / string / null.
        switch (parser.currentToken()) {
            case VALUE_NUMBER_INT:
                return parser.getIntValue();
            case VALUE_STRING:
                return parser.getText();
            case VALUE_NULL:
                return null;
            default:
                // Skip — best-effort. The id is optional and any peer that
                // sends a non-scalar id violates the spec; we just don't
                // correlate it.
                parser.skipChildren();
                return null;
        }
    }

    @Override
    public void serialize(JsonRpcMessage message, OutputStream out) throws IOException {
        mapper.writeValue(out, message);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> @Nullable T convertValue(RawJson value, Type type) {
        Object inner = value.unwrap();
        if (inner == null) {
            return null;
        }
        if (inner instanceof Decoded) {
            Decoded decoded = (Decoded) inner;
            if (decoded.failure != null) {
                throw new RuntimeException("Failed to convert result to " + decoded.type, decoded.failure);
            }
            return decoded.type.equals(type) || type == TypeFactory.rawClass(decoded.type) ? (T) decoded.value :
                    mapper.convertValue(decoded.value, mapper.getTypeFactory().constructType(type));
        }
        if (inner instanceof TokenBuffer) {
            try {
                JsonParser bufferParser = ((TokenBuffer) inner).asParser();
                bufferParser.nextToken();
                return mapper.readValue(bufferParser, mapper.getTypeFactory().constructType(type));
            } catch (IOException e) {
                throw new RuntimeException("Failed to convert TokenBuffer to " + type, e);
            }
        }
        return (T) mapper.convertValue(inner, mapper.getTypeFactory().constructType(type));
    }

    /**
     * A result decoded to the type its request expected, or the failure to decode it.
     */
    static final class Decoded {
        @JsonValue
        final @Nullable Object value;

        final Type type;
        final @Nullable Throwable failure;

        Decoded(@Nullable Object value, Type type, @Nullable Throwable failure) {
            this.value = value;
            this.type = type;
            this.failure = failure;
        }
    }
}
