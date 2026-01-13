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

import java.io.IOException;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.util.List;
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
                new TraceMessageHandler("both", new HeaderDelimitedMessageHandler(formatter, is, os)),
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

        assertThat(response.getResult()).isEqualTo("Hello Jon");
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

        assertThat(response.getResult()).isEqualTo("Hello Jon");
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

        assertThat(response.getResult()).isEqualTo("Hello Jon and Jim");
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

    record Person(String name) {
    }

    static class HelloController extends JsonRpcMethod<Person> {
        @Override
        public Object handle(Person person) {
            return "Hello " + person.name;
        }
    }
}
