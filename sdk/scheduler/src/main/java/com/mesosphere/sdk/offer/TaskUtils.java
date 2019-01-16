package com.mesosphere.sdk.offer;

import com.mesosphere.sdk.offer.taskdata.EnvConstants;
import com.mesosphere.sdk.offer.taskdata.TaskLabelReader;
import com.mesosphere.sdk.scheduler.plan.DefaultPodInstance;
import com.mesosphere.sdk.scheduler.plan.PodInstanceRequirement;
import com.mesosphere.sdk.scheduler.plan.Step;
import com.mesosphere.sdk.scheduler.recovery.FailureUtils;
import com.mesosphere.sdk.specification.CommandSpec;
import com.mesosphere.sdk.specification.ConfigFileSpec;
import com.mesosphere.sdk.specification.DiscoverySpec;
import com.mesosphere.sdk.specification.GoalState;
import com.mesosphere.sdk.specification.HealthCheckSpec;
import com.mesosphere.sdk.specification.PodInstance;
import com.mesosphere.sdk.specification.PodSpec;
import com.mesosphere.sdk.specification.ReadinessCheckSpec;
import com.mesosphere.sdk.specification.ResourceSpec;
import com.mesosphere.sdk.specification.ServiceSpec;
import com.mesosphere.sdk.specification.TaskSpec;
import com.mesosphere.sdk.state.ConfigStore;
import com.mesosphere.sdk.state.ConfigStoreException;

import com.google.common.annotations.VisibleForTesting;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.mesos.Protos;
import org.apache.mesos.Protos.TaskInfo;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Various utility methods for manipulating data in {@link TaskInfo}s.
 */

@SuppressWarnings({
    "checkstyle:LineLength",
    "checkstyle:MethodCount",
    "checkstyle:ExecutableStatementCount",
    "checkstyle:ReturnCount",
    "checkstyle:OverloadMethodsDeclarationOrder",
    "checkstyle:MultipleStringLiterals"
})
public final class TaskUtils {

  private static final Logger LOGGER = LoggingUtils.getLogger(TaskUtils.class);

  private TaskUtils() {
    // do not instantiate
  }

  /**
   * Returns the {@link TaskSpec} in the provided {@link com.mesosphere.sdk.specification.DefaultServiceSpec}
   * which matches the provided {@link TaskInfo}, or {@code null} if no match could be found.
   */
  public static Optional<PodSpec> getPodSpec(ServiceSpec serviceSpec, Protos.TaskInfo taskInfo) throws TaskException {
    String podType = new TaskLabelReader(taskInfo).getType();

    for (PodSpec podSpec : serviceSpec.getPods()) {
      if (podSpec.getType().equals(podType)) {
        return Optional.of(podSpec);
      }
    }

    return Optional.empty();
  }

  /**
   * Returns all the Task names for a PodInstance.
   *
   * @param podInstance A PodInstance
   * @return A List of all the task names.
   */
  public static List<String> getTaskNames(PodInstance podInstance) {
    return podInstance.getPod().getTasks().stream()
        .map(taskSpec -> TaskSpec.getInstanceName(podInstance, taskSpec))
        .collect(Collectors.toList());
  }

  /**
   * Returns all the task names for a pod, after filtering based on the passed in list of tasks to launch.
   *
   * @param podInstance   A PodInstance
   * @param tasksToLaunch The names of TaskSpecs which should be launched.
   * @return A list of the appropriate task names.
   */
  public static List<String> getTaskNames(PodInstance podInstance, Collection<String> tasksToLaunch) {
    LOGGER.debug("PodInstance tasks: {}", TaskUtils.getTaskNames(podInstance));
    return podInstance.getPod().getTasks().stream()
        .filter(taskSpec -> tasksToLaunch.contains(taskSpec.getName()))
        .map(taskSpec -> TaskSpec.getInstanceName(podInstance, taskSpec))
        .collect(Collectors.toList());
  }

  /**
   * Returns whether any tasks within the provided {@link ServiceSpec} have transport encryption specs defined.
   */
  public static boolean hasTasksWithTLS(ServiceSpec serviceSpec) {
    List<TaskSpec> tasks = new ArrayList<>();
    serviceSpec.getPods().forEach(pod -> tasks.addAll(pod.getTasks()));

    return tasks.stream().anyMatch(taskSpec -> !taskSpec.getTransportEncryption().isEmpty());
  }

