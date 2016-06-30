package org.apache.mesos.state.api;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import org.apache.mesos.Protos.FrameworkID;
import org.apache.mesos.Protos.SlaveID;
import org.apache.mesos.Protos.TaskInfo;
import org.apache.mesos.Protos.TaskState;
import org.apache.mesos.Protos.TaskStatus;
import org.apache.mesos.offer.TaskUtils;
import org.apache.mesos.state.StateStore;
import org.apache.mesos.state.StateStoreException;
import org.json.JSONArray;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import com.googlecode.protobuf.format.JsonFormat;

import java.util.Arrays;
import java.util.List;
import javax.ws.rs.core.Response;

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
        when(mockStateStore.fetchFrameworkId()).thenReturn(id);
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
        when(mockStateStore.fetchTask(taskName)).thenReturn(taskInfo);
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
