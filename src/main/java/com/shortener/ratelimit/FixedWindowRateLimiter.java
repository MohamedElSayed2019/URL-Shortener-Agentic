package com.hcb.sc.load.shortener.ratelimit;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Simple fixed-window rate limiter keyed by client identifier (e.g., IP).
 * A production system would use a sliding window / token bucket backed by
 * Redis; this in-process version demonstrates the reliability control
 * without adding external dependencies to the prototype.
 */
public final class FixedWindowRateLimiter {

    private final int maxRequestsPerWindow;
    private final long windowMs;
    private final ConcurrentHashMap<String, Window> windows = new ConcurrentHashMap<>();

    public FixedWindowRateLimiter(int maxRequestsPerWindow, long windowMs) {
        this.maxRequestsPerWindow = maxRequestsPerWindow;
        this.windowMs = windowMs;
    }

    /**
     * Lock-free check-and-increment. Uses atomic CAS on the window start
     * time to detect/reset expired windows and an AtomicInteger for the
     * counter, avoiding the {@code synchronized} block contention that a
     * naive implementation would incur under high concurrent request rates
     * (e.g., many redirects hitting the same client key simultaneously).
     */
    public boolean allow(String key) {
        long now = System.currentTimeMillis();
        Window w = windows.computeIfAbsent(key, k -> new Window(now));

        long windowStart = w.windowStart.get();
        if (now - windowStart > windowMs) {
            // Attempt to atomically claim the window reset; only one thread
            // wins the CAS and resets the counter, others just proceed to
            // increment against the (possibly just-reset) window.
            if (w.windowStart.compareAndSet(windowStart, now)) {
                w.count.set(0);
            }
        }
        int count = w.count.incrementAndGet();
        return count <= maxRequestsPerWindow;
    }

    private static final class Window {
        final AtomicLong windowStart;
        final AtomicInteger count = new AtomicInteger(0);
        Window(long start) { this.windowStart = new AtomicLong(start); }
    }
}
