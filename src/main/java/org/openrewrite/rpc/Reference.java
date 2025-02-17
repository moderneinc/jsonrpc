package org.openrewrite.rpc;

import lombok.Getter;
import org.jspecify.annotations.Nullable;

/**
 * An instance that is passed to the remote by reference (i.e. for instances
 * that are referentially deduplicated in the LST).
 */
@Getter
public class Reference {
    @SuppressWarnings("AccessStaticViaInstance")
    private static final ThreadLocal<Reference> flyweight = new ThreadLocal<>()
            .withInitial(Reference::new);

    @Nullable
    private Object value;

    /**
     * @param t Any instance.
     * @return A reference wrapper, which assists the sender to know when to pass by reference
     * rather than by value.
     */
    public static Reference asRef(@Nullable Object t) {
        Reference ref = flyweight.get();
        ref.value = t;
        return ref;
    }

    /**
     * @param maybeRef A reference (or not).
     * @param <T>      The type of the value.
     * @return The value of the reference, or the value itself if it is not a reference.
     */
    public static <T> @Nullable T getValue(@Nullable Object maybeRef) {
        // noinspection unchecked
        return (T) (maybeRef instanceof Reference ? ((Reference) maybeRef).getValue() : maybeRef);
    }
}
