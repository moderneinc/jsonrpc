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

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.moderne.jsonrpc.formatter.MessageFormatter;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;
import org.jspecify.annotations.Nullable;

@EqualsAndHashCode(callSuper = false)
@ToString
public class JsonRpcSuccess extends JsonRpcResponse {

    @Getter
    private final Object id;

    @Getter
    @Nullable
    private final Object result;

    @JsonIgnore
    @EqualsAndHashCode.Exclude
    @ToString.Exclude
    @Nullable
    private final transient MessageFormatter formatter;

    public JsonRpcSuccess(Object id, @Nullable Object result) {
        this(id, result, null);
    }

    private JsonRpcSuccess(Object id, @Nullable Object result, @Nullable MessageFormatter formatter) {
        this.id = id;
        this.result = result;
        this.formatter = formatter;
    }

    public static JsonRpcSuccess fromPayload(Object id, @Nullable Object result, @Nullable MessageFormatter formatter) {
        return new JsonRpcSuccess(id, result, formatter);
    }

    public <V> V getResult(Class<V> resultType) {
        assert formatter != null;
        return formatter.convertValue(result, resultType);
    }
}
