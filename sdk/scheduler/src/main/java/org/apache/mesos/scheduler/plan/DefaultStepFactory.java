package org.apache.mesos.scheduler.plan;

import com.google.protobuf.InvalidProtocolBufferException;
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

import java.util.*;

/**
 * This class is a default implementation of the {@link StepFactory} interface.
 */
public class DefaultStepFactory implements StepFactory {
    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultStepFactory.class);

    private final StateStore stateStore;
    private final OfferRequirementProvider offerRequirementProvider;
    private final TaskSpecificationProvider taskSpecificationProvider;

    public DefaultStepFactory(
            StateStore stateStore,
            OfferRequirementProvider offerRequirementProvider,
            TaskSpecificationProvider taskSpecificationProvider) {
        this.stateStore = stateStore;
        this.offerRequirementProvider = offerRequirementProvider;
        this.taskSpecificationProvider = taskSpecificationProvider;
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
                final TaskSpecification oldTaskSpecification =
                        taskSpecificationProvider.getTaskSpecification(
                                TaskUtils.unpackTaskInfo(taskInfoOptional.get()));
                Status status = getStatus(oldTaskSpecification, taskSpecification);
                LOGGER.info("Generating existing step for: {} with status: {}", taskSpecification.getName(), status);
                Map<Protos.TaskID, Status> tasks = new HashMap<>();
                tasks.put(taskInfoOptional.get().getTaskId(), status);
                return new DefaultStep(
                        taskInfoOptional.get().getName(),
                        Optional.of(offerRequirementProvider.getExistingOfferRequirement(
                                taskInfoOptional.get(), taskSpecification)),
                        tasks,
                        Collections.emptyList());
            }
        } catch (InvalidRequirementException | TaskException e) {
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
