package io.moderne.jsonrpc;

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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JsonRpcListenerTest {
    RecordingListener listener = new RecordingListener();

    /**
     * Sends to itself, so every request is observed both as sent and as received.
     */
    JsonRpc loopback;

    @BeforeEach
    void before() throws IOException {
        PipedOutputStream os = new PipedOutputStream();
        PipedInputStream is = new PipedInputStream(os);
        loopback = new JsonRpc(new HeaderDelimitedMessageHandler(is, os), new JsonMessageFormatter())
                .listener(listener);
    }

    @AfterEach
    void after() {
        loopback.shutdown();
    }

    @Test
    void successIsReportedOnBothSides() throws Exception {
        loopback.rpc("hello", handler(() -> "Hello")).bind()
                .send(JsonRpcRequest.newRequest("hello")).get(5, TimeUnit.SECONDS);

        listener.awaitEvents(2);
        assertThat(listener.events).containsExactlyInAnyOrder("sent:hello:SUCCESS", "received:hello:SUCCESS");
    }

    @Test
    void failingHandlerIsAnErrorOnBothSides() throws Exception {
        CompletableFuture<JsonRpcSuccess> response = loopback.rpc("hello", handler(() -> {
            throw new IllegalStateException("Boom");
        })).bind().send(JsonRpcRequest.newRequest("hello"));

        assertThatThrownBy(() -> response.get(5, TimeUnit.SECONDS)).hasCauseInstanceOf(JsonRpcException.class);
        listener.awaitEvents(2);
        assertThat(listener.events).containsExactlyInAnyOrder("sent:hello:ERROR", "received:hello:ERROR");
    }

    @Test
    void callerTimeoutIsReportedInsteadOfTheLateResponse() throws Exception {
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

        // The late reply is on the wire once the receiving side reports it; a request sent after
        // it is answered after it, so by then the late reply has been read.
        listener.awaitEvent("received:slow:SUCCESS");
        loopback.send(JsonRpcRequest.newRequest("ping")).get(5, TimeUnit.SECONDS);

        assertThat(listener.events).filteredOn(e -> e.startsWith("sent:slow")).containsExactly("sent:slow:TIMEOUT");
    }

    @Test
    void closedConnectionIsReportedAsClosed() throws Exception {
        JsonRpc closed = new JsonRpc(
                new HeaderDelimitedMessageHandler(new ByteArrayInputStream(new byte[0]), new ByteArrayOutputStream()),
                new JsonMessageFormatter())
                .listener(listener)
                .bind();
        try {
            CompletableFuture<JsonRpcSuccess> response = closed.send(JsonRpcRequest.newRequest("never-answered"));

            assertThatThrownBy(() -> response.get(5, TimeUnit.SECONDS)).hasCauseInstanceOf(JsonRpcException.class);
            assertThat(listener.events).containsExactly("sent:never-answered:CLOSED");
        } finally {
            closed.shutdown();
        }
    }

    @Test
    void listenerExceptionsDoNotAffectRequests() throws Exception {
        JsonRpcSuccess response = loopback.listener(new JsonRpcListener() {
                    @Override
                    public Completion requestSent(JsonRpcRequest request) {
                        return outcome -> {
                            throw new IllegalStateException("from completion");
                        };
                    }

                    @Override
                    public Completion requestReceived(JsonRpcRequest request) {
                        throw new IllegalStateException("from start");
                    }
                })
                .rpc("hello", handler(() -> "Hello")).bind()
                .send(JsonRpcRequest.newRequest("hello")).get(5, TimeUnit.SECONDS);

        assertThat(response.getResult(String.class)).isEqualTo("Hello");
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

    private static class RecordingListener implements JsonRpcListener {
        final List<String> events = new CopyOnWriteArrayList<>();

        @Override
        public Completion requestSent(JsonRpcRequest request) {
            return outcome -> events.add("sent:" + request.getMethod() + ":" + outcome);
        }

        @Override
        public Completion requestReceived(JsonRpcRequest request) {
            return outcome -> events.add("received:" + request.getMethod() + ":" + outcome);
        }

        /**
         * The receiving side reports after sending its reply, which can land after the caller has
         * the response.
         */
        void awaitEvents(int count) throws InterruptedException {
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
            while (events.size() < count && System.nanoTime() < deadline) {
                Thread.sleep(10);
            }
        }

        void awaitEvent(String event) throws InterruptedException {
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
            while (!events.contains(event) && System.nanoTime() < deadline) {
                Thread.sleep(10);
            }
        }
    }
}
