package org.openrewrite.rpc;

import org.jspecify.annotations.Nullable;
import org.openrewrite.Tree;
import org.openrewrite.TreeVisitor;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiFunction;
import java.util.function.Function;

import static java.util.Objects.requireNonNull;

public class TreeReceiver {
    private final TreeVisitor<? extends Tree, TreeReceiver> visitor;
    private final BlockingQueue<TreeDatum> data = new ArrayBlockingQueue<>(10);

    private final CompletableFuture<Tree> tree;

    public TreeReceiver(TreeVisitor<? extends Tree, TreeReceiver> visitor,
                        @Nullable Tree remoteState) {
        this.visitor = visitor;

        // This async process allows the visitor to consume the data being received in batches from
        // a remote process without blocking the thread that is reading those batches.
        this.tree = CompletableFuture.supplyAsync(() -> tree(remoteState));
    }

    public Tree getTree() {
        return tree.join();
    }

    public void putAll(List<TreeDatum> batch) throws InterruptedException {
        for (TreeDatum message : batch) {
            data.put(message);
        }
    }

    @SuppressWarnings("DataFlowIssue")
    public <V> V value(@Nullable V before) {
        try {
            TreeDatum message = data.take();
            switch (message.getState()) {
                case NO_CHANGE:
                    return before;
                case DELETE:
                    return null;
                case ADD:
                case CHANGE:
                    return message.getValue();
                default:
                    throw new UnsupportedOperationException("Unknown state type " + message.getState());
            }
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    public <T> T padding(Class<?> paddingClass,
                         T before,
                         BiFunction<@Nullable T, TreeReceiver, @Nullable T> visitPadding) {
        try {
            TreeDatum message = data.take();
            switch (message.getState()) {
                case NO_CHANGE:
                    return before;
                case DELETE:
                    //noinspection DataFlowIssue
                    return null;
                case ADD:
                    for (Constructor<?> ctor : paddingClass.getDeclaredConstructors()) {
                        Object[] args = new Object[ctor.getParameterCount()];
                        //noinspection unchecked,DataFlowIssue
                        return visitPadding.apply((T) ctor.newInstance(args), this);
                    }
                case CHANGE:
                    //noinspection DataFlowIssue
                    return visitPadding.apply(before, this);
                default:
                    throw new UnsupportedOperationException("Unknown state type " + message.getState());
            }
        } catch (InterruptedException | IllegalAccessException | InstantiationException |
                 InvocationTargetException e) {
            throw new RuntimeException(e);
        }
    }

    @SuppressWarnings("DataFlowIssue")
    public <T extends Tree> T tree(@Nullable T before) {
        try {
            TreeDatum message = data.take();
            switch (message.getState()) {
                case NO_CHANGE:
                    return before;
                case DELETE:
                    return null;
                case ADD:
                    before = newTree(message.getValue());
                    // intentional fall-through...
                case CHANGE:
                    //noinspection unchecked
                    return (T) visitor.visitNonNull(before, this);
                default:
                    throw new UnsupportedOperationException("Unknown state type " + message.getState());
            }
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    public <T> List<T> paddingList(List<T> before,
                                   Function<Object, T> newPadding,
                                   BiFunction<@Nullable T, TreeReceiver, @Nullable T> visitPadding) {
        return listDifferences(
                before,
                newPadding,
                t -> visitPadding.apply(t, this)
        );
    }

    public <T extends Tree> List<T> trees(@Nullable List<T> before) {
        //noinspection unchecked
        return listDifferences(
                before,
                v -> newTree((TreeDatum.Add) v),
                v -> (T) visitor.visitNonNull(v, this)
        );
    }

    private <T> List<T> listDifferences(@Nullable List<T> before,
                                        Function<Object, T> onAdd,
                                        Function<T, T> onChange) {
        try {
            TreeDatum msg = data.take();
            switch (msg.getState()) {
                case NO_CHANGE:
                    //noinspection DataFlowIssue
                    return before;
                case DELETE:
                    //noinspection DataFlowIssue
                    return null;
                case CHANGE:
                    List<Integer> positions = msg.getValue();
                    List<T> after = new ArrayList<>(positions.size());
                    for (int beforeIdx : positions) {
                        msg = data.take();
                        switch (msg.getState()) {
                            case NO_CHANGE:
                                after.add(requireNonNull(before).get(beforeIdx));
                                break;
                            case ADD:
                                after.add(onChange.apply(onAdd.apply(msg.getValue())));
                            case CHANGE:
                                after.add(onChange.apply(requireNonNull(before).get(beforeIdx)));
                                break;
                            default:
                                throw new UnsupportedOperationException("Unknown state type " + msg.getState());
                        }
                    }
                    return after;
                default:
                    throw new UnsupportedOperationException(msg.getState() + " is not supported for lists.");
            }
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    private <T extends Tree> T newTree(TreeDatum.Add add) {
        UUID id = add.getId();
        try {
            Class<?> clazz = Class.forName(add.getClassName());
            for (Constructor<?> ctor : clazz.getDeclaredConstructors()) {
                for (Parameter param : ctor.getParameters()) {
                    if (param.getType().equals(UUID.class)) {
                        Object[] args = new Object[ctor.getParameterCount()];
                        for (int i = 0; i < args.length; i++) {
                            if (ctor.getParameters()[i].getType().equals(UUID.class)) {
                                args[i] = id;
                            }
                        }
                        //noinspection unchecked
                        return (T) ctor.newInstance(args);
                    }
                }
            }
            throw new IllegalStateException("Unable to find a constructor for " + clazz + " that has a UUID argument");
        } catch (ClassNotFoundException | InvocationTargetException | IllegalAccessException |
                 InstantiationException e) {
            throw new RuntimeException(e);
        }
    }
}
