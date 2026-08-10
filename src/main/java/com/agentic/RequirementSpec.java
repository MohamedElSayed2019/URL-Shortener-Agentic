package com.hcb.sc.load.agentic;

/**
 * Represents the normalized understanding of a raw requirement: intent,
 * identified ambiguities, and the clarified engineering problem statement.
 * Produced by the Requirements agent and consumed by Design/Implementation.
 */
public final class RequirementSpec {
    private final String rawRequirement;
    private final String normalizedProblem;
    private final java.util.List<String> ambiguities;
    private final java.util.List<String> assumptions;

    public RequirementSpec(String rawRequirement, String normalizedProblem,
                            java.util.List<String> ambiguities, java.util.List<String> assumptions) {
        this.rawRequirement = rawRequirement;
        this.normalizedProblem = normalizedProblem;
        this.ambiguities = ambiguities;
        this.assumptions = assumptions;
    }

    public String getRawRequirement() { return rawRequirement; }
    public String getNormalizedProblem() { return normalizedProblem; }
    public java.util.List<String> getAmbiguities() { return ambiguities; }
    public java.util.List<String> getAssumptions() { return assumptions; }

    @Override
    public String toString() {
        return "RequirementSpec{" +
                "normalized='" + normalizedProblem + '\'' +
                ", ambiguities=" + ambiguities +
                ", assumptions=" + assumptions +
                '}';
    }
}
