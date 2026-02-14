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
    private static final long MACHINE_ID = 1L; // Unique machine ID (0-1023)

    private static final long MACHINE_ID_BITS = 10L;
    private static final long SEQUENCE_BITS = 12L;

    private static final long MAX_SEQUENCE = (1L << SEQUENCE_BITS) - 1;
    private static final long MACHINE_ID_SHIFT = SEQUENCE_BITS;
    private static final long TIMESTAMP_SHIFT = MACHINE_ID_SHIFT + MACHINE_ID_BITS;

    private static final AtomicLong lastTimestamp = new AtomicLong(-1L);
    private static final AtomicLong sequence = new AtomicLong(0L);

    private SnowflakeId() {
    }

    /**
     * @return A short, unique ID produced in a similar way as Twitter's Snowflake ID
     */
    public static synchronized String generateId() {
        long currentTimestamp = System.currentTimeMillis() - EPOCH;

        if (currentTimestamp == lastTimestamp.get()) {
            // Increment sequence within the same millisecond
            long seq = sequence.incrementAndGet() & MAX_SEQUENCE;
            if (seq == 0) {
                // Sequence exhausted, wait for next millisecond
                while (currentTimestamp <= lastTimestamp.get()) {
                    currentTimestamp = System.currentTimeMillis() - EPOCH;
                }
            }
        } else {
            sequence.set(0L);
        }

        lastTimestamp.set(currentTimestamp);

        return encodeBase62((currentTimestamp << TIMESTAMP_SHIFT) | (MACHINE_ID << MACHINE_ID_SHIFT) | sequence.get());
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
