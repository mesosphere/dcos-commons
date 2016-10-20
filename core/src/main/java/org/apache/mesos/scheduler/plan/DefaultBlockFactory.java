package org.apache.mesos.scheduler.plan;

import com.google.protobuf.InvalidProtocolBufferException;
import org.apache.mesos.Protos;
import org.apache.mesos.offer.*;
import org.apache.mesos.specification.DefaultTaskSpecification;
import org.apache.mesos.specification.InvalidTaskSpecificationException;
import org.apache.mesos.specification.TaskSpecification;
import org.apache.mesos.state.StateStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;

/**
 * This class is a default implementation of the BlockFactory interface.
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
    public Block getBlock(String taskType, TaskSpecification taskSpecification) throws InvalidRequirementException {
        logger.info("Generating block for: " + taskSpecification.getName());
        Optional<Protos.TaskInfo> taskInfoOptional = stateStore.fetchTask(taskSpecification.getName());
        if (!taskInfoOptional.isPresent()) {
            logger.info("Generating new block for: " + taskSpecification.getName());

            return new DefaultBlock(
                    taskSpecification.getName(),
                    offerRequirementProvider.getNewOfferRequirement(taskType, taskSpecification),
                    Status.PENDING);
        } else {
            try {
                TaskSpecification oldTaskSpecification =
                        DefaultTaskSpecification.create(TaskUtils.unpackTaskInfo(taskInfoOptional.get()));
                Status status = getStatus(oldTaskSpecification, taskSpecification);
                logger.info("Generating existing block for: " + taskSpecification.getName() +
                        " with status: " + status);
                return new DefaultBlock(
                        taskSpecification.getName(),
                        offerRequirementProvider.getExistingOfferRequirement(taskInfoOptional.get(), taskSpecification),
                        status);
            } catch (InvalidTaskSpecificationException e) {
                logger.error("Failed to generate TaskSpecification for existing Task with exception: ", e);
                throw new InvalidRequirementException(e);
            } catch (InvalidProtocolBufferException e) {
                String errorMessage = "Failed to unpack taskInfo: {}";
                logger.error(errorMessage, e);
                throw new InvalidRequirementException(errorMessage, e);
            }
        }
    }

    private Status getStatus(TaskSpecification oldTaskSpecification, TaskSpecification newTaskSpecification) {
        logger.info("Getting status for oldTask: " + oldTaskSpecification + " newTask: " + newTaskSpecification);
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
