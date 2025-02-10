package org.openrewrite.rpc.request;

import lombok.Value;
import org.openrewrite.rpc.Language;

import java.util.UUID;

@Value
public class VisitRequest implements RecipeRpcRequest {
    String visitor;
    UUID treeId;
    Language language;
    Object p;
}
