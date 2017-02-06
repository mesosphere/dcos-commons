package com.mesosphere.sdk.offer;

import com.mesosphere.sdk.scheduler.plan.PodInstanceRequirement;

/**
 * An OfferRequirementProvider generates OfferRequirements representing the requirements of a Container in two
 * scenarios: initial launch, and update of an already running container.
 */
public interface OfferRequirementProvider {
    OfferRequirement getNewOfferRequirement(PodInstanceRequirement podInstanceRequirement)
            throws InvalidRequirementException;
    OfferRequirement getExistingOfferRequirement(PodInstanceRequirement podInstanceRequirement)
            throws InvalidRequirementException;
}
