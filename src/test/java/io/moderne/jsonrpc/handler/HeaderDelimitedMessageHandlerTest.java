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
import io.moderne.jsonrpc.formatter.JsonMessageFormatter;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.InputStream;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HeaderDelimitedMessageHandlerTest {
    private static final JsonMessageFormatter FORMATTER = new JsonMessageFormatter();

    @Test
    void receiveThrowsEofWhenStreamClosedBetweenMessages() {
        InputStream empty = new ByteArrayInputStream(new byte[0]);
        HeaderDelimitedMessageHandler handler = new HeaderDelimitedMessageHandler(empty, new ByteArrayOutputStream());

        assertThatThrownBy(() -> handler.receive(FORMATTER))
                .isInstanceOf(EOFException.class);
    }

    @Test
    void receiveThrowsForNonEmptyMalformedHeader() {
        // A non-RPC line on the wire surfaces as JsonRpcReceiveException
        // (not EOF, not a returned JsonRpcError that JsonRpc.bind would
        // mistake for a peer response). The caller — JsonRpc.bind — turns
        // it into an error response sent back to the peer.
        InputStream noise = new ByteArrayInputStream("warning: something\n".getBytes());
        HeaderDelimitedMessageHandler handler = new HeaderDelimitedMessageHandler(noise, new ByteArrayOutputStream());

        assertThatThrownBy(() -> handler.receive(FORMATTER))
                .isInstanceOf(JsonRpcReceiveException.class)
                .hasMessageContaining("Expected Content-Length header");
    }

    @Test
    void receiveThrowsEofWhenStreamClosesMidMessage() {
        // Headers parsed, body promised but stream cuts off — must surface as
        // EOF so the reader loop can shut down rather than spin.
        String partial = "Content-Length: 100\r\n\r\n{partial";
        InputStream truncated = new ByteArrayInputStream(partial.getBytes());
        HeaderDelimitedMessageHandler handler = new HeaderDelimitedMessageHandler(truncated, new ByteArrayOutputStream());

        assertThatThrownBy(() -> handler.receive(FORMATTER))
                .isInstanceOf(EOFException.class);
    }
}
