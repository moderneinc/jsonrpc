package org.openrewrite.json.rpc;

import io.moderne.jsonrpc.JsonRpc;
import io.moderne.jsonrpc.handler.HeaderDelimitedMessageHandler;
import io.moderne.jsonrpc.handler.TraceMessageHandler;
import lombok.SneakyThrows;
import org.jspecify.annotations.NonNull;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Tree;
import org.openrewrite.TreeVisitor;
import org.openrewrite.json.JsonVisitor;
import org.openrewrite.json.tree.Json;
import org.openrewrite.rpc.RecipeRpcClient;
import org.openrewrite.rpc.RecipeRpcServer;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import java.io.IOException;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.time.Duration;

import static org.openrewrite.json.Assertions.json;
import static org.openrewrite.test.RewriteTest.toRecipe;

public class JsonSendReceiveTest implements RewriteTest {

    JsonRpc jsonRpc;
    RecipeRpcServer server;
    RecipeRpcClient client;

    @BeforeEach
    void before() throws IOException {
        PipedOutputStream os = new PipedOutputStream();
        PipedInputStream is = new PipedInputStream(os);
        jsonRpc = new JsonRpc(new TraceMessageHandler(new HeaderDelimitedMessageHandler(is, os)));
        server = new RecipeRpcServer(jsonRpc, Duration.ofSeconds(10));
        client = new RecipeRpcClient(jsonRpc).start();
    }

    @AfterEach
    void after() {
        jsonRpc.shutdown();
    }

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(toRecipe(() -> new TreeVisitor<>() {
            @SneakyThrows
            @Override
            public Tree preVisit(@NonNull Tree tree, ExecutionContext ctx) {
                return server.visit(getCursor(), ChangeValue.class.getName(), 0);
            }
        }));
    }

    @Test
    void sendReceiveIdempotence() {
        rewriteRun(
          //language=json
          json(
            """
              {
                "key": "value",
                "array": [1, 2, 3]
              }
              """,
            """
              {
                "key": "changed",
                "array": [1, 2, 3]
              }
              """
          )
        );
    }

    static class ChangeValue extends JsonVisitor<Integer> {
        @Override
        public Json visitLiteral(Json.Literal literal, Integer p) {
            if (literal.getValue().equals("value")) {
                return literal.withValue("changed");
            }
            return literal;
        }
    }
}