  /**
   * Returns the TaskInfos associated with a PodInstance if its ever been launched.  The list will be empty if the
   * PodInstance has never been launched.
   *
   * @param podInstance A PodInstance to match against in the provided tasks
   * @param tasks       All tasks in the StateStore
   * @return TaskInfos associated with the PodInstance.
   */
  public static Collection<Protos.TaskInfo> getPodTasks(PodInstance podInstance, Collection<Protos.TaskInfo> tasks) {
    return tasks.stream()
        .filter(taskInfo -> areEquivalent(taskInfo, podInstance))
        .collect(Collectors.toList());
  }

  /**
   * Returns whether the provided {@link TaskInfo}, representing a previously-launched task,
   * is in the same provided pod provided in the {@link PodInstance}.
   */
  public static boolean areEquivalent(TaskInfo taskInfo, PodInstance podInstance) {
    try {
      TaskLabelReader reader = new TaskLabelReader(taskInfo);
      return reader.getIndex() == podInstance.getIndex()
          && reader.getType().equals(podInstance.getPod().getType());
    } catch (TaskException e) {
      LOGGER.warn("Unable to extract pod type or index from TaskInfo", e);
      return false;
    }
  }

  /**
   * Determines whether two TaskSpecs are different.
   *
   * @param oldTaskSpec The previous definition of a Task.
   * @param newTaskSpec The new definition of a Task.
   * @return true if the Tasks are different, false otherwise.
   */
  public static boolean areDifferent(TaskSpec oldTaskSpec, TaskSpec newTaskSpec) {

    // Names

    String oldTaskName = oldTaskSpec.getName();
    String newTaskName = newTaskSpec.getName();
    if (!Objects.equals(oldTaskName, newTaskName)) {
      LOGGER.debug("Task names '{}' and '{}' are different.", oldTaskName, newTaskName);
      return true;
    }

    // Goal States

    GoalState oldGoalState = oldTaskSpec.getGoal();
    GoalState newGoalState = newTaskSpec.getGoal();
    if (!Objects.equals(oldGoalState, newGoalState)) {
      LOGGER.debug("Task goals '{}' and '{}' are different.", oldGoalState, newGoalState);
      return true;
    }

    // Resources (custom comparison)

    Map<String, ResourceSpec> oldResourceMap =
        getResourceSpecMap(oldTaskSpec.getResourceSet().getResources());
    Map<String, ResourceSpec> newResourceMap =
        getResourceSpecMap(newTaskSpec.getResourceSet().getResources());

    if (oldResourceMap.size() != newResourceMap.size()) {
      LOGGER.debug("Resource lengths are different for old resources: '{}' and new resources: '{}'",
          oldResourceMap, newResourceMap);
      return true;
    }

    for (Map.Entry<String, ResourceSpec> newEntry : newResourceMap.entrySet()) {
      String resourceName = newEntry.getKey();
      LOGGER.debug("Checking resource difference for: {}", resourceName);
      ResourceSpec oldResourceSpec = oldResourceMap.get(resourceName);
      if (oldResourceSpec == null) {
        LOGGER.debug("Resource not found: {}", resourceName);
        return true;
      } else if (!EqualsBuilder.reflectionEquals(oldResourceSpec, newEntry.getValue())) {
        LOGGER.debug("Resources are different.");
        return true;
      }
    }

    // Volumes (custom comparison)

    if (!volumesEqual(oldTaskSpec, newTaskSpec)) {
      LOGGER.debug("Task volumes '{}' and '{}' are different.",
          oldTaskSpec.getResourceSet().getVolumes(),
          newTaskSpec.getResourceSet().getVolumes());
      return true;
    }

    // Labels

    Map<String, String> oldLabels = oldTaskSpec.getTaskLabels();
    Map<String, String> newLabels = newTaskSpec.getTaskLabels();
    if (!Objects.equals(oldLabels, newLabels)) {
      LOGGER.debug("Task labels '{}' and '{}' are different.", oldLabels, newLabels);
      return true;
    }

    // CommandSpecs

    Optional<CommandSpec> oldCommand = oldTaskSpec.getCommand();
    Optional<CommandSpec> newCommand = newTaskSpec.getCommand();
    if (!Objects.equals(oldCommand, newCommand)) {
      LOGGER.debug("Task commands '{}' and '{}' are different.", oldCommand, newCommand);
      return true;
    }

    // Health checks

    Optional<HealthCheckSpec> oldHealthCheck = oldTaskSpec.getHealthCheck();
    Optional<HealthCheckSpec> newHealthCheck = newTaskSpec.getHealthCheck();
    if (!Objects.equals(oldHealthCheck, newHealthCheck)) {
      LOGGER.debug("Task healthchecks '{}' and '{}' are different.", oldHealthCheck, newHealthCheck);
      return true;
    }

    // Readiness checks

    Optional<ReadinessCheckSpec> oldReadinessCheck = oldTaskSpec.getReadinessCheck();
    Optional<ReadinessCheckSpec> newReadinessCheck = newTaskSpec.getReadinessCheck();
    if (!Objects.equals(oldReadinessCheck, newReadinessCheck)) {
      LOGGER.debug("Task readinesschecks '{}' and '{}' are different.", oldReadinessCheck, newReadinessCheck);
      return true;
    }

    // Config files

    Map<String, ConfigFileSpec> oldConfigMap = getConfigTemplateMap(oldTaskSpec.getConfigFiles());
    Map<String, ConfigFileSpec> newConfigMap = getConfigTemplateMap(newTaskSpec.getConfigFiles());
    if (!Objects.equals(oldConfigMap, newConfigMap)) {
      LOGGER.debug("Config templates '{}' and '{}' are different.", oldConfigMap, newConfigMap);
      return true;
    }

    // DiscoverySpecs

    Optional<DiscoverySpec> oldDiscoverySpec = oldTaskSpec.getDiscovery();
    Optional<DiscoverySpec> newDiscoverySpec = newTaskSpec.getDiscovery();
    if (!Objects.equals(oldDiscoverySpec, newDiscoverySpec)) {
      LOGGER.debug("DiscoverySpecs '{}' and '{}' are different.", oldDiscoverySpec, newDiscoverySpec);
      return true;
    }

    int oldTaskKillGracePeriodSeconds = oldTaskSpec.getTaskKillGracePeriodSeconds();
    int newTaskKillGracePeriodSeconds = newTaskSpec.getTaskKillGracePeriodSeconds();
    if (oldTaskKillGracePeriodSeconds != newTaskKillGracePeriodSeconds) {
      LOGGER.debug("TaskKillGracePeriodSeconds '{}' and '{}' are different.",
          oldTaskKillGracePeriodSeconds, newTaskKillGracePeriodSeconds);
      return true;
    }

    return false;
  }

