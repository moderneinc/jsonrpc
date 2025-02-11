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
import org.openrewrite.SourceFile;
import org.openrewrite.Tree;
import org.openrewrite.TreeVisitor;
import org.openrewrite.json.JsonVisitor;
import org.openrewrite.json.tree.Json;
import org.openrewrite.rpc.RecipeRpc;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import java.io.IOException;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.time.Duration;

import static org.openrewrite.json.Assertions.json;
import static org.openrewrite.test.RewriteTest.toRecipe;

public class JsonSendReceiveTest implements RewriteTest {
    RecipeRpc server;
    RecipeRpc client;

    @BeforeEach
    void before() throws IOException {
        PipedOutputStream serverOut = new PipedOutputStream();
        PipedOutputStream clientOut = new PipedOutputStream();
        PipedInputStream serverIn = new PipedInputStream(clientOut);
        PipedInputStream clientIn = new PipedInputStream(serverOut);

        JsonRpc serverJsonRpc = new JsonRpc(new TraceMessageHandler("server",
          new HeaderDelimitedMessageHandler(serverIn, serverOut)));
        server = new RecipeRpc(serverJsonRpc, Duration.ofSeconds(10));

        JsonRpc clientJsonRpc = new JsonRpc(new TraceMessageHandler("client",
          new HeaderDelimitedMessageHandler(clientIn, clientOut)));
        client = new RecipeRpc(clientJsonRpc, Duration.ofSeconds(10));
    }

    @AfterEach
    void after() {
        server.shutdown();
        client.shutdown();
    }

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(toRecipe(() -> new TreeVisitor<>() {
            @SneakyThrows
            @Override
            public Tree preVisit(@NonNull Tree tree, ExecutionContext ctx) {
                Tree t = server.visit((SourceFile) tree, ChangeValue.class.getName(), 0);
                stopAfterPreVisit();
                return t;
            }
        })).cycles(1).expectedCyclesThatMakeChanges(1);
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
                return literal.withValue("changed").withSource("\"changed\"");
            }
            return literal;
        }
    }
}
