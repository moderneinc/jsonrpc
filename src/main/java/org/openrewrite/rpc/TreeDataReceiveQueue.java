package org.openrewrite.rpc;

import org.jspecify.annotations.Nullable;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;

import static java.util.Objects.requireNonNull;

public class TreeDataReceiveQueue {
    private final List<TreeDatum> batch;
    private final Map<Integer, Object> refs;
    private final Supplier<TreeData> pull;

    public TreeDataReceiveQueue(Map<Integer, Object> refs, Supplier<TreeData> pull) {
        this.refs = refs;
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

    public <T, U> U receiveAndGet(@Nullable T before, Function<T, U> apply) {
        return receive(before == null ? null : apply.apply(before), null);
    }

    public <T> T receive(@Nullable T before) {
        return receive(before, null);
    }

    @SuppressWarnings("DataFlowIssue")
    public <T> T receive(@Nullable T before, @Nullable UnaryOperator<@Nullable T> onChange) {
        TreeDatum message = take();
        switch (message.getState()) {
            case NO_CHANGE:
                return before;
            case DELETE:
                return null;
            case ADD:
                Integer ref = message.getRef();
                if (ref != null) {
                    if (refs.containsKey(ref)) {
                        //noinspection unchecked
                        return (T) refs.get(ref);
                    } else {
                        before = onChange == null ?
                                message.getValue() :
                                newObj(message.getValueType());
                        refs.put(ref, before);
                    }
                } else {
                    before = onChange == null ?
                            message.getValue() :
                            newObj(message.getValueType());
                }
                // Intentional fall-through...
            case CHANGE:
                return onChange == null ? message.getValue() : onChange.apply(before);
            default:
                throw new UnsupportedOperationException("Unknown state type " + message.getState());
        }
    }

    public <T> List<T> receiveList(@Nullable List<T> before, UnaryOperator<@Nullable T> onChange) {
        TreeDatum msg = take();
        switch (msg.getState()) {
            case NO_CHANGE:
                //noinspection DataFlowIssue
                return before;
            case DELETE:
                //noinspection DataFlowIssue
                return null;
            case ADD:
                before = new ArrayList<>();
                // Intentional fall-through...
            case CHANGE:
                msg = take(); // the next message should be a CHANGE with a list of positions
                assert msg.getState() == TreeDatum.State.CHANGE;
                List<Integer> positions = msg.getValue();

                List<T> after = new ArrayList<>(positions.size());
                for (int beforeIdx : positions) {
                    msg = take();
                    switch (msg.getState()) {
                        case NO_CHANGE:
                            after.add(requireNonNull(before).get(beforeIdx));
                            break;
                        case ADD:
                            // Intentional fall-through...
                        case CHANGE:
                            after.add(onChange.apply(beforeIdx == -1 ?
                                    newObj(requireNonNull(msg.getValueType())) :
                                    requireNonNull(before).get(beforeIdx)));
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

    private static <T> T newObj(String type) {
        try {
            Class<?> clazz = Class.forName(type);
            for (Constructor<?> ctor : clazz.getDeclaredConstructors()) {
                Object[] args = new Object[ctor.getParameterCount()];
                for (int i = 0; i < args.length; i++) {
                    Class<?> paramType = ctor.getParameters()[i].getType();
                    if (paramType == boolean.class) {
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
            throw new IllegalStateException("Unable to find a constructor for " + clazz);
        } catch (ClassNotFoundException | InvocationTargetException | IllegalAccessException |
                 InstantiationException e) {
            throw new RuntimeException(e);
        }
    }
}
