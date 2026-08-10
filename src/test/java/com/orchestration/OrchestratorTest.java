package com.hcb.sc.load.orchestration;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class OrchestratorTest {

    @Test
    void runsIndependentTasksAndSucceeds() {
        Orchestrator orchestrator = new Orchestrator(new ConsoleApprovalGate(), 2);
        orchestrator.addTask(TaskNode.builder("a", Stage.REQUIREMENTS)
                .action(ctx -> TaskResult.ok("a done")).build());
        orchestrator.addTask(TaskNode.builder("b", Stage.DESIGN).dependsOn("a")
                .action(ctx -> TaskResult.ok("b done")).build());

        boolean result = orchestrator.run();

        assertTrue(result);
        assertEquals(TaskStatus.SUCCEEDED, findTask(orchestrator, "a").getStatus());
        assertEquals(TaskStatus.SUCCEEDED, findTask(orchestrator, "b").getStatus());
    }

    @Test
    void skipsDependentsWhenUpstreamFails() {
        Orchestrator orchestrator = new Orchestrator(new ConsoleApprovalGate(), 2);
        orchestrator.addTask(TaskNode.builder("fails", Stage.REQUIREMENTS)
                .action(ctx -> TaskResult.fail("boom", null)).maxRetries(0).build());
        orchestrator.addTask(TaskNode.builder("dependent", Stage.DESIGN).dependsOn("fails")
                .action(ctx -> TaskResult.ok("should not run")).build());

        boolean result = orchestrator.run();

        assertFalse(result);
        assertEquals(TaskStatus.ROLLED_BACK, findTask(orchestrator, "fails").getStatus());
        assertEquals(TaskStatus.SKIPPED, findTask(orchestrator, "dependent").getStatus());
    }

    @Test
    void retriesUpToMaxRetriesThenRollsBack() {
        AtomicInteger attempts = new AtomicInteger(0);
        Orchestrator orchestrator = new Orchestrator(new ConsoleApprovalGate(), 2);
        orchestrator.addTask(TaskNode.builder("flaky", Stage.IMPLEMENTATION)
                .action(ctx -> {
                    attempts.incrementAndGet();
                    return TaskResult.fail("always fails", null);
                })
                .maxRetries(2)
                .retryBackoffMs(1)
                .rollback(ctx -> TaskResult.ok("rolled back"))
                .build());

        orchestrator.run();

        assertEquals(3, attempts.get()); // initial + 2 retries
        assertEquals(TaskStatus.ROLLED_BACK, findTask(orchestrator, "flaky").getStatus());
        assertEquals(2, orchestrator.getContext().metrics().getRetries());
        assertEquals(1, orchestrator.getContext().metrics().getRollbacks());
    }

    @Test
    void guardrailBlocksTaskMissingUpstreamArtifact() {
        Orchestrator orchestrator = new Orchestrator(new ConsoleApprovalGate(), 2);
        orchestrator.addGuardrail(DefaultGuardrails.requireUpstreamContext("missing.artifact", Stage.TESTING));
        orchestrator.addTask(TaskNode.builder("t1", Stage.TESTING)
                .action(ctx -> TaskResult.ok("should be blocked before running")).build());

        boolean result = orchestrator.run();

        assertFalse(result);
        assertEquals(TaskStatus.FAILED, findTask(orchestrator, "t1").getStatus());
    }

    @Test
    void humanApprovalGateCanRejectTask() {
        Orchestrator orchestrator = new Orchestrator((task, ctx) -> false, 2); // always reject
        orchestrator.addTask(TaskNode.builder("needs-approval", Stage.RELEASE_READINESS)
                .requiresHumanApproval(true)
                .action(ctx -> TaskResult.ok("should not execute"))
                .build());

        boolean result = orchestrator.run();

        assertFalse(result);
        assertEquals(TaskStatus.FAILED, findTask(orchestrator, "needs-approval").getStatus());
    }

    @Test
    void safeStopHaltsRemainingScheduling() {
        Orchestrator orchestrator = new Orchestrator(new ConsoleApprovalGate(), 2);
        orchestrator.addTask(TaskNode.builder("first", Stage.REQUIREMENTS)
                .action(ctx -> {
                    ctx.requestSafeStop();
                    return TaskResult.ok("done, requesting stop");
                }).build());
        orchestrator.addTask(TaskNode.builder("second", Stage.DESIGN).dependsOn("first")
                .action(ctx -> TaskResult.ok("should not run after safe-stop")).build());

        orchestrator.run();

        assertEquals(TaskStatus.SAFE_STOPPED, findTask(orchestrator, "second").getStatus());
    }

    @Test
    void replanInjectsNewTasksMidGraph() {
        Orchestrator orchestrator = new Orchestrator(new ConsoleApprovalGate(), 2);
        orchestrator.addTask(TaskNode.builder("base", Stage.REQUIREMENTS)
                .action(ctx -> TaskResult.ok("base done")).build());

        orchestrator.replan(java.util.List.of(
                TaskNode.builder("dynamic", Stage.DESIGN).dependsOn("base")
                        .action(ctx -> TaskResult.ok("dynamic task executed")).build()
        ));

        boolean result = orchestrator.run();

        assertTrue(result);
        assertEquals(TaskStatus.SUCCEEDED, findTask(orchestrator, "dynamic").getStatus());
    }

    private TaskNode findTask(Orchestrator orchestrator, String id) {
        return orchestrator.getTasks().stream().filter(t -> t.getId().equals(id)).findFirst().orElseThrow();
    }
}
