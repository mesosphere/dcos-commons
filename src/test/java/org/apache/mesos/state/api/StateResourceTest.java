package org.apache.mesos.state.api;

import com.googlecode.protobuf.format.JsonFormat;
import org.apache.mesos.Protos.*;
import org.apache.mesos.offer.TaskUtils;
import org.apache.mesos.state.StateStore;
import org.apache.mesos.state.StateStoreException;
import org.json.JSONArray;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import javax.ws.rs.core.Response;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.when;

public class StateResourceTest {

    @Mock private StateStore mockStateStore;

    private StateResource resource;

    @Before
    public void beforeAll() {
        MockitoAnnotations.initMocks(this);
        resource = new StateResource(mockStateStore);
    }

    @Test
    public void testGetFrameworkId() {
        FrameworkID id = FrameworkID.newBuilder().setValue("aoeu-asdf").build();
        when(mockStateStore.fetchFrameworkId()).thenReturn(Optional.of(id));
        Response response = resource.getFrameworkId();
        assertEquals(200, response.getStatus());
        JSONArray json = new JSONArray((String) response.getEntity());
        assertEquals(1, json.length());
        assertEquals(id.getValue(), json.get(0));
    }

    @Test
    public void testGetFrameworkIdFails() {
        when(mockStateStore.fetchFrameworkId()).thenThrow(new StateStoreException("hi"));
        Response response = resource.getFrameworkId();
        assertEquals(500, response.getStatus());
    }

    @Test
    public void testGetTaskNames() {
        List<String> taskNames = Arrays.asList("task0", "task1", "task2");
        when(mockStateStore.fetchTaskNames()).thenReturn(taskNames);
        Response response = resource.getTaskNames();
        assertEquals(200, response.getStatus());
        JSONArray json = new JSONArray((String) response.getEntity());
        assertEquals(3, json.length());
        assertEquals(taskNames.get(0), json.get(0));
        assertEquals(taskNames.get(1), json.get(1));
        assertEquals(taskNames.get(2), json.get(2));
    }

    @Test
    public void testGetTaskNamesFails() {
        when(mockStateStore.fetchTaskNames()).thenThrow(new StateStoreException("hi"));
        Response response = resource.getTaskNames();
        assertEquals(500, response.getStatus());
    }

    @Test
    public void testGetTaskInfo() {
        String taskName = "task1";
        TaskInfo taskInfo = TaskInfo.newBuilder()
                .setName(taskName)
                .setTaskId(TaskUtils.toTaskId(taskName))
                .setSlaveId(SlaveID.newBuilder().setValue("ignored")) // proto field required
                .build();
        when(mockStateStore.fetchTask(taskName)).thenReturn(Optional.of(taskInfo));
        Response response = resource.getTaskInfo(taskName);
        assertEquals(200, response.getStatus());
        assertEquals(new JsonFormat().printToString(taskInfo), (String) response.getEntity());
    }

    @Test
    public void testGetTaskInfoFails() {
        String taskName = "task1";
        when(mockStateStore.fetchTask(taskName)).thenThrow(new StateStoreException("hi"));
        Response response = resource.getTaskInfo(taskName);
        assertEquals(500, response.getStatus());
    }

    @Test
    public void testGetTaskStatus() {
        String taskName = "task1";
        TaskStatus taskStatus = TaskStatus.newBuilder()
                .setState(TaskState.TASK_KILLING)
                .setTaskId(TaskUtils.toTaskId(taskName))
                .setSlaveId(SlaveID.newBuilder().setValue("ignored")) // proto field required
                .build();
        when(mockStateStore.fetchStatus(taskName)).thenReturn(taskStatus);
        Response response = resource.getTaskStatus(taskName);
        assertEquals(200, response.getStatus());
        assertEquals(new JsonFormat().printToString(taskStatus), (String) response.getEntity());
    }

    @Test
    public void testGetTaskStatusFails() {
        String taskName = "task1";
        when(mockStateStore.fetchStatus(taskName)).thenThrow(new StateStoreException("hi"));
        Response response = resource.getTaskStatus(taskName);
        assertEquals(500, response.getStatus());
    }
}
