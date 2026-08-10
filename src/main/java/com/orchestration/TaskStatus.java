package com.hcb.sc.load.orchestration;

/** Lifecycle status of a task node within the orchestration graph. */
public enum TaskStatus {
    PENDING,
    BLOCKED,
    RUNNING,
    AWAITING_APPROVAL,
    RETRYING,
    SUCCEEDED,
    FAILED,
    ROLLED_BACK,
    SKIPPED,
    SAFE_STOPPED
}
