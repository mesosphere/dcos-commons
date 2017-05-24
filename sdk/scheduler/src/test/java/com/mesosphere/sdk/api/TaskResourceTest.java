package com.mesosphere.sdk.api;

import org.apache.mesos.Protos.*;

import com.mesosphere.sdk.offer.CommonIdUtils;
import com.mesosphere.sdk.scheduler.TaskKiller;
import com.mesosphere.sdk.state.StateStore;
import com.mesosphere.sdk.state.StateStoreException;
import com.mesosphere.sdk.storage.StorageError.Reason;
import com.mesosphere.sdk.testutils.ResourceTestUtils;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import javax.ws.rs.core.Response;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.*;

public class TaskResourceTest {
    private static final String FRAMEWORK_NAME = "test-framework";

    @Mock private StateStore mockStateStore;
    @Mock private TaskKiller mockTaskKiller;

    private TaskResource resource;

    @Before
    public void beforeAll() {
        MockitoAnnotations.initMocks(this);
        resource = new TaskResource(mockStateStore, mockTaskKiller, FRAMEWORK_NAME);
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
        when(mockStateStore.fetchTaskNames()).thenThrow(new StateStoreException(Reason.UNKNOWN, "hi"));
        Response response = resource.getTaskNames();
        assertEquals(500, response.getStatus());
    }

    @Test
    public void testGetTaskInfo() {
        String taskName = "task1";
        TaskInfo taskInfo = getTaskInfoBuilder(taskName).build();
        when(mockStateStore.fetchTask(taskName)).thenReturn(Optional.of(taskInfo));
        Response response = resource.getTaskInfo(taskName);
        assertEquals(200, response.getStatus());
        assertEquals(taskInfo, response.getEntity());
    }

    @Test
    public void testGetTaskInfoFails() {
        String taskName = "task1";
        when(mockStateStore.fetchTask(taskName)).thenThrow(new StateStoreException(Reason.UNKNOWN, "hi"));
        Response response = resource.getTaskInfo(taskName);
        assertEquals(500, response.getStatus());
    }

    @Test
    public void testGetTaskStatus() {
        String taskName = "task1";
        TaskID taskId = CommonIdUtils.toTaskId(taskName);
        TaskStatus taskStatus = TaskStatus.newBuilder()
                .setState(TaskState.TASK_KILLING)
                .setTaskId(taskId)
                .setSlaveId(SlaveID.newBuilder().setValue("ignored")) // proto field required
                .build();
        when(mockStateStore.fetchStatus(taskName)).thenReturn(Optional.of(taskStatus));
        Response response = resource.getTaskStatus(taskName);
        assertEquals(200, response.getStatus());
        assertEquals(taskStatus, response.getEntity());
    }

    @Test
    public void testGetTaskStatusFails() {
        String taskName = "task1";
        when(mockStateStore.fetchStatus(taskName)).thenReturn(Optional.empty());
        Response response = resource.getTaskStatus(taskName);
        assertEquals(500, response.getStatus());
    }

    @Test
    public void testRestart() {
        String taskName = "task1";
        TaskInfo taskInfo = getTaskInfoBuilder(taskName).build();
        when(mockStateStore.fetchTask(taskName)).thenReturn(Optional.of(taskInfo));
        Response response = resource.restartTask(taskName, "false");

        verify(mockTaskKiller, times(1)).killTask(taskInfo.getTaskId(), false);
        assertEquals(202, response.getStatus());
    }

    @Test
    public void testRestartReplace() {
        String taskName = "task1";
        TaskInfo taskInfo = getTaskInfoBuilder(taskName).build();
        when(mockStateStore.fetchTask(taskName)).thenReturn(Optional.of(taskInfo));
        Response response = resource.restartTask(taskName, "true");

        verify(mockTaskKiller, times(1)).killTask(taskInfo.getTaskId(), true);
        assertEquals(202, response.getStatus());
    }

    @Test
    public void testRestartLookupFails() {
        String taskName = "task1";
        when(mockStateStore.fetchTask(taskName)).thenReturn(Optional.empty());
        Response response = resource.restartTask(taskName, "false");

        verifyNoMoreInteractions(mockTaskKiller);
        assertEquals(404, response.getStatus());
    }

    @Test
    public void testGetConnection() {
        String taskName = "task1";

        Value.Range range1 = Value.Range.newBuilder()
                .setBegin(2000)
                .setEnd(3000)
                .build();
        Value.Range range2 = Value.Range.newBuilder()
                .setBegin(8080)
                .setEnd(8080)
                .build();
        List<Value.Range> ranges = new ArrayList<>();
        ranges.add(range1);
        ranges.add(range2);
        Resource rangeResource = ResourceTestUtils.getUnreservedRanges("ports", ranges);

        TaskInfo taskInfo = getTaskInfoBuilder(taskName)
                .addResources(rangeResource)
                .build();

        when(mockStateStore.fetchTask(taskName)).thenReturn(Optional.of(taskInfo));

        Response response = resource.getConnection(taskName);
        assertEquals(200, response.getStatus());

        JSONObject obj = new JSONObject((String) response.getEntity());
        assertEquals(taskName + "." + FRAMEWORK_NAME + ".mesos", obj.get("dns"));
        assertEquals("2000-3000,8080", obj.get("ports"));
    }

    @Test
    public void testGetConnectionFails() {
        String taskName = "task1";
        when(mockStateStore.fetchTask(taskName)).thenReturn(Optional.empty());

        Response response = resource.getConnection(taskName);
        assertEquals(404, response.getStatus());
    }

    private static TaskInfo.Builder getTaskInfoBuilder(String taskName) {
        return TaskInfo.newBuilder()
                .setName(taskName)
                .setTaskId(CommonIdUtils.toTaskId(taskName))
                .setSlaveId(SlaveID.newBuilder().setValue("ignored")); // proto field required
    }
}
