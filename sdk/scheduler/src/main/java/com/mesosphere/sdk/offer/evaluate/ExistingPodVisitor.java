package com.mesosphere.sdk.offer.evaluate;

import com.mesosphere.sdk.offer.MesosResource;
import com.mesosphere.sdk.offer.MesosResourcePool;
import com.mesosphere.sdk.offer.OfferRecommendation;
import com.mesosphere.sdk.offer.ResourceUtils;
import com.mesosphere.sdk.offer.UnreserveOfferRecommendation;
import com.mesosphere.sdk.scheduler.plan.PodInstanceRequirement;
import com.mesosphere.sdk.scheduler.recovery.RecoveryType;
import com.mesosphere.sdk.specification.NamedVIPSpec;
import com.mesosphere.sdk.specification.PodSpec;
import com.mesosphere.sdk.specification.PortSpec;
import com.mesosphere.sdk.specification.ResourceSpec;
import com.mesosphere.sdk.specification.TaskSpec;
import com.mesosphere.sdk.specification.VolumeSpec;
import org.apache.mesos.Protos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * The ExistingPodVisitor traverses a {@link PodSpec} along with information about the existing tasks launched for that
 * pod, whether running or failed, and matches existing resource ids for tasks that are being launched and prepares
 * reservations for those that are being launched for the first time before passing the spec onto its delegate. This
 * visitor pass should be run before any that consume resources based on a PodSpec or that construct Protos based on a
 * PodSpec.
 */
public class ExistingPodVisitor implements SpecVisitor<List<OfferRecommendation>> {
    private final Logger LOGGER = LoggerFactory.getLogger(getClass());

    private Protos.ExecutorInfo executorInfo;
    private final MesosResourcePool mesosResourcePool;
    private final Map<String, Protos.TaskInfo> taskInfos;
    private final Map<String, TaskPortLookup> portsByTask;
    private final ReservationCreator reservationCreator;
    private final List<OfferRecommendation> unreserves;
    private final SpecVisitor delegate;

    private PodInstanceRequirement podInstanceRequirement;
    private Protos.TaskInfo.Builder activeTask;
    private Protos.ExecutorInfo.Builder activeExecutor;
    private VisitorResultCollector<List<OfferRecommendation>> collector;

    public ExistingPodVisitor(
            MesosResourcePool mesosResourcePool,
            Collection<Protos.TaskInfo> taskInfos,
            ReservationCreator reservationCreator,
            SpecVisitor delegate) {
        this.mesosResourcePool = mesosResourcePool;
        this.taskInfos = taskInfos.stream().collect(Collectors.toMap(t -> t.getName(), Function.identity()));
        this.portsByTask = taskInfos.stream().collect(Collectors.toMap(t -> t.getName(), t -> new TaskPortLookup(t)));
        this.reservationCreator = reservationCreator;
        this.unreserves = new ArrayList<>();
        this.delegate = delegate;
        this.collector = createVisitorResultCollector();
    }

    private static Protos.ExecutorInfo getExecutorInfo(Collection<Protos.TaskInfo> taskInfos, PodSpec podSpec) {
        if (taskInfos.isEmpty()) {
            Protos.ExecutorInfo.Builder executorBuilder = Protos.ExecutorInfo.newBuilder()
                    .setType(Protos.ExecutorInfo.Type.DEFAULT)
                    .setName(podSpec.getType())
                    .setExecutorId(Protos.ExecutorID.newBuilder().setValue(""));

            return executorBuilder.build();
        } else {
            return taskInfos.stream().findFirst().get().getExecutor();
        }
    }

    @Override
    public PodInstanceRequirement visitImplementation(PodInstanceRequirement podInstanceRequirement) {
        this.podInstanceRequirement = podInstanceRequirement;
        return podInstanceRequirement;
    }

    @Override
    public PodSpec visitImplementation(PodSpec podSpec) {
        executorInfo = getExecutorInfo(taskInfos.values(), podSpec);
        setActiveExecutor(executorInfo);

        return podSpec;
    }

    @Override
    public PodSpec finalizeImplementation(PodSpec podSpec) {
        for (Protos.Resource resource : executorInfo.getResourcesList()) {
            LOGGER.info("Unreserving orphaned executor resource: {}", resource);
            mesosResourcePool.free(new MesosResource(resource));
            unreserves.add(new UnreserveOfferRecommendation(mesosResourcePool.getOffer(), resource));
        }

        return podSpec;
    }

