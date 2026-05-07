/*
 * Copyright 2025 the original author or authors.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * https://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.moderne.jsonrpc.internal;

import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class SnowflakeIdTest {

    @Test
    void manyThreadsProduceNoDuplicates() throws Exception {
        // The synchronized version always serialized callers; the lock-free
        // version uses CAS on a packed (timestamp, sequence) state. This test
        // would have caught a naive AtomicLong replacement that allowed the
        // sequence counter to race across the millisecond boundary.
        int threads = 16;
        int idsPerThread = 5_000;
        Set<String> seen = ConcurrentHashMap.newKeySet(threads * idsPerThread);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        try {
            for (int t = 0; t < threads; t++) {
                pool.submit(() -> {
                    try {
                        start.await();
                        for (int i = 0; i < idsPerThread; i++) {
                            seen.add(SnowflakeId.generateId());
                        }
                    } catch (InterruptedException ignored) {
                    } finally {
                        done.countDown();
                    }
                });
            }
            start.countDown();
            assertThat(done.await(30, TimeUnit.SECONDS))
                    .as("all worker threads finished within timeout")
                    .isTrue();
        } finally {
            pool.shutdownNow();
        }

        assertThat(seen)
                .as("every generated id is unique across %d threads x %d ids", threads, idsPerThread)
                .hasSize(threads * idsPerThread);
    }
}
