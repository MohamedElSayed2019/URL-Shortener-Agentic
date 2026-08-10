package com.hcb.sc.load.orchestration;

/**
 * Governance interface for human approval checkpoints on high-impact
 * actions. A real deployment would wire this to a ticketing system,
 * Slack approval bot, or web UI; the default console implementation
 * satisfies the "human-in-the-loop" requirement for this prototype.
 */
public interface ApprovalGate {
    /**
     * @param task task requiring approval
     * @param ctx execution context (for showing artifacts/rationale)
     * @return true if approved, false if rejected
     */
    boolean requestApproval(TaskNode task, ExecutionContext ctx);
}
