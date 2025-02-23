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

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import io.moderne.jsonrpc.internal.SnowflakeId;
import lombok.EqualsAndHashCode;
import lombok.RequiredArgsConstructor;
import lombok.Value;

import java.util.*;

@Value
@EqualsAndHashCode(callSuper = false)
public class JsonRpcRequest extends JsonRpcMessage {
    private static final ObjectMapper mapper = new ObjectMapper()
            .disable(SerializationFeature.FAIL_ON_EMPTY_BEANS);

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
        private String id = SnowflakeId.generateId();

        private final Map<String, Object> namedParameters = new LinkedHashMap<>();
        private List<Object> positionalParameters;

        public Builder id(Number id) {
            return id(id.toString());
        }

        public Builder id(String id) {
            this.id = id;
            return this;
        }

        public Builder namedParameters(Object o) {
            return namedParameters(mapper.convertValue(o, new TypeReference<Map<String, Object>>() {
            }));
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
