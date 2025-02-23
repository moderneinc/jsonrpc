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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class JsonRpcTest {
    AtomicInteger n = new AtomicInteger();
    JsonRpc jsonRpc;

    @BeforeEach
    void before() throws IOException {
        PipedOutputStream os = new PipedOutputStream();
        PipedInputStream is = new PipedInputStream(os);
        jsonRpc = new JsonRpc(new TraceMessageHandler("both", new HeaderDelimitedMessageHandler(is, os)));
    }

    @AfterEach
    void after() {
        jsonRpc.shutdown();
    }

    @Test
    void requestResponse() throws ExecutionException, InterruptedException, TimeoutException {
        JsonRpcSuccess response = jsonRpc
                .handle("hello", new HelloController())
                .bind()
                .send(JsonRpcRequest.newRequest("hello")
                        .id(n.incrementAndGet())
                        .namedParameters(new Person("Jon"))
                        .build())
                .get(5, TimeUnit.SECONDS);

        assertThat(response.getResult()).isEqualTo("Hello Jon");
    }

    @Test
    void handleThrowsException() {
        assertThatThrownBy(() -> jsonRpc
                .<Person>handle("hello", person -> {
                    throw new IllegalStateException("Boom");
                })
                .bind()
                .send(JsonRpcRequest.newRequest("hello")
                        .id(n.incrementAndGet())
                        .namedParameters(new Person("Jon"))
                        .build())
                .get(5, TimeUnit.SECONDS)
        ).hasCauseInstanceOf(JsonRpcException.class);
    }

    @Test
    void notification() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        jsonRpc
                .<Person>handle("hello", person -> {
                    assertThat(person.name).isEqualTo("Jon");
                    latch.countDown();
                    return null;
                })
                .bind()
                .notification(JsonRpcRequest.newRequest("hello")
                        .id(n.incrementAndGet())
                        .namedParameters(new Person("Jon"))
                        .build());
        assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue();
    }

    @Test
    void positional() throws ExecutionException, InterruptedException, TimeoutException {
        JsonRpcSuccess response = jsonRpc
                .<String>handleList("hello", names ->
                        "Hello " + String.join(" and ", names))
                .bind()
                .send(JsonRpcRequest.newRequest("hello")
                        .id(n.incrementAndGet())
                        .positionalParameters("Jon", "Jim")
                        .build())
                .get(5, TimeUnit.SECONDS);

        assertThat(response.getResult()).isEqualTo("Hello Jon and Jim");
    }

    @Test
    void positionalRequestMismatchedToNamedParameterMethod() {
        assertThatThrownBy(() -> jsonRpc
                .handle("hello", new HelloController())
                .bind()
                .send(JsonRpcRequest.newRequest("hello")
                        .id(n.incrementAndGet())
                        .positionalParameters("Jon")
                        .build())
                .get(5, TimeUnit.SECONDS)
        ).hasCauseInstanceOf(JsonRpcException.class);
    }

    record Person(String name) {
    }

    static class HelloController implements NamedParamJsonRpcMethod<Person> {
        @Override
        public Object accept(Person person) {
            return "Hello " + person.name;
        }
    }
}
