package org.openrewrite.json.rpc;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.openrewrite.json.JsonVisitor;
import org.openrewrite.json.tree.Json;
import org.openrewrite.json.tree.JsonRightPadded;
import org.openrewrite.rpc.TreeReceiver;

import java.lang.reflect.InvocationTargetException;

import static java.util.Objects.requireNonNull;

public class JsonReceiver extends JsonVisitor<TreeReceiver> {

    public Json visitArray(Json.Array array, TreeReceiver receiver) {
        Json.Array a = array;
        a = a.withPrefix(receiver.value(a.getPrefix()));
        a = a.withMarkers(receiver.value(a.getMarkers()));
        a = a.withValues(receiver.trees(a.getValues()));
        return a;
    }

    public Json visitDocument(Json.Document document, TreeReceiver receiver) {
        Json.Document d = document;
        d = d.withPrefix(receiver.value(d.getPrefix()));
        d = d.withMarkers(receiver.value(d.getMarkers()));
        d = d.withValue(receiver.tree(d.getValue()));
        d = d.withEof(receiver.value(d.getEof()));
        return d;
    }

    public Json visitEmpty(Json.Empty empty, TreeReceiver receiver) {
        Json.Empty e = empty;
        e = e.withPrefix(receiver.value(e.getPrefix()));
        e = e.withMarkers(receiver.value(e.getMarkers()));
        return e;
    }

    public Json visitIdentifier(Json.Identifier identifier, TreeReceiver receiver) {
        Json.Identifier i = identifier;
        i = i.withPrefix(receiver.value(i.getPrefix()));
        i = i.withMarkers(receiver.value(i.getMarkers()));
        return i;
    }

    public Json visitLiteral(Json.Literal literal, TreeReceiver receiver) {
        Json.Literal l = literal;
        l = l.withPrefix(receiver.value(l.getPrefix()));
        l = l.withMarkers(receiver.value(l.getMarkers()));
        return l;
    }

    public Json visitMember(Json.Member member, TreeReceiver receiver) {
        Json.Member m = member;
        m = m.withPrefix(receiver.value(m.getPrefix()));
        m = m.withMarkers(receiver.value(m.getMarkers()));
        m = m.getPadding().withKey(receiver.padding(JsonRightPadded.class,
                m.getPadding().getKey(), this::visitRightPadded));
        m = m.withValue(receiver.tree(m.getValue()));
        return m;
    }

    public Json visitObject(Json.JsonObject obj, TreeReceiver receiver) {
        Json.JsonObject o = obj;
        o = o.withPrefix(receiver.value(o.getPrefix()));
        o = o.withMarkers(receiver.value(o.getMarkers()));
        o = o.getPadding().withMembers(receiver.paddingList(o.getPadding().getMembers(),
                v -> newPadding(), this::visitRightPadded));
        return o;
    }

    private JsonRightPadded<Json> newPadding() {
        try {
            //noinspection unchecked
            return (JsonRightPadded<@NonNull Json>) JsonRightPadded.class.getDeclaredConstructors()[0]
                    .newInstance(null, null, null);
        } catch (InstantiationException | InvocationTargetException | IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public @Nullable <T extends Json> JsonRightPadded<T> visitRightPadded(@Nullable JsonRightPadded<T> right, TreeReceiver treeReceiver) {
        // The visitor will short-circuit the call to this method if the padded element is null
        JsonRightPadded<T> r = requireNonNull(right);
        r = r.withElement(treeReceiver.tree(r.getElement()));
        r = r.withAfter(treeReceiver.value(r.getAfter()));
        r = r.withMarkers(treeReceiver.value(r.getMarkers()));
        return r;
    }
}
