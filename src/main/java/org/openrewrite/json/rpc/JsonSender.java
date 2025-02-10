package org.openrewrite.json.rpc;

import org.jspecify.annotations.Nullable;
import org.openrewrite.Cursor;
import org.openrewrite.Tree;
import org.openrewrite.json.JsonVisitor;
import org.openrewrite.json.tree.Json;
import org.openrewrite.json.tree.Json.JsonObject;
import org.openrewrite.json.tree.JsonRightPadded;
import org.openrewrite.rpc.TreeDataSendQueue;

import java.util.List;
import java.util.function.Consumer;

public class JsonSender extends JsonVisitor<TreeDataSendQueue> {

    @Override
    public Json visitDocument(Json.Document after, TreeDataSendQueue q) {
        json(after, q, before -> {
            q.visit(this, after.getValue(), Json.Document::getValue);
            q.value(after.getEof(), Json.Document::getEof);
        });
        q.flush();
        return after;
    }

    public Json visitArray(Json.Array after, TreeDataSendQueue q) {
        return json(after, q, before -> visitRightPadded(
                after.getPadding().getValues(),
                before == null ? null : before.getPadding().getValues(), q));
    }

    public Json visitEmpty(Json.Empty after, TreeDataSendQueue q) {
        return json(after, q, before -> {
        });
    }

    @Override
    public Json visitIdentifier(Json.Identifier after, TreeDataSendQueue q) {
        return json(after, q, before ->
                q.value(after.getName(), Json.Identifier::getName));
    }

    @Override
    public Json visitLiteral(Json.Literal after, TreeDataSendQueue q) {
        return json(after, q, before -> {
            q.value(after.getSource(), Json.Literal::getSource);
            q.value(after.getValue(), Json.Literal::getValue);
        });
    }

    public Json visitMember(Json.Member after, TreeDataSendQueue q) {
        return json(after, q, before -> {
            visitRightPadded(after.getPadding().getKey(),
                    before == null ? null : before.getPadding().getKey(), q);
            q.visit(this, after.getValue(), Json.Member::getValue);
        });
    }

    public Json visitObject(JsonObject after, TreeDataSendQueue q) {
        return json(after, q, before -> visitRightPadded(
                after.getPadding().getMembers(),
                before == null ? null : before.getPadding().getMembers(), q));
    }

    private <T extends Json> Json json(T after, TreeDataSendQueue q, Consumer<@Nullable T> onChange) {
        return q.tree(after, before -> {
            q.value(after.getPrefix(), Json::getPrefix);
            q.value(after.getMarkers(), Tree::getMarkers);
            onChange.accept(before);
        });
    }

    private <T extends Json> void visitRightPadded(@Nullable JsonRightPadded<T> after,
                                                   @Nullable JsonRightPadded<T> before,
                                                   TreeDataSendQueue q) {
        q.value(after, before, () -> onRightPaddedChange(after, before, q));
    }

    private <T extends Json> void visitRightPadded(@Nullable List<JsonRightPadded<T>> after,
                                                   @Nullable List<JsonRightPadded<T>> before,
                                                   TreeDataSendQueue q) {
        q.listDifferences(
                after,
                before,
                p -> p.getElement().getId(),
                t -> t.getElement().getClass().getName(),
                t -> t.getElement().getId(),
                (anAfter, aBefore) -> {
                    onRightPaddedChange(anAfter, aBefore, q);
                    return anAfter;
                }
        );
    }

    private <T extends Json> void onRightPaddedChange(@Nullable JsonRightPadded<T> anAfter,
                                                      @Nullable JsonRightPadded<T> aBefore,
                                                      TreeDataSendQueue q) {
        if (anAfter != null) {
            // Not essential to the operation of the sender, but useful for debugging
            setCursor(new Cursor(getCursor(), anAfter));
        }
        q.visit(this,
                anAfter == null ? null : anAfter.getElement(),
                p -> aBefore == null ? null : aBefore.getElement());
        if (anAfter != null) {
            setCursor(getCursor().getParent());
        }
        q.value(anAfter == null ? null : anAfter.getAfter(),
                p -> aBefore == null ? null : aBefore.getAfter());
        q.value(anAfter == null ? null : anAfter.getMarkers(),
                p -> aBefore == null ? null : aBefore.getMarkers());
    }
}
