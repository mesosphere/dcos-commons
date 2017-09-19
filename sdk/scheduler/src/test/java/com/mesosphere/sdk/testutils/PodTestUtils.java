package com.mesosphere.sdk.testutils;

import com.mesosphere.sdk.offer.Constants;
import com.mesosphere.sdk.offer.evaluate.placement.PlacementRule;
import com.mesosphere.sdk.scheduler.plan.PodInstanceRequirement;
import com.mesosphere.sdk.specification.*;
import com.mesosphere.sdk.specification.util.RLimit;
import org.apache.mesos.Protos;

import java.net.URI;
import java.util.*;

/**
 * Created by gabriel on 9/7/17.
 */
public class PodTestUtils {
    public static ResourceSet getResourceSet() {
        return new ResourceSet() {
            @Override
            public String getId() {
                return TestConstants.RESOURCE_SET_ID;
            }

            @Override
            public Collection<ResourceSpec> getResources() {
                return Arrays.asList(new ResourceSpec() {
                    @Override
                    public Protos.Value getValue() {
                        return Protos.Value.newBuilder()
                                .setType(Protos.Value.Type.SCALAR)
                                .setScalar(Protos.Value.Scalar.newBuilder()
                                        .setValue(1.0))
                                .build();
                    }

                    @Override
                    public String getName() {
                        return Constants.CPUS_RESOURCE_TYPE;
                    }

                    @Override
                    public String getRole() {
                        return TestConstants.ROLE;
                    }

                    @Override
                    public String getPreReservedRole() {
                        return TestConstants.PRE_RESERVED_ROLE;
                    }

                    @Override
                    public String getPrincipal() {
                        return TestConstants.PRINCIPAL;
                    }
                });
            }

            @Override
            public Collection<VolumeSpec> getVolumes() {
                return Collections.emptyList();
            }
        };
    }

    public static TaskSpec getTaskSpec() {
        return new TaskSpec() {
            @Override
            public String getName() {
                return TestConstants.TASK_NAME;
            }

            @Override
            public GoalState getGoal() {
                return GoalState.RUNNING;
            }

            @Override
            public ResourceSet getResourceSet() {
                return PodTestUtils.getResourceSet();
            }

            @Override
            public Optional<CommandSpec> getCommand() {
                return Optional.empty();
            }

            @Override
            public Optional<HealthCheckSpec> getHealthCheck() {
                return Optional.empty();
            }

            @Override
            public Optional<ReadinessCheckSpec> getReadinessCheck() {
                return Optional.empty();
            }

            @Override
            public Collection<ConfigFileSpec> getConfigFiles() {
                return Collections.emptyList();
            }

            @Override
            public Optional<DiscoverySpec> getDiscovery() {
                return Optional.empty();
            }

            @Override
            public Integer getTaskKillGracePeriodSeconds() {
                return null;
            }

            @Override
            public Collection<TransportEncryptionSpec> getTransportEncryption() {
                return Collections.emptyList();
            }
        };
    }

    public static PodSpec getPodSpec() {
        return new PodSpec() {
            @Override
            public String getType() {
                return TestConstants.POD_TYPE;
            }

            @Override
            public Integer getCount() {
                return 1;
            }

            @Override
            public Optional<String> getImage() {
                return Optional.empty();
            }

            @Override
            public Collection<NetworkSpec> getNetworks() {
                return Collections.emptyList();
            }

            @Override
            public Collection<RLimit> getRLimits() {
                return Collections.emptyList();
            }

            @Override
            public Collection<URI> getUris() {
                return Collections.emptyList();
            }

            @Override
            public Optional<String> getUser() {
                return Optional.empty();
            }

            @Override
            public List<TaskSpec> getTasks() {
                return Arrays.asList(getTaskSpec());
            }

            @Override
            public Optional<PlacementRule> getPlacementRule() {
                return Optional.empty();
            }

            @Override
            public Collection<VolumeSpec> getVolumes() {
                return Collections.emptyList();
            }

            @Override
            public String getPreReservedRole() {
                return TestConstants.PRE_RESERVED_ROLE;
            }

            @Override
            public Collection<SecretSpec> getSecrets() {
                return Collections.emptyList();
            }

            @Override
            public Boolean getSharePidNamespace() {
                return false;
            }
        };
    }

    public static PodInstance getPodInstance(int index) {
        return new PodInstance() {
            @Override
            public PodSpec getPod() {
                return getPodSpec();
            }

            @Override
            public int getIndex() {
                return index;
            }
        };
    }

    public static PodInstanceRequirement getPodInstanceRequirement(int index) {
        List<String> tasksToLaunch = Arrays.asList(getTaskSpec().getName());
        return PodInstanceRequirement.newBuilder(getPodInstance(index), tasksToLaunch).build();
    }
}
