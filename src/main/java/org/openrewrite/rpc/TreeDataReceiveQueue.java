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
import java.util.function.BiFunction;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;

import static java.util.Objects.requireNonNull;

public class TreeDataReceiveQueue {
    private final List<TreeDatum> batch;
    private final Supplier<TreeData> pull;

    public TreeDataReceiveQueue(Supplier<TreeData> pull) {
        this.batch = new ArrayList<>();
        this.pull = pull;
    }

    public TreeDatum take() {
        if (batch.isEmpty()) {
            TreeData data = pull.get();
            batch.addAll(data.getData());
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
                before = newTree(message.getValueType(), UUID.fromString(message.getValue()));
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
                (type, v) -> newTree(type, (UUID) v),
                v -> (T) visitor.visitNonNull(v, this)
        );
    }

    public <T> List<T> listDifferences(@Nullable List<T> before,
                                       BiFunction<String, Object, T> onAdd,
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
                            T newValue = onAdd.apply(requireNonNull(msg.getValueType()), msg.getValue());
                            after.add(onChange.apply(newValue));
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

    private static <T extends Tree> T newTree(String treeType, UUID id) {
        try {
            Class<?> clazz = Class.forName(treeType);
            for (Constructor<?> ctor : clazz.getDeclaredConstructors()) {
                for (Parameter param : ctor.getParameters()) {
                    if (param.getType().equals(UUID.class)) {
                        Object[] args = new Object[ctor.getParameterCount()];
                        for (int i = 0; i < args.length; i++) {
                            Class<?> paramType = ctor.getParameters()[i].getType();
                            if (paramType.equals(UUID.class)) {
                                args[i] = id;
                            } else if (paramType == boolean.class) {
                                args[i] = false;
                            } else if (paramType == int.class) {
                                args[i] = 0;
                            } else if (paramType == short.class) {
                                args[i] = (short) 0;
                            } else if (paramType == long.class) {
                                args[i] = 0L;
                            } else if (paramType == byte.class) {
                                args[i] = (byte) 0;
                            } else if (paramType == float.class) {
                                args[i] = 0.0f;
                            } else if (paramType == double.class) {
                                args[i] = 0.0d;
                            } else if (paramType == char.class) {
                                args[i] = '\u0000';
                            }
                        }
                        ctor.setAccessible(true);
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
