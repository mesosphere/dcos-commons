package org.apache.mesos.testutils;

import org.apache.mesos.Protos;
import org.apache.mesos.offer.InvalidRequirementException;
import org.apache.mesos.offer.OfferRequirement;

import java.util.*;

/**
 * This class provides utility methods for tests concerned with OfferRequirements.
 */
public class OfferRequirementTestUtils {
    public static OfferRequirement getOfferRequirement(Protos.Resource resource) throws InvalidRequirementException {
        return getOfferRequirement(Arrays.asList(resource));
    }

    public static OfferRequirement getOfferRequirement(List<Protos.Resource> resources) throws InvalidRequirementException {
        return new OfferRequirement(Arrays.asList(TaskTestUtils.getTaskInfo(resources)));
    }
}
