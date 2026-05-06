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

import io.moderne.jsonrpc.JsonRpcReceiveException;
import io.moderne.jsonrpc.JsonRpcRequest;
import io.moderne.jsonrpc.formatter.JsonMessageFormatter;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class NewLineDelimitedMessageHandlerTest {
    private static final JsonMessageFormatter FORMATTER = new JsonMessageFormatter();

    @Test
    void receiveDeserializesCompleteFrame() throws Exception {
        InputStream in = new ByteArrayInputStream(
                "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"ping\"}\n".getBytes(StandardCharsets.UTF_8));
        NewLineDelimitedMessageHandler handler = new NewLineDelimitedMessageHandler(in, new ByteArrayOutputStream());

        JsonRpcRequest msg = (JsonRpcRequest) handler.receive(FORMATTER);

        assertThat(msg.getMethod()).isEqualTo("ping");
        assertThat(msg.getId()).isEqualTo(1);
    }

    @Test
    void receiveThrowsEofWhenStreamClosedBetweenMessages() {
        InputStream empty = new ByteArrayInputStream(new byte[0]);
        NewLineDelimitedMessageHandler handler = new NewLineDelimitedMessageHandler(empty, new ByteArrayOutputStream());

        assertThatThrownBy(() -> handler.receive(FORMATTER))
                .isInstanceOf(EOFException.class)
                .hasMessageContaining("Stream closed");
    }

    @Test
    void receiveThrowsEofWhenStreamClosesMidMessage() {
        // Bytes received, no terminating newline, then EOF — peer died.
        // Must match HeaderDelimitedMessageHandler's mid-message EOF so the
        // reader loop shuts down instead of trying to parse partial bytes.
        InputStream truncated = new ByteArrayInputStream("{\"partial".getBytes(StandardCharsets.UTF_8));
        NewLineDelimitedMessageHandler handler = new NewLineDelimitedMessageHandler(truncated, new ByteArrayOutputStream());

        assertThatThrownBy(() -> handler.receive(FORMATTER))
                .isInstanceOf(EOFException.class)
                .hasMessageContaining("mid-message");
    }

    @Test
    void receiveThrowsForMalformedJsonInCompleteFrame() {
        // A complete frame (newline-terminated) whose contents fail to parse
        // surfaces as JsonRpcReceiveException — NOT as a returned JsonRpcError
        // that JsonRpc.bind would mistake for a peer response.
        InputStream noise = new ByteArrayInputStream("hello world\n".getBytes(StandardCharsets.UTF_8));
        NewLineDelimitedMessageHandler handler = new NewLineDelimitedMessageHandler(noise, new ByteArrayOutputStream());

        assertThatThrownBy(() -> handler.receive(FORMATTER))
                .isInstanceOf(JsonRpcReceiveException.class)
                .hasMessageContaining("Invalid Request");
    }

    @Test
    void receiveExtractsIdFromMalformedFrameWherePossible() {
        // Truncated JSON object that has a parseable id field. The recovery
        // path should pick up the id so the error response we send back can
        // correlate with the peer's original request.
        InputStream framed = new ByteArrayInputStream(
                "{\"id\":42,\"method\":\"oops\",\"params\":{\"unterminated\n".getBytes(StandardCharsets.UTF_8));
        NewLineDelimitedMessageHandler handler = new NewLineDelimitedMessageHandler(framed, new ByteArrayOutputStream());

        assertThatThrownBy(() -> handler.receive(FORMATTER))
                .isInstanceOfSatisfying(JsonRpcReceiveException.class, e -> {
                    assertThat(e.toError().getId()).isEqualTo(42);
                });
    }
}
