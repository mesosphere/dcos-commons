package org.apache.mesos.scheduler.recovery;

import org.apache.mesos.offer.OfferRequirement;

/**
 * This default implementation of the RecoveryRequirement interface contains no logic.  It merely encapsulates and
 * provides getters for the underlying OfferRequirement and RecoveryType objects.
 */
public class DefaultRecoveryRequirement implements RecoveryRequirement {
    private final OfferRequirement offerRequirement;
    private final RecoveryType recoveryType;

    public DefaultRecoveryRequirement(OfferRequirement offerRequirement, RecoveryType recoveryType) {
        this.offerRequirement = offerRequirement;
        this.recoveryType = recoveryType;
    }

    @Override
    public RecoveryType getRecoveryType() {
        return recoveryType;
    }

    @Override
    public OfferRequirement getOfferRequirement() {
        return offerRequirement;
    }
}
