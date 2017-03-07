package com.mesosphere.sdk.api;

import com.mesosphere.sdk.api.types.RestartHook;
import com.mesosphere.sdk.api.types.TaskInfoAndStatus;
import com.mesosphere.sdk.offer.CommonTaskUtils;
import com.mesosphere.sdk.scheduler.TaskKiller;
import com.mesosphere.sdk.state.StateStore;
import com.mesosphere.sdk.testutils.TaskTestUtils;
import com.mesosphere.sdk.testutils.TestConstants;

import org.apache.mesos.Protos;
import org.apache.mesos.Protos.TaskInfo;
import org.apache.mesos.Protos.TaskState;
import org.apache.mesos.Protos.TaskStatus;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import javax.ws.rs.core.Response;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.*;

public class PodsResourceTest {

    // build from hand to avoid default type/index settings:
    private static final TaskInfo NO_POD_TASK = Protos.TaskInfo.newBuilder()
            .setTaskId(TestConstants.TASK_ID)
            .setName(TestConstants.TASK_NAME)
            .setSlaveId(TestConstants.AGENT_ID)
            .build();
    private static final TaskStatus NO_POD_STATUS =
            TaskTestUtils.generateStatus(NO_POD_TASK.getTaskId(), TaskState.TASK_RUNNING);
    // test-0: two RUNNING tasks, one STAGING task, and one task without status:
    private static final TaskInfo POD_0_TASK_A;
    private static final TaskInfo POD_0_TASK_B;
    private static final TaskInfo POD_0_TASK_C;
    private static final TaskInfo POD_0_TASK_D;
    private static final TaskStatus POD_0_STATUS_A;
    private static final TaskStatus POD_0_STATUS_B;
    private static final TaskStatus POD_0_STATUS_C;
    // test-1: one RUNNING task, one FINISHED task:
    private static final TaskInfo POD_1_TASK_A;
    private static final TaskInfo POD_1_TASK_B;
    private static final TaskStatus POD_1_STATUS_A;
    private static final TaskStatus POD_1_STATUS_B;
    // test-2: one FAILED task:
    private static final TaskInfo POD_2_TASK_A;
    private static final TaskStatus POD_2_STATUS_A;
    static {
        // pod 0
        TaskInfo.Builder infoBuilder = NO_POD_TASK.toBuilder();
        CommonTaskUtils.setType(infoBuilder, "test");
        CommonTaskUtils.setIndex(infoBuilder, 0);
        POD_0_TASK_A = infoBuilder.setName("a").setTaskId(CommonTaskUtils.toTaskId("a")).build();
        POD_0_STATUS_A = TaskTestUtils.generateStatus(POD_0_TASK_A.getTaskId(), TaskState.TASK_RUNNING);

        POD_0_TASK_B = POD_0_TASK_A.toBuilder().setName("b").setTaskId(CommonTaskUtils.toTaskId("b")).build();
        POD_0_STATUS_B = TaskTestUtils.generateStatus(POD_0_TASK_B.getTaskId(), TaskState.TASK_STAGING);

        POD_0_TASK_C = POD_0_TASK_A.toBuilder().setName("c").setTaskId(CommonTaskUtils.toTaskId("c")).build();
        POD_0_STATUS_C = TaskTestUtils.generateStatus(POD_0_TASK_C.getTaskId(), TaskState.TASK_RUNNING);

        POD_0_TASK_D = POD_0_TASK_A.toBuilder().setName("d").setTaskId(CommonTaskUtils.toTaskId("d")).build();

        // pod 1
        infoBuilder = POD_0_TASK_A.toBuilder();
        CommonTaskUtils.setIndex(infoBuilder, 1);
        POD_1_TASK_A = infoBuilder.setName("a").setTaskId(CommonTaskUtils.toTaskId("a")).build();
        POD_1_STATUS_A = TaskTestUtils.generateStatus(POD_1_TASK_A.getTaskId(), TaskState.TASK_FINISHED);

        POD_1_TASK_B = POD_1_TASK_A.toBuilder().setName("b").setTaskId(CommonTaskUtils.toTaskId("b")).build();
        POD_1_STATUS_B = TaskTestUtils.generateStatus(POD_1_TASK_B.getTaskId(), TaskState.TASK_RUNNING);

        // pod 2
        infoBuilder = POD_0_TASK_A.toBuilder();
        CommonTaskUtils.setIndex(infoBuilder, 2);
        POD_2_TASK_A = infoBuilder.setName("a").setTaskId(CommonTaskUtils.toTaskId("a")).build();
        POD_2_STATUS_A = TaskTestUtils.generateStatus(POD_2_TASK_A.getTaskId(), TaskState.TASK_FINISHED);
    }
    private static final Collection<TaskInfo> TASK_INFOS = Arrays.asList(
            NO_POD_TASK,
            POD_0_TASK_A,
            POD_0_TASK_B,
            POD_0_TASK_C,
            POD_0_TASK_D,
            POD_1_TASK_A,
            POD_1_TASK_B,
            POD_2_TASK_A);
    private static final Collection<TaskStatus> TASK_STATUSES = Arrays.asList(
            NO_POD_STATUS,
            POD_0_STATUS_A,
            POD_0_STATUS_B,
            POD_0_STATUS_C,
            //POD_A_STATUS_3, (none created)
            POD_1_STATUS_A,
            POD_1_STATUS_B,
            POD_2_STATUS_A);

