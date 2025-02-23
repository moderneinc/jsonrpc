package io.moderne.jsonrpc;

import java.util.List;

public interface PositionalParamJsonRpcMethod<P> extends JsonRpcMethod {

    @Override
    default Object handle(Object params) throws Exception {
        if (params instanceof List) {
            //noinspection unchecked
            List<P> paramList = (List<P>) params;
            return accept(paramList);
        } else {
            throw new Exception("Expected a positional parameter list for method");
        }
    }

    Object accept(List<P> params) throws Exception;
}
