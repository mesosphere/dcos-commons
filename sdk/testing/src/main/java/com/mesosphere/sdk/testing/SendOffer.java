package com.mesosphere.sdk.testing;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import org.apache.mesos.Protos;
import org.apache.mesos.Scheduler;
import org.apache.mesos.SchedulerDriver;

import com.mesosphere.sdk.offer.Constants;
import com.mesosphere.sdk.specification.PodSpec;
import com.mesosphere.sdk.specification.ResourceSpec;
import com.mesosphere.sdk.specification.TaskSpec;
import com.mesosphere.sdk.specification.VolumeSpec;
import com.mesosphere.sdk.testutils.TestConstants;

/**
 * A {@link Send} for sending a {@link Protos.Offer} which matches a specified pod's requirements to a scheduler under
 * test.
 */
public class SendOffer implements Send {

    /**
     * Default executors have an additional overhead of 0.1 CPU, 32MB RAM, and 256MB disk.
     */
    private static final List<Protos.Resource> DEFAULT_EXECUTOR_RESOURCES = Arrays.asList(
            toUnreservedResource(Constants.CPUS_RESOURCE_TYPE, scalar(Constants.DEFAULT_EXECUTOR_CPUS), false),
            toUnreservedResource(Constants.MEMORY_RESOURCE_TYPE, scalar(Constants.DEFAULT_EXECUTOR_MEMORY), false),
            toUnreservedResource(Constants.DISK_RESOURCE_TYPE, scalar(Constants.DEFAULT_EXECUTOR_DISK), false));

    private final String podType;
    private final Optional<String> podToReuse;
    private final String hostname;

    /**
     * Builder for {@link SendOffer}.
     */
    public static class Builder {
        private final String podType;
        private Optional<String> podToReuse;
        private String hostname;

        /**
         * Creates a new offer which matches the resource requirements for the specified pod. By default, new unreserved
         * resources will be used for the offer.
         */
        public Builder(String podType) {
            this.podType = podType;
            this.podToReuse = Optional.empty();
            this.hostname = TestConstants.HOSTNAME;
        }

        /**
         * Specifies that the previously reserved resources from a previously launched pod should be used in this offer,
         * instead of new unreserved resources.
         *
         * @param podIndex the index of the previous pod to be offered
         */
        public Builder setPodIndexToReoffer(int podIndex) {
            this.podToReuse = Optional.of(getPodName(podType, podIndex));
            return this;
        }

        /**
         * Assigns a custom hostname to be used in the offer. Otherwise a common default will be used.
         */
        public Builder setHostname(String hostname) {
            this.hostname = hostname;
            return this;
        }

        public SendOffer build() {
            return new SendOffer(podType, podToReuse, hostname);
        }

        private static String getPodName(String podType, int podIndex) {
            return String.format("%s-%d", podType, podIndex);
        }
    }

    private SendOffer(String podType, Optional<String> podToReuse, String hostname) {
        this.podType = podType;
        this.podToReuse = podToReuse;
        this.hostname = hostname;
    }

    @Override
    public void send(ClusterState state, SchedulerDriver mockDriver, Scheduler scheduler) {
        Protos.Offer offer = getOfferForPod(state);
        state.addSentOffer(offer);
        scheduler.resourceOffers(mockDriver, Arrays.asList(offer));
    }

    @Override
    public String getDescription() {
        if (podToReuse.isPresent()) {
            return String.format("Reserved offer for pod=%s", podToReuse.get());
        } else {
            return String.format("Unreserved offer for pod type=%s", podType);
        }
    }

