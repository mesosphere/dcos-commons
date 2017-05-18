package com.mesosphere.sdk.scheduler.plan;

import com.mesosphere.sdk.offer.TaskUtils;
import com.mesosphere.sdk.specification.*;
import com.mesosphere.sdk.testutils.TestConstants;

import java.util.Arrays;
import java.util.List;

/**
 * Created by gabriel on 5/18/17.
 */
public class PodInstanceRequirementTestUtils {
    public static PodInstanceRequirement getCpuRequirement(double value) {
        return getCpuRequirement(value, 0);
    }

    public static PodInstanceRequirement getCpuRequirement(double value, int index) {
        PodInstance podInstance = getCpuPodInstance(value, index);
        List<String> taskNames = TaskUtils.getTaskNames(podInstance);
        return PodInstanceRequirement.newBuilder(getCpuPodInstance(value, index), taskNames).build();
    }

    public static PodInstance getCpuPodInstance(double value, int index) {
        return new DefaultPodInstance(getCpuPodSpec(value), index);
    }

    public static PodSpec getCpuPodSpec(double value) {
        return DefaultPodSpec.newBuilder("")
                .type(TestConstants.POD_TYPE)
                .count(1)
                .tasks(Arrays.asList(getCpuTaskSpec(value, TestConstants.POD_TYPE)))
                .build();
    }

    public static TaskSpec getCpuTaskSpec(double value, String type) {
        return DefaultTaskSpec.newBuilder()
                .name(TestConstants.TASK_NAME)
                .commandSpec(
                        DefaultCommandSpec.newBuilder(type)
                                .value(TestConstants.TASK_CMD)
                                .build())
                .goalState(GoalState.RUNNING)
                .resourceSet(getCpuResourceSet(value))
                .build();
    }

    public static ResourceSet getCpuResourceSet(double value) {
        return DefaultResourceSet.newBuilder(TestConstants.ROLE, TestConstants.PRINCIPAL)
                .id(TestConstants.RESOURCE_SET_ID)
                .cpus(value)
                .build();
    }
}
