package com.hcb.sc.load.orchestration;

import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

/**
 * Shared, cross-stage execution context. Preserves outputs, artifacts, and
 * decisions so downstream agents can reason over upstream results
 * (cross-stage context preservation + decision lineage).
 */
public final class ExecutionContext {
    private final Map<String, Object> artifacts = new ConcurrentHashMap<>();
    private final AuditLog auditLog = new AuditLog();
    private final MetricsCollector metrics = new MetricsCollector();
    private volatile boolean safeStopRequested = false;

    public void putArtifact(String key, Object value) {
        artifacts.put(key, value);
    }

    @SuppressWarnings("unchecked")
    public <T> T getArtifact(String key, Class<T> type) {
        Object v = artifacts.get(key);
        return v == null ? null : (T) v;
    }

    public boolean hasArtifact(String key) {
        return artifacts.containsKey(key);
    }

    public AuditLog audit() {
        return auditLog;
    }

    public MetricsCollector metrics() {
        return metrics;
    }

    public void requestSafeStop() {
        this.safeStopRequested = true;
    }

    public boolean isSafeStopRequested() {
        return safeStopRequested;
    }
}
