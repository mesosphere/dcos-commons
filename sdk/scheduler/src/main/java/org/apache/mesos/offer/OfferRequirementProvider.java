package org.apache.mesos.offer;

import org.apache.mesos.Protos;
import org.apache.mesos.specification.Pod;
import org.apache.mesos.specification.TaskSpecification;

import java.util.List;

/**
 * An OfferRequirementProvider generates OfferRequirements representing the requirements of a Container in two
 * scenarios: initial launch, and update of an already running container.
 */
public interface OfferRequirementProvider {
    /**
     * Provides an OfferRequirement encapsulating the needs of a given TaskSpecification.
     * @param pod The specification of a {@link Pod} which has never been launched before.
     * @return An OfferRequirement whose satisfaction will result in the launching of the indicated {@link Pod}.
     * @throws InvalidRequirementException when a failure in requirement creation is encountered.
     */
    OfferRequirement getNewOfferRequirement(Pod pod)
            throws InvalidRequirementException;

    /**
     * Provides an OfferRequirement encapsulating the needs of a given TaskSpecification.
     * @param taskSpecification The specification of a Task which has never been launched before.
     * @return An OfferRequirement whose satisfaction will result in the launching of the indicated Task.
     * @throws InvalidRequirementException when a failure in requirement creation is encountered.
     */
    OfferRequirement getNewOfferRequirement(TaskSpecification taskSpecification)
            throws InvalidRequirementException;

    /**
     * Provides an OfferRequirement encapsulating the needs of a {@link Pod} undergoing an update.
     * The previous incarnation of the pod is passed as its launched group of {@link org.apache.mesos.Protos.TaskInfo}s.
     * The desired state of the {@link Pod} is encapsulated in the Pod.
     * @param taskInfos The previously launched Pod's TaskInfos.
     * @param pod The {@link Pod} describing the new desired configuration for the Pod.
     * @return An OfferRequirement whose satisfaction will result in the launching of the indicated Pod.
     * @throws InvalidRequirementException when a failure in requirement creation is encountered.
     */
    OfferRequirement getExistingOfferRequirement(List<Protos.TaskInfo> taskInfos, Pod pod)
            throws InvalidRequirementException, IllegalStateException;

    /**
     * Provides an OfferRequirement encapsulating the needs of a Task undergoing an update.  The previous incarnation of
     * the task is passed as its launched TaskInfo.  The desired state of the Task is encapsulated in the
     * TaskSpecification.
     * @param taskInfo The previously launched Task's TaskInfo.
     * @param taskSpecification The TaskSpecification describing the new desired configuration for the Task.
     * @return An OfferRequirement whose satisfaction will result in the launching of the indicated Task.
     * @throws InvalidRequirementException when a failure in requirement creation is encountered.
     */
    OfferRequirement getExistingOfferRequirement(Protos.TaskInfo taskInfo, TaskSpecification taskSpecification)
        throws InvalidRequirementException, IllegalStateException;
}
