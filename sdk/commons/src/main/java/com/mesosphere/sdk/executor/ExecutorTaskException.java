package com.mesosphere.sdk.executor;

/**
 * Checked exception related to {@code ExecutorTask} operations.
 */
public class ExecutorTaskException extends Exception {
    public ExecutorTaskException() {
        super();
    }

    public ExecutorTaskException(String message) {
        super(message);
    }

    public ExecutorTaskException(String message, Throwable cause) {
        super(message, cause);
    }

    public ExecutorTaskException(Throwable cause) {
        super(cause);
    }

    protected ExecutorTaskException(
            String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
        super(message, cause, enableSuppression, writableStackTrace);
    }
}
