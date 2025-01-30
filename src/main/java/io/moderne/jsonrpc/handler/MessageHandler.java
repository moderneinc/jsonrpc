package io.moderne.jsonrpc.handler;

import io.moderne.jsonrpc.JsonRpcMessage;

public interface MessageHandler {

    JsonRpcMessage receive();

    void send(JsonRpcMessage msg);
}
