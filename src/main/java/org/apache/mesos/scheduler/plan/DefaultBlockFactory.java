package org.apache.mesos.scheduler.plan;

import org.apache.mesos.Protos;
import org.apache.mesos.offer.InvalidRequirementException;
import org.apache.mesos.offer.OfferRequirement;
import org.apache.mesos.offer.ResourceUtils;
import org.apache.mesos.offer.TaskUtils;
import org.apache.mesos.protobuf.TaskUtil;
import org.apache.mesos.protobuf.ValueUtils;
import org.apache.mesos.specification.DefaultTaskSpecification;
import org.apache.mesos.specification.ResourceSpecification;
import org.apache.mesos.specification.TaskSpecification;
import org.apache.mesos.state.StateStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * Created by gabriel on 8/27/16.
 */
public class DefaultBlockFactory implements BlockFactory {
    private final Logger logger = LoggerFactory.getLogger(getClass());
    private final StateStore stateStore;

    public DefaultBlockFactory(StateStore stateStore) {
        this.stateStore = stateStore;
    }

    @Override
    public Block getBlock(TaskSpecification taskSpecification) throws InvalidRequirementException {
        Optional<Protos.TaskInfo> taskInfoOptional = stateStore.fetchTask(taskSpecification.getName());
        if (!taskInfoOptional.isPresent()) {
            logger.info("Generating new block for: " + taskSpecification.getName());
            return new DefaultBlock(
                    taskSpecification.getName(),
                    getNewOfferRequirement(taskSpecification),
                    Status.PENDING);
        } else {
            TaskSpecification oldTaskSpecification = taskInfoToTaskSpec(taskInfoOptional.get());
            Status status = getStatus(oldTaskSpecification, taskSpecification);
            logger.info("Generating existing block for: " + taskSpecification.getName() + " with status: " + status);
            if (status.equals(Status.COMPLETE)) {
                return new DefaultBlock(taskSpecification.getName());
            } else {
                return new DefaultBlock(
                        taskSpecification.getName(),
                        getExistingOfferRequirement(taskInfoOptional.get(), taskSpecification),
                        status);
            }
        }
    }

    private Status getStatus(TaskSpecification oldTaskSpecification, TaskSpecification newTaskSpecification) {
        if (areDifferent(oldTaskSpecification, newTaskSpecification)) {
            return Status.PENDING;
        } else {
            Protos.TaskState taskState = stateStore.fetchStatus(newTaskSpecification.getName()).getState();
            switch (taskState) {
                case TASK_STAGING:
                case TASK_STARTING:
                    return Status.IN_PROGRESS;
                default:
                    return Status.COMPLETE;
            }
        }
    }

    private boolean areDifferent(TaskSpecification oldTaskSpecification, TaskSpecification newTaskSpecification) {
        if (!oldTaskSpecification.getName().equals(newTaskSpecification.getName())) {
            return true;
        }

        if (!oldTaskSpecification.getCommand().equals(oldTaskSpecification.getCommand())) {
            return true;
        }

        Map<String, ResourceSpecification> oldResourceMap = getResourceSpecMap(oldTaskSpecification.getResources());
        Map<String, ResourceSpecification> newResourceMap = getResourceSpecMap(newTaskSpecification.getResources());

        if (oldResourceMap.size() != newResourceMap.size()) {
            return true;
        }

        for (Map.Entry<String, ResourceSpecification> newEntry : newResourceMap.entrySet()) {
            ResourceSpecification oldResourceSpec = oldResourceMap.get(newEntry.getKey());
            if (oldResourceSpec == null) {
                return true;
            } else {
                return areDifferent(oldResourceSpec, newEntry.getValue());
            }
        }

        return false;
    }

    private boolean areDifferent(
            ResourceSpecification oldResourceSpecification,
            ResourceSpecification newResourceSpecification) {

        if (!ValueUtils.equal(oldResourceSpecification.getValue(), newResourceSpecification.getValue())) {
            return true;
        }

        if (!oldResourceSpecification.getRole().equals(newResourceSpecification.getRole())) {
            return true;
        }

        if (!oldResourceSpecification.getPrincipal().equals(newResourceSpecification.getPrincipal())) {
            return true;
        }

        return false;
    }

    private OfferRequirement getNewOfferRequirement(TaskSpecification taskSpecification) throws InvalidRequirementException {
        Protos.TaskInfo taskInfo = Protos.TaskInfo.newBuilder()
                .setName(taskSpecification.getName())
                .setCommand(taskSpecification.getCommand())
                .setTaskId(TaskUtils.emptyTaskId())
                .setSlaveId(TaskUtils.emptyAgentId())
                .addAllResources(getNewResources(taskSpecification))
                .build();

        return new OfferRequirement(Arrays.asList(taskInfo));
    }

    private Map<String, Protos.Resource> getResourceMap(Collection<Protos.Resource> resources) {
        Map<String, Protos.Resource> resourceMap = new HashMap<>();
        for (Protos.Resource resource : resources) {
            resourceMap.put(resource.getName(), resource);
        }

        return resourceMap;
    }

    private Map<String, ResourceSpecification> getResourceSpecMap(
            Collection<ResourceSpecification> resourceSpecifications) {
        Map<String, ResourceSpecification> resourceMap = new HashMap<>();
        for (ResourceSpecification resourceSpecification : resourceSpecifications) {
            resourceMap.put(resourceSpecification.getName(), resourceSpecification);
        }

        return resourceMap;
    }


    private OfferRequirement getExistingOfferRequirement(
            Protos.TaskInfo oldTask,
            TaskSpecification newTaskSpecification) throws InvalidRequirementException {

        Map<String, Protos.Resource> oldResourceMap = getResourceMap(oldTask.getResourcesList());
        List<Protos.Resource> updatedResources = new ArrayList<>();

        for (ResourceSpecification resourceSpecification : newTaskSpecification.getResources()) {
            Protos.Resource oldResource = oldResourceMap.get(resourceSpecification.getName());
            if (oldResource != null) {
                updatedResources.add(updateResource(oldResource, resourceSpecification));
            } else {
                    updatedResources.add(
                            ResourceUtils.getDesiredResource(resourceSpecification));
            }
        }

        Protos.TaskInfo.Builder taskBuilder = Protos.TaskInfo.newBuilder(oldTask)
                .clearResources()
                .setCommand(newTaskSpecification.getCommand())
                .addAllResources(updatedResources)
                .setTaskId(Protos.TaskID.newBuilder().setValue(""))
                .setSlaveId(Protos.SlaveID.newBuilder().setValue(""));

        return new OfferRequirement(Arrays.asList(taskBuilder.build()));
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

    private TaskSpecification taskInfoToTaskSpec(Protos.TaskInfo taskInfo) {
        return DefaultTaskSpecification.create(taskInfo);
    }

    private Iterable<? extends Protos.Resource> getNewResources(TaskSpecification taskSpecification) {
        Collection<Protos.Resource> resources = new ArrayList<>();

        for (ResourceSpecification resourceSpecification : taskSpecification.getResources()) {
            resources.add(ResourceUtils.getDesiredResource(resourceSpecification));
        }

        return resources;
    }
}
