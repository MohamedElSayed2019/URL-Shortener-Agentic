package com.hcb.sc.load.agentic;

import java.util.List;

/**
 * A single actionable engineering task produced by decomposition, with
 * declared dependencies for sequencing/parallelism.
 */
public final class DecomposedTask {
    private final String id;
    private final String title;
    private final List<String> dependsOn;

    public DecomposedTask(String id, String title, List<String> dependsOn) {
        this.id = id;
        this.title = title;
        this.dependsOn = dependsOn;
    }

    public String getId() { return id; }
    public String getTitle() { return title; }
    public List<String> getDependsOn() { return dependsOn; }
}
