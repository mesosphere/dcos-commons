package org.apache.mesos.offer;

import org.apache.commons.collections.CollectionUtils;
import org.apache.mesos.Protos;
import org.apache.mesos.specification.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * A default implementation of the OfferRequirementProvider interface.
 */
public class DefaultOfferRequirementProvider implements OfferRequirementProvider {
    private final Logger logger = LoggerFactory.getLogger(getClass());

    @Override
    public OfferRequirement getNewOfferRequirement(String taskType, TaskSpecification taskSpecification)
            throws InvalidRequirementException {
        Protos.TaskInfo taskInfo = Protos.TaskInfo.newBuilder()
                .setName(taskSpecification.getName())
                .setCommand(taskSpecification.getCommand())
                .setTaskId(TaskUtils.emptyTaskId())
                .setSlaveId(TaskUtils.emptyAgentId())
                .addAllResources(getNewResources(taskSpecification))
                .build();

        return new OfferRequirement(
                taskType,
                Arrays.asList(taskInfo),
                Optional.empty(),
                taskSpecification.getPlacement());
    }

    @Override
    public OfferRequirement getExistingOfferRequirement(Protos.TaskInfo taskInfo, TaskSpecification taskSpecification)
            throws InvalidRequirementException {

        validateVolumes(taskInfo, taskSpecification);
        Map<String, Protos.Resource> oldResourceMap = getResourceMap(taskInfo.getResourcesList());

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


        Protos.TaskInfo.Builder taskBuilder = Protos.TaskInfo.newBuilder(taskInfo)
                .clearResources()
                .setCommand(taskSpecification.getCommand())
                .addAllResources(updatedResources)
                .addAllResources(getVolumes(taskInfo.getResourcesList()))
                .setTaskId(TaskUtils.emptyTaskId())
                .setSlaveId(TaskUtils.emptyAgentId());

        try {
            return new OfferRequirement(
                    TaskUtils.getTaskType(taskInfo),
                    Arrays.asList(taskBuilder.build()),
                    Optional.empty(),
                    taskSpecification.getPlacement());
        } catch (TaskException e) {
            throw new InvalidRequirementException(e);
        }
    }

    private void validateVolumes(Protos.TaskInfo taskInfo, TaskSpecification taskSpecification)
            throws InvalidRequirementException {

        try {
            TaskSpecification oldTaskSpecification = DefaultTaskSpecification.create(taskInfo);
            Collection<VolumeSpecification> oldVolumes = oldTaskSpecification.getVolumes();
            Collection<VolumeSpecification> newVolumes = taskSpecification.getVolumes();

            if (oldVolumes.size() > 0 && newVolumes.size() > 0) {
                if (!CollectionUtils.isEqualCollection(oldVolumes, newVolumes)){
                    throw new InvalidRequirementException(
                            String.format("Volumes must be equal.  Old volumes: '%s', New volumes: '%s'",
                                    oldTaskSpecification.getVolumes(), taskSpecification.getVolumes()));
                }
            } else if (!(oldVolumes.size() == 0 && newVolumes.size() == 0)) {
                throw new InvalidRequirementException(
                        String.format("Volumes must be equal.  Old volumes: '%s', New volumes: '%s'",
                                oldTaskSpecification.getVolumes(), taskSpecification.getVolumes()));
            }
        } catch (InvalidTaskSpecificationException e) {
            throw new InvalidRequirementException(e);
        }
    }

    private Iterable<? extends Protos.Resource> getNewResources(TaskSpecification taskSpecification) {
        Collection<Protos.Resource> resources = new ArrayList<>();

        for (ResourceSpecification resourceSpecification : taskSpecification.getResources()) {
            resources.add(ResourceUtils.getDesiredResource(resourceSpecification));
        }

        if (taskSpecification.getVolumes().size() > 0) {
            for (VolumeSpecification volumeSpecification : taskSpecification.getVolumes()) {
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