    @Mock private TaskKiller mockTaskKiller;
    @Mock private StateStore mockStateStore;
    @Mock private RestartHook mockRestartHook;

    private PodsResource resource;

    @Before
    public void beforeAll() {
        MockitoAnnotations.initMocks(this);
        resource = new PodsResource(mockTaskKiller, mockStateStore, mockRestartHook);
    }

    @Test
    public void testGetPodNames() {
        when(mockStateStore.fetchTasks()).thenReturn(TASK_INFOS);
        Response response = resource.getPods();
        assertEquals(200, response.getStatus());
        JSONArray json = new JSONArray((String) response.getEntity());
        assertEquals(json.toString(), 4, json.length());

        assertEquals("test-0", json.get(0));
        assertEquals("test-1", json.get(1));
        assertEquals("test-2", json.get(2));
        assertEquals("UNKNOWN_POD_test-task-name", json.get(3));
    }

    @Test
    public void testGetAllPodStatuses() {
        when(mockStateStore.fetchTasks()).thenReturn(TASK_INFOS);
        when(mockStateStore.fetchStatuses()).thenReturn(TASK_STATUSES);
        Response response = resource.getPodStatuses();
        assertEquals(200, response.getStatus());
        JSONObject json = new JSONObject((String) response.getEntity());
        assertEquals(json.toString(), 4, json.length());

        JSONArray pod = json.getJSONArray("test-0");
        assertEquals(4, pod.length());

        JSONObject task = pod.getJSONObject(0);
        assertEquals(3, task.length());
        assertEquals("a", task.getString("name"));
        assertTrue(task.getString("id").startsWith("a__"));
        assertEquals("TASK_RUNNING", task.getString("state"));

        task = pod.getJSONObject(1);
        assertEquals(3, task.length());
        assertEquals("b", task.getString("name"));
        assertTrue(task.getString("id").startsWith("b__"));
        assertEquals("TASK_STAGING", task.getString("state"));

        task = pod.getJSONObject(2);
        assertEquals(3, task.length());
        assertEquals("c", task.getString("name"));
        assertTrue(task.getString("id").startsWith("c__"));
        assertEquals("TASK_RUNNING", task.getString("state"));

        task = pod.getJSONObject(3);
        assertEquals(3, task.length());
        assertEquals("d", task.getString("name"));
        assertTrue(task.getString("id").startsWith("d__"));
        assertEquals("No state defined", task.getString("state"));

        pod = json.getJSONArray("test-1");
        assertEquals(2, pod.length());

        task = pod.getJSONObject(0);
        assertEquals(3, task.length());
        assertEquals("a", task.getString("name"));
        assertTrue(task.getString("id").startsWith("a__"));
        assertEquals("TASK_FINISHED", task.getString("state"));

        task = pod.getJSONObject(1);
        assertEquals(3, task.length());
        assertEquals("b", task.getString("name"));
        assertTrue(task.getString("id").startsWith("b__"));
        assertEquals("TASK_RUNNING", task.getString("state"));

        pod = json.getJSONArray("test-2");
        assertEquals(1, pod.length());

        task = pod.getJSONObject(0);
        assertEquals(3, task.length());
        assertEquals("a", task.getString("name"));
        assertTrue(task.getString("id").startsWith("a__"));
        assertEquals("TASK_FINISHED", task.getString("state"));

        pod = json.getJSONArray("UNKNOWN_POD");
        assertEquals(1, pod.length());

        task = pod.getJSONObject(0);
        assertEquals(3, task.length());
        assertEquals("test-task-name", task.getString("name"));
        assertTrue(task.getString("id").startsWith("test-task-name__"));
        assertEquals("TASK_RUNNING", task.getString("state"));
    }

