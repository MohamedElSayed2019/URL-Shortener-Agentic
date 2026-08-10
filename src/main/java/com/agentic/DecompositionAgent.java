package com.hcb.sc.load.agentic;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Simulates the "Task Decomposition" agent: converts the normalized
 * requirement into an actionable, dependency-aware task list.
 */
public final class DecompositionAgent {

    public Map<String, DecomposedTask> decompose(RequirementSpec spec) {
        Map<String, DecomposedTask> tasks = new LinkedHashMap<>();
        tasks.put("design-api", new DecomposedTask("design-api", "Define REST API contract (shorten/redirect/analytics)", List.of()));
        tasks.put("design-data-model", new DecomposedTask("design-data-model", "Define data model (ShortUrl, ClickEvent)", List.of()));
        tasks.put("impl-encoder", new DecomposedTask("impl-encoder", "Implement Base62 encoder", List.of("design-data-model")));
        tasks.put("impl-repository", new DecomposedTask("impl-repository", "Implement thread-safe repository", List.of("design-data-model")));
        tasks.put("impl-service", new DecomposedTask("impl-service", "Implement service logic incl. validation & rate limit", List.of("impl-encoder", "impl-repository")));
        tasks.put("impl-api", new DecomposedTask("impl-api", "Implement HTTP API layer", List.of("design-api", "impl-service")));
        tasks.put("test-unit", new DecomposedTask("test-unit", "Write/execute unit tests for encoder & service", List.of("impl-service")));
        tasks.put("test-integration", new DecomposedTask("test-integration", "Write/execute integration tests for API", List.of("impl-api")));
        tasks.put("doc-generate", new DecomposedTask("doc-generate", "Generate API & architecture documentation", List.of("impl-api")));
        tasks.put("release-check", new DecomposedTask("release-check", "Release readiness gate (tests green, docs present)", List.of("test-unit", "test-integration", "doc-generate")));
        return tasks;
    }
}