    @Override
    public TaskSpec visitImplementation(TaskSpec taskSpec) {
        Protos.TaskInfo taskInfo = taskInfos.get(
                TaskSpec.getInstanceName(podInstanceRequirement.getPodInstance(), taskSpec));
        if (taskInfo != null) {
            setActiveTask(taskInfo);
        }

        return taskSpec;
    }

    @Override
    public TaskSpec finalizeImplementation(TaskSpec taskSpec) {
        if (isTaskActive()) {
            for (Protos.Resource resource : activeTask.getResourcesList()) {
                LOGGER.info("Unreserving orphaned task resource: {}", resource);
                mesosResourcePool.free(new MesosResource(resource));
                unreserves.add(new UnreserveOfferRecommendation(mesosResourcePool.getOffer(), resource));
            }
        }
        setActiveExecutor(executorInfo);

        return taskSpec;
    }

    @Override
    public ResourceSpec visitImplementation(ResourceSpec resourceSpec) {
        Optional<Protos.Resource> matchingResource = getMatchingResource(resourceSpec);
        Optional<String> resourceId = matchingResource.isPresent() ?
                ResourceUtils.getResourceId(matchingResource.get()) : Optional.empty();

        ResourceSpec matchedResourceSpec = new ResourceSpec() {
            @Override
            public Protos.Value getValue() {
                                         return resourceSpec.getValue();
                                                                        }

            @Override
            public String getName() {
                                  return resourceSpec.getName();
                                                                }

            @Override
            public String getRole() {
                                  return resourceSpec.getRole();
                                                                }

            @Override
            public String getPreReservedRole() {
                                             return resourceSpec.getPreReservedRole();
                                                                                      }

            @Override
            public String getPrincipal() {
                                       return resourceSpec.getPrincipal();
                                                                          }

            @Override
            public ResourceSpec getResourceSpec() {
                                                return this;
                                                            }

            @Override
            public Protos.Resource.Builder getResource() {
                return reservationCreator.withReservation(resourceSpec, resourceId);
            }

            @Override
            public String toString() {
                return resourceSpec.toString();
            }
        };

        return matchedResourceSpec;
    }

    @Override
    public VolumeSpec visitImplementation(VolumeSpec volumeSpec) {
        Optional<Protos.Resource> matchingResource = getMatchingResource(volumeSpec);
        VolumeSpec matchedVolumeSpec;

        Optional<String> persistenceId = matchingResource.isPresent() ?
                ResourceUtils.getPersistenceId(matchingResource.get().toBuilder()) : Optional.empty();
        Optional<String> resourceId = matchingResource.isPresent() ?
                ResourceUtils.getResourceId(matchingResource.get()) : Optional.empty();
        matchedVolumeSpec = new VolumeSpec() {
            @Override
            public Type getType() {
                                return volumeSpec.getType();
                                                            }

            @Override
            public String getContainerPath() {
                                           return volumeSpec.getContainerPath();
                                                                                }

            @Override
            public VolumeSpec getVolumeSpec() {
                                            return volumeSpec.getVolumeSpec();
                                                                              }

            @Override
            public Protos.Value getValue() {
                                         return volumeSpec.getValue();
                                                                      }

            @Override
            public String getName() {
                                  return volumeSpec.getName();
                                                              }

            @Override
            public String getRole() {
                                  return volumeSpec.getRole();
                                                              }

            @Override
            public String getPreReservedRole() {
                                             return volumeSpec.getPreReservedRole();
                                                                                    }

            @Override
            public String getPrincipal() {
                                       return volumeSpec.getPrincipal();
                                                                        }

            @Override
            public ResourceSpec getResourceSpec() {
                                                return this;
                                                            }

            @Override
            public Protos.Resource.Builder getResource() {
                Protos.Resource.Builder resourceBuilder = volumeSpec.getResource();

                if (volumeSpec.getContainerPath() != null) {
                    Protos.Resource.DiskInfo.Builder diskBuilder = resourceBuilder.getDiskBuilder();
                    diskBuilder.getVolumeBuilder()
                            .setContainerPath(volumeSpec.getContainerPath())
                            .setMode(Protos.Volume.Mode.RW);
                    diskBuilder.getPersistenceBuilder()
                            .setPrincipal(volumeSpec.getPrincipal());

                    if (persistenceId.isPresent()) {
                        diskBuilder.getPersistenceBuilder().setId(persistenceId.get());
                    }

                    if (volumeSpec.getType().equals(Type.MOUNT)) {
                        Optional<String> sourceRoot = ResourceUtils.getSourceRoot(resourceBuilder.build());
                        if (!sourceRoot.isPresent()) {
                            throw new IllegalStateException("Source path must be set on MOUNT volumes.");
                        }

                        Protos.Resource.DiskInfo.Source.Builder sourceBuilder = Protos.Resource.DiskInfo.Source
                                .newBuilder()
                                .setType(Protos.Resource.DiskInfo.Source.Type.MOUNT);
                        sourceBuilder.getMountBuilder().setRoot(sourceRoot.get());
                        diskBuilder.setSource(sourceBuilder);
                    }
                }

                return reservationCreator.withReservation(volumeSpec, resourceBuilder, resourceId);
            }

            @Override
            public String toString() {
                return volumeSpec.toString();
            }
        };

        return matchedVolumeSpec;
    }

