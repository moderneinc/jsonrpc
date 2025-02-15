package org.openrewrite.rpc;

import lombok.Value;
import org.openrewrite.FileAttributes;

import java.nio.file.Path;

@Value
public class ParserInput {
    Path sourcePath;
    String text;
    FileAttributes fileAttributes;
}