  /**
   * Utility method for checking if volumes changed between the two provided
   * {@link TaskSpec}s.
   *
   * @return whether the volume lists are equal
   */
  public static boolean volumesEqual(TaskSpec first, TaskSpec second) {
    return CollectionUtils.isEqualCollection(
        first.getResourceSet().getVolumes(),
        second.getResourceSet().getVolumes());
  }

  /**
   * Returns a name=>resourcespecification mapping of the provided list.
   *
   * @throws IllegalArgumentException if multiple resource specifications have matching names
   */
  private static Map<String, ResourceSpec> getResourceSpecMap(
      Collection<ResourceSpec> resourceSpecs) throws IllegalArgumentException
  {
    Map<String, ResourceSpec> resourceMap = new HashMap<>();
    for (ResourceSpec resourceSpec : resourceSpecs) {
      ResourceSpec prevValue = resourceMap.put(resourceSpec.getName(), resourceSpec);
      if (prevValue != null && !prevValue.getName().equals(Constants.PORTS_RESOURCE_TYPE)) {
        throw new IllegalArgumentException(String.format(
            "Non-port resources for a given task may not share the same name. " +
                "name:'%s' oldResource:'%s' newResource:'%s'",
            resourceSpec.getName(), prevValue, resourceSpec));
      }
    }

    return resourceMap;
  }

