package com.mesosphere.sdk.http.queries;

import com.mesosphere.sdk.framework.TaskKiller;
import com.mesosphere.sdk.http.RequestUtils;
import com.mesosphere.sdk.http.ResponseUtils;
import com.mesosphere.sdk.http.types.GroupedTasks;
import com.mesosphere.sdk.http.types.TaskInfoAndStatus;
import com.mesosphere.sdk.offer.LoggingUtils;
import com.mesosphere.sdk.offer.TaskException;
import com.mesosphere.sdk.offer.TaskUtils;
import com.mesosphere.sdk.offer.taskdata.TaskLabelReader;
import com.mesosphere.sdk.scheduler.recovery.FailureUtils;
import com.mesosphere.sdk.scheduler.recovery.RecoveryType;
import com.mesosphere.sdk.specification.PodInstance;
import com.mesosphere.sdk.specification.ServiceSpec;
import com.mesosphere.sdk.state.ConfigStore;
import com.mesosphere.sdk.state.GoalStateOverride;
import com.mesosphere.sdk.state.StateStore;

import com.google.common.annotations.VisibleForTesting;
import org.apache.mesos.Protos;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;

import javax.ws.rs.core.Response;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

import static com.mesosphere.sdk.http.ResponseUtils.jsonOkResponse;
import static com.mesosphere.sdk.http.ResponseUtils.jsonResponseBean;

/**
 * A read-only API for accessing information about the pods which compose the service, and restarting/replacing those
 * pods.
 */
public class PodQueries {

  private static final Logger LOGGER = LoggingUtils.getLogger(PodQueries.class);

  private static final FailureSetter DEFAULT_FAILURE_SETTER = new FailureSetter();

  /**
   * Pod 'name' to use in responses for tasks which have no pod information.
   */
  private static final String UNKNOWN_POD_LABEL = "UNKNOWN_POD";

  private PodQueries() {
    // do not instantiate
  }

  /**
   * Produces a listing of all pod instance names.
   */
  public static Response list(StateStore stateStore) {
    try {
      Set<String> podNames = new TreeSet<>();
      List<String> unknownTaskNames = new ArrayList<>();
      for (Protos.TaskInfo taskInfo : stateStore.fetchTasks()) {
        TaskLabelReader labels = new TaskLabelReader(taskInfo);
        try {
          podNames.add(PodInstance.getName(labels.getType(), labels.getIndex()));
        } catch (Exception e) {
          LOGGER.warn(String.format("Failed to extract pod information from task %s", taskInfo.getName()), e);
          unknownTaskNames.add(taskInfo.getName());
        }
      }

      JSONArray jsonArray = new JSONArray(podNames);

      if (!unknownTaskNames.isEmpty()) {
        Collections.sort(unknownTaskNames);
        for (String unknownName : unknownTaskNames) {
          jsonArray.put(String.format("%s_%s", UNKNOWN_POD_LABEL, unknownName));
        }
      }
      return jsonOkResponse(jsonArray);
    } catch (Exception e) {
      LOGGER.error("Failed to fetch list of pods", e);
      return Response.serverError().build();
    }
  }

  /**
   * Produces the summary statuses of all pod instances.
   */
  public static Response getStatuses(StateStore stateStore, String serviceName) {
    try {
      // Group the tasks by pod:
      GroupedTasks groupedTasks = GroupedTasks.create(stateStore);

      // Output statuses for all tasks in each pod:
      JSONObject responseJson = new JSONObject();
      responseJson.put("service", serviceName);
      for (Map.Entry<String, Map<Integer, List<TaskInfoAndStatus>>> podType
          : groupedTasks.byPodTypeAndIndex.entrySet()) {
        JSONObject podJson = new JSONObject();
        podJson.put("name", podType.getKey());
        for (Map.Entry<Integer, List<TaskInfoAndStatus>> podInstance : podType.getValue().entrySet()) {
          podJson.append("instances", getPodInstanceStatusJson(
              stateStore,
              PodInstance.getName(podType.getKey(), podInstance.getKey()),
              podInstance.getValue()));
        }
        responseJson.append("pods", podJson);
      }

      // Output an 'unknown pod' instance for any tasks which didn't have a resolvable pod:
      if (!groupedTasks.unknownPod.isEmpty()) {
        JSONObject podTypeJson = new JSONObject();
        podTypeJson.put("name", UNKNOWN_POD_LABEL);
        podTypeJson.append("instances", getPodInstanceStatusJson(
            stateStore,
            PodInstance.getName(UNKNOWN_POD_LABEL, 0),
            groupedTasks.unknownPod));
        responseJson.append("pods", podTypeJson);
      }

      return jsonOkResponse(responseJson);
    } catch (Exception e) {
      LOGGER.error("Failed to fetch collated list of task statuses by pod", e);
      return Response.serverError().build();
    }
  }

