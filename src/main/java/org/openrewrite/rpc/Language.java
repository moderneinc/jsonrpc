package org.openrewrite.rpc;

import org.jspecify.annotations.Nullable;
import org.openrewrite.Cursor;
import org.openrewrite.SourceFile;
import org.openrewrite.Tree;
import org.openrewrite.json.rpc.JsonReceiver;
import org.openrewrite.json.rpc.JsonSender;
import org.openrewrite.json.tree.Json;

public enum Language {
    Json,
    Properties,
    Xml,
    Yaml;

    public static Language fromCursor(Cursor cursor) {
        SourceFile sourceFile = cursor.firstEnclosingOrThrow(SourceFile.class);
        if (sourceFile instanceof Json) {
            return Json;
        }
        throw new UnsupportedOperationException("Unsupported language " + sourceFile.getClass().getSimpleName());
    }

    public TreeReceiver getReceiver(@Nullable Tree localState) {
        switch (this) {
            case Json:
                return new TreeReceiver(new JsonReceiver(), localState);
            case Properties:
            case Xml:
            case Yaml:
            default:
                throw new IllegalArgumentException("Unknown language " + this);
        }
    }

    public TreeSender getSender(@Nullable Tree remoteState, @Nullable Tree current) {
        switch (this) {
            case Json:
                return new TreeSender(new JsonSender(), remoteState, current);
            case Properties:
            case Xml:
            case Yaml:
            default:
                throw new IllegalArgumentException("Unknown language " + this);
        }
    }
}
