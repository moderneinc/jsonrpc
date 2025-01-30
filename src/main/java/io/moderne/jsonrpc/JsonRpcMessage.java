package io.moderne.jsonrpc;

import lombok.Getter;

@Getter
public abstract class JsonRpcMessage {
    private final String jsonrpc = "2.0";

    public abstract String getId();
}
