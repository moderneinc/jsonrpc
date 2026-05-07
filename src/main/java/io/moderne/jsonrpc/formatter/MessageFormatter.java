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

import io.moderne.jsonrpc.JsonRpcMessage;
import io.moderne.jsonrpc.RawJson;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Type;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

public interface MessageFormatter {
    JsonRpcMessage deserialize(InputStream in) throws IOException;

    void serialize(JsonRpcMessage message, OutputStream out) throws IOException;

    /**
     * Convert a {@link RawJson} value (typically from JSON-RPC {@code params}
     * or {@code result}) to a typed POJO. Implementations decide how to
     * handle each possible wrapped representation: a POJO awaiting
     * serialization, a parser-format-specific buffer captured during
     * {@code deserialize}, or {@code null}.
     */
    <T> @Nullable T convertValue(RawJson value, Type type);

    default Charset getEncoding() {
        return StandardCharsets.UTF_8;
    }
}
