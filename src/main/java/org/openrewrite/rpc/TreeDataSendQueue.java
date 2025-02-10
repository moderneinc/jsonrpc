package org.openrewrite.rpc;

import org.jspecify.annotations.Nullable;
import org.openrewrite.Tree;
import org.openrewrite.TreeVisitor;
import org.openrewrite.internal.ThrowingConsumer;

import java.util.*;
import java.util.function.BinaryOperator;
import java.util.function.Consumer;
import java.util.function.Function;

import static java.util.stream.Collectors.toList;
import static org.openrewrite.rpc.TreeDatum.ADDED_LIST_ITEM;

public class TreeDataSendQueue {

    @Nullable
    private Tree before;

    private final int batchSize;
    private List<TreeDatum> batch;
    private final Consumer<TreeData> drain;

    public TreeDataSendQueue(int batchSize, @Nullable Tree before, ThrowingConsumer<TreeData> drain) {
        this.batchSize = batchSize;
        this.batch = new ArrayList<>(batchSize);
        this.before = before;
        this.drain = drain;
    }

    public void put(TreeDatum treeDatum) {
        batch.add(treeDatum);
        if (batch.size() == batchSize) {
            flush();
        }
    }

    public void flush() {
        if (batch.isEmpty()) {
            return;
        }
        drain.accept(new TreeData(batch));
        batch = new ArrayList<>();
    }

    public <Parent, T> void value(@Nullable T after, Function<Parent, @Nullable T> beforeFn) {
        //noinspection unchecked
        T before = this.before == null ? null : beforeFn.apply((Parent) this.before);

        if (before == after) {
            put(new TreeDatum(TreeDatum.State.NO_CHANGE, null, null));
        } else if (before == null) {
            put(new TreeDatum(TreeDatum.State.ADD, after.getClass().getName(), after));
        } else if (after == null) {
            put(new TreeDatum(TreeDatum.State.DELETE, null, null));
        } else {
            put(new TreeDatum(TreeDatum.State.CHANGE, after.getClass().getName(), after));
        }
    }

    public <T extends Tree> T tree(@Nullable T after, Consumer<@Nullable T> onChange) {
        if (before == after) {
            put(new TreeDatum(TreeDatum.State.NO_CHANGE, null, null));
        } else if (before == null) {
            put(new TreeDatum(TreeDatum.State.ADD, after.getClass().getName(), after.getId()));
            //noinspection unchecked
            onChange.accept((T) before);
        } else if (after == null) {
            put(new TreeDatum(TreeDatum.State.DELETE, null, null));
        } else {
            put(new TreeDatum(TreeDatum.State.CHANGE, null, null));
            //noinspection unchecked
            onChange.accept((T) before);
        }
        //noinspection DataFlowIssue
        return after;
    }

    public <T> T value(@Nullable T after, @Nullable T before, Runnable onChange) {
        if (before == after) {
            put(new TreeDatum(TreeDatum.State.NO_CHANGE, null, null));
        } else if (before == null) {
            put(new TreeDatum(TreeDatum.State.ADD, null, null));
        } else if (after == null) {
            put(new TreeDatum(TreeDatum.State.DELETE, null, null));
        } else {
            put(new TreeDatum(TreeDatum.State.CHANGE, null, null));
            onChange.run();
        }
        //noinspection DataFlowIssue
        return after;
    }

    public <Parent> void visit(TreeVisitor<? extends Tree, TreeDataSendQueue> visitor,
                               @Nullable Tree after, Function<Parent, @Nullable Tree> beforeFn) {
        Tree lastBefore = this.before;
        //noinspection unchecked
        this.before = this.before == null ? null : beforeFn.apply((Parent) this.before);
        visitor.visit(after, this);
        this.before = lastBefore;
    }

    public <Parent extends Tree, T extends Tree> void visit(TreeVisitor<? extends Tree, TreeDataSendQueue> visitor,
                                                            List<T> after, Function<Parent, @Nullable Tree> beforeFn) {
//        List<TreeDatum> diff = listDifferences(after, before,
//                Tree::getId,
//                t -> new TreeDatum.Add(t.getId(), t.getClass().getName()),
//                (anAfter, aBefore) -> {
//                    visit(visitor, anAfter, aBefore);
//                    return anAfter;
//                });
//        for (TreeDatum datum : diff) {
//            put(datum);
//        }
    }

    public static <T> List<TreeDatum> listDifferences(@Nullable List<T> after,
                                                      @Nullable List<T> before,
                                                      Function<T, UUID> elementId,
                                                      Function<T, @Nullable String> typeOnAdd,
                                                      Function<T, ?> valueOnAdd,
                                                      BinaryOperator<@Nullable T> onChange) {
        List<TreeDatum> data = new ArrayList<>(Math.max(
                after == null ? 0 : after.size(),
                before == null ? 0 : before.size()));
        if (before == after) {
            data.add(new TreeDatum(TreeDatum.State.NO_CHANGE, null, null));
        } else if (after == null) {
            data.add(new TreeDatum(TreeDatum.State.DELETE, null, null));
        } else if (before == null) {
            data.add(new TreeDatum(TreeDatum.State.CHANGE, null, after.stream()
                    .map(a -> ADDED_LIST_ITEM).collect(toList())));
            for (T t : after) {
                data.add(new TreeDatum(TreeDatum.State.ADD, typeOnAdd.apply(t), valueOnAdd.apply(t)));
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

            data.add(new TreeDatum(TreeDatum.State.CHANGE, null, positions));

            for (T anAfter : after) {
                Integer beforePos = beforeSet.get(elementId.apply(anAfter));
                if (beforePos == null) {
                    data.add(new TreeDatum(TreeDatum.State.ADD, typeOnAdd.apply(anAfter),
                            valueOnAdd.apply(anAfter)));
                } else {
                    T aBefore = before.get(beforePos);
                    if (aBefore == anAfter) {
                        data.add(new TreeDatum(TreeDatum.State.NO_CHANGE, null, null));
                    } else {
                        data.add(new TreeDatum(TreeDatum.State.CHANGE, null, null));
                        onChange.apply(anAfter, aBefore);
                    }
                }
            }
        }
        return data;
    }
}
