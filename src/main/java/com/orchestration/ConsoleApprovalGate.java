package com.hcb.sc.load.orchestration;

import java.io.BufferedReader;
import java.io.InputStreamReader;

/**
 * Console-based approval gate. Auto-approves in non-interactive/CI mode
 * (system property {@code agentic.autoApprove=true}) so the demo can run
 * unattended, while still exercising the governance code path.
 */
public final class ConsoleApprovalGate implements ApprovalGate {

    @Override
    public boolean requestApproval(TaskNode task, ExecutionContext ctx) {
        boolean autoApprove = Boolean.parseBoolean(System.getProperty("agentic.autoApprove", "true"));
        if (autoApprove) {
            System.out.printf("[APPROVAL] Auto-approving high-impact task '%s' (%s) - autoApprove=true%n",
                    task.getId(), task.getDescription());
            return true;
        }
        try {
            System.out.printf("%n[APPROVAL REQUIRED] Task '%s' (%s) needs human sign-off.%n", task.getId(), task.getDescription());
            System.out.print("Approve? (y/n): ");
            BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
            String line = reader.readLine();
            return line != null && line.trim().equalsIgnoreCase("y");
        } catch (Exception e) {
            System.out.println("[APPROVAL] Failed to read input, defaulting to reject.");
            return false;
        }
    }
}
