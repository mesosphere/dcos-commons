package org.apache.mesos.state.api;

import com.google.inject.Inject;
import com.googlecode.protobuf.format.JsonFormat;
import org.apache.mesos.Protos;
import org.apache.mesos.state.StateStore;
import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * A read-only API for accessing task and frameworkId state from persistent storage.
 */
@Path("/v1/state")
public class StateResource {

    private static final Logger logger = LoggerFactory.getLogger(StateResource.class);

    private final StateStore stateStore;
    private final PropertyDeserializer propertyDeserializer;
    private final String frameworkName;

    /**
     * Creates a new StateResource which cannot deserialize Properties. Callers will receive a
     * "204 NO_CONTENT" HTTP response when attempting to view the content of a property.
     *
     * @param stateStore the source of data to be returned to callers
     */
    public StateResource(StateStore stateStore, String frameworkName) {
        this(stateStore, null, frameworkName);
    }

    /**
     * Creates a new StateResource which can deserialize Properties. Callers will be able to view
     * the content of individual Properties.
     *
     * @param stateStore the source of data to be returned to callers
     * @param propertyDeserializer a deserializer which can turn any Property in the provided
     *                             {@code stateStore} to valid JSON
     */
    @Inject
    public StateResource(StateStore stateStore, PropertyDeserializer propertyDeserializer, String frameworkName) {
        this.stateStore = stateStore;
        this.propertyDeserializer = propertyDeserializer;
        this.frameworkName = frameworkName;
    }

    /**
     * Produces the configured ID of the framework, or returns an error if reading that data failed.
     */
    @Path("/frameworkId")
    @GET
    public Response getFrameworkId() {
        try {
            Optional<Protos.FrameworkID> frameworkIDOptional = stateStore.fetchFrameworkId();
            if (frameworkIDOptional.isPresent()) {
                JSONArray idArray = new JSONArray(Arrays.asList(frameworkIDOptional.get().getValue()));
                return Response.ok(idArray.toString(), MediaType.APPLICATION_JSON).build();
            }
        } catch (Exception ex) {
            logger.error("Failed to fetch target configuration", ex);
        }

        return Response.serverError().build();
    }

    /**
     * Produces a listing of the names of all stored tasks.
     */
    @Path("/tasks")
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
    @Path("/tasks/info/{taskName}")
    @GET
    public Response getTaskInfo(@PathParam("taskName") String taskName) {
        try {
            logger.info("Attempting to fetch TaskInfo for task '{}'", taskName);
            Optional<Protos.TaskInfo> taskInfoOptional = stateStore.fetchTask(taskName);
            if (taskInfoOptional.isPresent()) {
                return Response.ok(new JsonFormat().printToString(taskInfoOptional.get()),
                        MediaType.APPLICATION_JSON).build();
            } else {
                return Response.status(Response.Status.NOT_FOUND).build();
            }
        } catch (Exception ex) {
            // Warning instead of Error: Subject to user input
            logger.warn(String.format(
                    "Failed to fetch requested TaskInfo for task '%s'", taskName), ex);
            return Response.serverError().build();
        }
    }

    /**
     * Produces the TaskStatus for the provided task name, or returns an error if that data doesn't
     * exist or the data couldn't be read. This may fail even if the task name is valid if
     * TaskStatus data hadn't yet been written by the Framework.
     */
    @Path("/tasks/status/{taskName}")
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
            return Response.status(Response.Status.NOT_FOUND).build();
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
    @Path("/tasks/connection/{taskName}")
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


    /**
     * Produces a listing of the names of all stored tasks.
     */
    @Path("/properties")
    @GET
    public Response getPropertyKeys() {
        try {
            JSONArray keyArray = new JSONArray(stateStore.fetchPropertyKeys());
            return Response.ok(keyArray.toString(), MediaType.APPLICATION_JSON).build();
        } catch (Exception ex) {
            logger.error("Failed to fetch list of property keys", ex);
            return Response.serverError().build();
        }
    }

    /**
     * Produces the TaskInfo for the provided task name, or returns an error if that name doesn't
     * exist or the data couldn't be read.
     */
    @Path("/properties/{key}")
    @GET
    public Response getProperty(@PathParam("key") String key) {
        try {
            if (propertyDeserializer == null) {
                logger.warn("Cannot deserialize requested Property '{}': " +
                        "No deserializer was provided to StateResource constructor", key);
                return Response.noContent().build(); // 204 NO_CONTENT
            } else {
                logger.info("Attempting to fetch Property for key '{}'", key);
                byte[] value = stateStore.fetchProperty(key);
                return Response.ok(propertyDeserializer.toJsonString(key, value),
                        MediaType.APPLICATION_JSON).build();
            }
        } catch (Exception ex) {
            // Warning instead of Error: Subject to user input
            logger.warn(String.format(
                    "Failed to fetch requested Property for key '%s'", key), ex);
            return Response.serverError().build();
        }
    }
}
