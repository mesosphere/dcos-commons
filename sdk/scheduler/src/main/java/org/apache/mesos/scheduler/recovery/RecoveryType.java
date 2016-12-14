package org.apache.mesos.scheduler.recovery;

/**
 * This indicates the type of recovery taking place.  Transient indicates an in place recovery with no posibility of
 * data-loss.  A permanent recovery is destructive and implies loss of data associated with a Pod.
 */
public enum RecoveryType {
    NONE,
    TRANSIENT,
    PERMANENT
}
