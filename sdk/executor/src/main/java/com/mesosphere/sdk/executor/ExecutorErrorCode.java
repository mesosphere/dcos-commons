package com.mesosphere.sdk.executor;

/**
 * This class provides exit codes for Executors.
 */
public class ExecutorErrorCode {
    public static final ExecutorErrorCode SUCCESS = new ExecutorErrorCode(0);
    public static final ExecutorErrorCode TERMINAL_TASK_FAILURE = new ExecutorErrorCode(1);
    public static final ExecutorErrorCode ALL_TASKS_EXITED = new ExecutorErrorCode(2);

    private final int value;

    private ExecutorErrorCode(int value) {
        this.value = value;
    }

    public int getValue() {
        return value;
    }
}
