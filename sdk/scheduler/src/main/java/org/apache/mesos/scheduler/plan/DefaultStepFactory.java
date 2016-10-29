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
 * This class is a default implementation of the StepFactory interface.
 */
public class DefaultStepFactory implements StepFactory {
    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultStepFactory.class);

    private final StateStore stateStore;
    private final OfferRequirementProvider offerRequirementProvider;

    public DefaultStepFactory(StateStore stateStore) {
        this(stateStore, new DefaultOfferRequirementProvider());
    }

    public DefaultStepFactory(StateStore stateStore, OfferRequirementProvider offerRequirementProvider) {
        this.stateStore = stateStore;
        this.offerRequirementProvider = offerRequirementProvider;
    }

    @Override
    public Step getStep(TaskSpecification taskSpecification) throws Step.InvalidStepException {
        LOGGER.info("Generating step for: {}", taskSpecification.getName());
        Optional<Protos.TaskInfo> taskInfoOptional = stateStore.fetchTask(taskSpecification.getName());

        try {
            if (!taskInfoOptional.isPresent()) {
                LOGGER.info("Generating new step for: {}", taskSpecification.getName());
                return new DefaultStep(
                        taskSpecification.getName(),
                        Optional.of(offerRequirementProvider.getNewOfferRequirement(taskSpecification)),
                        Status.PENDING,
                        Collections.emptyList());
            } else {
                Protos.TaskInfo taskInfo = TaskUtils.unpackTaskInfo(taskInfoOptional.get());
                TaskSpecification oldTaskSpecification = DefaultTaskSpecification.create(taskInfo);
                Status status = getStatus(oldTaskSpecification, taskSpecification);
                LOGGER.info("Generating existing step for: {} with status: {}", taskSpecification.getName(), status);
                return new DefaultStep(
                        taskSpecification.getName(),
                        Optional.of(offerRequirementProvider
                                .getExistingOfferRequirement(taskInfo, taskSpecification)),
                        status,
                        Collections.emptyList());
            }
        } catch (InvalidTaskSpecificationException | InvalidRequirementException | TaskException e) {
            LOGGER.error("Failed to generate TaskSpecification for existing Task with exception: ", e);
            throw new Step.InvalidStepException(e);
        } catch (InvalidProtocolBufferException e) {
            LOGGER.error(String.format("Failed to unpack taskInfo: %s", taskInfoOptional), e);
            throw new Step.InvalidStepException(e);
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
