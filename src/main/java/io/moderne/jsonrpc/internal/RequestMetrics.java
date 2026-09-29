/*
 * Copyright 2026 the original author or authors.
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

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Timer;

import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Micrometer is optional, so this is the only class that references it at runtime; {@code JsonRpc}
 * loads it only once metrics are configured.
 */
public final class RequestMetrics {
    public enum Outcome {
        SUCCESS, ERROR, CLOSED, TIMEOUT, CANCELLED;

        private final String tag = name().toLowerCase(Locale.ROOT);
    }

    private final MeterRegistry registry;
    private final Iterable<Tag> additionalTags;

    // Timers are looked up once per request, so they are cached rather than rebuilt: by method,
    // then by outcome ordinal.
    private final Map<String, Timer[]> clientTimers = new ConcurrentHashMap<>();
    private final Map<String, Timer[]> serverTimers = new ConcurrentHashMap<>();

    public RequestMetrics(MeterRegistry registry, Iterable<Tag> additionalTags) {
        this.registry = registry;
        this.additionalTags = additionalTags;
    }

    public Sample startClient(String method) {
        return new Sample(clientTimers, "jsonrpc.client.requests",
                "Requests sent to the JSON-RPC peer, until the response arrives", method);
    }

    public Sample startServer(String method) {
        return new Sample(serverTimers, "jsonrpc.server.requests",
                "Requests received from the JSON-RPC peer, until the reply is written", method);
    }

    public final class Sample {
        private final Map<String, Timer[]> timers;
        private final String name;
        private final String description;
        private final String method;
        private final Timer.Sample sample = Timer.start(registry);

        private Sample(Map<String, Timer[]> timers, String name, String description, String method) {
            this.timers = timers;
            this.name = name;
            this.description = description;
            this.method = method;
        }

        public void stop(Outcome outcome) {
            try {
                sample.stop(timer(outcome));
            } catch (RuntimeException ignored) {
                // A registry can reject a meter (Prometheus does for a name already registered with
                // other tag keys); that must not fail the request being measured.
            }
        }

        private Timer timer(Outcome outcome) {
            Timer[] byOutcome = timers.computeIfAbsent(method, m -> new Timer[Outcome.values().length]);
            Timer timer = byOutcome[outcome.ordinal()];
            if (timer == null) {
                // Racing threads register the same meter and the registry returns it to both.
                timer = Timer.builder(name)
                        .description(description)
                        .tags(additionalTags)
                        .tag("method", method)
                        .tag("outcome", outcome.tag)
                        .register(registry);
                byOutcome[outcome.ordinal()] = timer;
            }
            return timer;
        }
    }
}
