package org.openrewrite.rpc;

import org.junit.jupiter.api.Test;
import org.openrewrite.internal.ListUtils;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

public class TreeDataSendQueueTest {

    @Test
    void listDifference() {
        List<String> before = List.of("A", "B", "C", "D");
        List<String> after = List.of("A", "E", "F", "C");
        Map<String, UUID> ids = ListUtils.concatAll(before, after).stream()
          .distinct()
          .collect(Collectors.toMap(s -> s, s -> UUID.randomUUID()));

        List<TreeDatum> diff = TreeDataSendQueue.listDifferences(after, before, ids::get,
          ids::get, (anAfter, aBefore) -> null);

        assertThat(diff).containsExactly(
          new TreeDatum(TreeDatum.State.CHANGE, List.of(0, -1, -1, 2)),
          new TreeDatum(TreeDatum.State.NO_CHANGE, null) /* A */,
          new TreeDatum(TreeDatum.State.ADD, ids.get("E")),
          new TreeDatum(TreeDatum.State.ADD, ids.get("F")),
          new TreeDatum(TreeDatum.State.NO_CHANGE, null) /* C */
        );
    }
}
