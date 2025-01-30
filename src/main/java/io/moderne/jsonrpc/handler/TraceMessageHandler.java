package io.moderne.jsonrpc.handler;

import io.moderne.jsonrpc.JsonRpcMessage;
import io.moderne.jsonrpc.JsonRpcRequest;
import io.moderne.jsonrpc.JsonRpcResponse;
import lombok.RequiredArgsConstructor;

import java.io.PrintStream;

@RequiredArgsConstructor
public class TraceMessageHandler implements MessageHandler {
    private final MessageHandler delegate;
    private final PrintStream out;

    public TraceMessageHandler(MessageHandler delegate) {
        this(delegate, System.out);
    }

    @Override
    public JsonRpcMessage receive() {
        JsonRpcMessage message = delegate.receive();
        if (message instanceof JsonRpcResponse) {
            out.println("<-- " + message);
        }
        return message;
    }

    @Override
    public void send(JsonRpcMessage message) {
        if (message instanceof JsonRpcRequest) {
            out.println("--> " + message);
        }
        delegate.send(message);
    }
}
