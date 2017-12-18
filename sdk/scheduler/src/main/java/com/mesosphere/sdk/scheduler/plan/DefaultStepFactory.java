package com.mesosphere.sdk.scheduler.plan;

import com.mesosphere.sdk.offer.*;
import com.mesosphere.sdk.offer.taskdata.TaskLabelReader;
import com.mesosphere.sdk.scheduler.recovery.FailureUtils;
import com.mesosphere.sdk.specification.GoalState;
import com.mesosphere.sdk.specification.PodInstance;
import com.mesosphere.sdk.specification.TaskSpec;
import com.mesosphere.sdk.state.ConfigStoreException;
import com.mesosphere.sdk.state.ConfigTargetStore;
import com.mesosphere.sdk.state.StateStore;

import org.apache.commons.lang3.exception.ExceptionUtils;
import org.apache.mesos.Protos;
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

    public DefaultStepFactory(
            ConfigTargetStore configTargetStore,
            StateStore stateStore) {
        this.configTargetStore = configTargetStore;
        this.stateStore = stateStore;
    }

    @Override
    public Step getStep(PodInstance podInstance, Collection<String> tasksToLaunch) {
        try {
            LOGGER.info("Generating step for pod: {}, with tasks: {}", podInstance.getName(), tasksToLaunch);
            validate(podInstance, tasksToLaunch);

            List<Protos.TaskInfo> taskInfos = TaskUtils.getTaskNames(podInstance, tasksToLaunch).stream()
                    .map(taskName -> stateStore.fetchTask(taskName))
                    .filter(taskInfoOptional -> taskInfoOptional.isPresent())
                    .map(taskInfoOptional -> taskInfoOptional.get())
                    .collect(Collectors.toList());

            return new DeploymentStep(
                    TaskUtils.getStepName(podInstance, tasksToLaunch),
                    PodInstanceRequirement.newBuilder(podInstance, tasksToLaunch).build(),
                    stateStore)
                    .updateInitialStatus(taskInfos.isEmpty() ? Status.PENDING : getStatus(podInstance, taskInfos));
        } catch (Exception e) {
            LOGGER.error("Failed to generate Step with exception: ", e);
            return new DeploymentStep(
                    podInstance.getName(),
                    PodInstanceRequirement.newBuilder(podInstance, Collections.emptyList()).build(),
                    stateStore)
                    .addError(ExceptionUtils.getStackTrace(e));
        }
    }

    private void validate(PodInstance podInstance, Collection<String> tasksToLaunch) throws Exception {
        List<TaskSpec> taskSpecsToLaunch = podInstance.getPod().getTasks().stream()
                .filter(taskSpec -> tasksToLaunch.contains(taskSpec.getName()))
                .collect(Collectors.toList());

        List<String> resourceSetIds = taskSpecsToLaunch.stream()
                .map(taskSpec -> taskSpec.getResourceSet().getId())
                .collect(Collectors.toList());

        if (hasDuplicates(resourceSetIds)) {
            throw new Exception(String.format(
                    "Attempted to simultaneously launch tasks: %s in pod: %s using the same resource set id: %s. " +
                    "These tasks should either be run in separate steps or use different resource set ids",
                    tasksToLaunch, podInstance.getName(), resourceSetIds));
        }

        List<String> dnsPrefixes = taskSpecsToLaunch.stream()
                .map(taskSpec -> taskSpec.getDiscovery())
                .filter(discovery -> discovery.isPresent())
                .map(discovery -> discovery.get().getPrefix())
                .filter(prefix -> prefix.isPresent())
                .map(prefix -> prefix.get())
                .collect(Collectors.toList());

        if (hasDuplicates(dnsPrefixes)) {
            throw new Exception(String.format(
                    "Attempted to simultaneously launch tasks: %s in pod: %s using the same DNS name: %s. " +
                            "These tasks should either be run in separate steps or use different DNS names",
                    tasksToLaunch, podInstance.getName(), dnsPrefixes));
        }
    }

    private <T> boolean hasDuplicates(Collection<T> collection) {
        return new HashSet<T>(collection).size() < collection.size();
    }

    private Status getStatus(PodInstance podInstance, List<Protos.TaskInfo> taskInfos)
            throws ConfigStoreException, TaskException {

        List<Status> statuses = new ArrayList<>();
        UUID targetConfigId = configTargetStore.getTargetConfig();
        for (Protos.TaskInfo taskInfo : taskInfos) {
           statuses.add(getStatus(podInstance, taskInfo, targetConfigId));
        }

        for (Status status : statuses) {
            if (!status.equals(Status.COMPLETE)) {
                return Status.PENDING;
            }
        }

        return Status.COMPLETE;
    }

    private Status getStatus(PodInstance podInstance, Protos.TaskInfo taskInfo, UUID targetConfigId)
            throws TaskException {

        boolean isOnTarget = isOnTarget(taskInfo, targetConfigId);
        boolean hasReachedGoal = hasReachedGoalState(podInstance, taskInfo);

        if (hasReachedGoal) {
            GoalState goalState = TaskUtils.getGoalState(podInstance, taskInfo.getName());
            if (goalState.equals(GoalState.ONCE)) {
                LOGGER.info("Automatically on target configuration due to having reached {} goal.", goalState);
                isOnTarget = true;
            }
        }

        boolean hasPermanentlyFailed = FailureUtils.isPermanentlyFailed(taskInfo);

        String bitsLog = String.format("onTarget=%s reachedGoal=%s permanentlyFailed=%s",
                isOnTarget, hasReachedGoal, hasPermanentlyFailed);
        if ((isOnTarget && hasReachedGoal) || hasPermanentlyFailed) {
            LOGGER.info("Deployment of task '{}' is COMPLETE: {}", taskInfo.getName(), bitsLog);
            return Status.COMPLETE;
        } else {
            LOGGER.info("Deployment of task '{}' is PENDING: {}", taskInfo.getName(), bitsLog);
            return Status.PENDING;
        }

    }

    private static boolean isOnTarget(Protos.TaskInfo taskInfo, UUID targetConfigId) throws TaskException {
        UUID taskConfigId = new TaskLabelReader(taskInfo).getTargetConfiguration();
        return targetConfigId.equals(taskConfigId);
    }

    private boolean hasReachedGoalState(PodInstance podInstance, Protos.TaskInfo taskInfo) throws TaskException {
        GoalState goalState = TaskUtils.getGoalState(podInstance, taskInfo.getName());
        Optional<Protos.TaskStatus> status = stateStore.fetchStatus(taskInfo.getName());
        if (!status.isPresent()) {
            return false;
        }

        if (goalState.equals(GoalState.RUNNING)) {
            switch (status.get().getState()) {
                case TASK_RUNNING:
                    return true;
                default:
                    return false;
            }
        } else if (goalState.equals(GoalState.ONCE) || goalState.equals(GoalState.FINISH)) {
            switch (status.get().getState()) {
                case TASK_FINISHED:
                    return true;
                default:
                    return false;
            }
        } else {
            throw new TaskException("Unexpected goal state encountered: " + goalState);
        }
    }
}
