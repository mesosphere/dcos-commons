package com.mesosphere.sdk.offer;

import com.mesosphere.sdk.specification.PodInstance;

import java.util.Collection;

/**
 * An OfferRequirementProvider generates OfferRequirements representing the requirements of a Container in two
 * scenarios: initial launch, and update of an already running container.
 */
public interface OfferRequirementProvider {
    OfferRequirement getNewOfferRequirement(PodInstance podInstance, Collection<String> tasksToLaunch)
            throws InvalidRequirementException;
    OfferRequirement getExistingOfferRequirement(PodInstance podInstance, Collection<String> tasksToLaunch)
            throws InvalidRequirementException;
}
