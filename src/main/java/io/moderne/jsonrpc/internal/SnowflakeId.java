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

import java.util.concurrent.atomic.AtomicLong;

public final class SnowflakeId {
    private static final long EPOCH = 1640995200000L; // Custom epoch
    private static final long MACHINE_ID = 1L;        // Unique machine ID (0-1023)

    private static final long MACHINE_ID_BITS = 10L;
    private static final long SEQUENCE_BITS = 12L;

    private static final long MAX_SEQUENCE = (1L << SEQUENCE_BITS) - 1;
    private static final long MACHINE_ID_SHIFT = SEQUENCE_BITS;
    private static final long TIMESTAMP_SHIFT = MACHINE_ID_SHIFT + MACHINE_ID_BITS;

    /**
     * Packed (timestamp_since_epoch &lt;&lt; SEQUENCE_BITS | sequence_within_ms).
     * Single source of truth for monotonicity — split AtomicLongs would race
     * across the millisecond boundary and produce duplicates.
     */
    private static final AtomicLong state = new AtomicLong(0L);

    private SnowflakeId() {
    }

    /**
     * @return A short, unique ID produced in a similar way as Twitter's Snowflake ID.
     */
    public static String generateId() {
        while (true) {
            long currentMs = System.currentTimeMillis() - EPOCH;
            long prev = state.get();
            long prevTs = prev >>> SEQUENCE_BITS;
            long prevSeq = prev & MAX_SEQUENCE;

            long nextTs;
            long nextSeq;
            if (currentMs > prevTs) {
                // Forward time — new millisecond, reset sequence.
                nextTs = currentMs;
                nextSeq = 0L;
            } else {
                // Same ms (or clock went backward — keep prevTs to preserve
                // monotonicity, advance the sequence within it).
                nextTs = prevTs;
                nextSeq = prevSeq + 1;
                if (nextSeq > MAX_SEQUENCE) {
                    // Sequence exhausted within this ms — yield and retry
                    // until the clock advances or we observe a fresher state.
                    Thread.yield();
                    continue;
                }
            }
            long next = (nextTs << SEQUENCE_BITS) | nextSeq;
            if (state.compareAndSet(prev, next)) {
                return encodeBase62((nextTs << TIMESTAMP_SHIFT) | (MACHINE_ID << MACHINE_ID_SHIFT) | nextSeq);
            }
            // CAS lost the race — another thread updated state. Retry; the
            // fresh `prev` read will produce a higher sequence (or timestamp).
        }
    }

    private static final String BASE62_ALPHABET = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";

    private static String encodeBase62(long value) {
        StringBuilder sb = new StringBuilder();
        while (value > 0) {
            sb.append(BASE62_ALPHABET.charAt((int) (value % 62)));
            value /= 62;
        }
        return sb.reverse().toString();
    }
}
