package com.mesosphere.sdk.scheduler.recovery;

/**
 * This indicates the type of recovery taking place.  Transient indicates an in place recovery with no posibility of
 * data-loss.  A permanent recovery is destructive and implies loss of data associated with a Pod.
 */
public enum RecoveryType {

    /**
     * No action is needed. No-op.
     */
    NONE,

    /**
     * Transient failure (e.g. exited when goal=RUNNING, or exited non-zero when goal=ONCE or FINISHED, or
     * 'pod restart' cmd), relaunch the task in-place without modifying its persistent volumes.
     */
    TRANSIENT,

    /**
     * Permanent failure (e.g. bad machine or 'pod replace' cmd), destroy the current task's state and relaunch it
     * elsewhere from scratch.
     */
    PERMANENT
}
