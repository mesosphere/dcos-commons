package com.mesosphere.sdk.scheduler.plan;

/**
 * Status of an {@link Element}.  The Status indicates to {@link PlanManager}s how to handle {@link Plan} elements.  For
 * a {@link Step} the normal progression of states is:
 *     PENDING --> PREPARED --> STARTING --> COMPLETE
 * When something goes wrong, and a retry is desirable returning to PENDING from PREPARED or STARTING indicates that an
 * {@link Element} should be restarted.  COMPLETE is a terminal state which should not be changed, once it is reached
 * for any given processing of an {@link Element}.
 *
 * The ERROR state indicates that the initial construction of the {@link Element} failed.  This normally occurs when
 * an invalidation configuration is attempted.  For example, changing the disk size specification in a TaskSpec is
 * generally not a legal operation so attempting to create a {@link Step} {@link Element} to accomplish this work would
 * result in a {@link Step} with an ERROR state.
 *
 * The WAITING state indicates that some outside entity as interacted with an {@link Element} and indicated that
 * operation should be halted.  This is normally peformed by interrupting the
 * {@link com.mesosphere.sdk.scheduler.plan.strategy.Strategy} associated with an {@link Element}.  Clearing that
 * interruption should return the {@link Element} to an appropriate state, most likely the PENDING state.
 */
public enum Status {

    /**
     * Execution experienced an error.
     */
    ERROR,

    /**
     * Execution has been interrupted.
     *
     * This value is only returned and never set to a variable.
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
     * Execution has performed {@link org.apache.mesos.Protos.Offer.Operation}s and is waiting to determine the success
     * of those Operations.
     */
    STARTING,

    /**
     * Execution has completed.
     */
    COMPLETE,

    //TODO: make enum implement an interface, forbid step status to be set to WAITING or IN_PROGRESS
    /**
     * Only returned by Phase and Plan getStatus, to state that at least one child is complete and at
     * least one child is in progress (either PENDING or PREPARED).
     *
     * This value is only returned and never set to a variable.
     */
    IN_PROGRESS
}
