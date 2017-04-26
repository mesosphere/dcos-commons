package com.mesosphere.sdk.api;

import com.mesosphere.sdk.scheduler.TaskStatusWriter;
import com.mesosphere.sdk.state.StateStore;
import org.apache.mesos.Protos;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.*;
import javax.ws.rs.core.Response;
import java.util.Optional;

/**
 * Basic API to directly modify the TaskStatus of a Task.
 */
@Path("/v1/taskstatus")
public class TaskStatusResource {
    private final Logger LOGGER = LoggerFactory.getLogger(getClass());

    private final TaskStatusWriter taskStatusWriter;
    private final StateStore stateStore;

    public TaskStatusResource(TaskStatusWriter taskStatusWriter, StateStore stateStore) {
        this.taskStatusWriter = taskStatusWriter;
        this.stateStore = stateStore;
    }

    @POST
    @Path("/{name}")
    public Response updateState(@PathParam("name") String taskname,
                                 @QueryParam("targetState") Protos.TaskState targetState) throws Exception {
        LOGGER.info("Trying to update state of task {} to new desired state {}", taskname, targetState);
        Optional<Protos.TaskStatus> possibleTask = stateStore.fetchStatus(taskname);
        if (!possibleTask.isPresent()) {
            LOGGER.error("No task found for name {}", taskname);
            return ResponseUtils.plainResponse(String.format("No task with name %s", taskname),
                    Response.Status.NOT_FOUND);
        }

        Protos.TaskStatus taskStatus = possibleTask.get();
        LOGGER.info("Task {} found. currently={} desired={}", taskname,
                taskStatus.getState(),
                targetState);
        try {
            taskStatusWriter.writeTaskStatus(taskStatus.getTaskId(),
                    targetState,
                    "Task state manually overridden by API call");
        } catch (Exception e) {
            LOGGER.error(String.format("Failed to write new state %s for task %s", targetState, taskname), e);
            return Response.serverError().build();
        }

        JSONObject result = new JSONObject().put("newState", targetState.toString())
                .put("oldState", taskStatus.getState().toString());
        return ResponseUtils.jsonOkResponse(result);
    }
}
