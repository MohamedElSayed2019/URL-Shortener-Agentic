package com.hcb.sc.load.orchestration;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Append-only audit log providing observability/traceability across the
 * entire orchestration run. Thread-safe for parallel task execution.
 */
public final class AuditLog {
    private final List<DecisionLogEntry> entries = new CopyOnWriteArrayList<>();

    public void record(DecisionLogEntry entry) {
        entries.add(entry);
    }

    public List<DecisionLogEntry> entries() {
        return Collections.unmodifiableList(entries);
    }

    public void printAll() {
        entries.forEach(e -> System.out.println(e.toString()));
    }
}