  /**
   * Returns a name=>template mapping of the provided {@link ConfigFileSpec}s. Checks for unique paths and names
   * across all configs.
   *
   * @throws IllegalArgumentException if multiple config specifications have matching relative path strings
   */
  private static Map<String, ConfigFileSpec> getConfigTemplateMap(Collection<ConfigFileSpec> configSpecs)
      throws IllegalArgumentException
  {
    Set<String> configPaths = new HashSet<>();
    Map<String, ConfigFileSpec> configMap = new HashMap<>();
    for (ConfigFileSpec configSpec : configSpecs) {
      if (!configPaths.add(configSpec.getRelativePath())) {
        throw new IllegalArgumentException(String.format(
            "Config templates for a given task may not share the same path: '%s'",
            configSpec.getRelativePath()));

      }
      ConfigFileSpec prevSpec = configMap.put(configSpec.getName(), configSpec);
      if (prevSpec != null) {
        // A config of this name is already present in the map.
        throw new IllegalArgumentException(String.format(
            "Config templates for a given task may not share the same name: '%s'",
            configSpec.getName()));
      }
    }
    return configMap;
  }

  /**
   * Gets the {@link GoalState} of Task.
   *
   * @param podInstance A PodInstance containing tasks.
   * @param taskName    The name of the Task whose goal state is desired
   * @return The {@link GoalState} of the task.
   * @throws TaskException is thrown when unable to determine a task's {@link GoalState}
   */
  public static GoalState getGoalState(PodInstance podInstance, String taskName) throws TaskException {
    Optional<TaskSpec> taskSpec = getTaskSpec(podInstance, taskName);
    if (taskSpec.isPresent()) {
      return taskSpec.get().getGoal();
    } else {
      throw new TaskException("Failed to determine the goal state of Task: " + taskName);
    }
  }

  public static Optional<TaskSpec> getTaskSpec(ConfigStore<ServiceSpec> configStore, Protos.TaskInfo taskInfo)
      throws TaskException
  {
    return getTaskSpec(getPodInstance(configStore, taskInfo), taskInfo.getName());
  }

  public static Optional<TaskSpec> getTaskSpec(PodInstance podInstance, String taskName) {
    return podInstance.getPod().getTasks().stream()
        .filter(taskSpec -> TaskSpec.getInstanceName(podInstance, taskSpec).equals(taskName))
        .findFirst();
  }

  public static Optional<TaskSpec> getTaskSpec(ServiceSpec serviceSpec, String podType, String taskName) {
    for (PodSpec podSpec : serviceSpec.getPods()) {
      if (!podSpec.getType().equals(podType)) {
        continue;
      }
      for (TaskSpec taskSpec : podSpec.getTasks()) {
        if (taskSpec.getName().equals(taskName)) {
          return Optional.of(taskSpec);
        }
      }
    }
    return Optional.empty();
  }

