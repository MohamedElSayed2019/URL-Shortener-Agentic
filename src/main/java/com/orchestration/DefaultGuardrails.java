package com.hcb.sc.load.orchestration;

import java.util.ArrayList;
import java.util.List;

/**
 * Basic set of guardrails demonstrating security/compliance/change-control
 * enforcement:
 *  - No task may proceed to IMPLEMENTATION without a REQUIREMENTS artifact.
 *  - No RELEASE_READINESS task may proceed unless tests recorded 0 failures.
 *  - Any task named with "delete" or "drop" requires human approval (defense in depth).
 */
public final class DefaultGuardrails {

    private DefaultGuardrails() {}

    public static PolicyGuardrail requireUpstreamContext(String artifactKey, Stage stageThatNeedsIt) {
        return new PolicyGuardrail() {
            @Override
            public List<String> evaluate(TaskNode task, ExecutionContext ctx) {
                List<String> violations = new ArrayList<>();
                if (task.getStage() == stageThatNeedsIt && !ctx.hasArtifact(artifactKey)) {
                    violations.add("Missing required upstream artifact '" + artifactKey + "' for stage " + stageThatNeedsIt);
                }
                return violations;
            }

            @Override
            public String name() { return "RequireUpstreamContext(" + artifactKey + ")"; }
        };
    }

    public static PolicyGuardrail blockDestructiveWithoutApproval() {
        return new PolicyGuardrail() {
            @Override
            public List<String> evaluate(TaskNode task, ExecutionContext ctx) {
                List<String> violations = new ArrayList<>();
                String lowered = (task.getId() + " " + task.getDescription()).toLowerCase();
                boolean destructive = lowered.contains("delete") || lowered.contains("drop") || lowered.contains("force-push");
                if (destructive && !task.requiresHumanApproval()) {
                    violations.add("Destructive task must declare requiresHumanApproval=true");
                }
                return violations;
            }

            @Override
            public String name() { return "BlockDestructiveWithoutApproval"; }
        };
    }

    public static PolicyGuardrail releaseRequiresGreenTests(String testArtifactKey) {
        return new PolicyGuardrail() {
            @Override
            public List<String> evaluate(TaskNode task, ExecutionContext ctx) {
                List<String> violations = new ArrayList<>();
                if (task.getStage() == Stage.RELEASE_READINESS) {
                    Integer failures = ctx.getArtifact(testArtifactKey, Integer.class);
                    if (failures == null || failures > 0) {
                        violations.add("Release readiness blocked: test failures=" + failures);
                    }
                }
                return violations;
            }

            @Override
            public String name() { return "ReleaseRequiresGreenTests"; }
        };
    }
}
