package com.mesosphere.sdk.testing;

import java.util.Arrays;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import org.apache.mesos.Protos;
import org.apache.mesos.Scheduler;
import org.apache.mesos.SchedulerDriver;

import com.mesosphere.sdk.specification.PodSpec;
import com.mesosphere.sdk.specification.ResourceSpec;
import com.mesosphere.sdk.specification.ServiceSpec;
import com.mesosphere.sdk.specification.TaskSpec;
import com.mesosphere.sdk.specification.VolumeSpec;
import com.mesosphere.sdk.testutils.TestConstants;

/**
 * A {@link Send} for sending a {@link Protos.Offer} which matches a specified pod's requirements to a scheduler under
 * test.
 */
public class SendOffer implements Send {

    private final String podType;
    private final Optional<String> podToReuseResources;
    private final String hostname;

    /**
     * Builder for {@link SendOffer}.
     */
    public static class Builder {
        private final String podType;
        private Optional<String> podToReuseResources;
        private String hostname;

        /**
         * Creates a new offer which matches the resource requirements for the specified pod. By default, new unreserved
         * resources will be used for the offer.
         */
        public Builder(String podType) {
            this.podType = podType;
            this.podToReuseResources = Optional.empty();
            this.hostname = TestConstants.HOSTNAME;
        }

        /**
         * Specifies that the previously reserved resources from a previously launched pod should be used in this offer,
         * instead of new unreserved resources.
         *
         * @param podIndex the index of the previous pod to be offered
         */
        public Builder setResourcesFromPod(int podIndex) {
            this.podToReuseResources = Optional.of(String.format("%s-%d", podType, podIndex));
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
            return new SendOffer(podType, podToReuseResources, hostname);
        }
    }

    private SendOffer(String podType, Optional<String> podToReuseResources, String hostname) {
        this.podType = podType;
        this.podToReuseResources = podToReuseResources;
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
        if (podToReuseResources.isPresent()) {
            return String.format("Reserved offer for pod=%s", podToReuseResources.get());
        } else {
            return String.format("Unreserved offer for pod type=%s", podType);
        }
    }

    private Protos.Offer getOfferForPod(ClusterState state) {
        ServiceSpec serviceSpec = state.getSchedulerConfig().getServiceSpec();
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

        Protos.Offer.Builder offerBuilder = Protos.Offer.newBuilder()
                .setFrameworkId(TestConstants.FRAMEWORK_ID)
                .setSlaveId(TestConstants.AGENT_ID)
                .setHostname(hostname);
        offerBuilder.getIdBuilder().setValue(UUID.randomUUID().toString());
        for (TaskSpec taskSpec : matchingSpec.get().getTasks()) {
            if (podToReuseResources.isPresent()) {
                // Copy resources from prior pod launch:
                for (Protos.TaskInfo task : state.getLastLaunchedPod(podToReuseResources.get())) {
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
        return offerBuilder.build();
    }

    @SuppressWarnings("deprecation") // for Resource.setRole()
    private static Protos.Resource toUnreservedResource(ResourceSpec resourceSpec) {
        Protos.Resource.Builder resourceBuilder = Protos.Resource.newBuilder()
                .setRole("*")
                .setName(resourceSpec.getName())
                .setType(resourceSpec.getValue().getType());
        switch (resourceSpec.getValue().getType()) {
            case SCALAR:
                resourceBuilder.setScalar(resourceSpec.getValue().getScalar());
                break;
            case RANGES:
                resourceBuilder.setRanges(resourceSpec.getValue().getRanges());
                break;
            case SET:
                resourceBuilder.setSet(resourceSpec.getValue().getSet());
                break;
            default:
                throw new IllegalArgumentException("Unsupported value type: " + resourceSpec.getValue());
        }

        if (resourceSpec instanceof VolumeSpec &&
                ((VolumeSpec) resourceSpec).getType() == VolumeSpec.Type.MOUNT) {
            resourceBuilder.getDiskBuilder().getSourceBuilder()
                    .setType(Protos.Resource.DiskInfo.Source.Type.MOUNT)
                    .getMountBuilder().setRoot(TestConstants.MOUNT_ROOT);
        }
        return resourceBuilder.build();
    }
}
