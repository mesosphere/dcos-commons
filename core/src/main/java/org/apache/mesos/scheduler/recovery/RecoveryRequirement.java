package org.apache.mesos.scheduler.recovery;

import org.apache.mesos.offer.OfferRequirement;

/**
 * Implementation of this interface allows RecoverySchedulers to make decisions about the scheduling of recovery of
 * failed Tasks.
 */
public interface RecoveryRequirement {
    /**
     * Possible recovery types are NONE, TRANSIENT and PERMANENT.  NONE indicates that no recovery is under way.
     * TRANSIENT indicates that non-destructive recovery is being attempted.  PERMANENT indicates that a destructive
     * recovery operation is desired.
     */
    enum RecoveryType {
        NONE,
        TRANSIENT,
        PERMANENT
    }

    RecoveryType getRecoveryType();

    OfferRequirement getOfferRequirement();
}
