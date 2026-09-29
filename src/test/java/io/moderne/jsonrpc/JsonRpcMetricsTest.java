/*
 * Copyright 2026 the original author or authors.
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

import io.micrometer.core.instrument.Metrics;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.moderne.jsonrpc.formatter.JsonMessageFormatter;
import io.moderne.jsonrpc.handler.HeaderDelimitedMessageHandler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static java.util.stream.Collectors.toList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JsonRpcMetricsTest {
    SimpleMeterRegistry registry = new SimpleMeterRegistry();

    /**
     * Unique per test, so a recording that lands after an earlier test ended isn't counted here.
     */
    Tags peer = Tags.of("peer", UUID.randomUUID().toString());

    /**
     * Sends to itself, so every request is both a client and a server request.
     */
    JsonRpc loopback;

    @BeforeEach
    void before() throws IOException {
        Metrics.addRegistry(registry);
        PipedOutputStream os = new PipedOutputStream();
        PipedInputStream is = new PipedInputStream(os);
        loopback = new JsonRpc(new HeaderDelimitedMessageHandler(is, os), new JsonMessageFormatter())
                .tags(peer);
    }

    @AfterEach
    void after() {
        loopback.shutdown();
        Metrics.removeRegistry(registry);
    }

    @Test
    void successIsRecordedOnBothSides() throws Exception {
        loopback.rpc("hello", handler(() -> "Hello")).bind()
                .send(JsonRpcRequest.newRequest("hello")).get(5, TimeUnit.SECONDS);

        awaitRecorded("jsonrpc.server.requests", "hello", "success");
        assertThat(recorded("jsonrpc.client.requests")).containsExactly("hello:success");
        assertThat(recorded("jsonrpc.server.requests")).containsExactly("hello:success");
    }

    @Test
    void failingHandlerIsAnErrorOnBothSides() throws Exception {
        CompletableFuture<JsonRpcSuccess> response = loopback.rpc("hello", handler(() -> {
            throw new IllegalStateException("Boom");
        })).bind().send(JsonRpcRequest.newRequest("hello"));

        assertThatThrownBy(() -> response.get(5, TimeUnit.SECONDS)).hasCauseInstanceOf(JsonRpcException.class);
        awaitRecorded("jsonrpc.server.requests", "hello", "error");
        assertThat(recorded("jsonrpc.client.requests")).containsExactly("hello:error");
        assertThat(recorded("jsonrpc.server.requests")).containsExactly("hello:error");
    }

    @Test
    void callerTimeoutIsRecordedInsteadOfTheLateResponse() throws Exception {
        CountDownLatch release = new CountDownLatch(1);
        CompletableFuture<JsonRpcSuccess> response = loopback
                .rpc("slow", handler(() -> {
                    release.await();
                    return "late";
                }))
                .rpc("ping", handler(() -> "pong"))
                .bind()
                .send(JsonRpcRequest.newRequest("slow"));

        response.completeExceptionally(new TimeoutException());
        release.countDown();

        // The late reply is on the wire once the server side records it; a request sent after
        // it is answered after it, so by then the late reply has been read.
        awaitRecorded("jsonrpc.server.requests", "slow", "success");
        loopback.send(JsonRpcRequest.newRequest("ping")).get(5, TimeUnit.SECONDS);

        assertThat(recorded("jsonrpc.client.requests")).filteredOn(r -> r.startsWith("slow:"))
                .containsExactly("slow:timeout");
    }

    @Test
    void closedConnectionIsRecordedAsClosed() throws Exception {
        JsonRpc closed = new JsonRpc(
                new HeaderDelimitedMessageHandler(new ByteArrayInputStream(new byte[0]), new ByteArrayOutputStream()),
                new JsonMessageFormatter())
                .tags(peer)
                .bind();
        try {
            CompletableFuture<JsonRpcSuccess> response = closed.send(JsonRpcRequest.newRequest("never-answered"));

            assertThatThrownBy(() -> response.get(5, TimeUnit.SECONDS)).hasCauseInstanceOf(JsonRpcException.class);
            assertThat(recorded("jsonrpc.client.requests")).containsExactly("never-answered:closed");
        } finally {
            closed.shutdown();
        }
    }

    /**
     * Each recorded timer of the name, as {@code method:outcome}.
     */
    private List<String> recorded(String name) {
        return registry.find(name).tags(peer).timers().stream()
                .filter(t -> t.count() > 0)
                .map(t -> t.getId().getTag("method") + ":" + t.getId().getTag("outcome"))
                .collect(toList());
    }

    /**
     * The server side records after writing its reply, which can land after the client has the
     * response.
     */
    private void awaitRecorded(String name, String method, String outcome) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (!recorded(name).contains(method + ":" + outcome) && System.nanoTime() < deadline) {
            Thread.sleep(10);
        }
    }

    private static JsonRpcMethod<Void> handler(Handler handler) {
        return new JsonRpcMethod<Void>() {
            @Override
            protected Object handle(Void params) throws Exception {
                return handler.handle();
            }
        };
    }

    @FunctionalInterface
    private interface Handler {
        Object handle() throws Exception;
    }
}
