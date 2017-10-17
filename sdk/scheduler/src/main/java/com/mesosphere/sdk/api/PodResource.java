package com.mesosphere.sdk.api;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

import javax.inject.Singleton;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.core.Response;

import com.mesosphere.sdk.api.types.PrettyJsonResource;
import com.mesosphere.sdk.api.types.TaskInfoAndStatus;
import com.mesosphere.sdk.api.types.GroupedTasks;
import com.mesosphere.sdk.scheduler.TaskKiller;
import com.mesosphere.sdk.scheduler.recovery.RecoveryType;
import com.mesosphere.sdk.state.GoalStateOverride;
import com.mesosphere.sdk.state.StateStore;

import org.apache.mesos.Protos.TaskInfo;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.mesosphere.sdk.api.ResponseUtils.jsonOkResponse;
import static com.mesosphere.sdk.api.ResponseUtils.jsonResponseBean;

/**
 * A read-only API for accessing information about how to connect to the service.
 */
@Singleton
@Path("/v1/pod")
public class PodResource extends PrettyJsonResource {
    private static final Logger LOGGER = LoggerFactory.getLogger(PodResource.class);

    /**
     * Pod 'name' to use in responses for tasks which have no pod information.
     */
    private static final String UNKNOWN_POD_LABEL = "UNKNOWN_POD";

    private final StateStore stateStore;

    private TaskKiller taskKiller;

    /**
     * Creates a new instance which retrieves task/pod state from the provided {@link StateStore}.
     */
    public PodResource(StateStore stateStore) {
        this.stateStore = stateStore;
    }

    /**
     * Configures the {@link TaskKiller} instance to be invoked when restart/replace operations are invoked.
     */
    public void setTaskKiller(TaskKiller taskKiller) {
        this.taskKiller = taskKiller;
    }