  /**
   * Given a list of all tasks and failed tasks, returns a list of tasks (via returned
   * {@link PodInstanceRequirement#getTasksToLaunch()}) that should be relaunched.
   *
   * @param failedTasks tasks marked as needing recovery
   * @return list of pods, each with contained named tasks to be relaunched
   */
  public static List<PodInstanceRequirement> getPodRequirements(
      ConfigStore<ServiceSpec> configStore,
      Collection<Protos.TaskInfo> allTaskInfos,
      Collection<Protos.TaskStatus> allTaskStatuses,
      Collection<Protos.TaskInfo> failedTasks)
  {

    // Mapping of pods, to failed tasks within those pods.
    // Arbitrary consistent ordering: by pod instance name (e.g. "otherpodtype-0","podtype-0","podtype-1")
    Map<PodInstance, Collection<TaskSpec>> podsToFailedTasks =
        new TreeMap<>(Comparator.comparing(PodInstance::getName));
    for (Protos.TaskInfo taskInfo : failedTasks) {
      try {
        PodInstance podInstance = getPodInstance(configStore, taskInfo);
        Optional<TaskSpec> taskSpec = getTaskSpec(podInstance, taskInfo.getName());
        if (!taskSpec.isPresent()) {
          LOGGER.error("No TaskSpec found for failed task: {}", taskInfo.getName());
          continue;
        }
        Collection<TaskSpec> failedTaskSpecs = podsToFailedTasks.get(podInstance);
        if (failedTaskSpecs == null) {
          failedTaskSpecs = new ArrayList<>();
          podsToFailedTasks.put(podInstance, failedTaskSpecs);
        }
        failedTaskSpecs.add(taskSpec.get());
      } catch (TaskException e) {
        LOGGER.error(String.format("Failed to get pod instance for task: %s", taskInfo.getName()), e);
      }
    }
    if (podsToFailedTasks.isEmpty()) {
      // short circuit
      return Collections.emptyList();
    }

    // Log failed pod map
    for (Map.Entry<PodInstance, Collection<TaskSpec>> entry : podsToFailedTasks.entrySet()) {
      List<String> taskNames = entry.getValue().stream()
          .map(taskSpec -> taskSpec.getName())
          .collect(Collectors.toList());
      LOGGER.info("Failed pod: {} with tasks: {}", entry.getKey().getName(), taskNames);
    }

    // We treat a task as having "launched" if there exists a TaskStatus for it.
    Set<String> allLaunchedTaskIds = allTaskStatuses.stream()
        .map(status -> status.getTaskId().getValue())
        .collect(Collectors.toSet());

    Set<String> allLaunchedTaskNames = allTaskInfos.stream()
        .filter(taskInfo -> allLaunchedTaskIds.contains(taskInfo.getTaskId().getValue()))
        .map(taskInfo -> taskInfo.getName())
        .collect(Collectors.toSet());

    List<PodInstanceRequirement> podInstanceRequirements = new ArrayList<>();
    for (Map.Entry<PodInstance, Collection<TaskSpec>> entry : podsToFailedTasks.entrySet()) {
      boolean anyFailedTasksAreEssential = entry.getValue().stream().anyMatch(taskSpec -> taskSpec.isEssential());
      Collection<TaskSpec> taskSpecsToLaunch;
      if (anyFailedTasksAreEssential) {
        // One or more of the failed tasks in this pod are marked as 'essential'.
        // Relaunch all applicable tasks in the pod.
        taskSpecsToLaunch = entry.getKey().getPod().getTasks();
      } else {
        // None of the failed tasks in this pod are 'essential'.
        // Only recover the failed task(s), leave others in the pod as-is.
        taskSpecsToLaunch = entry.getValue();
      }

      // Additional filtering:
      // - Only relaunch tasks that aren't eligible for recovery. Those are instead handled by the deploy plan.
      // - Don't relaunch tasks that haven't been launched yet (as indicated by presence in allLaunchedTasks)
      taskSpecsToLaunch = taskSpecsToLaunch.stream()
          .filter(taskSpec -> isEligibleForRecovery(taskSpec) &&
              allLaunchedTaskNames.contains(TaskSpec.getInstanceName(entry.getKey(), taskSpec.getName())))
          .collect(Collectors.toList());

      if (taskSpecsToLaunch.isEmpty()) {
        LOGGER.info("No tasks to recover for pod: {}", entry.getKey().getName());
        continue;
      }

      LOGGER.info("Tasks to relaunch in pod {}: {}", entry.getKey().getName(), taskSpecsToLaunch.stream()
          .map(taskSpec -> String.format(
              "%s=%s", taskSpec.getName(), taskSpec.isEssential() ? "essential" : "nonessential"))
          .collect(Collectors.toList()));
      podInstanceRequirements.add(PodInstanceRequirement.newBuilder(
          entry.getKey(),
          taskSpecsToLaunch.stream()
              .map(taskSpec -> taskSpec.getName())
              .collect(Collectors.toList()))
          .build());
    }

    return podInstanceRequirements;
  }

  public static PodInstance getPodInstance(ConfigStore<ServiceSpec> configStore, Protos.TaskInfo taskInfo)
      throws TaskException
  {
    return getPodInstance(getPodSpec(configStore, taskInfo), taskInfo);
  }

  public static PodInstance getPodInstance(PodSpec podSpec, Protos.TaskInfo taskInfo) throws TaskException {
    return new DefaultPodInstance(podSpec, new TaskLabelReader(taskInfo).getIndex());
  }

