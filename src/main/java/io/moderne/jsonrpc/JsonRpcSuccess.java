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

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import io.moderne.jsonrpc.formatter.MessageFormatter;
import lombok.*;
import org.jspecify.annotations.Nullable;

@EqualsAndHashCode(callSuper = false)
@ToString
public class JsonRpcSuccess extends JsonRpcResponse {

    /**
     * String or Integer
     */
    @JsonDeserialize(using = JsonRpcIdDeserializer.class)
    @Getter
    private final Object id;

    /**
     * No need for polymorphic deserialization here, since the result type will
     * always be known by the requester.
     */
    @Getter
    @Nullable
    private final Object result;

    @JsonCreator
    public JsonRpcSuccess(
            @JsonProperty("id") Object id,
            @JsonProperty("result") @Nullable Object result) {
        this.id = id;
        this.result = result;
    }

    /**
     * The formatter to use for converting results.
     * -- SETTER --
     *  Sets the formatter to use for result conversion.
     *  This is called by MessageFormatter after deserialization.
     */
    @Setter
    @JsonIgnore
    @EqualsAndHashCode.Exclude
    @ToString.Exclude
    @Nullable
    private transient MessageFormatter formatter;

    public <V> V getResult(Class<V> resultType) {
        assert formatter != null;
        return formatter.convertValue(result, resultType);
    }
}
