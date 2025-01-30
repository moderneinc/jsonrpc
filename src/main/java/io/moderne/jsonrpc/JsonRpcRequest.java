package io.moderne.jsonrpc;

import lombok.EqualsAndHashCode;
import lombok.RequiredArgsConstructor;
import lombok.Value;

import java.util.*;

@Value
@EqualsAndHashCode(callSuper = false)
public class JsonRpcRequest extends JsonRpcMessage {
    String id;
    String method;

    /**
     * Either a Map of named parameters or a List of positional parameters.
     */
    Object params;

    public static Builder newRequest(String method) {
        return new Builder(method);
    }

    @RequiredArgsConstructor
    public static class Builder {
        private final String method;
        private String id = UUID.randomUUID().toString();

        private final Map<String, Object> namedParameters = new LinkedHashMap<>();
        private List<Object> positionalParameters;

        public Builder id(Number id) {
            return id(id.toString());
        }

        public Builder id(String id) {
            this.id = id;
            return this;
        }

        public Builder namedParameter(String name, Object value) {
            if (positionalParameters != null) {
                throw new IllegalStateException("Cannot mix named and positional parameters");
            }
            namedParameters.put(name, value);
            return this;
        }

        public Builder namedParameters(Map<String, Object> params) {
            if (positionalParameters != null) {
                throw new IllegalStateException("Cannot mix named and positional parameters");
            }
            this.namedParameters.putAll(params);
            return this;
        }

        @SafeVarargs
        public final <P> Builder positionalParameters(P... params) {
            List<Object> list = new ArrayList<>(params.length);
            Collections.addAll(list, params);
            return positionalParameters(list);
        }

        public Builder positionalParameters(List<Object> params) {
            if (!namedParameters.isEmpty()) {
                throw new IllegalStateException("Cannot mix named and positional parameters");
            }
            positionalParameters = params;
            return this;
        }

        public JsonRpcRequest build() {
            return new JsonRpcRequest(id, method,
                    positionalParameters == null ? namedParameters : positionalParameters);
        }
    }
}