    @Test
    public void testGetPodStatus() {
        when(mockStateStore.fetchTasks()).thenReturn(TASK_INFOS);
        when(mockStateStore.fetchStatuses()).thenReturn(TASK_STATUSES);
        Response response = resource.getPodStatus("test-1");
        assertEquals(200, response.getStatus());
        JSONArray json = new JSONArray((String) response.getEntity());
        assertEquals(json.toString(), 2, json.length());

        JSONObject task = json.getJSONObject(0);
        assertEquals(3, task.length());
        assertEquals("a", task.getString("name"));
        assertTrue(task.getString("id").startsWith("a__"));
        assertEquals("TASK_FINISHED", task.getString("state"));

        task = json.getJSONObject(1);
        assertEquals(3, task.length());
        assertEquals("b", task.getString("name"));
        assertTrue(task.getString("id").startsWith("b__"));
        assertEquals("TASK_RUNNING", task.getString("state"));
    }

    @Test
    public void testGetPodStatusNotFound() {
        when(mockStateStore.fetchTasks()).thenReturn(TASK_INFOS);
        when(mockStateStore.fetchStatuses()).thenReturn(TASK_STATUSES);
        Response response = resource.getPodStatus("aaa");
        assertEquals(404, response.getStatus());
    }

    @Test
    public void testGetPodInfo() {
        when(mockStateStore.fetchTasks()).thenReturn(TASK_INFOS);
        when(mockStateStore.fetchStatuses()).thenReturn(TASK_STATUSES);
        Response response = resource.getPodInfo("test-1");
        assertEquals(200, response.getStatus());
        @SuppressWarnings("unchecked")
        List<TaskInfoAndStatus> info = (List<TaskInfoAndStatus>) response.getEntity();
        assertEquals(2, info.size());
        assertEquals(POD_1_TASK_A, info.get(0).getInfo());
        assertEquals(Optional.of(POD_1_STATUS_A), info.get(0).getStatus());
        assertEquals(POD_1_TASK_B, info.get(1).getInfo());
        assertEquals(Optional.of(POD_1_STATUS_B), info.get(1).getStatus());
    }

    @Test
    public void testGetPodInfoNotFound() {
        when(mockStateStore.fetchTasks()).thenReturn(TASK_INFOS);
        when(mockStateStore.fetchStatuses()).thenReturn(TASK_STATUSES);
        Response response = resource.getPodInfo("aaa");
        assertEquals(404, response.getStatus());
    }

    // restart

    @Test
    public void testRestartPodNotFound() {
        when(mockStateStore.fetchTasks()).thenReturn(TASK_INFOS);
        when(mockStateStore.fetchStatuses()).thenReturn(TASK_STATUSES);
        Response response = resource.restartPod("aaa");
        assertEquals(404, response.getStatus());
    }

