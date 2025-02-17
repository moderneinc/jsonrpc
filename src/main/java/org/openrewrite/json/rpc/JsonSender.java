package org.openrewrite.json.rpc;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.openrewrite.Tree;
import org.openrewrite.json.JsonVisitor;
import org.openrewrite.json.tree.Json;
import org.openrewrite.json.tree.JsonRightPadded;
import org.openrewrite.rpc.TreeDataSendQueue;

import static org.openrewrite.rpc.Reference.asRef;

public class JsonSender extends JsonVisitor<TreeDataSendQueue> {

    @Override
    public Json preVisit(@NonNull Json j, TreeDataSendQueue q) {
        q.getAndSend(j, Tree::getId);
        q.getAndSend(j, j2 -> asRef(j2.getPrefix()));
        q.getAndSend(j, j2 -> asRef(j2.getMarkers()));
        return j;
    }

    @Override
    public Json visitDocument(Json.Document document, TreeDataSendQueue q) {
        q.getAndSend(document, (Json.Document d) -> d.getSourcePath().toString());
        q.getAndSend(document, (Json.Document d) -> d.getCharset().name());
        q.getAndSend(document, Json.Document::isCharsetBomMarked);
        q.getAndSend(document, Json.Document::getChecksum);
        q.getAndSend(document, Json.Document::getFileAttributes);
        q.getAndSend(document, Json.Document::getValue, j -> visit(j, q));
        q.getAndSend(document, d -> asRef(d.getEof()));
        return document;
    }

    @Override
    public Json visitArray(Json.Array array, TreeDataSendQueue q) {
        q.getAndSendList(array, a -> a.getPadding().getValues(),
                j -> j.getElement().getId(),
                j -> visitRightPadded(j, q));
        return array;
    }

    @Override
    public Json visitEmpty(Json.Empty empty, TreeDataSendQueue q) {
        return empty;
    }

    @Override
    public Json visitIdentifier(Json.Identifier identifier, TreeDataSendQueue q) {
        q.getAndSend(identifier, Json.Identifier::getName);
        return identifier;
    }

    @Override
    public Json visitLiteral(Json.Literal literal, TreeDataSendQueue q) {
        q.getAndSend(literal, Json.Literal::getSource);
        q.getAndSend(literal, Json.Literal::getValue);
        return literal;
    }

    @Override
    public Json visitMember(Json.Member member, TreeDataSendQueue q) {
        q.getAndSend(member, m -> m.getPadding().getKey(), j -> visitRightPadded(j, q));
        q.getAndSend(member, Json.Member::getValue, j -> visit(j, q));
        return member;
    }

    @Override
    public Json visitObject(Json.JsonObject obj, TreeDataSendQueue q) {
        q.getAndSendList(obj, o -> o.getPadding().getMembers(),
                j -> j.getElement().getId(),
                j -> visitRightPadded(j, q));
        return obj;
    }

    @Override
    public @Nullable <T extends Json> JsonRightPadded<T> visitRightPadded(@Nullable JsonRightPadded<T> right, TreeDataSendQueue q) {
        q.getAndSend(right, JsonRightPadded::getElement, j -> visit(j, q));
        q.getAndSend(right, j -> asRef(j.getAfter()));
        q.getAndSend(right, j -> asRef(j.getMarkers()));
        return right;
    }
}
