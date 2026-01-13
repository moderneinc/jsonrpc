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

import io.moderne.jsonrpc.formatter.MessageFormatter;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;

@SuppressWarnings("unused")
public abstract class JsonRpcMethod<P> {

    final Object convertAndHandle(Object params, MessageFormatter formatter) throws Exception {
        Type paramType = ((ParameterizedType) getClass().getGenericSuperclass())
                .getActualTypeArguments()[0];
        if (Void.class.equals(paramType)) {
            return handle(null);
        }
        return handle(formatter.convertValue(params, paramType));
    }

    protected abstract Object handle(P params) throws Exception;
}
