package org.apache.mesos.scheduler.plan;

import com.google.protobuf.InvalidProtocolBufferException;
import org.apache.mesos.Protos;
import org.apache.mesos.offer.*;
import org.apache.mesos.specification.DefaultTaskSpecification;
import org.apache.mesos.specification.InvalidTaskSpecificationException;
import org.apache.mesos.specification.PodSpecification;
import org.apache.mesos.specification.TaskSpecification;
import org.apache.mesos.state.StateStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

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
    public Step getStep(PodSpecification podSpecification)
            throws Step.InvalidStepException, InvalidProtocolBufferException, InvalidRequirementException {
        LOGGER.info("Generating step for: {}", podSpecification.getName());
        Optional<Collection<Protos.TaskInfo>> podOptional = null;

        // Make sure all task specs within the podSpecification get launched first.
        podOptional = stateStore.fetchPod(podSpecification.getName());
        if (!podOptional.isPresent()) {
            LOGGER.info("Generating new step for: {}", podSpecification.getName());
            try {
                return new DefaultStep(
                        podSpecification.getName(),
                        Optional.of(offerRequirementProvider.getNewOfferRequirement(podSpecification)),
                        Status.PENDING,
                        Collections.emptyList());
            } catch (InvalidRequirementException e) {
                throw new InvalidRequirementException("Failed to get step: %s", e);
            }

        }

        Optional<Protos.TaskInfo> taskInfoOptional = null;
        List<Protos.TaskInfo> launchedTaskInfos = new ArrayList<>();
        Status taskStatus, podStatus = null;
        try {
            for (TaskSpecification newTaskSpecification : podSpecification.getTaskSpecifications()) {
                taskInfoOptional = stateStore.fetchTask(podSpecification.getName(), newTaskSpecification.getName());
                Protos.TaskInfo taskInfo = TaskUtils.unpackTaskInfo(taskInfoOptional.get());
                launchedTaskInfos.add(taskInfo);
                TaskSpecification oldTaskSpecification = DefaultTaskSpecification.create(taskInfo);
                taskStatus = getStatus(oldTaskSpecification, newTaskSpecification, podSpecification.getName());
                podStatus = getPodStatus(taskStatus, podStatus, podSpecification.getName());
            }
            LOGGER.info("Generating existing step for: {} with status: {}", podSpecification.getName(), podStatus);
            return new DefaultStep(
                    podSpecification.getName(),
                    Optional.of(offerRequirementProvider
                            .getExistingOfferRequirement(launchedTaskInfos, podSpecification)),
                    podStatus,
                    Collections.emptyList());

        } catch (InvalidTaskSpecificationException | InvalidRequirementException | TaskException e) {
            LOGGER.error("Failed to generate TaskSpecification for existing Task with exception: ", e);
            throw new Step.InvalidStepException(e);
        } catch (InvalidProtocolBufferException e) {
            LOGGER.error(String.format("Failed to unpack taskInfo: %s", taskInfoOptional), e);
            throw new Step.InvalidStepException(e);
        }
    }

    private Status getStatus(TaskSpecification oldTaskSpec, TaskSpecification newTaskSpec, String podName) {

        LOGGER.info("Getting status for oldTask: " + oldTaskSpec + " newTask: " + newTaskSpec);
        if (TaskUtils.areDifferent(oldTaskSpec, newTaskSpec)) {
            return Status.PENDING;
        } else {
            Protos.TaskState taskState = stateStore.fetchStatus(podName, newTaskSpec.getName()).get().getState();
            switch (taskState) {
                case TASK_STAGING:
                case TASK_STARTING:
                    return Status.IN_PROGRESS;
                default:
                    return Status.COMPLETE;
            }
        }
    }

    private Status getPodStatus(Status taskStatus, Status podStatus, String podName) {
        LOGGER.info("Getting status for pod: {}", podName);
        // if a previous pod task was pending, not necessarily the current one, the pod is still pending
        if (taskStatus == Status.PENDING || podStatus == Status.PENDING) {
            return Status.PENDING;
            // if a previous pod task was IN_PROGRESS therefore the pod being IN_PROGRESS, even if the
            // current task status is COMPLETE we return the IN_PROGRESS state of the pod
        } else if (taskStatus == Status.IN_PROGRESS || podStatus == Status.IN_PROGRESS) {
            return Status.IN_PROGRESS;
            // if we've reached here then so far all tasks have been COMPLETE
        } else if (taskStatus == Status.COMPLETE || podStatus == Status.COMPLETE) {
            return Status.COMPLETE;
        } else {
            return Status.ERROR;
        }
    }
}
