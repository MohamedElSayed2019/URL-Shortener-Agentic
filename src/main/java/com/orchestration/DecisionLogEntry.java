package com.hcb.sc.load.orchestration;

import java.time.Instant;

/**
 * Immutable audit-grade record capturing a single decision/event in the
 * orchestration lifecycle. Used to build decision lineage across stages.
 */
public final class DecisionLogEntry {
    private final Instant timestamp;
    private final String taskId;
    private final Stage stage;
    private final String actor; // agent name or "HUMAN"
    private final String action;
    private final String rationale;
    private final TaskStatus resultingStatus;

    public DecisionLogEntry(String taskId, Stage stage, String actor, String action,
                             String rationale, TaskStatus resultingStatus) {
        this.timestamp = Instant.now();
        this.taskId = taskId;
        this.stage = stage;
        this.actor = actor;
        this.action = action;
        this.rationale = rationale;
        this.resultingStatus = resultingStatus;
    }

    public Instant getTimestamp() { return timestamp; }
    public String getTaskId() { return taskId; }
    public Stage getStage() { return stage; }
    public String getActor() { return actor; }
    public String getAction() { return action; }
    public String getRationale() { return rationale; }
    public TaskStatus getResultingStatus() { return resultingStatus; }

    @Override
    public String toString() {
        return String.format("[%s] stage=%s task=%s actor=%s action=%s status=%s rationale=%s",
                timestamp, stage, taskId, actor, action, resultingStatus, rationale);
    }
}
