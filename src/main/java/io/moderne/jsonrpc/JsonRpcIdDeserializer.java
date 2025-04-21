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
import com.fasterxml.jackson.core.ObjectCodec;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;

import java.io.IOException;

public class JsonRpcIdDeserializer extends JsonDeserializer<Object> {

    @Override
    public Object deserialize(JsonParser jsonParser, DeserializationContext deserializationContext) throws IOException {
        ObjectCodec codec = jsonParser.getCodec();
        JsonNode jsonNode = codec.readTree(jsonParser);
        if (jsonNode.isNumber()) {
            // The assumption here is that the id is either a String or an Integer, and likely
            // an Integer that is no larger than JavaScripts `Number.MAX_SAFE_INTEGER` since
            // any JSON-RPC client interacting with a JavaScript peer wouldn't be able to send
            // integer values larger than that without JavaScript converting that integer to a
            // float, losing precision, and therefore not being able to associate requests/responses
            // with the correct id.
            return jsonNode.asInt();
        } else if (jsonNode.isTextual()) {
            return jsonNode.asText();
        } else if (jsonNode.isNull()) {
            return null;
        } else {
            throw new IOException("A JSON-RPC ID according to the spec \"MUST contain a String, Number, or NULL value if included\". See §4 of https://www.jsonrpc.org/specification.");
        }
    }
}
