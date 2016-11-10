package org.apache.mesos.specification;

import org.apache.mesos.Protos;
import org.apache.mesos.offer.constrain.PlacementRule;
import org.apache.mesos.testutils.TestConstants;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Optional;

/**
 * This class provides TaskTypeSpecifications for testing purposes.
 */
public class TestTaskSetFactory {
    public static final int COUNT = 1;
    public static final double CPU = 1.0;
    public static final double MEM = 1000.0;
    public static final double DISK = 2000.0;
    public static final Protos.CommandInfo CMD = Protos.CommandInfo.newBuilder().setValue("echo test-cmd").build();

    public static TaskSet getTaskSet() {
        return getTaskSet(TestConstants.TASK_TYPE, COUNT, CMD.getValue(), CPU, MEM, DISK);
    }

    public static TaskSet getUpdateTaskSet() {
        return getTaskSet(TestConstants.TASK_TYPE, COUNT, CMD.getValue(), CPU + 1.0, MEM, DISK);
    }

    public static TaskSet getTaskSet(
            Collection<ConfigFileSpecification> configs,
            Optional<PlacementRule> placement) {
        return getTaskSet(TestConstants.TASK_TYPE, COUNT, CMD.getValue(), CPU, MEM, DISK, configs, placement);
    }

    public static TaskSet getTaskSet(
            String name,
            Integer count,
            String cmd,
            double cpu,
            double mem,
            double disk) {
        return getTaskSet(name, count, cmd, cpu, mem, disk, Collections.emptyList(), Optional.empty());
    }

    public static TaskSet getTaskSet(
            String name,
            Integer count,
            String cmd,
            double cpu,
            double mem,
            double disk,
            Collection<ConfigFileSpecification> configs,
            Optional<PlacementRule> placement) {

        return DefaultTaskSet.create(
                count,
                name,
                getCommand(cmd),
                getResources(cpu, mem, TestConstants.ROLE, TestConstants.PRINCIPAL),
                getVolumes(disk, TestConstants.ROLE, TestConstants.PRINCIPAL),
                configs,
                placement,
                Optional.empty());
    }

    public static TaskSpec getTaskSpec() {
        return getTaskSpec(
                TestConstants.TASK_NAME,
                CMD.getValue(),
                CPU,
                MEM,
                DISK);
    }

    public static ResourceSet getResourceSet() {

    }

    public static PodSpec getPodSpec() {
        return DefaultPodSpec.Builder.newBuilder()
                .type(TestConstants.POD_TYPE)
                .count(1)

    }

    public static TaskSpec getTaskSpec(
            String name,
            String cmd,
            double cpu,
            double mem,
            double disk) {

        return DefaultTaskSpec.newBuilder()
                .name(name)
                .goalState(TaskSpec.GoalState.RUNNING)
                .resourceSetId(TestConstants.RESOURCE_SET_ID)
                .pod(DefaultPodSpec.Builder.newBuilder()
                .)


        return new DefaultTaskSpec(
                name,
                TestConstants.TASK_TYPE,
                getCommand(cmd),
                getResources(cpu, mem, TestConstants.ROLE, TestConstants.PRINCIPAL),
                getVolumes(disk, TestConstants.ROLE, TestConstants.PRINCIPAL),
                Collections.emptyList(),
                Optional.empty(),
                Optional.empty());
    }


    private static Protos.CommandInfo getCommand(String cmd) {
        return Protos.CommandInfo.newBuilder()
                .setValue(cmd)
                .build();
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
                        principal),
                new DefaultResourceSpecification(
                        "mem",
                        Protos.Value.newBuilder()
                                .setType(Protos.Value.Type.SCALAR)
                                .setScalar(Protos.Value.Scalar.newBuilder().setValue(mem))
                                .build(),
                        role,
                        principal));
    }

    static Collection<VolumeSpecification> getVolumes(double diskSize, String role, String principal) {
        return Arrays.asList(
                new DefaultVolumeSpecification(
                        diskSize,
                        VolumeSpecification.Type.ROOT,
                        TestConstants.CONTAINER_PATH,
                        role,
                        principal));
    }
}