  /**
   * Produces the summary status of a single pod instance.
   */
  public static Response getStatus(StateStore stateStore, String podInstanceName) {
    try {
      Optional<Collection<TaskInfoAndStatus>> podTasks =
          GroupedTasks.create(stateStore).getPodInstanceTasks(podInstanceName);
      if (!podTasks.isPresent()) {
        return podNotFoundResponse(podInstanceName);
      }
      return jsonOkResponse(getPodInstanceStatusJson(stateStore, podInstanceName, podTasks.get()));
    } catch (Exception e) {
      LOGGER.error(String.format("Failed to fetch status for pod '%s'", podInstanceName), e);
      return Response.serverError().build();
    }
  }

  /**
   * Produces the full information for a single pod instance.
   */
  public static Response getInfo(StateStore stateStore, String podInstanceName) {
    try {
      Optional<Collection<TaskInfoAndStatus>> podTasks =
          GroupedTasks.create(stateStore).getPodInstanceTasks(podInstanceName);
      if (!podTasks.isPresent()) {
        return podNotFoundResponse(podInstanceName);
      }
      return jsonResponseBean(podTasks.get(), Response.Status.OK);
    } catch (Exception e) {
      LOGGER.error(String.format("Failed to fetch info for pod '%s'", podInstanceName), e);
      return Response.serverError().build();
    }
  }

  /**
   * Restarts a pod in a "paused" debug mode.
   */
  public static Response pause(StateStore stateStore, String podName, String bodyPayload) {
    Set<String> taskFilter;
    try {
      taskFilter = new HashSet<>(RequestUtils.parseJsonList(bodyPayload));
    } catch (JSONException e) {
      LOGGER.error(String.format("Failed to parse task filter '%s'", bodyPayload), e);
      return Response.status(Response.Status.BAD_REQUEST).build();
    }
    try {
      return overrideGoalState(stateStore, podName, taskFilter, GoalStateOverride.PAUSED);
    } catch (Exception e) {
      LOGGER.error(String.format("Failed to pause pod '%s' with task filter '%s'", podName, taskFilter), e);
      return Response.serverError().build();
    }
  }

  /**
   * Restarts a pod in a normal state following a prior "pause" command.
   */
  public static Response resume(StateStore stateStore, String podName, String bodyPayload) {
    Set<String> taskFilter;
    try {
      taskFilter = new HashSet<>(RequestUtils.parseJsonList(bodyPayload));
    } catch (JSONException e) {
      LOGGER.error(String.format("Failed to parse task filter '%s'", bodyPayload), e);
      return Response.status(Response.Status.BAD_REQUEST).build();
    }
    try {
      return overrideGoalState(stateStore, podName, taskFilter, GoalStateOverride.NONE);
    } catch (Exception e) {
      LOGGER.error(String.format("Failed to resume pod '%s' with task filter '%s'", podName, taskFilter), e);
      return Response.serverError().build();
    }
  }

