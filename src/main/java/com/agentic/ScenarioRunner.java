package com.hcb.sc.load.agentic;

import com.hcb.sc.load.orchestration.Orchestrator;

/**
 * Executes the three required demonstration scenarios (greenfield,
 * brownfield, ambiguous) against the orchestration engine and prints a
 * summary report for each, including metrics and audit trail excerpts.
 */
public final class ScenarioRunner {

    public void runAll() {
        runGreenfield();
        runBrownfield();
        runAmbiguous();
    }

    /** Greenfield: brand-new URL shortener built from a clear requirement. */
    public void runGreenfield() {
        String requirement = "Build a URL shortener service with core APIs, analytics, and reliability features. "
                + "Support custom alias and TTL expiry. Apply rate limiting on creation.";
        Orchestrator orchestrator = PipelineBuilder.build("GREENFIELD", requirement, false);
        report("GREENFIELD (new system)", orchestrator);
    }

    /**
     * Brownfield: enhances the already-built system with an additive feature,
     * demonstrating codebase reasoning over impacted modules.
     */
    public void runBrownfield() {
        String requirement = "Enhance the existing URL shortener to support a bulk analytics summary "
                + "across all managed links without breaking existing endpoints.";
        Orchestrator orchestrator = PipelineBuilder.build("BROWNFIELD", requirement, true);
        BrownfieldEnhancementTasks.addEnhancementTasks(orchestrator, "bulk analytics summary endpoint");
        report("BROWNFIELD (enhancement)", orchestrator);
    }

    /**
     * Ambiguous: intentionally vague requirement to demonstrate ambiguity
     * detection, assumption logging, and normalization before execution.
     */
    public void runAmbiguous() {
        String requirement = "We need something to shorten links for our marketing team, make it good.";
        Orchestrator orchestrator = PipelineBuilder.build("AMBIGUOUS", requirement, false);
        report("AMBIGUOUS (vague requirement)", orchestrator);
    }

    private void report(String label, Orchestrator orchestrator) {
        System.out.println("\n================ SCENARIO: " + label + " ================");
        boolean success = orchestrator.run();

        var spec = orchestrator.getContext().getArtifact(PipelineBuilder.ARTIFACT_REQUIREMENT_SPEC,
                com.hcb.sc.load.agentic.RequirementSpec.class);
        if (spec != null) {
            System.out.println("Normalized problem: " + spec.getNormalizedProblem());
            System.out.println("Ambiguities identified: " + spec.getAmbiguities());
            System.out.println("Assumptions made: " + spec.getAssumptions());
        }

        System.out.println("\nTask outcomes:");
        orchestrator.getTasks().forEach(t ->
                System.out.printf("  [%s] %-32s stage=%-18s status=%-16s attempts=%d%n",
                        success ? "OK" : "!!", t.getId(), t.getStage(), t.getStatus(), t.getAttempts()));

        System.out.println("\n" + orchestrator.getContext().metrics().summary());
        System.out.println("Overall scenario result: " + (success ? "SUCCESS" : "FAILED/DEGRADED"));

        System.out.println("\nAudit trail (last 5 entries):");
        var entries = orchestrator.getContext().audit().entries();
        entries.stream().skip(Math.max(0, entries.size() - 5)).forEach(System.out::println);
    }
}
