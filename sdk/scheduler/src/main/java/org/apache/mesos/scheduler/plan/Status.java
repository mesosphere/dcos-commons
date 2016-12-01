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
     * The step has been evaluated, and any Tasks relevant to it have been killed if necessary.
     */
    PREPARED,

    /**
     * Execution has performed {@link org.apache.mesos.Protos.Offer.Operation}s and is waiting to determine the succeess
     * of those Operations.
     */
    STARTING,

    /**
     * Execution has completed.
     */
    COMPLETE

}