  private static Response overrideGoalState(
      StateStore stateStore, String podInstanceName, Set<String> taskNameFilter, GoalStateOverride override)
  {
    Optional<Collection<TaskInfoAndStatus>> allPodTasks =
        GroupedTasks.create(stateStore).getPodInstanceTasks(podInstanceName);
    if (!allPodTasks.isPresent()) {
      return podNotFoundResponse(podInstanceName);
    }
    Collection<TaskInfoAndStatus> podTasks =
        RequestUtils.filterPodTasks(podInstanceName, allPodTasks.get(), taskNameFilter);
    if (podTasks.isEmpty() || podTasks.size() < taskNameFilter.size()) {
      // one or more requested tasks were not found.
      LOGGER.error("Request had task filter: {} but pod '{}' tasks are: {} (matching: {})",
          taskNameFilter,
          podInstanceName,
          allPodTasks.get().stream().map(t -> t.getInfo().getName()).collect(Collectors.toList()),
          podTasks.stream().map(t -> t.getInfo().getName()).collect(Collectors.toList()));
      return podNotFoundResponse(podInstanceName);
    }

    // invoke the restart request itself against ALL tasks. this ensures that they're ALL flagged as failed via
    // FailureUtils, which is then checked by DefaultRecoveryPlanManager.
    LOGGER.info("Performing {} goal state override of {} tasks in pod {}:",
        override, podTasks.size(), podInstanceName);

    // First pass: Store the desired override for each task
    GoalStateOverride.Status pendingStatus = override.newStatus(GoalStateOverride.Progress.PENDING);
    for (TaskInfoAndStatus taskToOverride : podTasks) {
      stateStore.storeGoalOverrideStatus(taskToOverride.getInfo().getName(), pendingStatus);
    }

    // Second pass: Restart the tasks. They will be updated to IN_PROGRESS once we receive a terminal TaskStatus.
    return killTasks(podInstanceName, podTasks);
  }

  /**
   * Restarts a pod instance in-place.
   */
  public static Response restart(StateStore stateStore, ConfigStore<ServiceSpec> configStore, String podInstanceName,
                                 RecoveryType recoveryType)
  {
    try {
      return restartPod(stateStore, configStore, podInstanceName, recoveryType, DEFAULT_FAILURE_SETTER);
    } catch (Exception e) {
      LOGGER.error(String.format("Failed to %s pod '%s'",
          recoveryType == RecoveryType.PERMANENT ? "replace" : "restart", podInstanceName), e);
      return Response.serverError().build();
    }
  }

  @VisibleForTesting
  static Response restartPod(
      StateStore stateStore,
      ConfigStore<ServiceSpec> configStore,
      String podInstanceName,
      RecoveryType recoveryType,
      FailureSetter failureSetter)
  {
    // look up all tasks in the provided pod name:
    Optional<Collection<TaskInfoAndStatus>> podTasks =
        GroupedTasks.create(stateStore).getPodInstanceTasks(podInstanceName);
    if (!podTasks.isPresent() || podTasks.get().isEmpty()) { // shouldn't ever be empty, but just in case
      return podNotFoundResponse(podInstanceName);
    }

    // invoke the restart request itself against ALL tasks. this ensures that they're ALL flagged as failed via
    // FailureUtils, which is then checked by DefaultRecoveryPlanManager.
    LOGGER.info("Performing {} of pod {} by killing {} tasks:",
        recoveryType == RecoveryType.PERMANENT ? "replace" : "restart", podInstanceName, podTasks.get().size());

    if (recoveryType.equals(RecoveryType.PERMANENT)) {
      Collection<Protos.TaskInfo> taskInfos = podTasks.get().stream()
          .map(taskInfoAndStatus -> taskInfoAndStatus.getInfo())
          .collect(Collectors.toList());
      failureSetter.setFailure(configStore, stateStore, taskInfos);
    }

    return killTasks(podInstanceName, podTasks.get());
  }

  /**
   * Broken out into a separate callback to simplify unit testing.
   */
  @VisibleForTesting
  static class FailureSetter {
    /**
     * Default behavior: Set permanently failed bit in state store tasks.
     */
    public void setFailure(
        ConfigStore<ServiceSpec> configStore, StateStore stateStore, Collection<Protos.TaskInfo> taskInfos)
    {
      getPods(configStore, taskInfos)
          .forEach(podInstance -> FailureUtils.setPermanentlyFailed(stateStore, podInstance));
    }
  }

