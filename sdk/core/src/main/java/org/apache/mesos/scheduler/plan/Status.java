package org.apache.mesos.scheduler.plan;

/**
 * Status of an {@link Element}.
 */
public enum Status {

    /**
     * Execution experienced an error.
     */
    ERROR,

    /**
     * Execution has been interrupted.
     */
    WAITING,

    /**
     * Execution is waiting for suitable offers.
     */
    PENDING,

    /**
     * Execution is in progress.
     */
    IN_PROGRESS,

    /**
     * Execution has completed.
     */
    COMPLETE

}
