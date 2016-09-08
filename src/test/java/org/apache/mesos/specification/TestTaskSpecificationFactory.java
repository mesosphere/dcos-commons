package org.apache.mesos.specification;

import org.apache.mesos.Protos;
import org.apache.mesos.protobuf.DefaultVolumeSpecification;
import org.apache.mesos.testutils.TestConstants;

import java.util.Arrays;
import java.util.Collection;
import java.util.Optional;

/**
 * This class provides TaskTypeSpecifications for testing purposes.
 */
public class TestTaskSpecificationFactory {
    public static final String NAME = "test-task-type";
    public static final int COUNT = 1;
    public static final double CPU = 1.0;
    public static final double MEM = 1000.0;
    public static final double DISK = 2000.0;
    public static final Protos.CommandInfo CMD = Protos.CommandInfo.newBuilder().setValue("echo test-cmd").build();

    public static TaskTypeSpecification getTaskTypeSpecification() {
        return getTaskTypeSpecification(
                NAME,
                COUNT,
                CMD.getValue(),
                CPU,
                MEM,
                DISK);
    }

    public static TaskTypeSpecification getTaskTypeSpecification(
            String name,
            Integer count,
            String cmd,
            double cpu,
            double mem,
            double disk) {

        return new DefaultTaskTypeSpecification(
                count,
                name,
                getCommand(cmd),
                getResources(cpu, mem, TestConstants.role, TestConstants.principal),
                getVolumes(disk, TestConstants.role, TestConstants.principal));
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

    static Optional<Collection<VolumeSpecification>> getVolumes(double diskSize, String role, String principal) {
        return Optional.of(
                Arrays.asList(
                        new DefaultVolumeSpecification(
                                diskSize,
                                VolumeSpecification.Type.ROOT,
                                TestConstants.containerPath,
                                role,
                                principal)));
    }
}
