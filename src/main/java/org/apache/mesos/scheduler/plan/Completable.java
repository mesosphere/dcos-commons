package org.apache.mesos.scheduler.plan;

/**
 * Common interface shared by {@link Plan}s, {@link Phase}s and {@link Block}s to indicate that they
 * are complete.
 */
public interface Completable {

    /**
     * Returns whether this object has completed all work.
     */
    boolean isComplete();
}
