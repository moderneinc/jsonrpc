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

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * An InputStream wrapper that limits the number of bytes that can be read
 * from the underlying stream. Once the limit is reached, further reads
 * return -1 (EOF).
 */
public class LimitedInputStream extends FilterInputStream {
    private long remaining;

    public LimitedInputStream(InputStream in, long limit) {
        super(in);
        this.remaining = limit;
    }

    @Override
    public int read() throws IOException {
        if (remaining <= 0) {
            return -1;
        }
        int result = in.read();
        if (result != -1) {
            remaining--;
        }
        return result;
    }

    @Override
    public int read(byte[] b, int off, int len) throws IOException {
        if (remaining <= 0) {
            return -1;
        }
        int toRead = (int) Math.min(len, remaining);
        int result = in.read(b, off, toRead);
        if (result > 0) {
            remaining -= result;
        }
        return result;
    }

    @Override
    public long skip(long n) throws IOException {
        long toSkip = Math.min(n, remaining);
        long skipped = in.skip(toSkip);
        remaining -= skipped;
        return skipped;
    }

    @Override
    public int available() throws IOException {
        return (int) Math.min(in.available(), remaining);
    }

    @Override
    public void close() {
        // Do not close the underlying stream - we need it for subsequent messages
    }

    /**
     * Skips any remaining bytes up to the limit.
     * Call this after reading to ensure the underlying stream is
     * positioned correctly for subsequent reads.
     */
    public void skipRemaining() throws IOException {
        while (remaining > 0) {
            long skipped = skip(remaining);
            if (skipped <= 0) {
                // skip() didn't work, try reading
                if (read() == -1) {
                    break;
                }
            }
        }
    }
}
