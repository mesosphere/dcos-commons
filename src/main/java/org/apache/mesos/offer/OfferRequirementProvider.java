package org.apache.mesos.offer;

import org.apache.mesos.Protos;
import org.apache.mesos.specification.TaskSpecification;

/**
 * Created by gabriel on 8/29/16.
 */
public interface OfferRequirementProvider {
    OfferRequirement getNewOfferRequirement(TaskSpecification taskSpecification) throws InvalidRequirementException;
    OfferRequirement getExistingOfferRequirement(Protos.TaskInfo taskInfo, TaskSpecification taskSpecification)
            throws InvalidRequirementException;
}
