package org.openrewrite.rpc;

import lombok.Value;

import java.util.List;

@Value
public class TreeData {
    List<TreeDatum> data;
}
