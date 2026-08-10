package com.hcb.sc.load.orchestration;

/**
 * The SDLC stages the orchestration engine coordinates.
 * Stages form the backbone of the dependency graph but individual
 * {@link TaskNode}s can map many tasks onto a single stage, and tasks
 * across stages can run in parallel when their dependencies allow it.
 */
public enum Stage {
    REQUIREMENTS,
    DESIGN,
    IMPLEMENTATION,
    TESTING,
    DOCUMENTATION,
    RELEASE_READINESS
}
