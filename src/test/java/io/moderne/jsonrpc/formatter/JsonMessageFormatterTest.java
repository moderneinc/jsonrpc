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
import io.moderne.jsonrpc.JsonRpcSuccess;
import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.exc.MismatchedInputException;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static java.nio.charset.StandardCharsets.UTF_8;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class JsonMessageFormatterTest {
    JsonMessageFormatter formatter = new JsonMessageFormatter();

    @Test
    void idAsString() throws IOException {
        assertThat(message("{\"jsonrpc\":\"2.0\",\"id\":\"1\"}").getId()).isEqualTo("1");
    }

    @Test
    void idAsNumber() throws IOException {
        assertThat(message("{\"jsonrpc\":\"2.0\",\"id\":1}").getId()).isEqualTo(1);
    }

    @Test
    void idAsNull() throws IOException {
        assertThat(message("{\"jsonrpc\":\"2.0\",\"id\":null}").getId()).isNull();
    }

    @Test
    void idNotIncluded() throws IOException {
        assertThat(message("{\"jsonrpc\":\"2.0\"}").getId()).isNull();
    }

    @Disabled
    @Test
    void idAsObjectFails() {
        assertThatThrownBy(() -> message("{\"jsonrpc\":\"2.0\",\"id\":[]}").getId())
                .hasMessageContaining("MUST");
    }

    @Test
    void aResultThatFailsToDecodeToItsExpectedTypeFailsOnConversion() throws IOException {
        byte[] wire = "{\"jsonrpc\":\"2.0\",\"id\":\"a\",\"result\":{\"k\":1}}".getBytes(UTF_8);

        JsonRpcSuccess success = (JsonRpcSuccess) formatter.deserialize(wire, 0, wire.length, id -> Integer.class);

        assertThat(success.getId()).isEqualTo("a");
        assertThatThrownBy(() -> success.getResult(Integer.class))
                .hasRootCauseInstanceOf(MismatchedInputException.class);
    }

    @Test
    void malformedJsonInATypedResultFailsTheMessage() {
        byte[] wire = "{\"jsonrpc\":\"2.0\",\"id\":\"a\",\"result\":{\"k\":}}".getBytes(UTF_8);

        assertThatThrownBy(() -> formatter.deserialize(wire, 0, wire.length, id -> Map.class))
                .isInstanceOf(JsonParseException.class);
    }

    @Test
    void anErrorWhileDecodingATypedResultFailsOnlyItsConversion() throws IOException {
        byte[] wire = "{\"jsonrpc\":\"2.0\",\"id\":\"a\",\"result\":{\"k\":1}}".getBytes(UTF_8);

        JsonRpcSuccess success = (JsonRpcSuccess) formatter.deserialize(wire, 0, wire.length, id -> Overflowing.class);

        assertThatThrownBy(() -> success.getResult(Overflowing.class))
                .hasRootCauseInstanceOf(StackOverflowError.class);
    }

    @Test
    void aNullResultStaysNullWhateverTypeItWasExpectedAs() throws IOException {
        byte[] wire = "{\"jsonrpc\":\"2.0\",\"id\":\"a\",\"result\":null}".getBytes(UTF_8);

        JsonRpcSuccess success = (JsonRpcSuccess) formatter.deserialize(wire, 0, wire.length, id -> int.class);

        assertThat(success.getResult(Integer.class)).isNull();
    }

    @Test
    void aResultDecodedToAGenericTypeIsReturnedAsIsForItsRawClass() throws IOException {
        byte[] wire = "{\"jsonrpc\":\"2.0\",\"id\":\"a\",\"result\":[1,2]}".getBytes(UTF_8);
        Type listOfInts = new TypeReference<List<Integer>>() {
        }.getType();

        JsonRpcSuccess success = (JsonRpcSuccess) formatter.deserialize(wire, 0, wire.length, id -> listOfInts);

        assertThat(success.getResult(List.class))
                .isEqualTo(Arrays.asList(1, 2))
                .isSameAs(success.getResult(List.class));
    }

    @JsonDeserialize(using = OverflowingDeserializer.class)
    static class Overflowing {
    }

    static class OverflowingDeserializer extends JsonDeserializer<Overflowing> {
        @Override
        public Overflowing deserialize(JsonParser p, DeserializationContext ctxt) {
            throw new StackOverflowError();
        }
    }

    private JsonRpcMessage message(String x) throws IOException {
        return formatter.deserialize(new ByteArrayInputStream(
                x.getBytes(formatter.getEncoding())
        ));
    }
}
