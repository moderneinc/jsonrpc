package org.openrewrite.json.rpc;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.openrewrite.json.JsonVisitor;
import org.openrewrite.json.tree.Json;
import org.openrewrite.json.tree.JsonRightPadded;
import org.openrewrite.json.tree.JsonValue;
import org.openrewrite.rpc.TreeDataReceiveQueue;

import java.nio.charset.Charset;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

public class JsonReceiver extends JsonVisitor<TreeDataReceiveQueue> {

    @Override
    public Json preVisit(@NonNull Json j, TreeDataReceiveQueue q) {
        j = j.withId(UUID.fromString(q.receiveAndGet(j.getId(), UUID::toString)));
        j = j.withPrefix(q.receive(j.getPrefix()));
        j = j.withMarkers(q.receive(j.getMarkers()));
        return j;
    }

    public Json visitDocument(Json.Document document, TreeDataReceiveQueue q) {
        String sourcePath = q.receiveAndGet(document.getSourcePath(), Path::toString);
        return ((Json.Document) document.withSourcePath(Paths.get(sourcePath))
                .withCharset(Charset.forName(q.receiveAndGet(document.getCharset(), Charset::name))))
                .withCharsetBomMarked(q.receive(document.isCharsetBomMarked()))
                .withChecksum(q.receive(document.getChecksum()))
                .withFileAttributes(q.receive(document.getFileAttributes()))
                .withValue(q.receive(document.getValue(), j -> (JsonValue) visit(j, q)))
                .withEof(q.receive(document.getEof()));
    }

    public Json visitArray(Json.Array array, TreeDataReceiveQueue q) {
        return array.getPadding().withValues(
                q.receiveList(array.getPadding().getValues(), j -> visitRightPadded(j, q)));
    }

    public Json visitEmpty(Json.Empty empty, TreeDataReceiveQueue q) {
        return empty;
    }

    public Json visitIdentifier(Json.Identifier identifier, TreeDataReceiveQueue q) {
        return identifier.withName(q.receive(identifier.getName()));
    }

    public Json visitLiteral(Json.Literal literal, TreeDataReceiveQueue q) {
        return literal.withSource(q.receive(literal.getSource()))
                .withValue(q.receive(literal.getValue()));
    }

    public Json visitMember(Json.Member member, TreeDataReceiveQueue q) {
        return member
                .getPadding().withKey(q.receive(member.getPadding().getKey(), j -> visitRightPadded(j, q)))
                .withValue(q.receive(member.getValue(), j -> (JsonValue) visit(j, q)));
    }

    public Json visitObject(Json.JsonObject object, TreeDataReceiveQueue q) {
        return object.getPadding().withMembers(
                q.receiveList(object.getPadding().getMembers(), j -> visitRightPadded(j, q)));
    }

    @Override
    public @Nullable <T extends Json> JsonRightPadded<T> visitRightPadded(@Nullable JsonRightPadded<T> right, TreeDataReceiveQueue q) {
        assert right != null : "TreeDataReceiveQueue should have instantiated an empty padding";

        //noinspection unchecked
        return right.withElement(q.receive(right.getElement(), j -> (T) visit(j, q)))
                .withAfter(q.receive(right.getAfter()))
                .withMarkers(q.receive(right.getMarkers()));
    }
}
