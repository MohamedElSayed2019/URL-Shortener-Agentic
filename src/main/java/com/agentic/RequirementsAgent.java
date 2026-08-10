package com.hcb.sc.load.agentic;

import java.util.ArrayList;
import java.util.List;

/**
 * Simulates the "Requirement Understanding" agent: interprets intent,
 * flags ambiguity, and normalizes free-text requirements into a clear
 * engineering problem statement. In a real system this step would call an
 * LLM; here it uses transparent heuristic rules so behavior is
 * deterministic and testable.
 */
public final class RequirementsAgent {

    public RequirementSpec analyze(String rawRequirement) {
        List<String> ambiguities = new ArrayList<>();
        List<String> assumptions = new ArrayList<>();
        String lower = rawRequirement.toLowerCase();

        if (!lower.contains("expir")) {
            ambiguities.add("No expiry/TTL policy specified for shortened links.");
            assumptions.add("Assume links are permanent unless a TTL is explicitly provided by the caller.");
        }
        if (!lower.contains("custom") && !lower.contains("alias")) {
            ambiguities.add("Unclear whether custom aliases (vanity codes) are required.");
            assumptions.add("Support optional custom aliases; default to auto-generated Base62 codes.");
        }
        if (!lower.contains("rate")) {
            ambiguities.add("No rate limiting / abuse-prevention requirement stated.");
            assumptions.add("Apply a conservative fixed-window rate limit per client to protect the service.");
        }
        if (!lower.contains("analytic")) {
            ambiguities.add("Analytics granularity (click counts vs. referrer/device breakdown) not specified.");
            assumptions.add("Provide click counts plus referrer breakdown as a reasonable default analytics scope.");
        }
        if (!lower.contains("auth")) {
            assumptions.add("No authentication required for this prototype; production would need API keys/OAuth.");
        }

        String normalized = "Design and implement a URL shortening service exposing: "
                + "(1) create-short-url with validation, optional custom alias, optional TTL; "
                + "(2) redirect-by-code with click analytics capture; "
                + "(3) analytics retrieval; (4) abuse protection via rate limiting; "
                + "all with production-quality tests and documentation.";

        return new RequirementSpec(rawRequirement, normalized, ambiguities, assumptions);
    }
}
