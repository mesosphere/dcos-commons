package com.mesosphere.sdk.scheduler.plan;

import org.apache.mesos.Protos;
import com.mesosphere.sdk.config.ConfigStoreException;
import com.mesosphere.sdk.config.ConfigTargetStore;
import com.mesosphere.sdk.offer.*;
import com.mesosphere.sdk.specification.PodInstance;
import com.mesosphere.sdk.specification.TaskSpec;
import com.mesosphere.sdk.state.StateStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;

/**
 * This class is a default implementation of the {@link StepFactory} interface.
 */
public class DefaultStepFactory implements StepFactory {
    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultStepFactory.class);

    private final ConfigTargetStore configTargetStore;
    private final StateStore stateStore;
    private final OfferRequirementProvider offerRequirementProvider;

    public DefaultStepFactory(
            ConfigTargetStore configTargetStore,
            StateStore stateStore,
            OfferRequirementProvider offerRequirementProvider) {
        this.configTargetStore = configTargetStore;
        this.stateStore = stateStore;
        this.offerRequirementProvider = offerRequirementProvider;
    }

    @Override
    public Step getStep(PodInstance podInstance, List<String> tasksToLaunch)
            throws Step.InvalidStepException, InvalidRequirementException {
        LOGGER.info("Generating step for pod: {}", podInstance.getName());

        validate(podInstance, tasksToLaunch);

        List<Protos.TaskInfo> taskInfos = TaskUtils.getTaskNames(podInstance).stream()
                .map(taskName -> stateStore.fetchTask(taskName))
                .filter(taskInfoOptional -> taskInfoOptional.isPresent())
                .map(taskInfoOptional -> taskInfoOptional.get())
                .filter(taskInfo -> tasksToLaunch.contains(taskInfo.getName()))
                .collect(Collectors.toList());

        try {
            if (taskInfos.isEmpty()) {
                LOGGER.info("Generating new step for: {}", podInstance.getName());
                return new DefaultStep(
                        podInstance.getName(),
                        Optional.of(offerRequirementProvider.getNewOfferRequirement(podInstance, tasksToLaunch)),
                        Status.PENDING,
                        podInstance,
                        Collections.emptyList());
            } else {
                // Note: This path is for deploying new versions of tasks, unlike transient recovery
                // which is only interested in relaunching tasks as they were. So while they omit
                // placement rules in their OfferRequirement, we include them.
                Status status = getStatus(podInstance, taskInfos);
                LOGGER.info("Generating existing step for: {} with status: {}", podInstance.getName(), status);
                return new DefaultStep(
                        podInstance.getName(),
                        Optional.of(offerRequirementProvider.getExistingOfferRequirement(podInstance, tasksToLaunch)),
                        status,
                        podInstance,
                        Collections.emptyList());
            }
        } catch (ConfigStoreException | TaskException | InvalidRequirementException e) {
            LOGGER.error("Failed to generate Step with exception: ", e);
            throw new Step.InvalidStepException(e);
        }
    }

    private void validate(PodInstance podInstance, List<String> tasksToLaunch) throws Step.InvalidStepException {
        List<TaskSpec> taskSpecsToLaunch = podInstance.getPod().getTasks().stream()
                .filter(taskSpec -> tasksToLaunch.contains(TaskSpec.getInstanceName(podInstance, taskSpec)))
                .collect(Collectors.toList());

        List<String> resourceSetIds = taskSpecsToLaunch.stream()
                .map(taskSpec -> taskSpec.getResourceSet().getId())
                .collect(Collectors.toList());

        Set<String> resourceSetIdsSet = new HashSet<>(resourceSetIds);

        if (resourceSetIdsSet.size() < resourceSetIds.size()) {
            throw new Step.InvalidStepException(
                    "Attempted to launch multiple Tasks using the same resource set id: " + resourceSetIds);
        }
    }

    private Status getStatus(PodInstance podInstance, List<Protos.TaskInfo> taskInfos)
            throws Step.InvalidStepException, ConfigStoreException, TaskException {

        List<Status> statuses = new ArrayList<>();
        for (Protos.TaskInfo taskInfo : taskInfos) {
           statuses.add(getStatus(podInstance, taskInfo));
        }

        for (Status status : statuses) {
            if (!status.equals(Status.COMPLETE)) {
                return Status.PENDING;
            }
        }

        return Status.COMPLETE;
    }

    private Status getStatus(PodInstance podInstance, Protos.TaskInfo taskInfo)
            throws TaskException, ConfigStoreException, Step.InvalidStepException {

        if (isOnTarget(taskInfo) && hasReachedGoalState(podInstance, taskInfo)) {
            return Status.COMPLETE;
        } else {
            return Status.PENDING;
        }

    }

    private boolean isOnTarget(Protos.TaskInfo taskInfo) throws ConfigStoreException, TaskException {
        UUID targetConfigId = configTargetStore.getTargetConfig();
        UUID taskConfigId = CommonTaskUtils.getTargetConfiguration(taskInfo);
        return targetConfigId.equals(taskConfigId);
    }

    private boolean hasReachedGoalState(PodInstance podInstance, Protos.TaskInfo taskInfo)
            throws Step.InvalidStepException {
        TaskSpec.GoalState goalState = null;
        try {
            goalState = TaskUtils.getGoalState(podInstance, taskInfo.getName());
        } catch (TaskException e) {
            throw new Step.InvalidStepException(e);
        }

        Optional<Protos.TaskStatus> status = stateStore.fetchStatus(taskInfo.getName());

        if (!status.isPresent()) {
            return false;
        }

        if (goalState.equals(TaskSpec.GoalState.RUNNING)) {
            switch (status.get().getState()) {
                case TASK_RUNNING:
                    return true;
                default:
                    return false;
            }
        } else if (goalState.equals(TaskSpec.GoalState.FINISHED)) {
            switch (status.get().getState()) {
                case TASK_FINISHED:
                    return true;
                default:
                    return false;
            }
        } else {
            throw new Step.InvalidStepException("Unexpected goal state encountered: " + goalState);
        }
    }

}
