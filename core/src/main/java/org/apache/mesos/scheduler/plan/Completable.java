package org.apache.mesos.scheduler.plan;

import org.apache.mesos.scheduler.Observable;

/**
 * Common interface shared by {@link Plan}s, {@link Phase}s and {@link Block}s to indicate that they
 * are complete.
 */
public interface Completable extends Observable {

    /**
     * Returns whether this object has completed all work.
     */
    boolean isComplete();
}
