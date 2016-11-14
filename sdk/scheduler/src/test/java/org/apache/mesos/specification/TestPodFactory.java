package org.apache.mesos.specification;

import org.apache.mesos.Protos;
import org.apache.mesos.testutils.TestConstants;

import java.util.*;

/**
 * This class provides TaskTypeSpecifications for testing purposes.
 */
public class TestPodFactory {
    public static final double CPU = 1.0;
    public static final double MEM = 1000.0;
    public static final double DISK = 2000.0;
    public static final Protos.CommandInfo CMD = Protos.CommandInfo.newBuilder().setValue("echo test-cmd").build();

    public static TaskSpec getTaskSpec() {
        return getTaskSpec(
                TestConstants.TASK_NAME,
                CMD.getValue(),
                CPU,
                MEM,
                DISK);
    }

    public static ResourceSet getResourceSet(double cpu, double mem, double disk) {
        return DefaultResourceSet.newBuilder()
                .id(TestConstants.RESOURCE_SET_ID)
                .resources(getResources(cpu, mem, TestConstants.ROLE, TestConstants.PRINCIPAL))
                .volumes(getVolumes(disk, TestConstants.ROLE, TestConstants.PRINCIPAL))
                .build();
    }

    public static PodSpec getPodSpec(String type, String taskName, String cmd, int count, double cpu, double mem, double disk) {
        ResourceSet resourceSet = getResourceSet(cpu, mem, disk);
        TaskSpec taskSpec = DefaultTaskSpec.newBuilder()
                .name(taskName)
                .goalState(TaskSpec.GoalState.RUNNING)
                .resourceSet(resourceSet)
                .commandSpec(DefaultCommandSpec.newBuilder()
                        .value(cmd)
                        .uris(Collections.emptyList())
                        .environment(Collections.emptyMap())
                        .build())
                .configFiles(Collections.emptyList())
                .type(TestConstants.POD_TYPE)
                .build();

        return DefaultPodSpec.newBuilder()
                .type(type)
                .count(count)
                .resources(Arrays.asList(resourceSet))
                .tasks(Arrays.asList(taskSpec))
                .build();
    }

    public static TaskSpec getTaskSpec(
            String name,
            String cmd,
            double cpu,
            double mem,
            double disk) {
        return getPodSpec(TestConstants.POD_TYPE, name, cmd, 1, cpu, mem, disk).getTasks().get(0);
    }


    static Collection<ResourceSpecification> getResources(
            double cpu,
            double mem,
            String role,
            String principal) {
        return Arrays.asList(
                new DefaultResourceSpecification(
                        "cpus",
                        Protos.Value.newBuilder()
                                .setType(Protos.Value.Type.SCALAR)
                                .setScalar(Protos.Value.Scalar.newBuilder().setValue(cpu))
                                .build(),
                        role,
                        principal,
                        "CPUS"),
                new DefaultResourceSpecification(
                        "mem",
                        Protos.Value.newBuilder()
                                .setType(Protos.Value.Type.SCALAR)
                                .setScalar(Protos.Value.Scalar.newBuilder().setValue(mem))
                                .build(),
                        role,
                        principal,
                        "MEM"));
    }

    static Collection<VolumeSpecification> getVolumes(double diskSize, String role, String principal) {
        return Arrays.asList(
                new DefaultVolumeSpecification(
                        diskSize,
                        VolumeSpecification.Type.ROOT,
                        TestConstants.CONTAINER_PATH,
                        role,
                        principal,
                        "VOLUME"));
    }
}