    private Protos.Offer getOfferForPod(ClusterState state) {
        Optional<PodSpec> matchingSpec = state.getServiceSpec().getPods().stream()
                .filter(podSpec -> podType.equals(podSpec.getType()))
                .findAny();
        if (!matchingSpec.isPresent()) {
            throw new IllegalArgumentException(String.format("No PodSpec found with type=%s: types=%s",
                    podType,
                    state.getServiceSpec().getPods().stream()
                            .map(podSpec -> podSpec.getType())
                            .collect(Collectors.toList())));
        }
        PodSpec podSpec = matchingSpec.get();

        Protos.Offer.Builder offerBuilder = Protos.Offer.newBuilder()
                .setFrameworkId(TestConstants.FRAMEWORK_ID)
                .setSlaveId(TestConstants.AGENT_ID)
                .setHostname(hostname);
        offerBuilder.getIdBuilder().setValue(UUID.randomUUID().toString());

        // Include pod/executor-level volumes:
        for (VolumeSpec volumeSpec : podSpec.getVolumes()) {
            offerBuilder.addResources(toUnreservedResource(volumeSpec));
        }

        // Include task-level resources (note: resources are not merged, e.g. 1.5cpu+1.0 cpu instead of 2.5cpu):
        for (TaskSpec taskSpec : podSpec.getTasks()) {
            if (podToReuse.isPresent()) {
                // Copy executor id and resources from prior pod launch:
                LaunchedPod pod = state.getLastLaunchedPod(podToReuse.get());
                offerBuilder
                        .addExecutorIds(pod.getExecutor().getExecutorId())
                        .addAllResources(pod.getExecutor().getResourcesList());
                for (Protos.TaskInfo task : pod.getTasks()) {
                    offerBuilder.addAllResources(task.getResourcesList());
                }
            } else {
                // Create new unreserved resources:
                for (ResourceSpec resourceSpec : taskSpec.getResourceSet().getResources()) {
                    offerBuilder.addResources(toUnreservedResource(resourceSpec));
                }
                for (VolumeSpec volumeSpec : taskSpec.getResourceSet().getVolumes()) {
                    offerBuilder.addResources(toUnreservedResource(volumeSpec));
                }
            }
        }

        // In addition to the resources required by the tasks, add some resources for the Default Executor overhead.
        // Note that if custom executors are being exercised, these marginal resources are not used and are effectively
        // just ignored.
        if (!podToReuse.isPresent()) {
            offerBuilder.addAllResources(DEFAULT_EXECUTOR_RESOURCES);
        }

        return offerBuilder.build();
    }

    private static Protos.Resource toUnreservedResource(ResourceSpec resourceSpec) {
        boolean isMountDisk = resourceSpec instanceof VolumeSpec &&
                ((VolumeSpec) resourceSpec).getType() == VolumeSpec.Type.MOUNT;
        return toUnreservedResource(resourceSpec.getName(), resourceSpec.getValue(), isMountDisk);
    }

    @SuppressWarnings("deprecation") // for Resource.setRole()
    private static Protos.Resource toUnreservedResource(String resourceName, Protos.Value value, boolean isMountDisk) {
        Protos.Resource.Builder resourceBuilder = Protos.Resource.newBuilder()
                .setRole("*")
                .setName(resourceName)
                .setType(value.getType());

        switch (value.getType()) {
            case SCALAR:
                resourceBuilder.setScalar(value.getScalar());
                break;
            case RANGES:
                resourceBuilder.setRanges(value.getRanges());
                break;
            case SET:
                resourceBuilder.setSet(value.getSet());
                break;
            default:
                throw new IllegalArgumentException("Unsupported value type: " + value);
        }

        if (isMountDisk) {
            resourceBuilder.getDiskBuilder().getSourceBuilder()
                    .setType(Protos.Resource.DiskInfo.Source.Type.MOUNT)
                    .getMountBuilder().setRoot(TestConstants.MOUNT_ROOT);
        }

        return resourceBuilder.build();
    }

    private static Protos.Value scalar(double val) {
        Protos.Value.Builder builder = Protos.Value.newBuilder()
                .setType(Protos.Value.Type.SCALAR);
        builder.getScalarBuilder().setValue(val);
        return builder.build();
    }
}
