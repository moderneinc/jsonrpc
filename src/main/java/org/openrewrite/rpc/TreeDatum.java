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

/**
 * A single piece of data in a tree, which can be a marker, leaf value, tree element, etc.
 */
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

    /**
     * Used for instances that should be referentially equal in multiple parts
     * of the tree (e.g. Space, some Marker types, JavaType). The first time the
     * object is seen, it is transmitted with a ref ID. Subsequent references
     * to the same object are transmitted with the ref ID and no value.
     */
    @Nullable
    Integer ref;

    public TreeDatum(State state, @Nullable String valueType, @Nullable Object value, @Nullable Integer ref) {
        this.state = state;
        this.valueType = valueType;
        this.value = value;
        this.ref = ref;
    }

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
