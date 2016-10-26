package org.apache.mesos.scheduler.api;

import com.googlecode.protobuf.format.JsonFormat;
import org.apache.mesos.Protos;
import org.apache.mesos.scheduler.TaskKiller;
import org.apache.mesos.state.StateStore;
import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * A read-only API for accessing task and frameworkId state from persistent storage.
 */
@Path("/v1/tasks")
public class TaskResource {

    private static final Logger logger = LoggerFactory.getLogger(TaskResource.class);

    private final StateStore stateStore;
    private final TaskKiller taskKiller;
    private final String frameworkName;

    /**
     * Creates a new TaskResource.
     *
     * @param stateStore the source of data to be returned to callers
     * @param taskKiller used to restart tasks
     * @param frameworkName name of the framework these tasks belong to
     */
    public TaskResource(StateStore stateStore, TaskKiller taskKiller, String frameworkName) {
        this.stateStore = stateStore;
        this.taskKiller = taskKiller;
        this.frameworkName = frameworkName;
    }

    /**
     * Produces a listing of the names of all stored tasks.
     */
    @GET
    public Response getTaskNames() {
        try {
            JSONArray nameArray = new JSONArray(stateStore.fetchTaskNames());
            return Response.ok(nameArray.toString(), MediaType.APPLICATION_JSON).build();
        } catch (Exception ex) {
            logger.error("Failed to fetch list of task names", ex);
            return Response.serverError().build();
        }
    }

    /**
     * Produces the TaskInfo for the provided task name, or returns an error if that name doesn't
     * exist or the data couldn't be read.
     */
    @Path("/info/{taskName}")
    @GET
    public Response getTaskInfo(@PathParam("taskName") String taskName) {
        try {
            logger.info("Attempting to fetch TaskInfo for task '{}'", taskName);
            Optional<Protos.TaskInfo> taskInfoOptional = stateStore.fetchTask(taskName);
            if (taskInfoOptional.isPresent()) {
                return Response.ok(new JsonFormat().printToString(taskInfoOptional.get()),
                        MediaType.APPLICATION_JSON).build();
            }
        } catch (Exception ex) {
            // Warning instead of Error: Subject to user input
            logger.warn(String.format(
                    "Failed to fetch requested TaskInfo for task '%s'", taskName), ex);
        }

        return Response.serverError().build();
    }

    /**
     * Produces the TaskStatus for the provided task name, or returns an error if that data doesn't
     * exist or the data couldn't be read. This may fail even if the task name is valid if
     * TaskStatus data hadn't yet been written by the Framework.
     */
    @Path("/status/{taskName}")
    @GET
    public Response getTaskStatus(@PathParam("taskName") String taskName) {
        logger.info("Attempting to fetch TaskInfo for task '{}'", taskName);

        Optional<Protos.TaskStatus> taskStatus = stateStore.fetchStatus(taskName);
        if (taskStatus.isPresent()) {
            return Response.ok(new JsonFormat().printToString(taskStatus.get()),
                    MediaType.APPLICATION_JSON).build();
        } else {
            // Warning instead of Error: Subject to user input
            logger.warn(String.format("Failed to fetch requested TaskStatus for task '%s'", taskName));
            return Response.serverError().build();
        }
    }

    /**
     * Returns connection information for the given task.  The response has the following format:
     *
     * {
     *     "dns": "task-name.framework-name.mesos"
     *     "ports": "8080,2000-3000"
     * }
     *
     * @param taskName Name of the task
     * @return 200 or 404
     */
    @Path("/connection/{taskName}")
    @GET
    public Response getConnection(@PathParam("taskName") String taskName) {
        Optional<Protos.TaskInfo> info = stateStore.fetchTask(taskName);
        if (info.isPresent()) {
            return Response.ok(
                    getTaskConnection(info.get()).toString(),
                    MediaType.APPLICATION_JSON)
                    .build();
        } else {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
    }

    private JSONObject getTaskConnection(Protos.TaskInfo taskInfo) {
        String dns = taskInfo.getName() + "." + frameworkName + ".mesos";

        JSONObject conn = new JSONObject();
        conn.put("dns", dns);
        conn.put("ports", getPortString(taskInfo));
        return conn;
    }

    private String getPortString(Protos.TaskInfo taskInfo) {
        List<String> ports = new ArrayList<>();
        for (Protos.Resource resource : taskInfo.getResourcesList()) {
            if (resource.getName() == "ports" && resource.getType() == Protos.Value.Type.RANGES) {
                for (Protos.Value.Range range : resource.getRanges().getRangeList()) {
                    Long begin = range.getBegin();
                    Long end = range.getEnd();
                    if (begin.equals(end)) {
                        ports.add(begin.toString());
                    } else {
                        ports.add(begin + "-" + end);
                    }
                }
            }
        }
        return String.join(",", ports);
    }

    @Path("/restart/{taskName}")
    @POST
    public Response restartTask(
            @PathParam("taskName") String name,
            @QueryParam("replace") String replace) {
        boolean taskExists = taskKiller.killTask(name, Boolean.parseBoolean(replace));

        if (taskExists) {
            return Response.accepted().build();
        } else {
            logger.error("User requested to kill non-existent task: " + name);
            return Response.status(Response.Status.NOT_FOUND).build();
        }
    }
}
