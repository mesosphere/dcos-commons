package com.mesosphere.sdk.api;

import com.mesosphere.sdk.api.types.GroupedTasks;
import com.mesosphere.sdk.api.types.PrettyJsonResource;
import com.mesosphere.sdk.api.types.TaskInfoAndStatus;
import com.mesosphere.sdk.offer.taskdata.TaskLabelReader;
import com.mesosphere.sdk.scheduler.TaskKiller;
import com.mesosphere.sdk.scheduler.recovery.RecoveryType;
import com.mesosphere.sdk.scheduler.recovery.TaskFailureListener;
import com.mesosphere.sdk.specification.PodInstance;
import com.mesosphere.sdk.state.GoalStateOverride;
import com.mesosphere.sdk.state.StateStore;
import org.apache.mesos.Protos;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.core.Response;
import java.util.*;
import java.util.stream.Collectors;

import static com.mesosphere.sdk.api.ResponseUtils.jsonOkResponse;
import static com.mesosphere.sdk.api.ResponseUtils.jsonResponseBean;

/**
 * A read-only API for accessing information about how to connect to the service.
 */
@Path("/v1/pod")
public class PodResource extends PrettyJsonResource {
    private static final Logger LOGGER = LoggerFactory.getLogger(PodResource.class);

    /**
     * Pod 'name' to use in responses for tasks which have no pod information.
     */
    private static final String UNKNOWN_POD_LABEL = "UNKNOWN_POD";

    private final StateStore stateStore;
    private final String serviceName;
    private final TaskFailureListener taskFailureListener;

    private TaskKiller taskKiller;

    /**
     * Creates a new instance which retrieves task/pod state from the provided {@link StateStore}.
     */
    public PodResource(
            StateStore stateStore,
            String serviceName,
            TaskFailureListener taskFailureListener) {
        this.stateStore = stateStore;
        this.serviceName = serviceName;
        this.taskFailureListener = taskFailureListener;
    }

