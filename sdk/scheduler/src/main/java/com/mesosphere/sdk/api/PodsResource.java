package com.mesosphere.sdk.api;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.stream.Collectors;

import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.core.Response;

import com.mesosphere.sdk.api.types.PrettyJsonResource;
import com.mesosphere.sdk.api.types.RestartHook;
import com.mesosphere.sdk.api.types.TaskInfoAndStatus;
import com.mesosphere.sdk.offer.CommonTaskUtils;
import com.mesosphere.sdk.scheduler.TaskKiller;
import com.mesosphere.sdk.specification.PodInstance;
import com.mesosphere.sdk.state.StateStore;

import org.apache.mesos.Protos.TaskID;
import org.apache.mesos.Protos.TaskInfo;
import org.apache.mesos.Protos.TaskStatus;
import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.mesosphere.sdk.api.ResponseUtils.jsonOkResponse;
import static com.mesosphere.sdk.api.ResponseUtils.jsonResponseBean;

/**
 * A read-only API for accessing information about how to connect to the service.
 */
@Path("/v1/pods")
public class PodsResource extends PrettyJsonResource {
    private static final Logger LOGGER = LoggerFactory.getLogger(PodsResource.class);

    /**
     * Pod 'name' to use in responses for tasks which have no pod information.
     */
    private static final String UNKNOWN_POD_LABEL = "UNKNOWN_POD";

    private final TaskKiller taskKiller;
    private final StateStore stateStore;
    private final RestartHook restartHook;

    /**
     * Creates a new instance which retrieves task/pod state from the provided {@link StateStore}, and which uses the
     * provided {@link TaskKiller} to restart/replace tasks, but with no {@link RestartHook} to be called before those
     * restart/replace operations.
     */
    public PodsResource(TaskKiller taskKiller, StateStore stateStore) {
        this(taskKiller, stateStore, null);
    }

    /**
     * Creates a new instance which retrieves task/pod state from the provided {@link StateStore}, and which uses the
     * provided {@link TaskKiller} to restart/replace tasks. Additionally, the provided {@link RestartHook} is invoked
     * before tasks are restarted or replaced.
     */
    public PodsResource(TaskKiller taskKiller, StateStore stateStore, RestartHook restartHook) {
        this.taskKiller = taskKiller;
        this.stateStore = stateStore;
        this.restartHook = restartHook;
    }

