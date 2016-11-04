package org.apache.mesos.testutils;

import org.apache.mesos.Protos;
import org.apache.mesos.offer.InvalidRequirementException;
import org.apache.mesos.offer.OfferRequirement;

import java.util.*;

/**
 * This class provides utility methods for tests concerned with OfferRequirements.
 */
public class OfferRequirementTestUtils {

    public static OfferRequirement getOfferRequirement() {
        try {
            return getOfferRequirement(ResourceTestUtils.getUnreservedCpu(1.0));
        } catch (InvalidRequirementException e) {
            throw new IllegalStateException(e);
        }
    }

    public static OfferRequirement getOfferRequirement(Protos.Resource resource) throws InvalidRequirementException {
        return getOfferRequirement(Arrays.asList(resource));
    }

    public static OfferRequirement getOfferRequirement(List<Protos.Resource> resources) throws InvalidRequirementException {
        return OfferRequirement.create(TestConstants.TASK_TYPE, Arrays.asList(TaskTestUtils.getTaskInfo(resources)));
    }
}
