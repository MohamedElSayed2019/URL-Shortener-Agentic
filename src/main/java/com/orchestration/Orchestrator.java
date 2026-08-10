package com.hcb.sc.load.orchestration;

import java.util.*;
import java.util.concurrent.*;

/**
 * Stateful, non-linear orchestration engine.
 *
 * <p>Design:
 * <ul>
 *   <li>Tasks form a directed acyclic graph (DAG) via {@link TaskNode#getDependsOn()}.</li>
 *   <li>Tasks whose dependencies are satisfied become eligible concurrently
 *       (parallel execution), while dependent tasks wait (synchronization) -
 *       this gives non-linear execution rather than a simple linear chain.</li>
 *   <li>Before running, each task passes through an entry gate of
 *       {@link PolicyGuardrail}s; violations block execution (governance).</li>
 *   <li>Tasks marked {@code requiresHumanApproval} pause for an
 *       {@link ApprovalGate} decision (human checkpoint on high-impact actions).</li>
 *   <li>Failures are retried up to {@code maxRetries} with backoff; if still
 *       failing, the engine invokes the task's rollback action and marks
 *       dependents SKIPPED (bounded retries + rollback + fallback).</li>
 *   <li>{@link ExecutionContext#requestSafeStop()} allows any agent (or an
 *       external monitor) to halt further scheduling safely (safe-stop control).</li>
 *   <li>All state transitions are written to the {@link AuditLog} for
 *       audit-grade traceability, and {@link MetricsCollector} tracks
 *       reliability metrics.</li>
 *   <li>Re-planning: {@code replan(...)} lets the caller inject new/updated
 *       tasks mid-run (e.g., when upstream outputs change), which are merged
 *       into the still-pending graph while preserving already-completed state.</li>
 * </ul>
 */
public final class Orchestrator {

    private final Map<String, TaskNode> tasks = new ConcurrentHashMap<>();
    private final ExecutionContext context = new ExecutionContext();
    private final List<PolicyGuardrail> guardrails = new ArrayList<>();
    private final ApprovalGate approvalGate;
    private final ExecutorService pool;

    public Orchestrator(ApprovalGate approvalGate, int parallelism) {
        this.approvalGate = approvalGate;
        this.pool = Executors.newFixedThreadPool(Math.max(2, parallelism));
    }

    public Orchestrator addTask(TaskNode task) {
        tasks.put(task.getId(), task);
        return this;
    }

    public Orchestrator addGuardrail(PolicyGuardrail guardrail) {
        guardrails.add(guardrail);
        return this;
    }

    public ExecutionContext getContext() { return context; }

    /** Injects additional tasks mid-run to support dynamic re-planning. */
    public void replan(List<TaskNode> newOrUpdatedTasks) {
        for (TaskNode t : newOrUpdatedTasks) {
            tasks.put(t.getId(), t);
        }
        context.audit().record(new DecisionLogEntry("ORCHESTRATOR", null, "ENGINE",
                "REPLAN", "Injected/updated " + newOrUpdatedTasks.size() + " task(s)", TaskStatus.PENDING));
    }

    /**
     * Executes the full DAG to completion (or until safe-stop / unrecoverable
     * failure chain). Returns true if all tasks reached a terminal
     * non-failing state.
     */
    public boolean run() {
        context.metrics().start();
        try {
            Set<String> finished = new HashSet<>(); // SUCCEEDED, SKIPPED, ROLLED_BACK terminal
            boolean progress = true;

            while (progress && !allTerminal(finished)) {
                progress = false;

                if (context.isSafeStopRequested()) {
                    markRemainingSafeStopped(finished);
                    break;
                }

                List<TaskNode> eligible = eligibleTasks(finished);
                if (eligible.isEmpty()) {
                    break; // either done or deadlocked (cyclic/blocked deps)
                }

                // Run all currently-eligible tasks in parallel, then
                // synchronize (join) before recomputing the next eligible set.
                List<Future<Void>> futures = new ArrayList<>();
                for (TaskNode task : eligible) {
                    task.setStatus(TaskStatus.RUNNING);
                    futures.add(pool.submit(() -> { executeWithRetry(task); return null; }));
                }
                for (Future<Void> f : futures) {
                    try { f.get(); } catch (Exception ignored) { /* individual task errors handled internally */ }
                }

                for (TaskNode task : eligible) {
                    if (isTerminal(task.getStatus())) {
                        finished.add(task.getId());
                        progress = true;
                    }
                }
            }

            return context.metrics().getFailed() == 0;
        } finally {
            context.metrics().stop();
            pool.shutdown();
        }
    }

    private boolean allTerminal(Set<String> finished) {
        return finished.size() == tasks.size();
    }

    private boolean isTerminal(TaskStatus s) {
        return s == TaskStatus.SUCCEEDED || s == TaskStatus.SKIPPED
                || s == TaskStatus.ROLLED_BACK || s == TaskStatus.SAFE_STOPPED
                || s == TaskStatus.FAILED;
    }