    @Test
    public void testRestartPodManyRunning() {
        when(mockStateStore.fetchTasks()).thenReturn(TASK_INFOS);
        when(mockStateStore.fetchStatuses()).thenReturn(TASK_STATUSES);
        when(mockRestartHook.notify(Arrays.asList(
                TaskInfoAndStatus.create(POD_0_TASK_A, Optional.of(POD_0_STATUS_A)),
                TaskInfoAndStatus.create(POD_0_TASK_B, Optional.of(POD_0_STATUS_B)),
                TaskInfoAndStatus.create(POD_0_TASK_C, Optional.of(POD_0_STATUS_C)),
                TaskInfoAndStatus.create(POD_0_TASK_D, Optional.empty())), false)).thenReturn(true);

        Response response = resource.restartPod("test-0");
        assertEquals(200, response.getStatus());
        JSONObject json = new JSONObject((String) response.getEntity());
        assertEquals(2, json.length());
        assertEquals("test-0", json.getString("pod"));
        assertEquals(4, json.getJSONArray("tasks").length());
        assertEquals("a", json.getJSONArray("tasks").get(0));
        assertEquals("b", json.getJSONArray("tasks").get(1));
        assertEquals("c", json.getJSONArray("tasks").get(2));
        assertEquals("d", json.getJSONArray("tasks").get(3));

        verify(mockTaskKiller).killTask(POD_0_TASK_A.getTaskId(), false);
        verify(mockTaskKiller).killTask(POD_0_TASK_B.getTaskId(), false);
        verify(mockTaskKiller).killTask(POD_0_TASK_C.getTaskId(), false);
        verify(mockTaskKiller).killTask(POD_0_TASK_D.getTaskId(), false);
        verifyNoMoreInteractions(mockTaskKiller);
    }

    @Test
    public void testRestartPodOneRunning() {
        when(mockStateStore.fetchTasks()).thenReturn(TASK_INFOS);
        when(mockStateStore.fetchStatuses()).thenReturn(TASK_STATUSES);
        when(mockRestartHook.notify(Arrays.asList(
                TaskInfoAndStatus.create(POD_1_TASK_A, Optional.of(POD_1_STATUS_A)),
                TaskInfoAndStatus.create(POD_1_TASK_B, Optional.of(POD_1_STATUS_B))), false)).thenReturn(true);

        Response response = resource.restartPod("test-1");
        assertEquals(200, response.getStatus());
        JSONObject json = new JSONObject((String) response.getEntity());
        assertEquals(2, json.length());
        assertEquals("test-1", json.getString("pod"));
        assertEquals(2, json.getJSONArray("tasks").length());
        assertEquals("a", json.getJSONArray("tasks").get(0));
        assertEquals("b", json.getJSONArray("tasks").get(1));

        verify(mockTaskKiller).killTask(POD_1_TASK_A.getTaskId(), false);
        verify(mockTaskKiller).killTask(POD_1_TASK_B.getTaskId(), false);
        verifyNoMoreInteractions(mockTaskKiller);
    }

    @Test
    public void testRestartPodHookRejected() {
        when(mockStateStore.fetchTasks()).thenReturn(TASK_INFOS);
        when(mockStateStore.fetchStatuses()).thenReturn(TASK_STATUSES);
        when(mockRestartHook.notify(Arrays.asList(
                TaskInfoAndStatus.create(POD_1_TASK_A, Optional.of(POD_1_STATUS_A)),
                TaskInfoAndStatus.create(POD_1_TASK_B, Optional.of(POD_1_STATUS_B))), false)).thenReturn(false);

        Response response = resource.restartPod("test-1");
        assertEquals(409, response.getStatus());

        verifyZeroInteractions(mockTaskKiller);
        verify(mockRestartHook).notify(anyCollectionOf(TaskInfoAndStatus.class), anyBoolean());
    }

    // replace

    @Test
    public void testReplacePodNotFound() {
        when(mockStateStore.fetchTasks()).thenReturn(TASK_INFOS);
        when(mockStateStore.fetchStatuses()).thenReturn(TASK_STATUSES);
        Response response = resource.replacePod("aaa");
        assertEquals(404, response.getStatus());
    }