    /**
     * Produces a listing of all pod instance names.
     */
    @GET
    public Response getPods() {
        try {
            GroupedTasks groupedTasks = GroupedTasks.create(stateStore);

            Set<String> podNames = new TreeSet<>();
            podNames.addAll(groupedTasks.byPod.keySet());
            JSONArray jsonArray = new JSONArray(podNames);

            List<String> unknownTaskNames = groupedTasks.unknownPod.stream()
                    .map(t -> t.getInfo().getName())
                    .collect(Collectors.toList());
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
            JSONObject json = new JSONObject();
            for (Map.Entry<String, List<TaskInfoAndStatus>> podTasks : groupedTasks.byPod.entrySet()) {
                json.put(podTasks.getKey(), getStatusesJson(podTasks.getValue()));
            }

            // Output 'unknown pod' for any tasks which didn't have a resolvable pod:
            if (!groupedTasks.unknownPod.isEmpty()) {
                json.put(UNKNOWN_POD_LABEL, getStatusesJson(groupedTasks.unknownPod));
            }

            return jsonOkResponse(json);
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
    public Response getPodStatus(@PathParam("name") String name) {
        try {
            List<TaskInfoAndStatus> podTasks = GroupedTasks.create(stateStore).byPod.get(name);
            if (podTasks == null) {
                return ResponseUtils.elementNotFoundResponse();
            }
            return jsonOkResponse(getStatusesJson(podTasks));
        } catch (Exception e) {
            LOGGER.error(String.format("Failed to fetch status for pod '%s'", name), e);
            return Response.serverError().build();
        }
    }

    /**
     * Produces the full information for a single pod instance.
     */
    @Path("/{name}/info")
    @GET
    public Response getPodInfo(@PathParam("name") String name) {
        try {
            List<TaskInfoAndStatus> podTasks = GroupedTasks.create(stateStore).byPod.get(name);
            if (podTasks == null) {
                return ResponseUtils.elementNotFoundResponse();
            }
            return jsonResponseBean(podTasks, Response.Status.OK);
        } catch (Exception e) {
            LOGGER.error(String.format("Failed to fetch info for pod '%s'", name), e);
            return Response.serverError().build();
        }
    }

    /**
     * Restarts a pod in a "stopped" debug mode.
     */
    @Path("/{name}/stop")
    @POST
    public Response stopPod(@PathParam("name") String podName, String bodyPayload) {
        Set<String> taskFilter;
        try {
            taskFilter = new HashSet<>(RequestUtils.parseJsonList(bodyPayload));
        } catch (JSONException e) {
            LOGGER.error(String.format("Failed to parse task filter '%s'", bodyPayload), e);
            return Response.status(Response.Status.BAD_REQUEST).build();
        }
        try {
            return overrideGoalState(podName, taskFilter, GoalStateOverride.STOPPED);
        } catch (Exception e) {
            LOGGER.error(String.format("Failed to stop pod '%s' with task filter '%s'", podName, taskFilter), e);
            return Response.serverError().build();
        }
    }

    /**
     * Restarts a pod in a normal state following a prior "stop" command.
     */
    @Path("/{name}/start")
    @POST
    public Response startPod(@PathParam("name") String podName, String bodyPayload) {
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
            LOGGER.error(String.format("Failed to start pod '%s' with task filter '%s'", podName, taskFilter), e);
            return Response.serverError().build();
        }
    }

    private Response overrideGoalState(String podName, Set<String> taskNameFilter, GoalStateOverride override) {
        List<TaskInfoAndStatus> allPodTasks = GroupedTasks.create(stateStore).byPod.get(podName);
        if (allPodTasks == null) {
            return ResponseUtils.elementNotFoundResponse();
        }
        List<TaskInfoAndStatus> podTasks = RequestUtils.filterPodTasks(podName, allPodTasks, taskNameFilter);
        if (podTasks.isEmpty() || podTasks.size() < taskNameFilter.size()) {
            // one or more requested tasks were not found.
            LOGGER.error("Request had task filter: {} but pod '{}' tasks are: {} (matching: {})",
                    taskNameFilter,
                    podName,
                    allPodTasks.stream().map(t -> t.getInfo().getName()).collect(Collectors.toList()),
                    podTasks.stream().map(t -> t.getInfo().getName()).collect(Collectors.toList()));
            return ResponseUtils.elementNotFoundResponse();
        }

        // invoke the restart request itself against ALL tasks. this ensures that they're ALL flagged as failed via
        // FailureUtils, which is then checked by DefaultRecoveryPlanManager.
        LOGGER.info("Performing {} goal state override of {} tasks in pod {}:",
                override, podTasks.size(), podName);
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
        return killTasks(taskKiller, podName, podTasks, RecoveryType.TRANSIENT);
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

    private Response restartPod(String podName, RecoveryType recoveryType) {
        // look up all tasks in the provided pod name:
        List<TaskInfoAndStatus> podTasks = GroupedTasks.create(stateStore).byPod.get(podName);
        if (podTasks == null || podTasks.isEmpty()) { // shouldn't ever be empty, but just in case
            return ResponseUtils.elementNotFoundResponse();
        }

        // invoke the restart request itself against ALL tasks. this ensures that they're ALL flagged as failed via
        // FailureUtils, which is then checked by DefaultRecoveryPlanManager.
        LOGGER.info("Performing {} restart of pod {} by killing {} tasks:",
                recoveryType, podName, podTasks.size());
        if (taskKiller == null) {
            LOGGER.error("Task killer wasn't initialized yet (scheduler started recently?), exiting early.");
            return Response.status(Response.Status.SERVICE_UNAVAILABLE).build();
        }
        return killTasks(taskKiller, podName, podTasks, recoveryType);
    }

    private static Response killTasks(
            TaskKiller taskKiller,
            String podName,
            Collection<TaskInfoAndStatus> tasksToKill,
            RecoveryType recoveryType) {
        for (TaskInfoAndStatus taskToKill : tasksToKill) {
            final TaskInfo taskInfo = taskToKill.getInfo();
            if (taskToKill.hasStatus()) {
                LOGGER.info("  {} ({}): currently in state {}",
                        taskInfo.getName(),
                        taskInfo.getTaskId().getValue(),
                        taskToKill.getStatus().get().getState());
            } else {
                LOGGER.info("  {} ({}): no status available",
                        taskInfo.getName(),
                        taskInfo.getTaskId().getValue());
            }
            taskKiller.killTask(taskInfo.getTaskId(), recoveryType);
        }

        JSONObject json = new JSONObject();
        json.put("pod", podName);
        json.put("tasks", tasksToKill.stream().map(t -> t.getInfo().getName()).collect(Collectors.toList()));
        return jsonOkResponse(json);
    }

    private static JSONArray getStatusesJson(List<TaskInfoAndStatus> tasks) {
        JSONArray jsonPod = new JSONArray();
        for (TaskInfoAndStatus task : tasks) {
            JSONObject jsonTask = new JSONObject();
            jsonTask.put("id", task.getInfo().getTaskId().getValue());
            jsonTask.put("name", task.getInfo().getName());
            if (task.hasStatus()) {
                jsonTask.put("state", task.getStatus().get().getState().toString());
            }
            jsonPod.put(jsonTask);
        }
        return jsonPod;
    }
}
