package org.openrewrite.rpc;

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
    Yaml;

    public static Language fromSourceFile(SourceFile sourceFile) {
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
