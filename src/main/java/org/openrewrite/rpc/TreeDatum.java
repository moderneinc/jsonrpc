package org.openrewrite.rpc;

import lombok.Value;
import org.jspecify.annotations.Nullable;
import org.openrewrite.Tree;

import java.util.UUID;

@Value
public class TreeDatum {
    public static final int ADDED_LIST_ITEM = -1;

    State state;

    /**
     * Not always a {@link Tree}. This can be a marker or leaf element
     * value element of a tree as well. At any rate, it's a part of
     * the data modeled by a {@link Tree}.
     */
    @Nullable
    Object value;

    public <V> V getValue() {
        //noinspection DataFlowIssue
        return (V) value;
    }

    public enum State {
        NO_CHANGE,
        ADD,
        DELETE,
        CHANGE
    }

    /**
     * An ID-containing model object that is being created anew. This is
     * used to create an initial shell of the object which is filled in by
     * future messages.
     */
    @Value
    public static class Add {
        UUID id;

        /**
         * Used to construct a new instance of the class with
         * initially only the ID populated. Subsequent {@link TreeDatum}
         * messages will fill in the object fully.
         */
        String className;
    }
}
