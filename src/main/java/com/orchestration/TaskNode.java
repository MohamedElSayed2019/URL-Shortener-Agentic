package com.hcb.sc.load.orchestration;

import java.util.function.Function;

/**
 * A node in the orchestration dependency graph. Each node represents a
 * unit of agentic work bound to an SDLC {@link Stage}. Supports:
 * - dependency declarations (edges) for sequencing/parallelism
 * - human approval gates for high-impact actions
 * - bounded retries with backoff
 * - rollback actions
 * - policy guardrail checks (entry gate) prior to execution
 */
public final class TaskNode {
    private final String id;
    private final Stage stage;
    private final String description;
    private final String[] dependsOn;
    private final Function<ExecutionContext, TaskResult> action;
    private final Function<ExecutionContext, TaskResult> rollbackAction;
    private final boolean requiresHumanApproval;
    private final int maxRetries;
    private final long retryBackoffMs;

    private volatile TaskStatus status = TaskStatus.PENDING;
    private volatile int attempts = 0;
    private volatile boolean failed = false;

    private TaskNode(Builder b) {
        this.id = b.id;
        this.stage = b.stage;
        this.description = b.description;
        this.dependsOn = b.dependsOn;
        this.action = b.action;
        this.rollbackAction = b.rollbackAction;
        this.requiresHumanApproval = b.requiresHumanApproval;
        this.maxRetries = b.maxRetries;
        this.retryBackoffMs = b.retryBackoffMs;
    }

    public static Builder builder(String id, Stage stage) {
        return new Builder(id, stage);
    }

    public String getId() { return id; }
    public Stage getStage() { return stage; }
    public String getDescription() { return description; }
    public String[] getDependsOn() { return dependsOn; }
    public Function<ExecutionContext, TaskResult> getAction() { return action; }
    public Function<ExecutionContext, TaskResult> getRollbackAction() { return rollbackAction; }
    public boolean requiresHumanApproval() { return requiresHumanApproval; }
    public int getMaxRetries() { return maxRetries; }
    public long getRetryBackoffMs() { return retryBackoffMs; }

    public TaskStatus getStatus() { return status; }
    public void setStatus(TaskStatus status) { this.status = status; }
    public int getAttempts() { return attempts; }
    public void incrementAttempts() { attempts++; }
    public boolean isFailed() { return failed; }
    public void markFailed() { this.failed = true; }

    public static final class Builder {
        private final String id;
        private final Stage stage;
        private String description = "";
        private String[] dependsOn = new String[0];
        private Function<ExecutionContext, TaskResult> action;
        private Function<ExecutionContext, TaskResult> rollbackAction = ctx -> TaskResult.ok("no-op rollback");
        private boolean requiresHumanApproval = false;
        private int maxRetries = 0;
        private long retryBackoffMs = 200;

        private Builder(String id, Stage stage) {
            this.id = id;
            this.stage = stage;
        }

        public Builder description(String d) { this.description = d; return this; }
        public Builder dependsOn(String... ids) { this.dependsOn = ids; return this; }
        public Builder action(Function<ExecutionContext, TaskResult> a) { this.action = a; return this; }
        public Builder rollback(Function<ExecutionContext, TaskResult> r) { this.rollbackAction = r; return this; }
        public Builder requiresHumanApproval(boolean v) { this.requiresHumanApproval = v; return this; }
        public Builder maxRetries(int n) { this.maxRetries = n; return this; }
        public Builder retryBackoffMs(long ms) { this.retryBackoffMs = ms; return this; }

        public TaskNode build() {
            if (action == null) {
                throw new IllegalStateException("Task " + id + " requires an action");
            }
            return new TaskNode(this);
        }
    }
}
