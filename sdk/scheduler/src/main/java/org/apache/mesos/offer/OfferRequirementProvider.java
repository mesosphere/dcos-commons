package org.apache.mesos.offer;

import org.apache.mesos.Protos;
import org.apache.mesos.specification.PodSpecification;
import org.apache.mesos.specification.TaskSpecification;

import java.util.List;

/**
 * An OfferRequirementProvider generates OfferRequirements representing the requirements of a Container in two
 * scenarios: initial launch, and update of an already running container.
 */
public interface OfferRequirementProvider {
    /**
     * Provides an OfferRequirement encapsulating the needs of a given TaskSpecification.
     * @param podSpecification The specification of a {@link PodSpecification} which has never been launched before.
     * @return An OfferRequirement whose satisfaction will result in the launching of the indicated {@link PodSpecification}.
     * @throws InvalidRequirementException when a failure in requirement creation is encountered.
     */
    OfferRequirement getNewOfferRequirement(PodSpecification podSpecification)
            throws InvalidRequirementException;

    /**
     * Provides an OfferRequirement encapsulating the needs of a {@link PodSpecification} undergoing an update.
     * The previous incarnation of the podSpecification is passed as its launched group of {@link org.apache.mesos.Protos.TaskInfo}s.
     * The desired state of the {@link PodSpecification} is encapsulated in the PodSpecification.
     * @param taskInfos The previously launched PodSpecification's TaskInfos.
     * @param podSpecification The {@link PodSpecification} describing the new desired configuration for the PodSpecification.
     * @return An OfferRequirement whose satisfaction will result in the launching of the indicated PodSpecification.
     * @throws InvalidRequirementException when a failure in requirement creation is encountered.
     */
    OfferRequirement getExistingOfferRequirement(List<Protos.TaskInfo> taskInfos, PodSpecification podSpecification)
            throws InvalidRequirementException, IllegalStateException;
}
