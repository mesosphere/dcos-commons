package com.mesosphere.sdk.testutils;

import com.mesosphere.sdk.offer.constrain.PlacementRule;
import com.mesosphere.sdk.offer.constrain.PlacementUtils;
import com.mesosphere.sdk.specification.*;
import org.apache.mesos.Protos;
import org.junit.contrib.java.lang.system.EnvironmentVariables;

import com.mesosphere.sdk.offer.InvalidRequirementException;
import com.mesosphere.sdk.offer.OfferRequirement;

import java.util.*;
import java.util.stream.Collectors;

/**
 * This class provides utility methods for tests concerned with OfferRequirements.
 */
public class OfferRequirementTestUtils {

    private OfferRequirementTestUtils() {
        // do not instantiate
    }

    public static OfferRequirement getOfferRequirement() {
        try {
            return getOfferRequirement(ResourceTestUtils.getUnreservedCpu(1.0));
        } catch (InvalidRequirementException e) {
            throw new IllegalStateException(e);
        }
    }

    public static OfferRequirement getOfferRequirement(Protos.Resource resource) throws InvalidRequirementException {
        return getOfferRequirement(Arrays.asList(resource));
    }

    public static OfferRequirement getOfferRequirement(
            List<Protos.Resource> resources) throws InvalidRequirementException {
        return OfferRequirement.create(TestConstants.TASK_TYPE, 0, Arrays.asList(TaskTestUtils.getTaskInfo(resources)));
    }

    public static OfferRequirement getOfferRequirement(
            Protos.Resource resource, PlacementRule placementRule) throws InvalidRequirementException {
        return getOfferRequirement(Arrays.asList(resource), placementRule);
    }

    public static OfferRequirement getOfferRequirement(
            List<Protos.Resource> resources, PlacementRule placementRule) throws InvalidRequirementException {
        return OfferRequirement.create(
                TestConstants.TASK_TYPE,
                0,
                Arrays.asList(TaskTestUtils.getTaskInfo(resources)),
                Optional.empty(),
                Optional.of(placementRule));
    }

    public static PodSpec withResource(PodSpec podSpec, Protos.Resource resource, String principal) {
        return withResources(podSpec, Arrays.asList(resource), principal);
    }

    public static PodSpec withResource(
            PodSpec podSpec,
            Protos.Resource resource,
            String principal,
            List<String> avoidAgents,
            List<String> collocateAgents) {
        return withResources(podSpec, Arrays.asList(resource), principal, avoidAgents, collocateAgents);
    }

    public static PodSpec withVolume(PodSpec podSpec, Protos.Resource resource, String principal) {
        return addResources(
                podSpec,
                Collections.emptyList(),
                Arrays.asList(volumeFromResource(resource, principal)),
                Collections.emptyList(),
                Collections.emptyList());
    }

    public static PodSpec withResources(PodSpec podSpec, Collection<Protos.Resource> resources, String principal) {
        return addResources(
                podSpec,
                resources.stream().map(r -> specFromResource(r, principal)).collect(Collectors.toList()),
                Collections.emptyList(),
                Collections.emptyList(),
                Collections.emptyList());
    }

    public static PodSpec withResources(
            PodSpec podSpec,
            Collection<Protos.Resource> resources,
            String principal,
            List<String> avoidAgents,
            List<String> collocateAgents) {
        return addResources(
                podSpec,
                resources.stream().map(r -> specFromResource(r, principal)).collect(Collectors.toList()),
                Collections.emptyList(),
                avoidAgents,
                collocateAgents);
    }

    private static PodSpec addResources(
            PodSpec podSpec,
            Collection<ResourceSpec> resources,
            Collection<VolumeSpec> volumes,
            List<String> avoidAgents,
            List<String> collocateAgents) {
        Optional<PlacementRule> placement = PlacementUtils.getAgentPlacementRule(avoidAgents, collocateAgents);
        DefaultPodSpec.Builder podBuilder = DefaultPodSpec.newBuilder(podSpec);
        if (placement.isPresent()) {
            podBuilder.placementRule(placement.get());
        }

        DefaultTaskSpec taskSpec = (DefaultTaskSpec) podSpec.getTasks().get(0);
        DefaultTaskSpec.Builder taskBuilder = DefaultTaskSpec.newBuilder(taskSpec);
        DefaultResourceSet resourceSet = (DefaultResourceSet) taskSpec.getResourceSet();
        DefaultResourceSet.Builder resourceBuilder = DefaultResourceSet.newBuilder(resourceSet);

        if (!resources.isEmpty()) {
            resourceBuilder.resources(resources);
        }
        resourceBuilder.volumes(volumes);
        resourceBuilder.id(resourceSet.getId());
        taskBuilder.resourceSet(resourceBuilder.build());
        podBuilder.tasks(Arrays.asList(taskBuilder.build()));

        return podBuilder.build();
    }

    private static ResourceSpec specFromResource(Protos.Resource resource, String principal) {
        Protos.Value.Builder valueBuilder = Protos.Value.newBuilder();

        valueBuilder.setType(resource.getType());
        switch (resource.getType()) {
            case SCALAR:
                valueBuilder.setScalar(resource.getScalar());
                break;
            case RANGES:
                valueBuilder.setRanges(resource.getRanges());
                break;
            case SET:
                valueBuilder.setSet(resource.getSet());
                break;
            default:
                throw new IllegalArgumentException("Resource has unknown type");
        }

        return new DefaultResourceSpec(
                resource.getName(),
                valueBuilder.build(),
                resource.getRole(),
                principal,
                null);
    }

    private static VolumeSpec volumeFromResource(Protos.Resource resource, String principal) {
        VolumeSpec.Type volumeType;

        Protos.Resource.DiskInfo.Source.Type resourceType = resource.getDisk().getSource().getType();
        if (resource.getDisk().hasSource() && resourceType.equals(Protos.Resource.DiskInfo.Source.Type.MOUNT)) {
            volumeType = VolumeSpec.Type.MOUNT;
        } else if (resource.getDisk().hasSource() && resourceType.equals(Protos.Resource.DiskInfo.Source.Type.PATH)) {
            volumeType = VolumeSpec.Type.PATH;
        } else {
            volumeType = VolumeSpec.Type.ROOT;
        }

        return new DefaultVolumeSpec(
                resource.getScalar().getValue(),
                volumeType,
                resource.getDisk().getVolume().getContainerPath(),
                resource.getRole(),
                principal,
                null);
    }

    public static EnvironmentVariables getOfferRequirementProviderEnvironment() {
        EnvironmentVariables vars = new EnvironmentVariables();
        vars.set("EXECUTOR_URI", "");
        vars.set("LIBMESOS_URI", "");
        return vars;
    }
}
