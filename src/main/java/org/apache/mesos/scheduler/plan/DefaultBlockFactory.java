package org.apache.mesos.scheduler.plan;

import org.apache.mesos.Protos;
import org.apache.mesos.offer.DefaultOfferRequirementProvider;
import org.apache.mesos.offer.InvalidRequirementException;
import org.apache.mesos.offer.OfferRequirementProvider;
import org.apache.mesos.offer.ResourceUtils;
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
    private final OfferRequirementProvider offerRequirementProvider;

    public DefaultBlockFactory(StateStore stateStore) {
        this.stateStore = stateStore;
        this.offerRequirementProvider = new DefaultOfferRequirementProvider();
    }

    @Override
    public Block getBlock(TaskSpecification taskSpecification) throws InvalidRequirementException {
        logger.info("Generating block for: " + taskSpecification.getName());
        Optional<Protos.TaskInfo> taskInfoOptional = stateStore.fetchTask(taskSpecification.getName());
        if (!taskInfoOptional.isPresent()) {
            logger.info("Generating new block for: " + taskSpecification.getName());
            return new DefaultBlock(
                    taskSpecification.getName(),
                    offerRequirementProvider.getNewOfferRequirement(taskSpecification),
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
                        offerRequirementProvider.getExistingOfferRequirement(taskInfoOptional.get(), taskSpecification),
                        status);
            }
        }
    }

    private Status getStatus(TaskSpecification oldTaskSpecification, TaskSpecification newTaskSpecification) {
        logger.info("Getting status for oldTask: " + oldTaskSpecification + " newTask: " + newTaskSpecification);
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
            logger.info("Task names are different.");
            return true;
        }

        if (!oldTaskSpecification.getCommand().equals(oldTaskSpecification.getCommand())) {
            logger.info("Task commands are different.");
            return true;
        }

        Map<String, ResourceSpecification> oldResourceMap = getResourceSpecMap(oldTaskSpecification.getResources());
        Map<String, ResourceSpecification> newResourceMap = getResourceSpecMap(newTaskSpecification.getResources());

        if (oldResourceMap.size() != newResourceMap.size()) {
            logger.info("Resource lengths are different.");
            return true;
        }

        for (Map.Entry<String, ResourceSpecification> newEntry : newResourceMap.entrySet()) {
            String resourceName = newEntry.getKey();
            logger.info("Checking resource difference for: " + resourceName);
            ResourceSpecification oldResourceSpec = oldResourceMap.get(resourceName);
            if (oldResourceSpec == null) {
                logger.info("Resource not found.");
                return true;
            } else if (areDifferent(oldResourceSpec, newEntry.getValue())) {
                return true;
            }
        }

        return false;
    }

    private boolean areDifferent(
            ResourceSpecification oldResourceSpecification,
            ResourceSpecification newResourceSpecification) {

        Protos.Value oldValue = oldResourceSpecification.getValue();
        Protos.Value newValue = newResourceSpecification.getValue();
        if (!ValueUtils.equal(oldValue, newValue)) {
            logger.info("Values are different.");
            return true;
        } else {
            logger.info("Values are EQUAL, oldValue: " + oldValue + " newValue: " + newValue);
        }

        if (!oldResourceSpecification.getRole().equals(newResourceSpecification.getRole())) {
            logger.info("Roles are different.");
            return true;
        }

        if (!oldResourceSpecification.getPrincipal().equals(newResourceSpecification.getPrincipal())) {
            logger.info("Principals are different.");
            return true;
        }

        return false;
    }

    private Map<String, ResourceSpecification> getResourceSpecMap(
            Collection<ResourceSpecification> resourceSpecifications) {
        Map<String, ResourceSpecification> resourceMap = new HashMap<>();
        for (ResourceSpecification resourceSpecification : resourceSpecifications) {
            resourceMap.put(resourceSpecification.getName(), resourceSpecification);
        }

        return resourceMap;
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
