package com.hcb.sc.load.orchestration;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Tracks reliability metrics for the orchestration run: success rate,
 * retry/rollback frequency, MTTR (mean time to recovery), and end-to-end
 * latency.
 */
public final class MetricsCollector {
    private final AtomicInteger totalTasks = new AtomicInteger();
    private final AtomicInteger succeeded = new AtomicInteger();
    private final AtomicInteger failed = new AtomicInteger();
    private final AtomicInteger retries = new AtomicInteger();
    private final AtomicInteger rollbacks = new AtomicInteger();
    private final AtomicLong totalRecoveryTimeMs = new AtomicLong();
    private final AtomicInteger recoveryEvents = new AtomicInteger();
    private volatile long startNanos;
    private volatile long endNanos;

    public void start() { startNanos = System.nanoTime(); }
    public void stop() { endNanos = System.nanoTime(); }

    public void taskStarted() { totalTasks.incrementAndGet(); }
    public void taskSucceeded() { succeeded.incrementAndGet(); }
    public void taskFailed() { failed.incrementAndGet(); }
    public void retryOccurred() { retries.incrementAndGet(); }
    public void rollbackOccurred() { rollbacks.incrementAndGet(); }

    public void recoveryTimeMs(long ms) {
        totalRecoveryTimeMs.addAndGet(ms);
        recoveryEvents.incrementAndGet();
    }

    public double successRate() {
        int total = totalTasks.get();
        return total == 0 ? 0.0 : (double) succeeded.get() / total;
    }

    public double mttrMs() {
        int events = recoveryEvents.get();
        return events == 0 ? 0.0 : (double) totalRecoveryTimeMs.get() / events;
    }

    public long endToEndLatencyMs() {
        if (endNanos == 0) return (System.nanoTime() - startNanos) / 1_000_000;
        return (endNanos - startNanos) / 1_000_000;
    }

    public int getRetries() { return retries.get(); }
    public int getRollbacks() { return rollbacks.get(); }
    public int getTotalTasks() { return totalTasks.get(); }
    public int getSucceeded() { return succeeded.get(); }
    public int getFailed() { return failed.get(); }

    public String summary() {
        return String.format(
            "Metrics[total=%d, succeeded=%d, failed=%d, successRate=%.1f%%, retries=%d, rollbacks=%d, mttrMs=%.1f, e2eLatencyMs=%d]",
            getTotalTasks(), getSucceeded(), getFailed(), successRate() * 100, getRetries(), getRollbacks(), mttrMs(), endToEndLatencyMs());
    }
}
