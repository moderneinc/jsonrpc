package io.moderne.jsonrpc;

import lombok.EqualsAndHashCode;
import lombok.Value;

@Value
@EqualsAndHashCode(callSuper = false)
public class JsonRpcSuccess<R> extends JsonRpcResponse {
    String id;
    R result;
}
