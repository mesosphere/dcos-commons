package org.apache.mesos.scheduler.plan;

import org.apache.mesos.Protos;
import org.apache.mesos.offer.*;
import org.apache.mesos.specification.DefaultTaskSpecification;
import org.apache.mesos.specification.ResourceSpecification;
import org.apache.mesos.specification.TaskSpecification;
import org.apache.mesos.state.StateStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Optional;

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
        if (TaskUtils.areDifferent(oldTaskSpecification, newTaskSpecification)) {
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
