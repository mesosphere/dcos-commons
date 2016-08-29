package org.apache.mesos.offer;

import org.apache.mesos.Protos;
import org.apache.mesos.specification.ResourceSpecification;
import org.apache.mesos.specification.TaskSpecification;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * Created by gabriel on 8/29/16.
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
        List<Protos.Resource> updatedResources = new ArrayList<>();

        for (ResourceSpecification resourceSpecification : taskSpecification.getResources()) {
            Protos.Resource oldResource = oldResourceMap.get(resourceSpecification.getName());
            if (oldResource != null) {
                updatedResources.add(updateResource(oldResource, resourceSpecification));
            } else {
                updatedResources.add(
                        ResourceUtils.getDesiredResource(resourceSpecification));
            }
        }

        Protos.TaskInfo.Builder taskBuilder = Protos.TaskInfo.newBuilder(taskInfo)
                .clearResources()
                .setCommand(taskSpecification.getCommand())
                .addAllResources(updatedResources)
                .setTaskId(Protos.TaskID.newBuilder().setValue(""))
                .setSlaveId(Protos.SlaveID.newBuilder().setValue(""));

        return new OfferRequirement(Arrays.asList(taskBuilder.build()));
    }

    private Iterable<? extends Protos.Resource> getNewResources(TaskSpecification taskSpecification) {
        Collection<Protos.Resource> resources = new ArrayList<>();

        for (ResourceSpecification resourceSpecification : taskSpecification.getResources()) {
            resources.add(ResourceUtils.getDesiredResource(resourceSpecification));
        }

        return resources;
    }

    private Map<String, Protos.Resource> getResourceMap(Collection<Protos.Resource> resources) {
        Map<String, Protos.Resource> resourceMap = new HashMap<>();
        for (Protos.Resource resource : resources) {
            resourceMap.put(resource.getName(), resource);
        }

        return resourceMap;
    }

    private Protos.Resource updateResource(Protos.Resource resource, ResourceSpecification resourceSpecification) {
        Protos.Resource.Builder builder = Protos.Resource.newBuilder(resource);
        switch (resource.getType()) {
            case SCALAR:
                return builder.setScalar(resourceSpecification.getValue().getScalar()).build();
            case RANGES:
                return builder.setRanges(resourceSpecification.getValue().getRanges()).build();
            case SET:
                return builder.setSet(resourceSpecification.getValue().getSet()).build();
            default:
                logger.error("Encountered unexpected Value type: " + resource.getType());
                return resource;
        }
    }
}