    private List<TaskNode> eligibleTasks(Set<String> finished) {
        List<TaskNode> eligible = new ArrayList<>();
        for (TaskNode t : tasks.values()) {
            if (finished.contains(t.getId())) continue;
            if (t.getStatus() == TaskStatus.RUNNING) continue;
            boolean depsSatisfied = true;
            boolean anyDepFailed = false;
            for (String dep : t.getDependsOn()) {
                TaskNode depTask = tasks.get(dep);
                if (depTask == null) continue;
                if (!finished.contains(dep)) { depsSatisfied = false; break; }
                if (depTask.isFailed()) { anyDepFailed = true; }
            }
            if (anyDepFailed) {
                t.setStatus(TaskStatus.SKIPPED);
                finished.add(t.getId());
                context.audit().record(new DecisionLogEntry(t.getId(), t.getStage(), "ENGINE",
                        "SKIP", "Upstream dependency failed", TaskStatus.SKIPPED));
                continue;
            }
            if (depsSatisfied) {
                eligible.add(t);
            }
        }
        return eligible;
    }

    private void markRemainingSafeStopped(Set<String> finished) {
        for (TaskNode t : tasks.values()) {
            if (!finished.contains(t.getId()) && !isTerminal(t.getStatus())) {
                t.setStatus(TaskStatus.SAFE_STOPPED);
                finished.add(t.getId());
                context.audit().record(new DecisionLogEntry(t.getId(), t.getStage(), "ENGINE",
                        "SAFE_STOP", "Safe-stop requested; halting scheduling", TaskStatus.SAFE_STOPPED));
            }
        }
    }

    private void executeWithRetry(TaskNode task) {
        // Entry gate: policy guardrails
        List<String> violations = new ArrayList<>();
        for (PolicyGuardrail g : guardrails) {
            violations.addAll(g.evaluate(task, context));
        }
        if (!violations.isEmpty()) {
            task.setStatus(TaskStatus.FAILED);
            task.markFailed();
            context.metrics().taskStarted();
            context.metrics().taskFailed();
            context.audit().record(new DecisionLogEntry(task.getId(), task.getStage(), "GOVERNANCE",
                    "BLOCKED", "Guardrail violations: " + String.join("; ", violations), TaskStatus.FAILED));
            return;
        }

        // Human approval checkpoint
        if (task.requiresHumanApproval()) {
            task.setStatus(TaskStatus.AWAITING_APPROVAL);
            context.audit().record(new DecisionLogEntry(task.getId(), task.getStage(), "ENGINE",
                    "AWAITING_APPROVAL", "High-impact task requires sign-off", TaskStatus.AWAITING_APPROVAL));
            boolean approved = approvalGate.requestApproval(task, context);
            context.audit().record(new DecisionLogEntry(task.getId(), task.getStage(), "HUMAN",
                    approved ? "APPROVED" : "REJECTED", "Human decision recorded", task.getStatus()));
            if (!approved) {
                task.setStatus(TaskStatus.FAILED);
                task.markFailed();
                context.metrics().taskStarted();
                context.metrics().taskFailed();
                return;
            }
        }

        long recoveryStart = System.currentTimeMillis();
        context.metrics().taskStarted();
        int attempt = 0;
        while (true) {
            attempt++;
            task.incrementAttempts();
            TaskResult result;
            try {
                result = task.getAction().apply(context);
            } catch (Exception e) {
                result = TaskResult.fail("Exception: " + e.getMessage(), e);
            }

            if (result.isSuccess()) {
                task.setStatus(TaskStatus.SUCCEEDED);
                context.metrics().taskSucceeded();
                if (attempt > 1) {
                    context.metrics().recoveryTimeMs(System.currentTimeMillis() - recoveryStart);
                }
                context.audit().record(new DecisionLogEntry(task.getId(), task.getStage(), "AGENT",
                        "EXECUTE", result.getMessage() + " (attempt " + attempt + ")", TaskStatus.SUCCEEDED));
                return;
            }

            context.audit().record(new DecisionLogEntry(task.getId(), task.getStage(), "AGENT",
                    "EXECUTE_FAILED", result.getMessage() + " (attempt " + attempt + ")", TaskStatus.RETRYING));

            if (attempt <= task.getMaxRetries()) {
                task.setStatus(TaskStatus.RETRYING);
                context.metrics().retryOccurred();
                try { Thread.sleep(task.getRetryBackoffMs() * attempt); } catch (InterruptedException ignored) {}
                continue;
            }

            // Exhausted retries -> rollback (fallback control)
            task.setStatus(TaskStatus.FAILED);
            task.markFailed();
            context.metrics().taskFailed();
            try {
                TaskResult rb = task.getRollbackAction().apply(context);
                context.metrics().rollbackOccurred();
                task.setStatus(TaskStatus.ROLLED_BACK);
                context.audit().record(new DecisionLogEntry(task.getId(), task.getStage(), "AGENT",
                        "ROLLBACK", rb.getMessage(), TaskStatus.ROLLED_BACK));
            } catch (Exception rbEx) {
                context.audit().record(new DecisionLogEntry(task.getId(), task.getStage(), "AGENT",
                        "ROLLBACK_FAILED", rbEx.getMessage(), TaskStatus.FAILED));
            }
            return;
        }
    }

    public Collection<TaskNode> getTasks() {
        return tasks.values();
    }
}
