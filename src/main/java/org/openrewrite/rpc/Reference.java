package org.openrewrite.rpc;

import lombok.Getter;
import org.jspecify.annotations.Nullable;

@Getter
public class Reference {
    @SuppressWarnings("AccessStaticViaInstance")
    private static final ThreadLocal<Reference> flyweight = new ThreadLocal<>()
            .withInitial(Reference::new);

    @Nullable
    private Object value;

    public static Reference asRef(@Nullable Object t) {
        Reference ref = flyweight.get();
        ref.value = t;
        return ref;
    }

    public static <T> @Nullable T getValue(@Nullable Object maybeRef) {
        // noinspection unchecked
        return (T) (maybeRef instanceof Reference ? ((Reference) maybeRef).getValue() : maybeRef);
    }
}
