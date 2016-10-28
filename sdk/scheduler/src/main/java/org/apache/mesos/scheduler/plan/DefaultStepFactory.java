package org.apache.mesos.scheduler.plan;

import com.google.protobuf.InvalidProtocolBufferException;
import org.apache.mesos.Protos;
import org.apache.mesos.offer.*;
import org.apache.mesos.specification.DefaultTaskSpecification;
import org.apache.mesos.specification.InvalidTaskSpecificationException;
import org.apache.mesos.specification.Pod;
import org.apache.mesos.specification.TaskSpecification;
import org.apache.mesos.state.StateStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
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
    public Step getStep(Pod pod)
            throws Step.InvalidStepException, InvalidProtocolBufferException {
        LOGGER.info("Generating step for: {}", pod.getName());
        Optional<Protos.TaskInfo> taskInfoOptional = null;

        // Make sure all task specs within the pod get launched first.
        for (TaskSpecification taskSpecification : pod.getTaskSpecifications()) {
            taskInfoOptional = stateStore.fetchTask(taskSpecification.getName());
            if (!taskInfoOptional.isPresent()) {
                LOGGER.info("Generating new step for: {}", taskSpecification.getName());
                return new DefaultStep(
                        taskSpecification.getName(),
                        Optional.of(offerRequirementProvider.getNewOfferRequirement(taskSpecification)),
                        Status.PENDING,
                        Collections.emptyList());
            } else {
                // if any task within a pod is in the stateStore, other tasks within the same pod are guaranteed
                // to be in the stateStore as well since tasks are atomically stored as a collection
                // so we don't need to check the rest of taskInfos and we break
                break;
            }

        }

        // if pod tasks are already launched, generate next step
        // all tasks need to be completed in order for the pod to have a completed status
        try {
            List<Protos.TaskInfo> launchedTaskInfos = new ArrayList<>();
            Status status = Status.COMPLETE; // if no tasks, status is complete
            boolean statusComplete;
            for (TaskSpecification taskSpecification : pod.getTaskSpecifications()) {
                taskInfoOptional = stateStore.fetchTask(taskSpecification.getName());
                Protos.TaskInfo taskInfo = TaskUtils.unpackTaskInfo(taskInfoOptional.get());
                launchedTaskInfos.add(taskInfo);
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
            return new DefaultStep(
                    pod.getName(),
                    Optional.of(offerRequirementProvider
                            .getExistingOfferRequirement(launchedTaskInfos, pod)),
                    status,
                    Collections.emptyList());

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
