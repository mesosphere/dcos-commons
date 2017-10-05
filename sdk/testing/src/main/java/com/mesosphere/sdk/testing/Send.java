package com.mesosphere.sdk.testing;

import java.util.Arrays;
import java.util.Optional;
import java.util.stream.Collectors;

import org.apache.mesos.Protos;
import org.apache.mesos.Scheduler;
import org.apache.mesos.SchedulerDriver;

import com.mesosphere.sdk.offer.ResourceBuilder;
import com.mesosphere.sdk.specification.PodSpec;
import com.mesosphere.sdk.specification.ResourceSpec;
import com.mesosphere.sdk.specification.ServiceSpec;
import com.mesosphere.sdk.specification.TaskSpec;
import com.mesosphere.sdk.specification.VolumeSpec;
import com.mesosphere.sdk.testutils.TestConstants;

/**
 * A type of {@link Tick} that performs an operation against the scheduler.
 */
public interface Send extends SimulationTick {

    public static Send register() {
        return new Send() {
            @Override
            public void run(ClusterState state, SchedulerDriver mockDriver, Scheduler scheduler) {
                scheduler.registered(
                        mockDriver,
                        Protos.FrameworkID.newBuilder()
                                .setValue("test-framework-id")
                                .build(),
                        Protos.MasterInfo.newBuilder()
                                .setId("test-master-id")
                                .setIp(1)
                                .setPort(2)
                                .build());
            }

            @Override
            public String getDescription() {
                return String.format("Framework registration completed");
            }
        };
    }

    public static Send offerForPod(String podType) {
        return new Send() {
            @Override
            public void run(ClusterState state, SchedulerDriver mockDriver, Scheduler scheduler) {
                Protos.Offer offer = getOfferForPod(state.getSchedulerConfig().getServiceSpec());
                state.addSentOffer(offer);
                scheduler.resourceOffers(mockDriver, Arrays.asList(offer));
            }

            @Override
            public String getDescription() {
                return String.format("Offer for pod type=%s", podType);
            }

            private Protos.Offer getOfferForPod(ServiceSpec serviceSpec) {
                Protos.Offer.Builder offerBuilder = Protos.Offer.newBuilder()
                        .setId(TestConstants.OFFER_ID)
                        .setFrameworkId(TestConstants.FRAMEWORK_ID)
                        .setSlaveId(TestConstants.AGENT_ID)
                        .setHostname(TestConstants.HOSTNAME);
                Optional<PodSpec> matchingSpec = serviceSpec.getPods().stream()
                        .filter(podSpec -> podType.equals(podSpec.getType()))
                        .findAny();
                if (!matchingSpec.isPresent()) {
                    throw new IllegalArgumentException(String.format("No PodSpec found with type=%s: types=%s",
                            podType,
                            serviceSpec.getPods().stream()
                                    .map(podSpec -> podSpec.getType())
                                    .collect(Collectors.toList())));
                }
                for (TaskSpec task : matchingSpec.get().getTasks()) {
                    // TODO these resources are no good, they're setting the resource id. either clear that or find something else
                    for (ResourceSpec resource : task.getResourceSet().getResources()) {
                        offerBuilder.addResources(ResourceBuilder.fromSpec(resource, Optional.empty()).build());
                    }
                    for (VolumeSpec volume : task.getResourceSet().getVolumes()) {
                        if (volume.getType() == VolumeSpec.Type.MOUNT) {
                            offerBuilder.addResources(
                                    ResourceBuilder.fromMountVolumeSpec(volume, Optional.empty(), Optional.empty(), "path").build());
                        } else {
                            offerBuilder.addResources(
                                    ResourceBuilder.fromRootVolumeSpec(volume, Optional.empty(), Optional.empty()).build());
                        }
                    }
                }
                return offerBuilder.build();
            }
        };
    }

    public static Send taskStatus(String taskName, Protos.TaskState taskState) {
        return new Send() {
            @Override
            public void run(ClusterState state, SchedulerDriver mockDriver, Scheduler scheduler) {
                scheduler.statusUpdate(mockDriver, Protos.TaskStatus.newBuilder()
                        .setTaskId(state.getTaskId(taskName))
                        .setState(taskState)
                        .setMessage("This is a test status")
                        .build());
            }

            @Override
            public String getDescription() {
                return String.format("TaskStatus[state=%s] for name=%s", taskState, taskName);
            }
        };
    }

    /**
     * Performs an action against the provided scheduler, optionally updating the provided cluster state to reflect
     * the action.
     */
    public void run(ClusterState state, SchedulerDriver mockDriver, Scheduler scheduler);
}
