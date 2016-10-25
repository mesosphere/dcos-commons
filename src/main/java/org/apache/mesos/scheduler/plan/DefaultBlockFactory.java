package org.apache.mesos.scheduler.plan;

import org.apache.mesos.Protos;
import org.apache.mesos.offer.InvalidRequirementException;
import org.apache.mesos.offer.OfferRequirementProvider;
import org.apache.mesos.offer.TaskException;
import org.apache.mesos.offer.TaskUtils;
import org.apache.mesos.specification.TaskSpecification;
import org.apache.mesos.specification.TaskSpecificationProvider;
import org.apache.mesos.state.StateStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;

/**
 * This class is a default implementation of the BlockFactory interface.
 */
public class DefaultBlockFactory implements BlockFactory {
    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultBlockFactory.class);

    private final StateStore stateStore;
    private final OfferRequirementProvider offerRequirementProvider;
    private final TaskSpecificationProvider taskSpecificationProvider;

    public DefaultBlockFactory(
            StateStore stateStore,
            OfferRequirementProvider offerRequirementProvider,
            TaskSpecificationProvider taskSpecificationProvider) {
        this.stateStore = stateStore;
        this.offerRequirementProvider = offerRequirementProvider;
        this.taskSpecificationProvider = taskSpecificationProvider;
    }

    @Override
    public Block getBlock(String taskType, TaskSpecification taskSpecification) throws InvalidRequirementException {
        LOGGER.info("Generating block for: " + taskSpecification.getName());
        Optional<Protos.TaskInfo> taskInfoOptional = stateStore.fetchTask(taskSpecification.getName());
        if (!taskInfoOptional.isPresent()) {
            LOGGER.info("Generating new block for: " + taskSpecification.getName());
            return new DefaultBlock(
                    taskSpecification.getName(),
                    offerRequirementProvider.getNewOfferRequirement(taskType, taskSpecification),
                    Status.PENDING);
        } else {
            final TaskSpecification oldTaskSpecification;
            try {
                oldTaskSpecification =
                        taskSpecificationProvider.getTaskSpecification(taskInfoOptional.get());
            } catch (TaskException e) {
                throw new InvalidRequirementException(e);
            }
            Status status = getStatus(oldTaskSpecification, taskSpecification);
            LOGGER.info("Generating existing block for: " + taskSpecification.getName() +
                    " with status: " + status);
            return new DefaultBlock(
                    taskSpecification.getName(),
                    offerRequirementProvider.getExistingOfferRequirement(taskInfoOptional.get(), taskSpecification),
                    status);
        }
    }

    private Status getStatus(TaskSpecification oldTaskSpecification, TaskSpecification newTaskSpecification) {
        LOGGER.info("Getting status for oldTask: " + oldTaskSpecification + " newTask: " + newTaskSpecification);
        if (TaskUtils.areDifferent(oldTaskSpecification, newTaskSpecification)) {
            return Status.PENDING;
        } else {
            Protos.TaskState taskState = stateStore.fetchStatus(newTaskSpecification.getName()).get().getState();
            switch (taskState) {
                case TASK_STAGING:
                case TASK_STARTING:
                    return Status.IN_PROGRESS;
                default:
                    return Status.COMPLETE;
            }
        }
    }
}
