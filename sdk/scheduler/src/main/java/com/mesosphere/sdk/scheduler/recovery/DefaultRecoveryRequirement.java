package com.mesosphere.sdk.scheduler.recovery;

import com.mesosphere.sdk.offer.OfferRequirement;
import com.mesosphere.sdk.specification.PodInstance;

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
