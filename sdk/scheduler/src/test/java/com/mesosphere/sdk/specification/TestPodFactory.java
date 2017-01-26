package com.mesosphere.sdk.specification;

import org.apache.mesos.Protos;

import com.mesosphere.sdk.config.ConfigNamespace;
import com.mesosphere.sdk.testutils.TestConstants;

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
        return getTaskSpec(TestConstants.TASK_NAME, TestConstants.RESOURCE_SET_ID);
    }

    public static TaskSpec getTaskSpec(String name, String resourceSetId) {
        return getTaskSpec(
                name,
                CMD.getValue(),
                resourceSetId,
                CPU,
                MEM,
                DISK);
    }

    public static TaskSpec getTaskSpec(
            String name,
            String cmd,
            String resourceSetId,
            double cpu,
            double mem,
            double disk) {
        return getTaskSpec(name, cmd, getResourceSet(resourceSetId, cpu, mem, disk));
    }

    public static TaskSpec getTaskSpec(
            String name,
            String cmd,
            ResourceSet resourceSet) {
        return getTaskSpec(name, cmd, resourceSet, Collections.emptyList());
    }

    public static TaskSpec getTaskSpec(
            String name,
            String cmd,
            ResourceSet resourceSet,
            Collection<ConfigFileSpec> configs) {
        return DefaultTaskSpec.newBuilder()
                .name(name)
                .goalState(GoalState.RUNNING)
                .resourceSet(resourceSet)
                .commandSpec(DefaultCommandSpec.newBuilder(ConfigNamespace.emptyInstance())
                        .value(cmd)
                        .environment(Collections.emptyMap())
                        .build())
                .configFiles(configs)
                .build();
    }

    public static ResourceSet getResourceSet(String id, double cpu, double mem, double disk) {
        return DefaultResourceSet.newBuilder(TestConstants.ROLE, TestConstants.PRINCIPAL)
                .id(id)
                .cpus(cpu)
                .memory(mem)
                .addVolume(VolumeSpec.Type.ROOT.toString(), disk, TestConstants.CONTAINER_PATH)
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
        return getPodSpec(type, count, Arrays.asList(getTaskSpec(taskName, cmd, resourceSetId, cpu, mem, disk)));
    }

    public static PodSpec getPodSpec(String type, int count, List<TaskSpec> taskSpecs) {
        return DefaultPodSpec.newBuilder()
                .type(type)
                .count(count)
                .tasks(taskSpecs)
                .build();
    }
}
