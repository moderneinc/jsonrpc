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

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.cfg.ConstructorDetector;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.module.paramnames.ParameterNamesModule;

import java.util.List;
import java.util.Map;

@SuppressWarnings("unused")
public interface JsonRpcMethod {
    Object handle(Object params) throws Exception;

    @SuppressWarnings("unchecked")
    static <P> JsonRpcMethod named(String p1, P1<P> handler) {
        return params -> {
            if (params instanceof Map) {
                Map<String, Object> paramMap = (Map<String, Object>) params;
                return handler.handle((P) paramMap.get(p1));
            } else {
                throw new Exception("Expected a named parameter map for method");
            }
        };
    }

    @SuppressWarnings("unchecked")
    static <P> JsonRpcMethod positional(P1<List<P>> handler) {
        return params -> {
            if (params instanceof List) {
                List<P> paramList = (List<P>) params;
                return handler.handle(paramList);
            } else {
                throw new Exception("Expected a positional parameter list for method");
            }
        };
    }

    @SuppressWarnings("unchecked")
    static <P, Q> JsonRpcMethod named(String p1, String p2, P2<P, Q> handler) {
        return params -> {
            if (params instanceof Map) {
                Map<String, Object> paramMap = (Map<String, Object>) params;
                return handler.handle((P) paramMap.get(p1), (Q) paramMap.get(p2));
            } else {
                throw new Exception("Expected a named parameter map for method");
            }
        };
    }

    static <T> JsonRpcMethod typed(Class<T> type, P1<T> handler) {
        return params -> handler.handle(JsonRpcMethodParameterUtils
                .convertNamedParameters(params, type));
    }

    @SuppressWarnings("unchecked")
    static <P, Q, R> JsonRpcMethod named(String p1, String p2, String p3, P3<P, Q, R> handler) {
        return params -> {
            if (params instanceof Map) {
                Map<String, Object> paramMap = (Map<String, Object>) params;
                return handler.handle((P) paramMap.get(p1), (Q) paramMap.get(p2), (R) paramMap.get(p3));
            } else {
                throw new Exception("Expected a named parameter map for method");
            }
        };
    }

    @SuppressWarnings("unchecked")
    static <P, Q, R, S> JsonRpcMethod named(String p1, String p2, String p3, String p4, P4<P, Q, R, S> handler) {
        return params -> {
            if (params instanceof Map) {
                Map<String, Object> paramMap = (Map<String, Object>) params;
                return handler.handle((P) paramMap.get(p1), (Q) paramMap.get(p2), (R) paramMap.get(p3), (S) paramMap.get(p4));
            } else {
                throw new Exception("Expected a named parameter map for method");
            }
        };
    }

    interface P1<P> {
        Object handle(P p1) throws Exception;
    }

    interface P2<P, Q> {
        Object handle(P p1, Q p2) throws Exception;
    }

    interface P3<P, Q, R> {
        Object handle(P p1, Q p2, R p3) throws Exception;
    }

    interface P4<P, Q, R, S> {
        Object handle(P p1, Q p2, R p3, S p4) throws Exception;
    }
}

class JsonRpcMethodParameterUtils {
    private static final ObjectMapper mapper = JsonMapper.builder()
            // to be able to construct classes that have @Data and a single field
            // see https://cowtowncoder.medium.com/jackson-2-12-most-wanted-3-5-246624e2d3d0
            .constructorDetector(ConstructorDetector.USE_PROPERTIES_BASED)
            .build()
            .registerModules(new ParameterNamesModule(), new JavaTimeModule())
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .setSerializationInclusion(JsonInclude.Include.NON_NULL);

    public static <T> T convertNamedParameters(Object params, Class<T> clazz) throws Exception {
        if (params instanceof Map) {
            //noinspection unchecked
            Map<String, Object> paramMap = (Map<String, Object>) params;
            return mapper.convertValue(paramMap, clazz);
        } else {
            throw new Exception("Expected a named parameter map for method");
        }
    }
}
