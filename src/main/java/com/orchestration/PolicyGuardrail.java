package com.hcb.sc.load.orchestration;

import java.util.List;

/**
 * Policy guardrail evaluated at task entry gates. Guardrails enforce
 * security, compliance, and change-control constraints before an agent
 * is permitted to execute a task.
 */
public interface PolicyGuardrail {
    /** @return list of violation messages; empty list means the gate passes. */
    List<String> evaluate(TaskNode task, ExecutionContext ctx);

    String name();
}