  private static Set<PodInstance> getPods(
      ConfigStore<ServiceSpec> configStore, Collection<Protos.TaskInfo> taskInfos)
  {
    Set<PodInstance> podInstances = new HashSet<>();
    for (Protos.TaskInfo taskInfo : taskInfos) {
      if (taskInfo.getTaskId().getValue().isEmpty()) {
        // Skip marking 'stub' tasks which haven't been launched as permanently failed:
        LOGGER.info("Not marking task {} as failed due to empty taskId", taskInfo.getName());
        continue;
      }
      try {
        podInstances.add(TaskUtils.getPodInstance(configStore, taskInfo));
      } catch (TaskException e) {
        LOGGER.error(String.format("Failed to get pod for task %s", taskInfo.getTaskId().getValue()), e);
      }
    }

    return podInstances;
  }

  private static Response killTasks(String podName, Collection<TaskInfoAndStatus> tasksToKill) {
    for (TaskInfoAndStatus taskToKill : tasksToKill) {
      final Protos.TaskInfo taskInfo = taskToKill.getInfo();
      if (taskToKill.hasStatus()) {
        LOGGER.info("  {} ({}): currently has status {}",
            taskInfo.getName(),
            taskInfo.getTaskId().getValue(),
            taskToKill.getStatus().get().getState());
      } else {
        LOGGER.info("  {} ({}): no status available",
            taskInfo.getName(),
            taskInfo.getTaskId().getValue());
      }
      TaskKiller.killTask(taskInfo.getTaskId());
    }

    JSONObject json = new JSONObject();
    json.put("pod", podName);
    json.put("tasks", tasksToKill.stream().map(t -> t.getInfo().getName()).collect(Collectors.toList()));
    return jsonOkResponse(json);
  }

  /**
   * Returns a JSON object describing a pod instance and its tasks of the form:
   * <code>{
   * "name": "pod-0",
   * "tasks": [ {
   * "id": "pod-0-server",
   * "name": "server",
   * "status": "RUNNING"
   * }, ... ]
   * }</code>
   */
  private static JSONObject getPodInstanceStatusJson(
      StateStore stateStore, String podInstanceName, Collection<TaskInfoAndStatus> tasks)
  {
    JSONObject jsonPod = new JSONObject();
    jsonPod.put("name", podInstanceName);
    for (TaskInfoAndStatus task : tasks) {
      JSONObject jsonTask = new JSONObject();
      jsonTask.put("id", task.getInfo().getTaskId().getValue());
      jsonTask.put("name", task.getInfo().getName());
      Optional<String> stateString = getTaskStateString(stateStore, task.getInfo().getName(), task.getStatus());
      if (stateString.isPresent()) {
        jsonTask.put("status", stateString.get());
      }
      jsonPod.append("tasks", jsonTask);
    }
    return jsonPod;
  }

  private static Optional<String> getTaskStateString(
      StateStore stateStore, String taskName, Optional<Protos.TaskStatus> mesosStatus)
  {
    GoalStateOverride.Status overrideStatus = stateStore.fetchGoalOverrideStatus(taskName);
    if (!mesosStatus.isPresent()) {
      // This task has never been prepared -- even if its goal state is overridden, it doesn't have a run state.
      return Optional.empty();
    } else if (!GoalStateOverride.Status.INACTIVE.equals(overrideStatus)) {
      // This task is affected by an override. Use the override status as applicable.
      switch (overrideStatus.progress) {
        case COMPLETE:
          return Optional.of(overrideStatus.target.getSerializedName());
        case IN_PROGRESS:
        case PENDING:
          return Optional.of(overrideStatus.target.getTransitioningName());
        default:
          LOGGER.error("Unsupported progress state: {}", overrideStatus.progress);
          return Optional.empty();
      }
    }

    String stateString = mesosStatus.get().getState().toString();
    if (stateString.startsWith("TASK_")) { // should always be the case
      // Trim "TASK_" prefix ("TASK_RUNNING" => "RUNNING"):
      stateString = stateString.substring("TASK_".length());
    }
    return Optional.of(stateString);
  }

  private static Response podNotFoundResponse(String podInstanceName) {
    return ResponseUtils.notFoundResponse("Pod " + podInstanceName);
  }
}
