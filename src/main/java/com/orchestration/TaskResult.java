package com.hcb.sc.load.orchestration;

/**
 * Result returned by a task's execution function.
 */
public final class TaskResult {
    private final boolean success;
    private final String message;
    private final Throwable error;

    private TaskResult(boolean success, String message, Throwable error) {
        this.success = success;
        this.message = message;
        this.error = error;
    }

    public static TaskResult ok(String message) {
        return new TaskResult(true, message, null);
    }

    public static TaskResult fail(String message, Throwable error) {
        return new TaskResult(false, message, error);
    }

    public boolean isSuccess() { return success; }
    public String getMessage() { return message; }
    public Throwable getError() { return error; }
}
