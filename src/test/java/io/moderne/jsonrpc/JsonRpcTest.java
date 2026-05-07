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

import io.moderne.jsonrpc.formatter.JsonMessageFormatter;
import io.moderne.jsonrpc.handler.HeaderDelimitedMessageHandler;
import io.moderne.jsonrpc.handler.TraceMessageHandler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class JsonRpcTest {
    JsonRpc jsonRpc;

    @BeforeEach
    void before() throws IOException {
        PipedOutputStream os = new PipedOutputStream();
        PipedInputStream is = new PipedInputStream(os);
        JsonMessageFormatter formatter = new JsonMessageFormatter();
        jsonRpc = new JsonRpc(
                new TraceMessageHandler("both", new HeaderDelimitedMessageHandler(is, os)),
                formatter);
    }

    @AfterEach
    void after() {
        jsonRpc.shutdown();
    }

    @Test
    void requestResponse() throws ExecutionException, InterruptedException, TimeoutException {
        JsonRpcSuccess response = jsonRpc
                .rpc("hello", new HelloController())
                .bind()
                .send(JsonRpcRequest.newRequest("hello", new Person("Jon")))
                .get(5, TimeUnit.SECONDS);

        assertThat(response.getResult(String.class)).isEqualTo("Hello Jon");
    }

    @Test
    void noParams() throws ExecutionException, InterruptedException, TimeoutException {
        JsonRpcSuccess response = jsonRpc
                .rpc("hello", new JsonRpcMethod<Void>() {
                    @Override
                    protected Object handle(Void params) {
                        return "Hello Jon";
                    }
                })
                .bind()
                .send(JsonRpcRequest.newRequest("hello"))
                .get(5, TimeUnit.SECONDS);

        assertThat(response.getResult(String.class)).isEqualTo("Hello Jon");
    }

    @Test
    void rpcThrowsException() {
        assertThatThrownBy(() -> jsonRpc
                .rpc("hello", new JsonRpcMethod<Person>() {
                    @Override
                    protected Object handle(Person params) {
                        throw new IllegalStateException("Boom");
                    }
                })
                .bind()
                .send(JsonRpcRequest.newRequest("hello", new Person("Jon")))
                .get(5, TimeUnit.SECONDS)
        ).hasCauseInstanceOf(JsonRpcException.class);
    }

    @Test
    void notification() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        jsonRpc
                .rpc("hello", new JsonRpcMethod<Person>() {
                    @Override
                    protected Object handle(Person person) {
                        assertThat(person.name).isEqualTo("Jon");
                        latch.countDown();
                        return null;
                    }
                })
                .bind()
                .notify(JsonRpcRequest.newRequest("hello", new Person("Jon")));
        assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue();
    }

    @Test
    void positional() throws ExecutionException, InterruptedException, TimeoutException {
        JsonRpcSuccess response = jsonRpc
                .rpc("hello", new JsonRpcMethod<List<String>>() {
                    @Override
                    protected Object handle(List<String> names) {
                        return "Hello " + String.join(" and ", names);
                    }
                })
                .bind()
                .send(JsonRpcRequest.newRequest("hello", List.of("Jon", "Jim")))
                .get(5, TimeUnit.SECONDS);

        assertThat(response.getResult(String.class)).isEqualTo("Hello Jon and Jim");
    }

    @Test
    void positionalRequestMismatchedToNamedParameterMethod() {
        assertThatThrownBy(() -> jsonRpc
                .rpc("hello", new HelloController())
                .bind()
                .send(JsonRpcRequest.newRequest("hello", List.of("Jon")))
                .get(5, TimeUnit.SECONDS)
        ).hasCauseInstanceOf(JsonRpcException.class);
    }

    @Test
    void readerLoopExitsCleanlyOnEof() throws Exception {
        // When the peer closes the stream, the reader loop must shut down
        // instead of spinning at full CPU constructing JsonRpcException
        // instances per garbage byte.
        ByteArrayInputStream closedStream = new ByteArrayInputStream(new byte[0]);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        JsonMessageFormatter formatter = new JsonMessageFormatter();
        JsonRpc localRpc = new JsonRpc(
                new HeaderDelimitedMessageHandler(closedStream, out),
                formatter)
                .bind();
        try {
            CompletableFuture<JsonRpcSuccess> inFlight =
                    localRpc.send(JsonRpcRequest.newRequest("never-arrives"));

            // The in-flight request must fail (not hang) once the loop sees EOF.
            assertThatThrownBy(() -> inFlight.get(5, TimeUnit.SECONDS))
                    .hasCauseInstanceOf(JsonRpcException.class);
        } finally {
            localRpc.shutdown();
        }
    }

    @Test
    void malformedInboundDoesNotCompleteOpenClientRequests() throws Exception {
        // Pre-fix bug: HeaderDelimitedMessageHandler returned JsonRpcError for
        // frame/parse failures. JsonRpc.bind treated every JsonRpcError as a
        // peer response. A malformed inbound message with no extractable id
        // hit the "Error with no id — fail all open requests" branch and
        // completed every open client future exceptionally — even though the
        // peer never sent us anything correlated to those requests.
        //
        // Post-fix: handler throws JsonRpcReceiveException; JsonRpc.bind
        // routes that to the peer as an error response and leaves
        // openRequests untouched.
        PipedOutputStream peerToServer = new PipedOutputStream();
        PipedInputStream serverIn = new PipedInputStream(peerToServer);
        ByteArrayOutputStream serverOut = new ByteArrayOutputStream();
        JsonMessageFormatter formatter = new JsonMessageFormatter();
        JsonRpc localRpc = new JsonRpc(
                new HeaderDelimitedMessageHandler(serverIn, serverOut),
                formatter).bind();
        try {
            CompletableFuture<JsonRpcSuccess> open =
                    localRpc.send(JsonRpcRequest.newRequest("waiting"));

            // 5 ASCII bytes that are not valid JSON. extractId() returns null.
            byte[] body = "hello".getBytes(StandardCharsets.UTF_8);
            byte[] frame = ("Content-Length: " + body.length + "\r\n\r\n")
                    .getBytes(StandardCharsets.UTF_8);
            peerToServer.write(frame);
            peerToServer.write(body);
            peerToServer.flush();

            // Wait for the error response to land in serverOut.
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
            while (System.nanoTime() < deadline &&
                    !serverOut.toString(StandardCharsets.UTF_8).contains("Invalid Request")) {
                Thread.sleep(20);
            }

            assertThat(serverOut.toString(StandardCharsets.UTF_8))
                    .as("error response sent back to peer for malformed inbound")
                    .contains("Invalid Request");
            assertThat(open.isDone())
                    .as("open client request stays pending — malformed inbound is not a response to it")
                    .isFalse();
        } finally {
            localRpc.shutdown();
        }
    }

    record Person(String name) {
    }

    static class HelloController extends JsonRpcMethod<Person> {
        @Override
        public Object handle(Person person) {
            return "Hello " + person.name;
        }
    }
}
