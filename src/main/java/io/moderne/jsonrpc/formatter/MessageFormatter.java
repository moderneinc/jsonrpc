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
     * Converts a value (typically from JSON-RPC params or result) to the specified type.
     */
    <T> T convertValue(Object value, Type type);

    default Charset getEncoding() {
        return StandardCharsets.UTF_8;
    }
}
