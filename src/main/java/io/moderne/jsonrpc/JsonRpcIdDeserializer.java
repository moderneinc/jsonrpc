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

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;

import java.io.IOException;

public class JsonRpcIdDeserializer extends JsonDeserializer<Object> {

    @Override
    public Object deserialize(JsonParser parser, DeserializationContext context) throws IOException {
        // Direct token inspection — no JsonNode tree allocation. The id field
        // is a scalar by spec ("String, Number, or NULL value if included"),
        // so a single switch on the current token covers every legal shape.
        switch (parser.currentToken()) {
            case VALUE_NUMBER_INT:
                // Per the comment that used to live here: assume int. Any
                // JSON-RPC client interacting with a JavaScript peer cannot
                // send integers larger than Number.MAX_SAFE_INTEGER without
                // losing precision, so widening to long isn't worth the API
                // change.
                return parser.getIntValue();
            case VALUE_STRING:
                return parser.getText();
            case VALUE_NULL:
                return null;
            default:
                throw new IOException("A JSON-RPC ID according to the spec \"MUST contain a String, Number, or NULL value if included\". See §4 of https://www.jsonrpc.org/specification.");
        }
    }
}
