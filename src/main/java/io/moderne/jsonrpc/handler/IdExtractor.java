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

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import org.jspecify.annotations.Nullable;

import java.io.IOException;

/**
 * Recovery utility for handlers: try to extract the JSON-RPC "id" field from
 * raw message bytes when full deserialization has failed, so the error response
 * we send back to the peer can correlate with their original request.
 * <p>
 * Uses a streaming parser to read only the top-level "id" field without parsing
 * the rest of the (possibly malformed) message.
 */
final class IdExtractor {
    private static final JsonFactory JSON_FACTORY = new JsonFactory();

    private IdExtractor() {
    }

    static @Nullable Object extractId(byte @Nullable [] content) {
        if (content == null) {
            return null;
        }
        try (JsonParser parser = JSON_FACTORY.createParser(content)) {
            if (parser.nextToken() != JsonToken.START_OBJECT) {
                return null;
            }
            while (parser.nextToken() != JsonToken.END_OBJECT) {
                String field = parser.currentName();
                parser.nextToken();
                if ("id".equals(field)) {
                    switch (parser.currentToken()) {
                        case VALUE_NUMBER_INT:
                            return parser.getIntValue();
                        case VALUE_STRING:
                            return parser.getText();
                        default:
                            return null;
                    }
                }
                // Skip over values of other top-level fields without parsing them
                parser.skipChildren();
            }
        } catch (IOException ignored) {
        }
        return null;
    }
}
