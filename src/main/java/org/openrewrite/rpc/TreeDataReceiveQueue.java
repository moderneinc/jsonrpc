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
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;

import static java.util.Objects.requireNonNull;

public class TreeDataReceiveQueue {
    private final List<TreeDatum> batch;
    private final Supplier<List<TreeDatum>> pull;

    public TreeDataReceiveQueue(Supplier<List<TreeDatum>> pull) {
        this.batch = new ArrayList<>();
        this.pull = pull;
    }

    public TreeDatum take() {
        if (batch.isEmpty()) {
            batch.addAll(pull.get());
        }
        return batch.remove(0);
    }

    @SuppressWarnings("DataFlowIssue")
    public <V> V value(@Nullable V before) {
        TreeDatum message = take();
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
    }

    @SuppressWarnings("DataFlowIssue")
    public <T extends Tree> T tree(TreeVisitor<? extends Tree, TreeDataReceiveQueue> visitor,
                                   @Nullable T before) {
        TreeDatum message = take();
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
    }

    public <T extends Tree> List<T> trees(TreeVisitor<? extends Tree, TreeDataReceiveQueue> visitor,
                                          @Nullable List<T> before) {
        //noinspection unchecked
        return listDifferences(
                before,
                v -> newTree((TreeDatum.Add) v),
                v -> (T) visitor.visitNonNull(v, this)
        );
    }

    public <T> List<T> listDifferences(@Nullable List<T> before,
                                       Function<Object, T> onAdd,
                                       UnaryOperator<T> onChange) {
        TreeDatum msg = take();
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
                    msg = take();
                    switch (msg.getState()) {
                        case NO_CHANGE:
                            after.add(requireNonNull(before).get(beforeIdx));
                            break;
                        case ADD:
                            after.add(onChange.apply(onAdd.apply(msg.getValue())));
                            break;
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
