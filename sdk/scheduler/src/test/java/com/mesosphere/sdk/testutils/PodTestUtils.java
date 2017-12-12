package com.mesosphere.sdk.testutils;

import com.mesosphere.sdk.offer.Constants;
import com.mesosphere.sdk.scheduler.plan.DefaultPodInstance;
import com.mesosphere.sdk.scheduler.plan.PodInstanceRequirement;
import com.mesosphere.sdk.specification.*;
import org.apache.mesos.Protos;

import java.util.*;

/**
 * Utilities for generating pods for testing.
 */
public class PodTestUtils {
    public static ResourceSet getResourceSet() {
        Collection<ResourceSpec> resources = Arrays.asList(
                new DefaultResourceSpec(
                        Constants.CPUS_RESOURCE_TYPE,
                        Protos.Value.newBuilder()
                                .setType(Protos.Value.Type.SCALAR)
                                .setScalar(Protos.Value.Scalar.newBuilder()
                                        .setValue(1.0))
                                .build(),
                        TestConstants.ROLE,
                        TestConstants.PRE_RESERVED_ROLE,
                        TestConstants.PRINCIPAL));

        return new DefaultResourceSet(
                TestConstants.RESOURCE_SET_ID,
                resources,
                Collections.emptyList(),
                TestConstants.ROLE,
                TestConstants.PRE_RESERVED_ROLE,
                TestConstants.PRINCIPAL);
    }

    public static TaskSpec getTaskSpec() {
        return DefaultTaskSpec.newBuilder()
                .name(TestConstants.TASK_NAME)
                .goalState(GoalState.RUNNING)
                .resourceSet(getResourceSet())
                .build();
    }

    public static PodSpec getPodSpec() {
        return DefaultPodSpec.newBuilder("http://executor.uri")
                .type(TestConstants.POD_TYPE)
                .count(1)
                .tasks(Arrays.asList(getTaskSpec()))
                .preReservedRole(TestConstants.PRE_RESERVED_ROLE)
                .build();
    }

    public static PodInstance getPodInstance(int index) {
        return new DefaultPodInstance(getPodSpec(), index);
    }

    public static PodInstanceRequirement getPodInstanceRequirement(int index) {
        List<String> tasksToLaunch = Arrays.asList(getTaskSpec().getName());
        return PodInstanceRequirement.newBuilder(getPodInstance(index), tasksToLaunch).build();
    }
}
