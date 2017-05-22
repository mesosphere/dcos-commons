package com.mesosphere.sdk.scheduler.plan;

import com.mesosphere.sdk.offer.Constants;
import com.mesosphere.sdk.offer.TaskUtils;
import com.mesosphere.sdk.specification.*;
import com.mesosphere.sdk.testutils.TestConstants;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Created by gabriel on 5/18/17.
 */
public class PodInstanceRequirementTestUtils {
    public static PodInstanceRequirement getRootVolumeRequirement(double cpus, double diskSize) {
        return getRootVolumeRequirement(cpus, diskSize, 0);
    }

    public static PodInstanceRequirement getRootVolumeRequirement(double cpus, double diskSize, int index) {
        return getRequirement(getRootVolumeResourceSet(cpus, diskSize), index);
    }

    public static PodInstanceRequirement getCpuRequirement(double value) {
        return getCpuRequirement(value, 0);
    }

    public static PodInstanceRequirement getCpuRequirement(double value, int index) {
        return getRequirement(getCpuResourceSet(value), index);
    }

    public static ResourceSet getCpuResourceSet(double value) {
        return DefaultResourceSet.newBuilder(TestConstants.ROLE, TestConstants.PRINCIPAL)
                .id(TestConstants.RESOURCE_SET_ID)
                .cpus(value)
                .build();
    }


    /**
     * Gets a test root volume resource set
     * @param cpus Some resource other than disk must be specified so CPU size is required.
     * @param diskSize The disk size required.
     */
    public static ResourceSet getRootVolumeResourceSet(double cpus, double diskSize) {
        return DefaultResourceSet.newBuilder(TestConstants.ROLE, TestConstants.PRINCIPAL)
                .id(TestConstants.RESOURCE_SET_ID)
                .cpus(cpus)
                .addVolume(Constants.ROOT_DISK_TYPE, diskSize, TestConstants.CONTAINER_PATH)
                .build();
    }

    private static PodInstanceRequirement getRequirement(ResourceSet resourceSet, int index) {
        TaskSpec taskSpec = DefaultTaskSpec.newBuilder()
                .name(TestConstants.TASK_NAME)
                .commandSpec(
                        DefaultCommandSpec.newBuilder(TestConstants.POD_TYPE)
                                .value(TestConstants.TASK_CMD)
                                .build())
                .goalState(GoalState.RUNNING)
                .resourceSet(resourceSet)
                .build();

        PodSpec podSpec = DefaultPodSpec.newBuilder("executor-uri")
                .type(TestConstants.POD_TYPE)
                .count(1)
                .tasks(Arrays.asList(taskSpec))
                .build();

        PodInstance podInstance = new DefaultPodInstance(podSpec, index);

        List<String> taskNames = podInstance.getPod().getTasks().stream()
                .map(ts -> ts.getName())
                .collect(Collectors.toList());

        return PodInstanceRequirement.newBuilder(podInstance, taskNames).build();
    }
}
