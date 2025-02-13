package org.openrewrite.json.rpc;

import org.openrewrite.json.JsonVisitor;
import org.openrewrite.json.tree.Json;
import org.openrewrite.json.tree.JsonRightPadded;
import org.openrewrite.rpc.TreeDataReceiveQueue;
import org.openrewrite.rpc.TreeDatum;

import java.lang.reflect.InvocationTargetException;
import java.nio.charset.Charset;
import java.nio.file.Paths;
import java.util.List;
import java.util.function.UnaryOperator;

public class JsonReceiver extends JsonVisitor<TreeDataReceiveQueue> {

    public Json visitArray(Json.Array before, TreeDataReceiveQueue q) {
        return json(before, q, a -> a
                .getPadding().withValues(receiveRightPadded(a.getPadding().getValues(), q)));
    }

    public Json visitDocument(Json.Document before, TreeDataReceiveQueue q) {
        return json(before, q, d -> {
            //noinspection ConstantValue
            String sourcePath = q.value(d.getSourcePath() == null ? null : d.getSourcePath().toString());
            d = d.withSourcePath(Paths.get(sourcePath));

            d = (Json.Document) d.withCharset(Charset.forName(q.value(d.getCharset().name())));
            d = d.withCharsetBomMarked(q.value(d.isCharsetBomMarked()));
            d = d.withChecksum(q.value(d.getChecksum()));
            d = d.withFileAttributes(q.value(d.getFileAttributes()));
            d = d.withValue(q.tree(this, d.getValue()));
            d = d.withEof(q.value(d.getEof()));
            return d;
        });
    }

    public Json visitEmpty(Json.Empty before, TreeDataReceiveQueue q) {
        return json(before, q, e -> e);
    }

    public Json visitIdentifier(Json.Identifier before, TreeDataReceiveQueue q) {
        return json(before, q, i -> i.withName(q.value(i.getName())));
    }

    public Json visitLiteral(Json.Literal before, TreeDataReceiveQueue q) {
        return json(before, q, l -> l
                .withSource(q.value(l.getSource()))
                .withValue(q.value(l.getValue())));
    }

    public Json visitMember(Json.Member before, TreeDataReceiveQueue q) {
        return json(before, q, m -> m
                .getPadding().withKey(receiveRightPadded(m.getPadding().getKey(), q))
                .withValue(q.tree(this, m.getValue())));
    }

    public Json visitObject(Json.JsonObject before, TreeDataReceiveQueue q) {
        return json(before, q, o -> o
                .getPadding().withMembers(receiveRightPadded(o.getPadding().getMembers(), q)));
    }

    private <J extends Json> J json(J before, TreeDataReceiveQueue q, UnaryOperator<J> onChange) {
        J b = before;
        b = b.withPrefix(q.value(b.getPrefix()));
        b = b.withMarkers(q.value(b.getMarkers()));
        b = onChange.apply(b);
        return b;
    }

    private <T extends Json> JsonRightPadded<T> newJsonRightPadded() {
        try {
            //noinspection unchecked
            return (JsonRightPadded<T>) JsonRightPadded.class.getDeclaredConstructors()[0]
                    .newInstance(null, null, null);
        } catch (InstantiationException | InvocationTargetException | IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    public <T extends Json> JsonRightPadded<T> receiveRightPadded(JsonRightPadded<T> before, TreeDataReceiveQueue q) {
        TreeDatum message = q.take();
        switch (message.getState()) {
            case NO_CHANGE:
                return before;
            case DELETE:
                //noinspection DataFlowIssue
                return null;
            case ADD:
                return onRightPaddedChanged(newJsonRightPadded(), q);
            case CHANGE:
                return onRightPaddedChanged(before, q);
            default:
                throw new UnsupportedOperationException("Unknown state type " + message.getState());
        }
    }

    public <T extends Json> List<JsonRightPadded<T>> receiveRightPadded(List<JsonRightPadded<T>> before, TreeDataReceiveQueue q) {
        return q.listDifferences(
                before,
                (type, t) -> newJsonRightPadded(),
                t -> onRightPaddedChanged(t, q)
        );
    }

    private <T extends Json> JsonRightPadded<T> onRightPaddedChanged(JsonRightPadded<T> before,
                                                                     TreeDataReceiveQueue q) {
        JsonRightPadded<T> r = before;
        r = r.withElement(q.tree(this, r.getElement()));
        r = r.withAfter(q.value(r.getAfter()));
        r = r.withMarkers(q.value(r.getMarkers()));
        return r;
    }
}
