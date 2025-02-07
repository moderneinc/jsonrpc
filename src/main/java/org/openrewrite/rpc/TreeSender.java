package org.openrewrite.rpc;

import lombok.Getter;
import lombok.Value;
import org.jspecify.annotations.Nullable;
import org.openrewrite.Tree;
import org.openrewrite.TreeVisitor;

import java.util.*;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Function;

import static java.util.stream.Collectors.toList;
import static org.openrewrite.rpc.TreeDatum.ADDED_LIST_ITEM;

public class TreeSender {
    private @Nullable Tree beforeParent;
    private final TreeVisitor<? extends Tree, TreeSender> visitor;
    private final CompletableFuture<Integer> done;

    @Getter
    private final BlockingQueue<TreeDatum> data = new ArrayBlockingQueue<>(10);

    /**
     * @param visitor     The language-specific visitor to use to send the tree
     * @param remoteState The state last known to the remote process
     * @param current     The current state of the tree to send
     */
    public TreeSender(TreeVisitor<? extends Tree, TreeSender> visitor,
                      @Nullable Tree remoteState,
                      @Nullable Tree current) {
        this.visitor = visitor;
        this.beforeParent = remoteState;

        // This async process allows the visitor to fill up the data queue in a non-blocking way
        // so that batches of queued data can be sent periodically to the remote process until
        // the visit is all the way complete.
        this.done = CompletableFuture.supplyAsync(() -> {
            visitor.visit(current, this);
            return 0;
        });
    }

    public boolean isDone() {
        return done.isDone();
    }

    public <P> P send(@Nullable P parent, BiConsumer<ForEachComponent<P>, P> accept) {
        if (parent == null) {
            //noinspection DataFlowIssue
            return null;
        }
        accept.accept(new ForEachComponent<>(parent), parent);
        return parent;
    }

    @Value
    public class ForEachComponent<P> {
        P parent;

        public <V> void value(@Nullable V value, Function<P, V> beforeFn) {
            V before = beforeFn.apply(parent);
            try {
                if (before == value) {
                    data.put(new TreeDatum(TreeDatum.State.NO_CHANGE, null));
                } else if (before == null) {
                    data.put(new TreeDatum(TreeDatum.State.ADD, value));
                } else if (value == null) {
                    data.put(new TreeDatum(TreeDatum.State.DELETE, null));
                } else {
                    data.put(new TreeDatum(TreeDatum.State.CHANGE, value));
                }
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }

        public <T> void padding(@Nullable T after, Function<P, T> beforeFn,
                                BiFunction<@Nullable T, TreeSender, @Nullable T> visitPadding) {
            T before = beforeFn.apply(parent);
            try {
                if (before == after) {
                    data.put(new TreeDatum(TreeDatum.State.NO_CHANGE, null));
                } else if (before == null) {
                    data.put(new TreeDatum(TreeDatum.State.ADD, null));
                } else if (after == null) {
                    data.put(new TreeDatum(TreeDatum.State.DELETE, null));
                } else {
                    data.put(new TreeDatum(TreeDatum.State.CHANGE, null));
                    visitPadding.apply(after, TreeSender.this);
                }
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }

        public <T extends Tree> void tree(@Nullable T after, Function<P, T> beforeFn) {
            //noinspection unchecked
            Tree before = beforeParent == null ? null : beforeFn.apply((P) beforeParent);
            try {
                if (before == after) {
                    data.put(new TreeDatum(TreeDatum.State.NO_CHANGE, null));
                } else if (before == null) {
                    data.put(new TreeDatum(TreeDatum.State.ADD, after.getId()));
                } else if (after == null) {
                    data.put(new TreeDatum(TreeDatum.State.DELETE, null));
                } else {
                    Tree priorParent = beforeParent;
                    beforeParent = before;
                    data.put(new TreeDatum(TreeDatum.State.CHANGE, null));
                    visitor.visit(after, TreeSender.this);
                    beforeParent = priorParent;
                }
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }

        public <T> void paddingList(@Nullable List<T> after,
                                    Function<P, List<T>> beforeFn,
                                    Function<T, UUID> elementId,
                                    BiFunction<@Nullable T, TreeSender, @Nullable T> visitPadding) {
            listDifferences(
                    after,
                    beforeFn.apply(parent),
                    elementId,
                    t -> null,
                    t -> visitPadding.apply(t, TreeSender.this)
            );
        }

        public <T extends Tree> void trees(@Nullable List<T> after, Function<P, @Nullable List<T>> beforeFn) {
            //noinspection unchecked
            listDifferences(
                    after,
                    beforeFn.apply(parent),
                    Tree::getId,
                    Tree::getId,
                    (T t) -> (T) visitor.visit(t, TreeSender.this)
            );
        }

        <T> void listDifferences(@Nullable List<T> after,
                                 @Nullable List<T> before,
                                 Function<T, UUID> elementId,
                                 Function<T, ?> valueOnAdd,
                                 Function<@Nullable T, @Nullable T> onChange) {
            for (TreeDatum datum : TreeSender.listDifferences(after, before, elementId,
                    valueOnAdd, onChange)) {
                try {
                    data.put(datum);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }
        }
    }

    static <T> List<TreeDatum> listDifferences(@Nullable List<T> after,
                                               @Nullable List<T> before,
                                               Function<T, UUID> elementId,
                                               Function<T, ?> valueOnAdd,
                                               Function<@Nullable T, @Nullable T> onChange) {
        List<TreeDatum> data = new ArrayList<>(Math.max(
                after == null ? 0 : after.size(),
                before == null ? 0 : before.size()));
        if (before == after) {
            data.add(new TreeDatum(TreeDatum.State.NO_CHANGE, null));
        } else if (after == null) {
            data.add(new TreeDatum(TreeDatum.State.DELETE, null));
        } else if (before == null) {
            data.add(new TreeDatum(TreeDatum.State.CHANGE, after.stream()
                    .map(a -> ADDED_LIST_ITEM).collect(toList())));
            for (T t : after) {
                data.add(new TreeDatum(TreeDatum.State.ADD, valueOnAdd.apply(t)));
            }
        } else {

            Map<UUID, Integer> beforeSet = new IdentityHashMap<>();
            for (int i = 0; i < before.size(); i++) {
                beforeSet.put(elementId.apply(before.get(i)), i);
            }

            List<Integer> positions = new ArrayList<>();
            for (T t : after) {
                Integer beforePos = beforeSet.get(elementId.apply(t));
                positions.add(beforePos == null ? ADDED_LIST_ITEM : beforePos);
            }

            data.add(new TreeDatum(TreeDatum.State.CHANGE, positions));

            for (T t : after) {
                Integer beforePos = beforeSet.get(elementId.apply(t));
                if (beforePos == null) {
                    data.add(new TreeDatum(TreeDatum.State.ADD, valueOnAdd.apply(t)));
                } else {
                    changeOrNoChange(before.get(beforePos), t, onChange, data);
                }
            }
        }
        return data;
    }

    private static <T> void changeOrNoChange(T before, T after,
                                             Function<@Nullable T, @Nullable T> onChange,
                                             List<TreeDatum> data) {
        if (before == after) {
            data.add(new TreeDatum(TreeDatum.State.NO_CHANGE, null));
        } else {
            data.add(new TreeDatum(TreeDatum.State.CHANGE, null));
            onChange.apply(after);
        }
    }
}
