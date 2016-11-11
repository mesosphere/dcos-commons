package org.apache.mesos.offer;

import org.apache.mesos.specification.PodInstance;

/**
 * An OfferRequirementProvider generates OfferRequirements representing the requirements of a Container in two
 * scenarios: initial launch, and update of an already running container.
 */
public interface OfferRequirementProvider {
    OfferRequirement getNewOfferRequirement(PodInstance podInstance) throws InvalidRequirementException;
    OfferRequirement getExistingOfferRequirement(PodInstance podInstance) throws InvalidRequirementException;
}
