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
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;

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

    private JsonRpcMessage message(String x) throws IOException {
        return formatter.deserialize(new ByteArrayInputStream(
                x.getBytes(formatter.getEncoding())
        ));
    }
}