    @Override
    public PortSpec visitImplementation(PortSpec portSpec) {
        PortSpec matchedPortSpec;

        TaskPortLookup portFinder = activeTask != null ? portsByTask.get(activeTask.getName()) : null;
        Optional<Protos.Resource> matchingResource = getMatchingResource(portSpec);
        Optional<String> resourceId = matchingResource.isPresent() ?
                ResourceUtils.getResourceId(matchingResource.get()) : Optional.empty();

        if (portFinder == null) {
            matchedPortSpec = new PortSpec() {
                @Override
                public String getPortName() {
                    return portSpec.getPortName();
                }

                @Override
                public Protos.DiscoveryInfo.Visibility getVisibility() {
                    return portSpec.getVisibility();
                }

                @Override
                public Collection<String> getNetworkNames() {
                    return portSpec.getNetworkNames();
                }

                @Override
                public long getPort() {
                    return portSpec.getPort();
                }

                @Override
                public Protos.Value getValue() {
                    return portSpec.getValue();
                }

                @Override
                public String getName() {
                    return portSpec.getName();
                }

                @Override
                public String getRole() {
                    return portSpec.getRole();
                }

                @Override
                public String getPreReservedRole() {
                    return portSpec.getPreReservedRole();
                }

                @Override
                public String getPrincipal() {
                    return portSpec.getPrincipal();
                }

                @Override
                public ResourceSpec getResourceSpec() {
                    return this;
                }

                @Override
                public Protos.Resource.Builder getResource() {
                    return reservationCreator.withReservation(portSpec, portSpec.getResource(), resourceId);
                }

                @Override
                public String toString() {
                    return portSpec.toString();
                }
            };
        } else {
            matchedPortSpec = new PortSpec() {
                @Override
                public String getPortName() {
                                          return portSpec.getPortName();
                                                                        }

                @Override
                public Protos.DiscoveryInfo.Visibility getVisibility() {
                                                                     return portSpec.getVisibility();
                                                                                                     }

                @Override
                public Collection<String> getNetworkNames() {
                                                          return portSpec.getNetworkNames();
                                                                                            }

                @Override
                public long getPort() {
                    Optional<Long> port = portFinder.getPriorPort(portSpec);
                    return port.isPresent() ? port.get() : portSpec.getPort();
                }

                @Override
                public Protos.Value getValue() {
                    Protos.Value.Builder valueBuilder = portSpec.getValue().toBuilder();
                    valueBuilder.getRangesBuilder().getRangeBuilder(0).setBegin(getPort()).setEnd(getPort());
                    return valueBuilder.build();
                }

                @Override
                public String getName() {
                                      return portSpec.getName();
                                                                }

                @Override
                public String getRole() {
                                      return portSpec.getRole();
                                                                }

                @Override
                public String getPreReservedRole() {
                                                 return portSpec.getPreReservedRole();
                                                                                      }

                @Override
                public String getPrincipal() {
                                           return portSpec.getPrincipal();
                                                                          }

                @Override
                public ResourceSpec getResourceSpec() {
                                                    return this;
                                                                }

                @Override
                public Protos.Resource.Builder getResource() {
                    Protos.Resource.Builder resourceBuilder =
                            portSpec.getResource().setRanges(getValue().getRanges());

                    return reservationCreator.withReservation(portSpec, resourceBuilder, resourceId);
                }

                @Override
                public String toString() {
                                       return portSpec.toString();
                                                                  }
            };
        }

        return matchedPortSpec;
    }

