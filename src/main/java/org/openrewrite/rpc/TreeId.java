package org.openrewrite.rpc;

import lombok.Value;

import java.util.UUID;

@Value
public class TreeId {
    UUID id;
    Language language;
}