  private static PodSpec getPodSpec(ConfigStore<ServiceSpec> configStore, Protos.TaskInfo taskInfo)
      throws TaskException
  {
    UUID configId = new TaskLabelReader(taskInfo).getTargetConfiguration();
    ServiceSpec serviceSpec;

    try {
      serviceSpec = configStore.fetch(configId);
    } catch (ConfigStoreException e) {
      throw new TaskException(String.format(
          "Unable to retrieve ServiceSpecification ID %s referenced by TaskInfo[%s]",
          configId, taskInfo.getName()), e);
    }

    Optional<PodSpec> podSpecOptional = getPodSpec(serviceSpec, taskInfo);
    if (!podSpecOptional.isPresent()) {
      throw new TaskException(String.format(
          "No TaskSpecification found for TaskInfo[%s]", taskInfo.getName()));
    } else {
      return podSpecOptional.get();
    }
  }

  /**
   * Filters and returns all {@link Protos.TaskInfo}s for tasks needing recovery.
   *
   * @return terminated TaskInfos
   */
  public static Collection<Protos.TaskInfo> getTasksNeedingRecovery(
      ConfigStore<ServiceSpec> configStore,
      Collection<Protos.TaskInfo> allTaskInfos,
      Collection<Protos.TaskStatus> allTaskStatuses) throws TaskException
  {

    Map<Protos.TaskID, Protos.TaskStatus> statusMap = new HashMap<>();
    for (Protos.TaskStatus status : allTaskStatuses) {
      statusMap.put(status.getTaskId(), status);
    }

    List<Protos.TaskInfo> results = new ArrayList<>();
    for (Protos.TaskInfo info : allTaskInfos) {
      Protos.TaskStatus status = statusMap.get(info.getTaskId());
      if (status == null) {
        continue;
      }

      Optional<TaskSpec> taskSpec = getTaskSpec(configStore, info);
      if (!taskSpec.isPresent()) {
        throw new TaskException("Failed to determine TaskSpec from TaskInfo: " + info);
      }

      boolean markedPermanentlyFailed = FailureUtils.isPermanentlyFailed(info);
      if (isEligibleForRecovery(taskSpec.get()) && (isRecoveryNeeded(status) || markedPermanentlyFailed)) {
        LOGGER.info("{} needs recovery with state: {}, goal state: {}, marked permanently failed: {}",
            info.getName(), status.getState(), taskSpec.get().getGoal().name(), markedPermanentlyFailed);
        results.add(info);
      }
    }
    return results;
  }

  /**
   * Returns whether the provided {@link TaskSpec} should be managed by the recovery plan if the task has failed.
   * <p>
   * Tasks with a {@code ONCE} or {@code FINISH} goal state are effectively only managed by the deploy plan, and are
   * not the responsibility of the recovery plan. Recovery only applies to tasks that had reached their goal state of
   * {@code RUNNING} and then later failed.
   */
  private static boolean isEligibleForRecovery(TaskSpec taskSpec) {
    switch (taskSpec.getGoal()) {
      case RUNNING:
        return true;
      case ONCE:
      case FINISH:
        // Tasks with a ONCE or FINISH goal state are effectively managed by the deploy plan, and are not the
        // responsibility of the recovery plan. Recovery only applies to tasks that had reached their goal state
        // of RUNNING and then later failed.
        return false;
      case UNKNOWN:
      default:
        throw new IllegalArgumentException(String.format("Unsupported goal state: %s", taskSpec.getGoal()));
    }
  }

  /**
   * Returns whether the provided {@link TaskStatus} shows that the task needs to recover.
   * <p>
   * This assumes that the task is not supposed to be {@code FINISHED}.
   */
  @VisibleForTesting
  static boolean isRecoveryNeeded(Protos.TaskStatus taskStatus) {
    // Note that we include FINISHED as "needs recovery", because we assume the task is supposed to be RUNNING.
    if (isTerminal(taskStatus)) {
      return true;
    }
    // Non-terminal cases which need recovery:
    switch (taskStatus.getState()) {
      case TASK_LOST:
      case TASK_UNREACHABLE:
        return true;
      default:
        return false;
    }
  }