    @Override
    public NamedVIPSpec visitImplementation(NamedVIPSpec namedVIPSpec) throws SpecVisitorException {
        PortSpec visitedPortSpec = visitImplementation((PortSpec) namedVIPSpec);

        return new NamedVIPSpec(
                namedVIPSpec.getValue(),
                namedVIPSpec.getRole(),
                namedVIPSpec.getPreReservedRole(),
                namedVIPSpec.getPrincipal(),
                namedVIPSpec.getEnvKey().isPresent() ? namedVIPSpec.getEnvKey().get() : null,
                namedVIPSpec.getPortName(),
                namedVIPSpec.getProtocol(),
                namedVIPSpec.getVisibility(),
                namedVIPSpec.getVipName(),
                namedVIPSpec.getVipPort(),
                namedVIPSpec.getNetworkNames()) {

            @Override
            public long getPort() {
                return visitedPortSpec.getPort();
            }

            @Override
            public Protos.Value getValue() {
                return visitedPortSpec.getValue();
            }

            @Override
            public Protos.Resource.Builder getResource() {
                return visitedPortSpec.getResource();
            }
        };
    }

    @Override
    public Optional<SpecVisitor> getDelegate() {
        return Optional.of(delegate);
    }

    @Override
    public void compileResultImplementation() {
        getVisitorResultCollector().setResult(unreserves);
    }

    @Override
    public VisitorResultCollector<List<OfferRecommendation>> getVisitorResultCollector() {
        return collector;
    }

    private void setActiveTask(Protos.TaskInfo task) {
        activeExecutor = null;
        activeTask = task.toBuilder();
    }

    private void setActiveExecutor(Protos.ExecutorInfo executor) {
        activeExecutor = executor.toBuilder();
        activeTask = null;
    }

    private boolean isTaskActive() {
        return activeTask != null;
    }

    private boolean isExecutorActive() {
        return activeExecutor != null;
    }

    private Optional<Protos.Resource> getMatchingResource(ResourceSpec resourceSpec) {
        return getMatchingResource(r -> r.getName().equals(resourceSpec.getName()));
    }

    private Optional<Protos.Resource> getMatchingResource(Predicate<Protos.Resource> isMatching) {
        if (podInstanceRequirement.getRecoveryType().equals(RecoveryType.PERMANENT)) {
            return Optional.empty();
        }

        if (isTaskActive()) {
            List<Protos.Resource> resources = activeTask.getResourcesList();
            for (int i = 0; i < activeTask.getResourcesCount(); ++i) {
                Protos.Resource resource = resources.get(i);
                if (isMatching.test(resource)) {
                    if (!ResourceUtils.getResourceId(resource).isPresent()) {
                        LOGGER.error("Failed to find resource ID for resource: {}", resource);
                        continue;
                    }
                    activeTask.removeResources(i);

                    return Optional.of(resource);
                }
            }

            return Optional.empty();
        } else if (isExecutorActive()) {
            List<Protos.Resource> resources = activeExecutor.getResourcesList();
            for (int i = 0; i < activeExecutor.getResourcesCount(); ++i) {
                Protos.Resource resource = resources.get(i);
                if (isMatching.test(resource)) {
                    if (!ResourceUtils.getResourceId(resource).isPresent()) {
                        LOGGER.error("Failed to find resource ID for resource: {}", resource);
                        continue;
                    }
                    activeExecutor.removeResources(i);

                    return Optional.of(resource);
                }
            }
        }

        return Optional.empty();
    }

    private Optional<Protos.Resource> getMatchingResource(VolumeSpec volumeSpec) {
        return getMatchingResource(
                r -> (r.getName().equals(volumeSpec.getName()) &&
                        r.getDisk().getVolume().getContainerPath().equals(volumeSpec.getContainerPath())));
    }
}
