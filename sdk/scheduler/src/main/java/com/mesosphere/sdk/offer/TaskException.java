package com.mesosphere.sdk.offer;

/**
 * This class encapsulates Exceptions associated with Tasks.
 */
public class TaskException extends Exception {
    public TaskException(Throwable cause) {
        super(cause);
    }

    public TaskException(String message) {
        super(message);
    }

    public TaskException(String message, Throwable cause) {
        super(message, cause);
    }
}