  /**
   * Marks the TaskInfo for replacement if the agent has been decomissioned.
   */
  public static Collection<Protos.TaskInfo> getTasksForReplacement(
      Collection<Protos.TaskStatus> alltaskStatuses,
      Collection<Protos.TaskInfo> allTaskInfos)
  {
    Map<Protos.TaskID, Protos.TaskInfo> infoMap = new HashMap<>();
    for (Protos.TaskInfo taskInfo : allTaskInfos) {
      infoMap.put(taskInfo.getTaskId(), taskInfo);
    }

    List<Protos.TaskInfo> tasksNeedingReplace = new ArrayList<>();
    for (Protos.TaskStatus taskStatus: alltaskStatuses) {
      if (taskStatus.getState().equals(Protos.TaskState.TASK_GONE_BY_OPERATOR) &&
          !FailureUtils.isPermanentlyFailed(infoMap.get(taskStatus.getTaskId())))
      {
        LOGGER.info("{} needs replacement with state: {}",
            infoMap.get(taskStatus.getTaskId()).getName(),
            taskStatus.getState());
        tasksNeedingReplace.add(infoMap.get(taskStatus.getTaskId()));
      }
    }
    return tasksNeedingReplace;
  }

  /**
   * Returns whether the provided {@link TaskStatus} has reached a terminal state.
   */
  public static boolean isTerminal(Protos.TaskStatus taskStatus) {
    switch (taskStatus.getState()) {
      case TASK_DROPPED:
      case TASK_ERROR:
      case TASK_FAILED:
      case TASK_FINISHED:
      case TASK_GONE:
      case TASK_KILLED:
        //an agent marked as gone should never come back therefore this is terminal
        return true;
      case TASK_GONE_BY_OPERATOR:
        // mesos.proto: "might return to RUNNING in the future"
      case TASK_KILLING:
      case TASK_LOST:
      case TASK_RUNNING:
      case TASK_STAGING:
      case TASK_STARTING:
      case TASK_UNKNOWN:
        // mesos.proto: "may or may not still be running"
      case TASK_UNREACHABLE:
        break;
      default:
        return false;
    }

    return false;
  }

  /**
   * Returns a default name for a {@link Step} given a PodInstance and the tasks to be launched in it.
   *
   * @param podInstance   The PodInstance to be launched by a {@link Step}.
   * @param tasksToLaunch The tasks to be launched in the Pod.
   * @return The {@link Step} name
   */
  public static String getStepName(PodInstance podInstance, Collection<String> tasksToLaunch) {
    return podInstance.getName() + ":" + tasksToLaunch;
  }

  /**
   * Determines if a task is launched in any zones.
   *
   * @param taskInfo The {@link TaskInfo} to get zone information from.
   * @return A boolean indicating whether the task is in a zone.
   */
  public static boolean taskHasZone(Protos.TaskInfo taskInfo) {
    return taskInfo.getCommand().getEnvironment().getVariablesList().stream()
        .anyMatch(variable -> variable.getName().equals(EnvConstants.ZONE_TASKENV));
  }

  /**
   * Gets the zone of a task.
   *
   * @param taskInfo The {@link TaskInfo} to get zone information from.
   * @return A string indicating the zone the task is in.
   */
  public static String getTaskZone(Protos.TaskInfo taskInfo) {
    return taskInfo.getCommand().getEnvironment().getVariablesList().stream()
        .filter(variable -> variable.getName().equals(EnvConstants.ZONE_TASKENV)).findFirst().get().getValue();
  }

  /**
   * Gets the IP address associated with the task.
   *
   * @param taskStatus the {@link TaskStatus} to get the IP address from.
   * @return A String indicating the IP address associated with the task.
   */
  public static String getTaskIPAddress(Protos.TaskStatus taskStatus) throws IllegalStateException {
    List<Protos.NetworkInfo> networkInfo = taskStatus.getContainerStatus().getNetworkInfosList();
    if (networkInfo.isEmpty()) {
      throw new IllegalStateException(
          String.format("No network info can be found for the task info: %s", taskStatus.toString()));
    }
    return networkInfo.stream().findFirst().get().getIpAddresses(0).getIpAddress();
  }
}
