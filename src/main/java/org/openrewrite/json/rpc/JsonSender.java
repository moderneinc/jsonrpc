package org.openrewrite.json.rpc;

import org.jspecify.annotations.Nullable;
import org.openrewrite.json.JsonVisitor;
import org.openrewrite.json.tree.Json;
import org.openrewrite.json.tree.JsonRightPadded;
import org.openrewrite.rpc.TreeSender;

public class JsonSender extends JsonVisitor<TreeSender> {

    public Json visitArray(Json.Array array, TreeSender sender) {
        return sender.send(array, (ctx, a) -> {
            ctx.value(a.getPrefix(), Json.Array::getPrefix);
            ctx.value(a.getMarkers(), Json.Array::getMarkers);
            ctx.trees(a.getValues(), Json.Array::getValues);
        });
    }

    public Json visitDocument(Json.Document document, TreeSender sender) {
        return sender.send(document, (ctx, d) -> {
            ctx.value(d.getPrefix(), Json.Document::getPrefix);
            ctx.value(d.getMarkers(), Json.Document::getMarkers);
            ctx.tree(d.getValue(), Json.Document::getValue);
            ctx.value(d.getEof(), Json.Document::getEof);
        });
    }

    public Json visitEmpty(Json.Empty empty, TreeSender sender) {
        return sender.send(empty, (ctx, e) -> {
            ctx.value(e.getPrefix(), Json.Empty::getPrefix);
            ctx.value(e.getMarkers(), Json.Empty::getMarkers);
        });
    }

    public Json visitIdentifier(Json.Identifier identifier, TreeSender sender) {
        return sender.send(identifier, (ctx, i) -> {
            ctx.value(i.getPrefix(), Json.Identifier::getPrefix);
            ctx.value(i.getMarkers(), Json.Identifier::getMarkers);
        });
    }

    public Json visitLiteral(Json.Literal literal, TreeSender sender) {
        return sender.send(literal, (ctx, l) -> {
            ctx.value(l.getPrefix(), Json.Literal::getPrefix);
            ctx.value(l.getMarkers(), Json.Literal::getMarkers);
        });
    }

    public Json visitMember(Json.Member member, TreeSender sender) {
        return sender.send(member, (ctx, m) -> {
            ctx.value(m.getPrefix(), Json.Member::getPrefix);
            ctx.value(m.getMarkers(), Json.Member::getMarkers);
            ctx.padding(m.getPadding().getKey(),
                    before -> before.getPadding().getKey(),
                    this::visitRightPadded);
            ctx.tree(m.getValue(), Json.Member::getValue);
        });
    }

    public Json visitObject(Json.JsonObject obj, TreeSender sender) {
        return sender.send(obj, (ctx, o) -> {
            ctx.value(o.getPrefix(), Json.JsonObject::getPrefix);
            ctx.value(o.getMarkers(), Json.JsonObject::getMarkers);
            ctx.paddingList(o.getPadding().getMembers(),
                    before -> before.getPadding().getMembers(),
                    padded -> padded.getElement().getId(),
                    this::visitRightPadded);
        });
    }

    @Override
    public @Nullable <T extends Json> JsonRightPadded<T> visitRightPadded(@Nullable JsonRightPadded<T> right,
                                                                          TreeSender treeSender) {
        return treeSender.send(right, (ctx, r) -> {
            ctx.tree(r.getElement(), JsonRightPadded::getElement);
            ctx.value(r.getAfter(), JsonRightPadded::getAfter);
            ctx.value(r.getMarkers(), JsonRightPadded::getMarkers);
        });
    }
}
