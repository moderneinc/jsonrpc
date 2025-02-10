package org.openrewrite.json.rpc;

import org.openrewrite.json.JsonVisitor;
import org.openrewrite.json.tree.Json;
import org.openrewrite.json.tree.JsonRightPadded;
import org.openrewrite.rpc.TreeDataReceiveQueue;
import org.openrewrite.rpc.TreeDatum;

import java.lang.reflect.InvocationTargetException;
import java.util.List;

public class JsonReceiver extends JsonVisitor<TreeDataReceiveQueue> {

    public Json visitArray(Json.Array before, TreeDataReceiveQueue q) {
        Json.Array a = before;
        a = a.withPrefix(q.value(a.getPrefix()));
        a = a.withMarkers(q.value(a.getMarkers()));
        a = a.getPadding().withValues(receiveRightPadded(a.getPadding().getValues(), q));
        return a;
    }

    public Json visitDocument(Json.Document before, TreeDataReceiveQueue q) {
        Json.Document d = before;
        d = d.withPrefix(q.value(d.getPrefix()));
        d = d.withMarkers(q.value(d.getMarkers()));
        d = d.withValue(q.tree(this, d.getValue()));
        d = d.withEof(q.value(d.getEof()));
        return d;
    }

    public Json visitEmpty(Json.Empty before, TreeDataReceiveQueue q) {
        Json.Empty e = before;
        e = e.withPrefix(q.value(e.getPrefix()));
        e = e.withMarkers(q.value(e.getMarkers()));
        return e;
    }

    public Json visitIdentifier(Json.Identifier before, TreeDataReceiveQueue q) {
        Json.Identifier i = before;
        i = i.withPrefix(q.value(i.getPrefix()));
        i = i.withMarkers(q.value(i.getMarkers()));
        i = i.withName(q.value(i.getName()));
        return i;
    }

    public Json visitLiteral(Json.Literal before, TreeDataReceiveQueue q) {
        Json.Literal l = before;
        l = l.withPrefix(q.value(l.getPrefix()));
        l = l.withMarkers(q.value(l.getMarkers()));
        l = l.withSource(q.value(l.getSource()));
        l = l.withValue(q.value(l.getValue()));
        return l;
    }

    public Json visitMember(Json.Member before, TreeDataReceiveQueue q) {
        Json.Member m = before;
        m = m.withPrefix(q.value(m.getPrefix()));
        m = m.withMarkers(q.value(m.getMarkers()));
        m = m.getPadding().withKey(receiveRightPadded(m.getPadding().getKey(), q));
        m = m.withValue(q.tree(this, m.getValue()));
        return m;
    }

    public Json visitObject(Json.JsonObject before, TreeDataReceiveQueue q) {
        Json.JsonObject o = before;
        o = o.withPrefix(q.value(o.getPrefix()));
        o = o.withMarkers(q.value(o.getMarkers()));
        o = o.getPadding().withMembers(receiveRightPadded(o.getPadding().getMembers(), q));
        return o;
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
