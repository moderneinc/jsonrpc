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
package io.moderne.jsonrpc;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import io.moderne.jsonrpc.formatter.MessageFormatter;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.lang.reflect.Type;

/**
 * Library-owned wrapper for the {@code params}, {@code result}, and inbound
 * {@code error} JSON values on JSON-RPC messages. Holds either:
 * <ul>
 *   <li>a POJO (outbound — wrapped at request construction, serialized
 *       through whatever the {@link MessageFormatter} uses on the wire), or</li>
 *   <li>a parser-format-specific buffer (inbound — produced by the formatter
 *       during {@code deserialize}; converted lazily to a typed POJO when the
 *       consumer asks via {@link #as(MessageFormatter, Class)}), or</li>
 *   <li>{@code null}.</li>
 * </ul>
 * Keeps Jackson types out of the public ABI: consumers see only library
 * classes, so adding/upgrading/swapping the wire format does not force them
 * to recompile.
 */
@JsonSerialize(using = RawJson.RawJsonSerializer.class)
public final class RawJson {

    private static final RawJson NULL = new RawJson(null);

    private final @Nullable Object value;

    private RawJson(@Nullable Object value) {
        this.value = value;
    }

    /**
     * Wrap an arbitrary value. {@code null} returns the canonical null
     * instance (no allocation per call).
     */
    public static RawJson of(@Nullable Object value) {
        return value == null ? NULL : new RawJson(value);
    }

    public boolean isNull() {
        return value == null;
    }

    /**
     * Convert this value to {@code type} using {@code formatter}. Returns
     * {@code null} when {@link #isNull()}; otherwise delegates to
     * {@link MessageFormatter#convertValue(RawJson, Type)}.
     */
    public <T> @Nullable T as(MessageFormatter formatter, Class<T> type) {
        return formatter.convertValue(this, type);
    }

    public <T> @Nullable T as(MessageFormatter formatter, Type type) {
        return formatter.convertValue(this, type);
    }

    /**
     * Internal accessor for {@link MessageFormatter} implementations and the
     * default Jackson serializer. Returns the wrapped value (POJO, parser
     * buffer, or {@code null}). Library consumers should use
     * {@link #as(MessageFormatter, Class)} instead — calling {@code unwrap()}
     * directly couples them to the wire format.
     */
    public @Nullable Object unwrap() {
        return value;
    }

    public static final class RawJsonSerializer extends StdSerializer<RawJson> {
        public RawJsonSerializer() {
            super(RawJson.class);
        }

        @Override
        public void serialize(RawJson value, JsonGenerator gen, SerializerProvider serializers) throws IOException {
            Object inner = value.unwrap();
            if (inner == null) {
                gen.writeNull();
                return;
            }
            // Jackson's defaultSerializeValue handles both POJOs and the
            // format's own buffered token type (e.g. TokenBuffer for JSON —
            // it knows how to replay the captured tokens into the writer).
            serializers.defaultSerializeValue(inner, gen);
        }
    }
}
