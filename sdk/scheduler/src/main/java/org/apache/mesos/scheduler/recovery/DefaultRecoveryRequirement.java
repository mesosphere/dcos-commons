package org.apache.mesos.scheduler.recovery;

import org.apache.mesos.offer.OfferRequirement;
import org.apache.mesos.specification.PodInstance;

/**
 * This default implementation of the RecoveryRequirement interface contains no logic.  It merely encapsulates and
 * provides getters for the underlying OfferRequirement and RecoveryType objects.
 */
public class DefaultRecoveryRequirement implements RecoveryRequirement {
    private final OfferRequirement offerRequirement;
    private final RecoveryType recoveryType;
    private final PodInstance podInstance;

    public DefaultRecoveryRequirement(
            OfferRequirement offerRequirement,
            RecoveryType recoveryType,
            PodInstance podInstance) {
        this.offerRequirement = offerRequirement;
        this.recoveryType = recoveryType;
        this.podInstance = podInstance;
    }

    @Override
    public RecoveryType getRecoveryType() {
        return recoveryType;
    }

    @Override
    public OfferRequirement getOfferRequirement() {
        return offerRequirement;
    }

    @Override
    public PodInstance getPodInstance() {
        return podInstance;
    }
}
