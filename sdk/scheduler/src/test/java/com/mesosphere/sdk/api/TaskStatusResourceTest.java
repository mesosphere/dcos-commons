package com.mesosphere.sdk.api;

import com.mesosphere.sdk.scheduler.TaskStatusWriter;
import com.mesosphere.sdk.state.StateStore;
import org.apache.mesos.Protos;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import javax.ws.rs.core.Response;
import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

public class TaskStatusResourceTest {

    @Mock
    private TaskStatusWriter mockTaskStatusWriter;
    @Mock
    private StateStore mockStateStore;

    private TaskStatusResource taskStatusResource;

    @Before
    public void beforeEach() {
        MockitoAnnotations.initMocks(this);
        taskStatusResource = new TaskStatusResource(mockTaskStatusWriter, mockStateStore);
    }

    @Test
    public void updateStateSuccess() throws Exception {
        Protos.TaskStatus taskStatus = Protos.TaskStatus.newBuilder()
                .setTaskId(Protos.TaskID.newBuilder().setValue("task-id").build())
                .setState(Protos.TaskState.TASK_RUNNING)
                .build();
        when(mockStateStore.fetchStatus("test-task")).thenReturn(Optional.of(taskStatus));
        Response response = taskStatusResource.updateState("test-task", Protos.TaskState.TASK_DROPPED);
        JSONObject result = new JSONObject(response.getEntity().toString());
        assertEquals("TASK_RUNNING", result.getString("oldState"));
        assertEquals("TASK_DROPPED", result.getString("newState"));
    }

    @Test
    public void updateStateTaskNotFound() throws Exception {
        when(mockStateStore.fetchStatus("test-task")).thenReturn(Optional.empty());
        Response response = taskStatusResource.updateState("test-task", Protos.TaskState.TASK_DROPPED);
        assertEquals(Response.Status.NOT_FOUND.getStatusCode(), response.getStatus());
    }

    @Test
    public void updateStateWriteTaskStatusFail() throws Exception {
        Protos.TaskStatus taskStatus = Protos.TaskStatus.newBuilder()
                .setTaskId(Protos.TaskID.newBuilder().setValue("task-id").build())
                .setState(Protos.TaskState.TASK_RUNNING)
                .build();
        when(mockStateStore.fetchStatus("test-task")).thenReturn(Optional.of(taskStatus));
        doThrow(new Exception()).when(mockTaskStatusWriter).writeTaskStatus(Protos.TaskID.newBuilder().setValue("task-id").build(),
                Protos.TaskState.TASK_DROPPED,
                "Task state manually overridden by API call");
        Response response = taskStatusResource.updateState("test-task", Protos.TaskState.TASK_DROPPED);
        assertEquals(Response.Status.INTERNAL_SERVER_ERROR.getStatusCode(), response.getStatus());

    }
}
