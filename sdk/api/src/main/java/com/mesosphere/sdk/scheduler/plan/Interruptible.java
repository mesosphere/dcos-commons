package com.mesosphere.sdk.scheduler.plan;

/**
 * Interface for plan components which may be flagged as interrupted. The interrupt call is an override on top of any
 * internal status the object may otherwise have.
 */
public interface Interruptible {

    /**
     * A call to interrupt indicates to an {@link Interruptible} that it should not continue work beyond the current
     * point, until {@link #proceed()} is called. This call has no effect if the object is already interrupted.
     */
    void interrupt();

    /**
     * A call to proceed indicates to an {@link Interruptible} that it should may cancel a previous {@link #interrupt()}
     * call and resume with any in-progress work. This call has no effect if the object is already proceeding.
     */
    void proceed();

    /**
     * Indicates whether the object is interrupted or not.
     *
     * @return true if the object is interrupted, false otherwise.
     */
    boolean isInterrupted();

}