    @Test
    public void testReplacePodManyRunning() {
        when(mockStateStore.fetchTasks()).thenReturn(TASK_INFOS);
        when(mockStateStore.fetchStatuses()).thenReturn(TASK_STATUSES);
        when(mockRestartHook.notify(Arrays.asList(
                TaskInfoAndStatus.create(POD_0_TASK_A, Optional.of(POD_0_STATUS_A)),
                TaskInfoAndStatus.create(POD_0_TASK_B, Optional.of(POD_0_STATUS_B)),
                TaskInfoAndStatus.create(POD_0_TASK_C, Optional.of(POD_0_STATUS_C)),
                TaskInfoAndStatus.create(POD_0_TASK_D, Optional.empty())), true)).thenReturn(true);

        Response response = resource.replacePod("test-0");
        assertEquals(200, response.getStatus());
        JSONObject json = new JSONObject((String) response.getEntity());
        assertEquals(2, json.length());
        assertEquals("test-0", json.getString("pod"));
        assertEquals(4, json.getJSONArray("tasks").length());
        assertEquals("a", json.getJSONArray("tasks").get(0));
        assertEquals("b", json.getJSONArray("tasks").get(1));
        assertEquals("c", json.getJSONArray("tasks").get(2));
        assertEquals("d", json.getJSONArray("tasks").get(3));

        verify(mockTaskKiller).killTask(POD_0_TASK_A.getTaskId(), true);
        verify(mockTaskKiller).killTask(POD_0_TASK_B.getTaskId(), true);
        verify(mockTaskKiller).killTask(POD_0_TASK_C.getTaskId(), true);
        verify(mockTaskKiller).killTask(POD_0_TASK_D.getTaskId(), true);
        verifyNoMoreInteractions(mockTaskKiller);
    }

    @Test
    public void testReplacePodOneRunning() {
        when(mockStateStore.fetchTasks()).thenReturn(TASK_INFOS);
        when(mockStateStore.fetchStatuses()).thenReturn(TASK_STATUSES);
        when(mockRestartHook.notify(Arrays.asList(
                TaskInfoAndStatus.create(POD_1_TASK_A, Optional.of(POD_1_STATUS_A)),
                TaskInfoAndStatus.create(POD_1_TASK_B, Optional.of(POD_1_STATUS_B))), true)).thenReturn(true);

        Response response = resource.replacePod("test-1");
        assertEquals(200, response.getStatus());
        JSONObject json = new JSONObject((String) response.getEntity());
        assertEquals(2, json.length());
        assertEquals("test-1", json.getString("pod"));
        assertEquals(2, json.getJSONArray("tasks").length());
        assertEquals("a", json.getJSONArray("tasks").get(0));
        assertEquals("b", json.getJSONArray("tasks").get(1));

        verify(mockTaskKiller).killTask(POD_1_TASK_A.getTaskId(), true);
        verify(mockTaskKiller).killTask(POD_1_TASK_B.getTaskId(), true);
        verifyNoMoreInteractions(mockTaskKiller);
    }

    @Test
    public void testReplacePodHookRejected() {
        when(mockStateStore.fetchTasks()).thenReturn(TASK_INFOS);
        when(mockStateStore.fetchStatuses()).thenReturn(TASK_STATUSES);
        when(mockRestartHook.notify(Arrays.asList(
                TaskInfoAndStatus.create(POD_1_TASK_A, Optional.of(POD_1_STATUS_A)),
                TaskInfoAndStatus.create(POD_1_TASK_B, Optional.of(POD_1_STATUS_B))), true)).thenReturn(false);

        Response response = resource.replacePod("test-1");
        assertEquals(409, response.getStatus());

        verifyZeroInteractions(mockTaskKiller);
        verify(mockRestartHook).notify(anyCollectionOf(TaskInfoAndStatus.class), anyBoolean());
    }
}
