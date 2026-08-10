package com.hcb.sc.load.agentic;

import com.hcb.sc.load.orchestration.Orchestrator;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PipelineIntegrationTest {

    @Test
    void greenfieldPipelineCompletesSuccessfully() {
        System.setProperty("agentic.autoApprove", "true");
        Orchestrator orchestrator = PipelineBuilder.build("TEST-GREENFIELD",
                "Build a URL shortener with custom alias, TTL expiry, and rate limiting.", false);

        boolean success = orchestrator.run();

        assertTrue(success, "Greenfield pipeline should complete without failures");
        assertEquals(0, orchestrator.getContext().getArtifact(PipelineBuilder.ARTIFACT_TEST_FAILURES, Integer.class));
    }

    @Test
    void brownfieldPipelineAddsAdditiveEnhancementSuccessfully() {
        System.setProperty("agentic.autoApprove", "true");
        Orchestrator orchestrator = PipelineBuilder.build("TEST-BROWNFIELD",
                "Enhance the existing shortener with bulk analytics.", true);
        BrownfieldEnhancementTasks.addEnhancementTasks(orchestrator, "bulk analytics summary");

        boolean success = orchestrator.run();

        assertTrue(success, "Brownfield pipeline should complete without failures");
        assertNotNull(orchestrator.getContext().getArtifact(BrownfieldEnhancementTasks.ARTIFACT_ENHANCEMENT_RESULT, String.class));
    }

    @Test
    void ambiguousRequirementProducesDetectedAmbiguitiesAndAssumptions() {
        RequirementsAgent agent = new RequirementsAgent();
        RequirementSpec spec = agent.analyze("We need something to shorten links for our marketing team, make it good.");

        assertFalse(spec.getAmbiguities().isEmpty(), "Vague requirement should surface ambiguities");
        assertFalse(spec.getAssumptions().isEmpty(), "Agent should log assumptions made to resolve ambiguity");
    }

    @Test
    void releaseGateRequiresApprovalWhenMarkedHighImpact() {
        System.setProperty("agentic.autoApprove", "true"); // auto-approve so pipeline completes in CI
        Orchestrator orchestrator = PipelineBuilder.build("TEST-HIGH-IMPACT",
                "Build a URL shortener service.", true);

        boolean success = orchestrator.run();

        assertTrue(success);
        var releaseTask = orchestrator.getTasks().stream()
                .filter(t -> t.getId().equals("release-check")).findFirst().orElseThrow();
        assertTrue(releaseTask.requiresHumanApproval());
    }
}
