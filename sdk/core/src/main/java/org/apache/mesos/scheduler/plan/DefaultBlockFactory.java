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

import java.util.Collections;
import java.util.Optional;

/**
 * This class is a default implementation of the BlockFactory interface.
 */
public class DefaultBlockFactory implements BlockFactory {
    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultBlockFactory.class);

    private final StateStore stateStore;
    private final OfferRequirementProvider offerRequirementProvider;

    public DefaultBlockFactory(StateStore stateStore) {
        this(stateStore, new DefaultOfferRequirementProvider());
    }

    public DefaultBlockFactory(StateStore stateStore, OfferRequirementProvider offerRequirementProvider) {
        this.stateStore = stateStore;
        this.offerRequirementProvider = offerRequirementProvider;
    }

    @Override
    public Block getBlock(TaskSpecification taskSpecification)
            throws Block.InvalidBlockException, InvalidProtocolBufferException {
        LOGGER.info("Generating block for: " + taskSpecification.getName());
        Optional<Protos.TaskInfo> taskInfoOptional = stateStore.fetchTask(taskSpecification.getName());

        try {
            if (!taskInfoOptional.isPresent()) {
                LOGGER.info("Generating new block for: " + taskSpecification.getName());
                return new DefaultBlock(
                        taskSpecification.getName(),
                        Optional.of(offerRequirementProvider.getNewOfferRequirement(taskSpecification)),
                        Status.PENDING,
                        Collections.emptyList());
            } else {
                Protos.TaskInfo taskInfo = TaskUtils.unpackTaskInfo(taskInfoOptional.get());
                TaskSpecification oldTaskSpecification = DefaultTaskSpecification.create(taskInfo);
                Status status = getStatus(oldTaskSpecification, taskSpecification);
                LOGGER.info("Generating existing block for: " + taskSpecification.getName() +
                        " with status: " + status);
                return new DefaultBlock(
                        taskSpecification.getName(),
                        Optional.of(offerRequirementProvider
                                .getExistingOfferRequirement(taskInfo, taskSpecification)),
                        status,
                        Collections.emptyList());
            }
        } catch (InvalidTaskSpecificationException | InvalidRequirementException | TaskException e) {
            LOGGER.error("Failed to generate TaskSpecification for existing Task with exception: ", e);
            throw new Block.InvalidBlockException(e);
        } catch (InvalidProtocolBufferException e) {
            LOGGER.error("Failed to unpack taskInfo: {}", e);
            throw new InvalidProtocolBufferException(e.toString());
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
