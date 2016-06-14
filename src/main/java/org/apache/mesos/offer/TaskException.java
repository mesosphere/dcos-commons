package org.apache.mesos.offer;

/**
 * This class encapsulates Exceptions associated with Tasks
 */
public class TaskException extends Exception {
    public TaskException(String message) {
        super(message);
    }
}