    /**
     * Produces a listing of all pod instance names.
     */
    @GET
    public Response getPods() {
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
    @Path("/status")
    @GET
    public Response getPodStatuses() {
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
    @Path("/{name}/status")
    @GET
    public Response getPodStatus(@PathParam("name") String podInstanceName) {
        try {
            Optional<Collection<TaskInfoAndStatus>> podTasks =
                    GroupedTasks.create(stateStore).getPodInstanceTasks(podInstanceName);
            if (!podTasks.isPresent()) {
                return ResponseUtils.elementNotFoundResponse();
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
    @Path("/{name}/info")
    @GET
    public Response getPodInfo(@PathParam("name") String podInstanceName) {
        try {
            Optional<Collection<TaskInfoAndStatus>> podTasks =
                    GroupedTasks.create(stateStore).getPodInstanceTasks(podInstanceName);
            if (!podTasks.isPresent()) {
                return ResponseUtils.elementNotFoundResponse();
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
    @Path("/{name}/pause")
    @POST
    public Response pausePod(@PathParam("name") String podName, String bodyPayload) {
        Set<String> taskFilter;
        try {
            taskFilter = new HashSet<>(RequestUtils.parseJsonList(bodyPayload));
        } catch (JSONException e) {
            LOGGER.error(String.format("Failed to parse task filter '%s'", bodyPayload), e);
            return Response.status(Response.Status.BAD_REQUEST).build();
        }
        try {
            return overrideGoalState(podName, taskFilter, GoalStateOverride.PAUSED);
        } catch (Exception e) {
            LOGGER.error(String.format("Failed to pause pod '%s' with task filter '%s'", podName, taskFilter), e);
            return Response.serverError().build();
        }
    }

    /**
     * Restarts a pod in a normal state following a prior "pause" command.
     */
    @Path("/{name}/resume")
    @POST
    public Response resumePod(@PathParam("name") String podName, String bodyPayload) {
        Set<String> taskFilter;
        try {
            taskFilter = new HashSet<>(RequestUtils.parseJsonList(bodyPayload));
        } catch (JSONException e) {
            LOGGER.error(String.format("Failed to parse task filter '%s'", bodyPayload), e);
            return Response.status(Response.Status.BAD_REQUEST).build();
        }
        try {
            return overrideGoalState(podName, taskFilter, GoalStateOverride.NONE);
        } catch (Exception e) {
            LOGGER.error(String.format("Failed to resume pod '%s' with task filter '%s'", podName, taskFilter), e);
            return Response.serverError().build();
        }
    }

    private Response overrideGoalState(String podInstanceName, Set<String> taskNameFilter, GoalStateOverride override) {
        Optional<Collection<TaskInfoAndStatus>> allPodTasks =
                GroupedTasks.create(stateStore).getPodInstanceTasks(podInstanceName);
        if (!allPodTasks.isPresent()) {
            return ResponseUtils.elementNotFoundResponse();
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
            return ResponseUtils.elementNotFoundResponse();
        }

        // invoke the restart request itself against ALL tasks. this ensures that they're ALL flagged as failed via
        // FailureUtils, which is then checked by DefaultRecoveryPlanManager.
        LOGGER.info("Performing {} goal state override of {} tasks in pod {}:",
                override, podTasks.size(), podInstanceName);
        if (taskKiller == null) {
            LOGGER.error("Task killer wasn't initialized yet (scheduler started recently?), exiting early.");
            return Response.status(Response.Status.SERVICE_UNAVAILABLE).build();
        }

        // First pass: Store the desired override for each task
        GoalStateOverride.Status pendingStatus = override.newStatus(GoalStateOverride.Progress.PENDING);
        for (TaskInfoAndStatus taskToOverride : podTasks) {
            stateStore.storeGoalOverrideStatus(taskToOverride.getInfo().getName(), pendingStatus);
        }

        // Second pass: Restart the tasks. They will be updated to IN_PROGRESS once we receive a terminal TaskStatus.
        return killTasks(taskKiller, podInstanceName, podTasks, RecoveryType.TRANSIENT);
    }

    /**
     * Restarts a pod instance in-place.
     */
    @Path("/{name}/restart")
    @POST
    public Response restartPod(@PathParam("name") String name) {
        try {
            return restartPod(name, RecoveryType.TRANSIENT);
        } catch (Exception e) {
            LOGGER.error(String.format("Failed to restart pod '%s'", name), e);
            return Response.serverError().build();
        }
    }

    /**
     * Replaces a pod instance with a new instance on a different agent.
     */
    @Path("/{name}/replace")
    @POST
    public Response replacePod(@PathParam("name") String name) {
        try {
            return restartPod(name, RecoveryType.PERMANENT);
        } catch (Exception e) {
            LOGGER.error(String.format("Failed to replace pod '%s'", name), e);
            return Response.serverError().build();
        }
    }

    private Response restartPod(String podInstanceName, RecoveryType recoveryType) {
        // look up all tasks in the provided pod name:
        Optional<Collection<TaskInfoAndStatus>> podTasks =
                GroupedTasks.create(stateStore).getPodInstanceTasks(podInstanceName);
        if (!podTasks.isPresent() || podTasks.get().isEmpty()) { // shouldn't ever be empty, but just in case
            return ResponseUtils.elementNotFoundResponse();
        }

        // invoke the restart request itself against ALL tasks. this ensures that they're ALL flagged as failed via
        // FailureUtils, which is then checked by DefaultRecoveryPlanManager.
        LOGGER.info("Performing {} restart of pod {} by killing {} tasks:",
                recoveryType, podInstanceName, podTasks.get().size());

        if (taskKiller == null) {
            LOGGER.error("Task killer wasn't initialized yet (scheduler started recently?), exiting early.");
            return Response.status(Response.Status.SERVICE_UNAVAILABLE).build();
        }

        if (recoveryType.equals(RecoveryType.PERMANENT)) {
            Collection<Protos.TaskID> taskIds = podTasks.get().stream()
                    .map(taskInfoAndStatus -> taskInfoAndStatus.getInfo().getTaskId())
                    .collect(Collectors.toList());
            taskFailureListener.tasksFailed(taskIds);
        }

        return killTasks(taskKiller, podInstanceName, podTasks.get(), recoveryType);
    }

    private static Response killTasks(
            TaskKiller taskKiller,
            String podName,
            Collection<TaskInfoAndStatus> tasksToKill,
            RecoveryType recoveryType) {
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
            taskKiller.killTask(taskInfo.getTaskId());
        }

        JSONObject json = new JSONObject();
        json.put("pod", podName);
        json.put("tasks", tasksToKill.stream().map(t -> t.getInfo().getName()).collect(Collectors.toList()));
        return jsonOkResponse(json);
    }

    /**
     * Returns a JSON object describing a pod instance and its tasks of the form:
     * <code>{
     *   "name": "pod-0",
     *   "tasks": [ {
     *     "id": "pod-0-server",
     *     "name": "server",
     *     "status": "RUNNING"
     *   }, ... ]
     * }</code>
     */
    private static JSONObject getPodInstanceStatusJson(
            StateStore stateStore, String podInstanceName, Collection<TaskInfoAndStatus> tasks) {
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
            StateStore stateStore, String taskName, Optional<Protos.TaskStatus> mesosStatus) {
        GoalStateOverride.Status overrideStatus = stateStore.fetchGoalOverrideStatus(taskName);
        if (!GoalStateOverride.Status.INACTIVE.equals(overrideStatus)) {
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
        if (!mesosStatus.isPresent()) {
            return Optional.empty();
        }
        String stateString = mesosStatus.get().getState().toString();
        if (stateString.startsWith("TASK_")) { // should always be the case
            // Trim "TASK_" prefix ("TASK_RUNNING" => "RUNNING"):
            stateString = stateString.substring("TASK_".length());
        }
        return Optional.of(stateString);
    }

    /**
     * Configures the {@link TaskKiller} instance to be invoked when restart/replace operations are invoked.
     */
    public void setTaskKiller(TaskKiller taskKiller) {
        this.taskKiller = taskKiller;
    }
}
