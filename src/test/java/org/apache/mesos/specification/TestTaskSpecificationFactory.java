package org.apache.mesos.specification;

import org.apache.mesos.Protos;
import org.apache.mesos.offer.ResourceTestUtils;

import java.util.Arrays;
import java.util.Collection;

/**
 * Created by gabriel on 8/28/16.
 */
public class TestTaskSpecificationFactory {
    public static TaskTypeSpecification getTaskSpecification(
            String name,
            Integer count,
            String cmd,
            double cpu,
            double mem) {

        return new DefaultTaskTypeSpecification(
                count,
                name,
                getCommand(cmd),
                getResources(cpu, mem, ResourceTestUtils.testRole, ResourceTestUtils.testPrincipal));
    }

    private static Protos.CommandInfo getCommand(String cmd) {
        return Protos.CommandInfo.newBuilder()
                .setValue(cmd)
                .build();
    }

    private static Collection<ResourceSpecification> getResources(
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
}
