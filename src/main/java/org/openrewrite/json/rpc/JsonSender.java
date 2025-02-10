package org.openrewrite.json.rpc;

import org.jspecify.annotations.Nullable;
import org.openrewrite.Tree;
import org.openrewrite.json.JsonIsoVisitor;
import org.openrewrite.json.tree.Json;
import org.openrewrite.json.tree.Json.JsonObject;
import org.openrewrite.json.tree.JsonKey;
import org.openrewrite.json.tree.JsonRightPadded;
import org.openrewrite.rpc.TreeDataSendQueue;
import org.openrewrite.rpc.TreeDatum;

import java.util.List;

import static org.openrewrite.rpc.TreeDataSendQueue.listDifferences;

public class JsonSender extends JsonIsoVisitor<TreeDataSendQueue> {

    @Override
    public Json.Document visitDocument(Json.Document after, TreeDataSendQueue q) {
        q.tree(after, before -> {
            q.value(after.getPrefix(), Json::getPrefix);
            q.value(after.getMarkers(), Tree::getMarkers);
            q.visit(this, after.getValue(), Json.Document::getValue);
            q.value(after.getEof(), Json.Document::getEof);
        });
        q.flush();
        return after;
    }

    public Json.Array visitArray(Json.Array after, TreeDataSendQueue q) {
        return q.tree(after, before -> {
            q.value(after.getPrefix(), Json::getPrefix);
            q.value(after.getMarkers(), Tree::getMarkers);
            visitRightPadded(after.getPadding().getValues(),
                    before == null ? null : before.getPadding().getValues(), q);
        });
    }

    public Json.Empty visitEmpty(Json.Empty after, TreeDataSendQueue q) {
        return q.tree(after, before -> {
            q.value(after.getPrefix(), Json::getPrefix);
            q.value(after.getMarkers(), Tree::getMarkers);
        });
    }

    @Override
    public Json.Identifier visitIdentifier(Json.Identifier after, TreeDataSendQueue q) {
        return q.tree(after, before -> {
            q.value(after.getPrefix(), Json::getPrefix);
            q.value(after.getMarkers(), Tree::getMarkers);
            q.value(after.getName(), Json.Identifier::getName);
        });
    }

    @Override
    public Json.Literal visitLiteral(Json.Literal after, TreeDataSendQueue q) {
        return q.tree(after, before -> {
            q.value(after.getPrefix(), Json::getPrefix);
            q.value(after.getMarkers(), Tree::getMarkers);
            q.value(after.getValue(), Json.Literal::getValue);
        });
    }

    public Json.Member visitMember(Json.Member after, TreeDataSendQueue q) {
        return q.tree(after, before -> {
            q.value(after.getPrefix(), Json::getPrefix);
            q.value(after.getMarkers(), Tree::getMarkers);

            JsonRightPadded<JsonKey> beforeKey = before == null ? null : before.getPadding().getKey();
            q.value(after.getPadding().getKey(), beforeKey, () ->
                    onRightPaddedChange(after.getPadding().getKey(), beforeKey, q));

            visitRightPadded(after.getPadding().getKey(), q);
            q.visit(this, after.getValue(), Json.Member::getValue);
        });
    }

    public JsonObject visitObject(JsonObject after, TreeDataSendQueue q) {
        return q.tree(after, before -> {
            q.value(after.getPrefix(), Json::getPrefix);
            q.value(after.getMarkers(), Tree::getMarkers);
            visitRightPadded(after.getPadding().getMembers(),
                    before == null ? null : before.getPadding().getMembers(), q);
        });
    }

    private <T extends Json> void visitRightPadded(@Nullable List<JsonRightPadded<T>> after,
                                                   @Nullable List<JsonRightPadded<T>> before,
                                                   TreeDataSendQueue q) {
        List<TreeDatum> diff = listDifferences(
                after,
                before,
                p -> p.getElement().getId(),
                t -> null,
                (anAfter, aBefore) -> {
                    onRightPaddedChange(aBefore, anAfter, q);
                    return anAfter;
                }
        );
        for (TreeDatum datum : diff) {
            q.put(datum);
        }
    }

    private <T extends Json> void onRightPaddedChange(@Nullable JsonRightPadded<T> aBefore,
                                                      @Nullable JsonRightPadded<T> anAfter,
                                                      TreeDataSendQueue q) {
        q.visit(this,
                anAfter == null ? null : anAfter.getElement(),
                p -> aBefore == null ? null : aBefore.getElement());
        q.value(anAfter == null ? null : anAfter.getAfter(),
                p -> aBefore == null ? null : aBefore.getAfter());
        q.value(anAfter == null ? null : anAfter.getMarkers(),
                p -> aBefore == null ? null : aBefore.getMarkers());
    }
}