    /**
     * Produces a listing of all pod instance names.
     */
    @GET
    public Response getPods() {
        try {
            Set<String> podNames = new TreeSet<>();
            List<String> unknownTaskNames = new ArrayList<>();
            for (TaskInfo taskInfo : stateStore.fetchTasks()) {
                Optional<String> podNameOptional = getPodInstanceName(taskInfo);
                if (podNameOptional.isPresent()) {
                    podNames.add(podNameOptional.get());
                } else {
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
                return Response.status(Response.Status.NOT_FOUND).build();
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
                return Response.status(Response.Status.NOT_FOUND).build();
            }
            return jsonResponseBean(podTasks, Response.Status.OK);
        } catch (Exception e) {
            LOGGER.error(String.format("Failed to fetch info for pod '%s'", name), e);
            return Response.serverError().build();
        }
    }

    /**
     * Restarts a pod instance in-place.
     */
    @Path("/{name}/restart")
    @POST
    public Response restartPod(@PathParam("name") String name) {
        try {
            return restartPod(name, false);
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
            return restartPod(name, true);
        } catch (Exception e) {
            LOGGER.error(String.format("Failed to replace pod '%s'", name), e);
            return Response.serverError().build();
        }
    }

    private Response restartPod(String name, boolean destructive) {
        // look up all tasks in the provided pod name:
        List<TaskInfoAndStatus> podTasks = GroupedTasks.create(stateStore).byPod.get(name);
        if (podTasks == null || podTasks.isEmpty()) { // shouldn't ever be empty, but just in case
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        final String operation = destructive ? "replace" : "restart";

        // notify hook (if any) about *all* known tasks in the pod before the restart is finally issued:
        if (restartHook != null && !restartHook.notify(podTasks, destructive)) {
            // hook says to abort
            LOGGER.error("Aborting {} of pod {}: Hook has rejected the operation", operation, name);
            return Response.status(Response.Status.CONFLICT).build();
        }

        final List<String> restartedTaskNames = new ArrayList<>();
        // invoke the restart request itself against ALL tasks. this ensures that they're ALL flagged as failed via
        // FailureUtils, which is then checked by DefaultRecoveryPlanManager.
        LOGGER.info("Completing {} of pod {} by killing {} tasks:",
                operation, name, podTasks.size());
        for (TaskInfoAndStatus taskToKill : podTasks) {
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
            taskKiller.killTask(taskInfo.getTaskId(), destructive);
            restartedTaskNames.add(taskInfo.getName());
        }

        JSONObject json = new JSONObject();
        json.put("pod", name);
        json.put("tasks", restartedTaskNames);
        return jsonOkResponse(json);
    }

    /**
     * Utility class for sorting/grouping {@link TaskInfo}s and/or {@link TaskStatus}es into pods.
     */
    private static class GroupedTasks {
        /** pod instance => tasks in pod. */
        private final Map<String, List<TaskInfoAndStatus>> byPod = new TreeMap<>();
        /** tasks for which the pod instance couldn't be determined. */
        private final List<TaskInfoAndStatus> unknownPod = new ArrayList<>();

        private static GroupedTasks create(StateStore stateStore) {
            return new GroupedTasks(stateStore.fetchTasks(), stateStore.fetchStatuses());
        }

        private GroupedTasks(Collection<TaskInfo> taskInfos, Collection<TaskStatus> taskStatuses) {
            Map<TaskID, TaskStatus> taskStatusesById = taskStatuses.stream()
                    .collect(Collectors.toMap(status -> status.getTaskId(), Function.identity()));

            // map TaskInfos (and TaskStatuses if available) into pod instances:
            for (TaskInfo taskInfo : taskInfos) {
                TaskInfoAndStatus taskInfoAndStatus = TaskInfoAndStatus.create(
                        taskInfo,
                        Optional.ofNullable(taskStatusesById.get(taskInfo.getTaskId())));
                Optional<String> podNameOptional = getPodInstanceName(taskInfo);
                if (podNameOptional.isPresent()) {
                    List<TaskInfoAndStatus> tasksAndStatuses = byPod.get(podNameOptional.get());
                    if (tasksAndStatuses == null) {
                        tasksAndStatuses = new ArrayList<>();
                        byPod.put(podNameOptional.get(), tasksAndStatuses);
                    }
                    tasksAndStatuses.add(taskInfoAndStatus);
                } else {
                    unknownPod.add(taskInfoAndStatus);
                }
            }

            // sort the tasks within each pod by the task names (for user convenience):
            for (List<TaskInfoAndStatus> podTasks : byPod.values()) {
                podTasks.sort(new Comparator<TaskInfoAndStatus>() {
                    @Override
                    public int compare(TaskInfoAndStatus a, TaskInfoAndStatus b) {
                        return a.getInfo().getName().compareTo(b.getInfo().getName());
                    }
                });
            }
        }
    }

    private static JSONArray getStatusesJson(List<TaskInfoAndStatus> tasks) {
        JSONArray jsonPod = new JSONArray();
        for (TaskInfoAndStatus task : tasks) {
            JSONObject jsonTask = new JSONObject();
            jsonTask.put("id", task.getInfo().getTaskId().getValue());
            jsonTask.put("name", task.getInfo().getName());
            if (task.hasStatus()) {
                jsonTask.put("state", task.getStatus().get().getState().toString());
            } else {
                jsonTask.put("state", "No state defined");
            }
            jsonPod.put(jsonTask);
        }
        return jsonPod;
    }

    private static Optional<String> getPodInstanceName(TaskInfo taskInfo) {
        try {
            return Optional.of(PodInstance.getName(
                    CommonTaskUtils.getType(taskInfo),
                    CommonTaskUtils.getIndex(taskInfo)));
        } catch (Exception e) {
            LOGGER.warn(String.format("Failed to extract pod information from task %s", taskInfo.getName()), e);
            return Optional.empty();
        }
    }
}
