package com.mesosphere.sdk.testutils;

import com.mesosphere.sdk.testing.TaskTestUtils;
import com.mesosphere.sdk.testing.TestConstants;
import org.apache.mesos.Protos;
import org.junit.contrib.java.lang.system.EnvironmentVariables;

import com.mesosphere.sdk.offer.InvalidRequirementException;
import com.mesosphere.sdk.offer.OfferRequirement;

import java.util.*;

/**
 * This class provides utility methods for tests concerned with OfferRequirements.
 */
public class OfferRequirementTestUtils {

    private OfferRequirementTestUtils() {
        // do not instantiate
    }

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
        return OfferRequirement.create(TestConstants.TASK_TYPE, 0, Arrays.asList(TaskTestUtils.getTaskInfo(resources)));
    }

    public static EnvironmentVariables getOfferRequirementProviderEnvironment() {
        EnvironmentVariables vars = new EnvironmentVariables();
        vars.set("EXECUTOR_URI", "");
        vars.set("LIBMESOS_URI", "");
        return vars;
    }
}
