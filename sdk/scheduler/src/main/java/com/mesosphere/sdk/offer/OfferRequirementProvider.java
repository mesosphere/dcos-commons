package com.mesosphere.sdk.offer;

import com.mesosphere.sdk.specification.PodInstance;

import java.util.List;

/**
 * An OfferRequirementProvider generates OfferRequirements representing the requirements of a Container in two
 * scenarios: initial launch, and update of an already running container.
 */
public interface OfferRequirementProvider {
    OfferRequirement getNewOfferRequirement(PodInstance podInstance, List<String> tasksToLaunch)
            throws InvalidRequirementException;
    OfferRequirement getExistingOfferRequirement(PodInstance podInstance, List<String> tasksToLaunch)
            throws InvalidRequirementException;
}
