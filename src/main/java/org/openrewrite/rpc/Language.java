package org.openrewrite.rpc;

import org.jspecify.annotations.Nullable;
import org.openrewrite.Cursor;
import org.openrewrite.SourceFile;
import org.openrewrite.Tree;
import org.openrewrite.TreeVisitor;
import org.openrewrite.json.rpc.JsonReceiver;
import org.openrewrite.json.rpc.JsonSender;
import org.openrewrite.json.tree.Json;

public enum Language {
    Json,
    Properties,
    Xml,
    Yaml,
    Null;

    public static Language fromCursor(Cursor cursor) {
        SourceFile sourceFile = cursor.firstEnclosingOrThrow(SourceFile.class);
        return fromSourceFile(sourceFile);
    }

    public static Language fromSourceFile(@Nullable SourceFile sourceFile) {
        if (sourceFile == null) {
            return Null;
        }
        if (sourceFile instanceof Json) {
            return Json;
        }
        throw new UnsupportedOperationException("Unsupported language " + sourceFile.getClass().getSimpleName());
    }

    public TreeVisitor<? extends Tree, TreeDataReceiveQueue> getReceiver() {
        switch (this) {
            case Json:
                return new JsonReceiver();
            case Properties:
            case Xml:
            case Yaml:
            default:
                throw new IllegalArgumentException("Unknown language " + this);
        }
    }

    public TreeVisitor<? extends Tree, TreeDataSendQueue> getSender() {
        switch (this) {
            case Null:
                return new TreeVisitor<Tree, TreeDataSendQueue>() {
                    @Override
                    public @Nullable Tree visit(@Nullable Tree tree, TreeDataSendQueue q) {
                        q.put(new TreeDatum(TreeDatum.State.DELETE, null));
                        return null;
                    }
                };
            case Json:
                return new JsonSender();
            case Properties:
            case Xml:
            case Yaml:
            default:
                throw new IllegalArgumentException("Unknown language " + this);
        }
    }
}
