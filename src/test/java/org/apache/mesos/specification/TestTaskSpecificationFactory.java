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

        return new TaskTypeSpecification() {
            @Override
            public int getCount() {
                return count;
            }

            @Override
            public String getName() {
                return name;
            }

            @Override
            public Protos.CommandInfo getCommand() {
                return Protos.CommandInfo.newBuilder()
                        .setValue(cmd)
                        .build();
            }

            @Override
            public Collection<ResourceSpecification> getResources() {
                return Arrays.asList(
                        new ResourceSpecification() {
                            @Override
                            public Protos.Value getValue() {
                                return Protos.Value.newBuilder()
                                        .setType(Protos.Value.Type.SCALAR)
                                        .setScalar(Protos.Value.Scalar.newBuilder().setValue(cpu))
                                        .build();
                            }

                            @Override
                            public String getRole() {
                                return ResourceTestUtils.testRole;
                            }

                            @Override
                            public String getPrincipal() {
                                return ResourceTestUtils.testPrincipal;
                            }

                            @Override
                            public String getName() {
                                return "cpus";
                            }
                        },
                        new ResourceSpecification() {
                            @Override
                            public Protos.Value getValue() {
                                return Protos.Value.newBuilder()
                                        .setType(Protos.Value.Type.SCALAR)
                                        .setScalar(Protos.Value.Scalar.newBuilder().setValue(mem))
                                        .build();
                            }

                            @Override
                            public String getRole() {
                                return ResourceTestUtils.testRole;
                            }

                            @Override
                            public String getPrincipal() {
                                return ResourceTestUtils.testPrincipal;
                            }

                            @Override
                            public String getName() {
                                return "mem";
                            }
                        }
                );
            }
        };
    }
}
