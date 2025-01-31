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

import io.moderne.jsonrpc.handler.HeaderDelimitedMessageHandler;
import io.moderne.jsonrpc.handler.TraceMessageHandler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

import static io.moderne.jsonrpc.JsonRpcMethod.namedParameters;
import static io.moderne.jsonrpc.JsonRpcMethod.positionalParameters;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class JsonRpcTest {
    AtomicInteger n = new AtomicInteger();
    JsonRpc jsonRpc;

    @BeforeEach
    void before() throws IOException {
        PipedOutputStream os = new PipedOutputStream();
        PipedInputStream is = new PipedInputStream(os);
        jsonRpc = new JsonRpc(new TraceMessageHandler(new HeaderDelimitedMessageHandler(is, os)));
    }

    @AfterEach
    void after() {
        jsonRpc.shutdown();
    }

    @Test
    void requestResponse() throws ExecutionException, InterruptedException, TimeoutException {
        JsonRpcSuccess<String> response = jsonRpc
                .method("hello", namedParameters("name", (String name) -> "Hello " + name))
                .start()
                .<String>send(JsonRpcRequest.newRequest("hello")
                        .id(n.incrementAndGet())
                        .namedParameter("name", "Jon")
                        .build())
                .get(5, TimeUnit.SECONDS);

        assertThat(response.getResult()).isEqualTo("Hello Jon");
    }

    @Test
    void methodThrowsException() {
        assertThatThrownBy(() -> jsonRpc
                .method("hello", namedParameters("name", (String name) -> {
                    throw new IllegalStateException("Boom");
                }))
                .start()
                .send(JsonRpcRequest.newRequest("hello")
                        .id(n.incrementAndGet())
                        .namedParameter("name", "Jon")
                        .build())
                .get(5, TimeUnit.SECONDS)
        ).hasCauseInstanceOf(JsonRpcException.class);
    }

    @Test
    void notification() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        jsonRpc
                .method("hello", namedParameters("name", (String name) -> {
                    assertThat(name).isEqualTo("Jon");
                    latch.countDown();
                    return null;
                }))
                .start()
                .notification(JsonRpcRequest.newRequest("hello")
                        .id(n.incrementAndGet())
                        .namedParameter("name", "Jon")
                        .build());
        assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue();
    }

    @Test
    void positional() throws ExecutionException, InterruptedException, TimeoutException {
        JsonRpcSuccess<String> response = jsonRpc
                .method("hello", positionalParameters("name", (List<String> names) -> "Hello " + String.join(" and ", names)))
                .start()
                .<String>send(JsonRpcRequest.newRequest("hello")
                        .id(n.incrementAndGet())
                        .positionalParameters("Jon", "Jim")
                        .build())
                .get(5, TimeUnit.SECONDS);

        assertThat(response.getResult()).isEqualTo("Hello Jon and Jim");
    }

    @Test
    void positionalRequestNamedParamHandler() {
        assertThatThrownBy(() -> jsonRpc
                .method("hello", namedParameters("name", (String name) -> "Hello " + name))
                .start()
                .send(JsonRpcRequest.newRequest("hello")
                        .id(n.incrementAndGet())
                        .positionalParameters("Jon")
                        .build())
                .get(5, TimeUnit.SECONDS)
        ).hasCauseInstanceOf(JsonRpcException.class);
    }
}
