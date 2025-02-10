package org.openrewrite.rpc;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.cfg.ConstructorDetector;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.module.paramnames.ParameterNamesModule;
import lombok.Value;
import org.jspecify.annotations.Nullable;
import org.openrewrite.Tree;

import java.util.Map;

@Value
public class TreeDatum {
    private static final ObjectMapper mapper = JsonMapper.builder()
            // to be able to construct classes that have @Data and a single field
            // see https://cowtowncoder.medium.com/jackson-2-12-most-wanted-3-5-246624e2d3d0
            .constructorDetector(ConstructorDetector.USE_PROPERTIES_BASED)
            .build()
            .registerModules(new ParameterNamesModule(), new JavaTimeModule())
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .setSerializationInclusion(JsonInclude.Include.NON_NULL);

    public static final int ADDED_LIST_ITEM = -1;

    State state;

    /**
     * Used to construct a new instance of the class with
     * initially only the ID populated. Subsequent {@link TreeDatum}
     * messages will fill in the object fully.
     */
    @Nullable
    String valueType;

    /**
     * Not always a {@link Tree}. This can be a marker or leaf element
     * value element of a tree as well. At any rate, it's a part of
     * the data modeled by a {@link Tree}.
     * <p>
     * In the case of a {@link Tree} ADD, this is the tree ID.
     */
    @Nullable
    Object value;

    public <V> V getValue() {
        if (value instanceof Map) {
            try {
                //noinspection unchecked
                return (V) mapper.convertValue(value, Class.forName(valueType));
            } catch (ClassNotFoundException e) {
                throw new RuntimeException(e);
            }
        }
        //noinspection DataFlowIssue,unchecked
        return (V) value;
    }

    public enum State {
        NO_CHANGE,
        ADD,
        DELETE,
        CHANGE
    }
}
