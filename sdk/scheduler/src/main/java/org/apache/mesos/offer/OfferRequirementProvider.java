package org.apache.mesos.offer;

import org.apache.mesos.Protos;
import org.apache.mesos.specification.PodInstance;

import java.util.List;
import java.util.Optional;

/**
 * An OfferRequirementProvider generates OfferRequirements representing the requirements of a Container in two
 * scenarios: initial launch, and update of an already running container.
 */
public interface OfferRequirementProvider {
    OfferRequirement getNewOfferRequirement(PodInstance podInstance) throws InvalidRequirementException;

    OfferRequirement getExistingOfferRequirement(
            List<Protos.TaskInfo> taskInfos,
            Optional<Protos.ExecutorInfo> executorInfoOptional,
            PodInstance podInstance) throws InvalidRequirementException;
}
