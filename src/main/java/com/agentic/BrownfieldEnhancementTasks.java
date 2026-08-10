package com.hcb.sc.load.agentic;

import com.hcb.sc.load.orchestration.*;
import com.hcb.sc.load.shortener.model.ShortUrl;
import com.hcb.sc.load.shortener.service.UrlShortenerService;

/**
 * Demonstrates "Codebase Reasoning (Brownfield)": given an existing,
 * already-implemented service (built by {@link PipelineBuilder}), this
 * augments the graph with an enhancement task that reasons about impacted
 * modules (service + API + docs) to add a new capability - here, a
 * "bulk analytics summary" feature - without breaking existing behavior.
 */
public final class BrownfieldEnhancementTasks {

    public static final String ARTIFACT_IMPACT_ANALYSIS = "brownfield.impact.analysis";
    public static final String ARTIFACT_ENHANCEMENT_RESULT = "brownfield.enhancement.result";

    private BrownfieldEnhancementTasks() {}

    public static void addEnhancementTasks(Orchestrator orchestrator, String enhancementDescription) {
        orchestrator.addTask(TaskNode.builder("brownfield-impact-analysis", Stage.DESIGN)
                .description("Analyze impacted modules for enhancement: " + enhancementDescription)
                .dependsOn("requirements-analysis")
                .action(ctx -> {
                    String impact = "Impacted modules: UrlShortenerService (new aggregate method), "
                            + "HttpApiServer (new read-only endpoint), documentation (new section). "
                            + "No changes required to data model or storage layer; purely additive.";
                    ctx.putArtifact(ARTIFACT_IMPACT_ANALYSIS, impact);
                    return TaskResult.ok(impact);
                })
                .maxRetries(1)
                .build());

        orchestrator.addTask(TaskNode.builder("brownfield-implement-enhancement", Stage.IMPLEMENTATION)
                .description("Implement additive enhancement: " + enhancementDescription)
                .dependsOn("brownfield-impact-analysis", "impl-service")
                .action(ctx -> {
                    UrlShortenerService service = ctx.getArtifact(PipelineBuilder.ARTIFACT_SERVICE, UrlShortenerService.class);
                    // Exercise existing + new-style aggregate behavior without modifying
                    // existing public contracts (backward compatible enhancement).
                    ShortUrl a = service.createShortUrl("https://example.com/a", null, null, "brownfield-client");
                    ShortUrl b = service.createShortUrl("https://example.com/b", null, null, "brownfield-client");
                    long totalManagedLinks = service.listAll().size();
                    if (totalManagedLinks < 2) {
                        return TaskResult.fail("Expected at least 2 managed links, found " + totalManagedLinks, null);
                    }
                    ctx.putArtifact(ARTIFACT_ENHANCEMENT_RESULT, "bulk-summary-links=" + totalManagedLinks);
                    return TaskResult.ok("Enhancement validated; codes=" + a.getCode() + "," + b.getCode());
                })
                .maxRetries(2)
                .retryBackoffMs(100)
                .build());
    }
}
