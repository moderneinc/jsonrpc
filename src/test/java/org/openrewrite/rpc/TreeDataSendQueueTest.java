package org.openrewrite.rpc;

import org.junit.jupiter.api.Test;
import org.openrewrite.internal.ListUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

public class TreeDataSendQueueTest {

    @Test
    void sendDifference() throws InterruptedException {
        List<String> before = List.of("A", "B", "C", "D");
        List<String> after = List.of("A", "E", "F", "C");
        Map<String, UUID> ids = ListUtils.concatAll(before, after).stream()
          .distinct()
          .collect(Collectors.toMap(s -> s, s -> UUID.randomUUID()));

        CountDownLatch latch = new CountDownLatch(1);
        TreeDataSendQueue q = new TreeDataSendQueue(10, t -> {
            assertThat(t.getData()).containsExactly(
              new TreeDatum(TreeDatum.State.CHANGE, null, List.of(0, -1, -1, 2), null),
              new TreeDatum(TreeDatum.State.NO_CHANGE, null, null, null) /* A */,
              new TreeDatum(TreeDatum.State.ADD, "string", ids.get("E"), null),
              new TreeDatum(TreeDatum.State.ADD, "string", ids.get("F"), null),
              new TreeDatum(TreeDatum.State.NO_CHANGE, null, null, null) /* C */
            );
            latch.countDown();
        }, new HashMap<>());

        q.send(after, before, () -> {
        });
        q.flush();

        assertThat(latch.await(10, TimeUnit.SECONDS)).isTrue();
    }
}
