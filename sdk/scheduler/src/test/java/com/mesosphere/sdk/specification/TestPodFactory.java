package com.mesosphere.sdk.specification;

import org.apache.mesos.Protos;

import com.mesosphere.sdk.config.ConfigNamespace;
import com.mesosphere.sdk.testutils.TestConstants;

import java.util.*;
import java.util.stream.Collectors;

/**
 * This class provides TaskTypeSpecifications for testing purposes.
 */
public class TestPodFactory {
    public static final double CPU = 1.0;
    public static final double MEM = 1000.0;
    public static final double DISK = 2000.0;
    public static final Protos.CommandInfo CMD = Protos.CommandInfo.newBuilder().setValue("echo test-cmd").build();

    public static TaskSpec getTaskSpec() {
        return getTaskSpec(TestConstants.TASK_NAME, TestConstants.RESOURCE_SET_ID);
    }

    public static TaskSpec getTaskSpec(String name, String resourceSetId) {
        return getTaskSpec(
                name,
                resourceSetId,
                CMD.getValue(),
                CPU,
                MEM,
                DISK);
    }

    public static TaskSpec getTaskSpec(
            String name,
            String resourceSetId,
            String cmd,
            double cpu,
            double mem,
            double disk) {
        return getPodSpec(TestConstants.POD_TYPE, resourceSetId, name, cmd, 1, cpu, mem, disk).getTasks().get(0);
    }

    public static ResourceSet getResourceSet(String id, double cpu, double mem, double disk) {
        return DefaultResourceSet.newBuilder(TestConstants.ROLE, TestConstants.PRINCIPAL)
                .id(id)
                .resources(getResources(cpu, mem, TestConstants.ROLE, TestConstants.PRINCIPAL))
                .volumes(getVolumes(disk, TestConstants.ROLE, TestConstants.PRINCIPAL))
                .build();
    }

    public static PodSpec getPodSpec(
            String type,
            String resourceSetId,
            String taskName,
            String cmd,
            int count,
            double cpu,
            double mem,
            double disk) {
        ResourceSet resourceSet = getResourceSet(resourceSetId, cpu, mem, disk);
        TaskSpec taskSpec = DefaultTaskSpec.newBuilder()
                .name(taskName)
                .goalState(GoalState.RUNNING)
                .resourceSet(resourceSet)
                .commandSpec(DefaultCommandSpec.newBuilder(ConfigNamespace.emptyInstance())
                        .value(cmd)
                        .uris(Collections.emptyList())
                        .environment(Collections.emptyMap())
                        .build())
                .configFiles(Collections.emptyList())
                .build();

        return DefaultPodSpec.newBuilder()
                .type(type)
                .count(count)
                .resources(Arrays.asList(resourceSet))
                .tasks(Arrays.asList(taskSpec))
                .build();
    }

    public static PodSpec getPodSpec(String type, int count, List<TaskSpec> taskSpecs) {
        List<ResourceSet> resourceSets = taskSpecs.stream()
                .map(taskSpec -> taskSpec.getResourceSet())
                .collect(Collectors.toList());

        return DefaultPodSpec.newBuilder()
                .type(type)
                .count(count)
                .resources(resourceSets)
                .tasks(taskSpecs)
                .build();
    }

    static Collection<ResourceSpec> getResources(
            double cpu,
            double mem,
            String role,
            String principal) {
        return Arrays.asList(
                new DefaultResourceSpec(
                        "cpus",
                        Protos.Value.newBuilder()
                                .setType(Protos.Value.Type.SCALAR)
                                .setScalar(Protos.Value.Scalar.newBuilder().setValue(cpu))
                                .build(),
                        role,
                        principal,
                        "CPUS"),
                new DefaultResourceSpec(
                        "mem",
                        Protos.Value.newBuilder()
                                .setType(Protos.Value.Type.SCALAR)
                                .setScalar(Protos.Value.Scalar.newBuilder().setValue(mem))
                                .build(),
                        role,
                        principal,
                        "MEM"));
    }

    static Collection<VolumeSpec> getVolumes(double diskSize, String role, String principal) {
        return Arrays.asList(
                new DefaultVolumeSpec(
                        diskSize,
                        VolumeSpec.Type.ROOT,
                        TestConstants.CONTAINER_PATH,
                        role,
                        principal,
                        "VOLUME"));
    }
}
