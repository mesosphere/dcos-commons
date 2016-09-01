package org.apache.mesos.offer;

import org.apache.mesos.Protos;
import org.apache.mesos.specification.ResourceSpecification;
import org.apache.mesos.specification.TaskSpecification;
import org.apache.mesos.specification.VolumeSpecification;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * A default implementation of the OfferRequirementProvider interface.
 */
public class DefaultOfferRequirementProvider implements OfferRequirementProvider {
    private final Logger logger = LoggerFactory.getLogger(getClass());

    @Override
    public OfferRequirement getNewOfferRequirement(TaskSpecification taskSpecification)
            throws InvalidRequirementException {
        Protos.TaskInfo taskInfo = Protos.TaskInfo.newBuilder()
                .setName(taskSpecification.getName())
                .setCommand(taskSpecification.getCommand())
                .setTaskId(TaskUtils.emptyTaskId())
                .setSlaveId(TaskUtils.emptyAgentId())
                .addAllResources(getNewResources(taskSpecification))
                .build();

        return new OfferRequirement(Arrays.asList(taskInfo));
    }

    @Override
    public OfferRequirement getExistingOfferRequirement(Protos.TaskInfo taskInfo, TaskSpecification taskSpecification)
            throws InvalidRequirementException {

        Map<String, Protos.Resource> oldResourceMap = getResourceMap(taskInfo.getResourcesList());
        Collection<Protos.Resource> oldVolumes = getVolumes(taskInfo.getResourcesList());
        List<Protos.Resource> updatedResources = new ArrayList<>();

        for (ResourceSpecification resourceSpecification : taskSpecification.getResources()) {
            Protos.Resource oldResource = oldResourceMap.get(resourceSpecification.getName());
            if (oldResource != null) {
                try {
                    updatedResources.add(ResourceUtils.updateResource(oldResource, resourceSpecification));
                } catch (IllegalArgumentException e) {
                    logger.error("Failed to update Resources with exception: ", e);
                    // On failure to update resources keep the old resources.
                    updatedResources.add(oldResource);
                }
            } else {
                updatedResources.add(ResourceUtils.getDesiredResource(resourceSpecification));
            }
        }

        for (Protos.Resource resource : oldVolumes) {
           updatedResources.add(resource);
        }

        Protos.TaskInfo.Builder taskBuilder = Protos.TaskInfo.newBuilder(taskInfo)
                .clearResources()
                .setCommand(taskSpecification.getCommand())
                .addAllResources(updatedResources)
                .setTaskId(TaskUtils.emptyTaskId())
                .setSlaveId(TaskUtils.emptyAgentId());

        return new OfferRequirement(Arrays.asList(taskBuilder.build()));
    }

    private Iterable<? extends Protos.Resource> getNewResources(TaskSpecification taskSpecification) {
        Collection<Protos.Resource> resources = new ArrayList<>();

        for (ResourceSpecification resourceSpecification : taskSpecification.getResources()) {
            resources.add(ResourceUtils.getDesiredResource(resourceSpecification));
        }

        if (taskSpecification.getVolumes().isPresent()) {
            for (VolumeSpecification volumeSpecification : taskSpecification.getVolumes().get()) {
                switch (volumeSpecification.getType()) {
                    case ROOT:
                        resources.add(
                                ResourceUtils.getDesiredRootVolume(
                                        volumeSpecification.getRole(),
                                        volumeSpecification.getPrincipal(),
                                        volumeSpecification.getValue().getScalar().getValue(),
                                        volumeSpecification.getContainerPath()));
                        break;
                    case MOUNT:
                        resources.add(
                                ResourceUtils.getDesiredMountVolume(
                                        volumeSpecification.getRole(),
                                        volumeSpecification.getPrincipal(),
                                        volumeSpecification.getValue().getScalar().getValue(),
                                        volumeSpecification.getContainerPath()));
                        break;
                    default:
                        logger.error("Encountered unsupported disk type: " + volumeSpecification.getType());
                }
            }
        }

        return resources;
    }

    private Map<String, Protos.Resource> getResourceMap(Collection<Protos.Resource> resources) {
        Map<String, Protos.Resource> resourceMap = new HashMap<>();
        for (Protos.Resource resource : resources) {
            if (!resource.hasDisk()) {
                resourceMap.put(resource.getName(), resource);
            }
        }

        return resourceMap;
    }

    private Collection<Protos.Resource> getVolumes(Collection<Protos.Resource> resources) {
        List<Protos.Resource> volumes = new ArrayList<>();
        for (Protos.Resource resource : resources) {
            if (resource.hasDisk()) {
                volumes.add(resource);
            }
        }

        return volumes;
    }
}
