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

import java.util.List;
import java.util.Map;

public interface JsonRpcMethod {
    Object handle(Object params) throws Exception;

    @SuppressWarnings("unchecked")
    static <P> JsonRpcMethod namedParameters(String p1, P1<P> handler) {
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
    static <P> JsonRpcMethod positionalParameters(String p1, P1<List<P>> handler) {
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
    static <P, Q> JsonRpcMethod namedParameters(String p1, String p2, P2<P, Q> handler) {
        return params -> {
            if (params instanceof Map) {
                Map<String, Object> paramMap = (Map<String, Object>) params;
                return handler.handle((P) paramMap.get(p1), (Q) paramMap.get(p2));
            } else {
                throw new Exception("Expected a named parameter map for method");
            }
        };
    }

    @SuppressWarnings("unchecked")
    static <P, Q, R> JsonRpcMethod namedParameters(String p1, String p2, String p3, P3<P, Q, R> handler) {
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
    static <P, Q, R, S> JsonRpcMethod namedParameters(String p1, String p2, String p3, String p4, P4<P, Q, R, S> handler) {
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
        Object handle(P p1);
    }

    interface P2<P, Q> {
        Object handle(P p1, Q p2);
    }

    interface P3<P, Q, R> {
        Object handle(P p1, Q p2, R p3);
    }

    interface P4<P, Q, R, S> {
        Object handle(P p1, Q p2, R p3, S p4);
    }
}
