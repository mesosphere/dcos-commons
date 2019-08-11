package com.mesosphere.sdk.scheduler.plan;

import com.mesosphere.sdk.offer.LoggingUtils;
import com.mesosphere.sdk.offer.TaskException;
import com.mesosphere.sdk.offer.TaskUtils;
import com.mesosphere.sdk.offer.taskdata.TaskLabelReader;
import com.mesosphere.sdk.scheduler.recovery.FailureUtils;
import com.mesosphere.sdk.specification.GoalState;
import com.mesosphere.sdk.specification.PodInstance;
import com.mesosphere.sdk.specification.TaskSpec;
import com.mesosphere.sdk.state.ConfigStoreException;
import com.mesosphere.sdk.state.ConfigTargetStore;
import com.mesosphere.sdk.state.StateStore;

import com.google.common.annotations.VisibleForTesting;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.apache.mesos.Protos;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * This class is a default implementation of the {@link StepFactory} interface.
 */
@SuppressWarnings({
    "checkstyle:IllegalCatch",
    "checkstyle:ThrowsCount"
})
public class DefaultStepFactory implements StepFactory {
  private static final Logger LOGGER = LoggingUtils.getLogger(DefaultStepFactory.class);

  private final ConfigTargetStore configTargetStore;

  private final StateStore stateStore;

  public DefaultStepFactory(
      ConfigTargetStore configTargetStore,
      StateStore stateStore)
  {
    this.configTargetStore = configTargetStore;
    this.stateStore = stateStore;
  }

  @Override
  public Step getStep(PodInstance podInstance, Collection<String> tasksToLaunch) {
    try {
      LOGGER.info("Generating step for pod: {}, with tasks: {}",
          podInstance.getName(),
          tasksToLaunch);
      validate(podInstance, tasksToLaunch);

      List<Protos.TaskInfo> taskInfos = TaskUtils
          .getTaskNames(podInstance, tasksToLaunch)
          .stream()
          .map(stateStore::fetchTask)
          .filter(Optional::isPresent)
          .map(Optional::get)
          .collect(Collectors.toList());

      return new DeploymentStep(
          TaskUtils.getStepName(podInstance, tasksToLaunch),
          PodInstanceRequirement.newBuilder(podInstance, tasksToLaunch).build(),
          stateStore)
          .updateInitialStatus(taskInfos.isEmpty() ?
              Status.PENDING : getStatus(podInstance, taskInfos));
    } catch (Exception e) {
      LOGGER.error("Failed to generate Step with exception: ", e);
      return new DeploymentStep(
          podInstance.getName(),
          PodInstanceRequirement.newBuilder(podInstance, Collections.emptyList()).build(),
          stateStore)
          .addError(ExceptionUtils.getStackTrace(e));
    }
  }

  private void validate(PodInstance podInstance, Collection<String> tasksToLaunch)
      throws Exception
  {
    List<TaskSpec> taskSpecsToLaunch = podInstance.getPod().getTasks().stream()
        .filter(taskSpec -> tasksToLaunch.contains(taskSpec.getName()))
        .collect(Collectors.toList());

    List<String> resourceSetIds = taskSpecsToLaunch.stream()
        .map(taskSpec -> taskSpec.getResourceSet().getId())
        .collect(Collectors.toList());

    if (hasDuplicates(resourceSetIds)) {
      throw new Exception(String.format(
          "Attempted to simultaneously launch tasks: %s in pod: %s " +
              "using the same resource set id: %s. " +
              "These tasks should either be run in separate steps or use " +
              "different resource set ids",
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
      throws ConfigStoreException, TaskException
  {

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
      throws TaskException
  {
    GoalState goalState = TaskUtils.getGoalState(podInstance, taskInfo.getName());

    boolean hasReachedGoal = hasReachedGoalState(taskInfo, goalState, targetConfigId);
    boolean hasPermanentlyFailed = FailureUtils.isPermanentlyFailed(taskInfo);
    // If the task is permanently failed ("pod replace"), its deployment is owned by the recovery plan, not the
    // deploy plan. The deploy plan can consider it complete until it is no longer marked as failed, at which point
    // the deploy plan will resume showing it as PENDING deployment.
    Status status = hasReachedGoal || hasPermanentlyFailed ? Status.COMPLETE : Status.PENDING;

    LOGGER.info("Deployment of {} task '{}' is {}: hasReachedGoal={} permanentlyFailed={}",
        goalState, taskInfo.getName(), status, hasReachedGoal, hasPermanentlyFailed);
    return status;
  }

  @VisibleForTesting
  protected boolean hasReachedGoalState(Protos.TaskInfo taskInfo,
                                        GoalState goalState,
                                        UUID targetConfigId)
      throws TaskException
  {
    Optional<Protos.TaskStatus> status = stateStore.fetchStatus(taskInfo.getName());
    if (!status.isPresent()) {
      // Task has never been run and therefore doesn't have any status information yet.
      return false;
    }

    switch (goalState) {
      case RUNNING: {
        // Task needs to be running, on the right config ID, and readiness checks (if any) need to have passed.
        if (!Protos.TaskState.TASK_RUNNING.equals(status.get().getState())) {
          return false;
        }
        TaskLabelReader taskLabelReader = new TaskLabelReader(taskInfo);
        return taskLabelReader.getTargetConfiguration().equals(targetConfigId)
            && taskLabelReader.isReadinessCheckSucceeded(status.get());
      }
      case FINISH:
        // Task needs to have finished running successfully and the config ID needs to match the target config.
        return Protos.TaskState.TASK_FINISHED.equals(status.get().getState())
            && new TaskLabelReader(taskInfo).getTargetConfiguration().equals(targetConfigId);
      case ONCE:
        // Task needs to have finished running successfully but the config ID can be anything.
        return Protos.TaskState.TASK_FINISHED.equals(status.get().getState());
      case UNKNOWN:
      default:
        throw new IllegalArgumentException(String.format(
            "Unsupported goal state for task %s: %s", taskInfo.getName(), goalState));
    }
  }
}
