package org.apache.mesos.state;

import org.apache.curator.test.TestingServer;
import org.apache.mesos.Protos;
import org.apache.mesos.Protos.SlaveID;
import org.apache.mesos.offer.TaskUtils;
import org.junit.Before;
import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.util.*;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Tests to validate the operation of the {@link CuratorStateStore}.
 */
public class CuratorStateStoreTest {
    private static final Protos.FrameworkID FRAMEWORK_ID =
            Protos.FrameworkID.newBuilder().setValue("test-framework-id").build();
    private static final String TASK_NAME = "test-task-name";
    private static final String ROOT_ZK_PATH = "/test-root-path";
    private static final Protos.TaskState TASK_STATE = Protos.TaskState.TASK_STAGING;
    private static final Protos.TaskStatus TASK_STATUS = Protos.TaskStatus.newBuilder()
            .setTaskId(TaskUtils.toTaskId(TASK_NAME))
            .setState(TASK_STATE)
            .build();

    private TestingServer testZk;
    private CuratorStateStore store;

    @Before
    public void beforeEach() throws Exception {
        testZk = new TestingServer();
        store = new CuratorStateStore(ROOT_ZK_PATH, testZk.getConnectString());
    }

    @Test
    public void testStoreFetchFrameworkId() throws Exception {
        Protos.FrameworkID fwkId = FRAMEWORK_ID;
        store.storeFrameworkId(fwkId);
        assertEquals(fwkId, store.fetchFrameworkId());
    }

    @Test(expected=StateStoreException.class)
    public void testFetchEmptyFrameworkId() throws Exception {
        store.fetchFrameworkId();
    }

    @Test
    public void testStoreClearFrameworkId() throws Exception {
        Protos.FrameworkID fwkId = FRAMEWORK_ID;
        store.storeFrameworkId(fwkId);
        store.clearFrameworkId();
    }

    @Test(expected=StateStoreException.class)
    public void testStoreClearFetchFrameworkId() throws Exception {
        store.storeFrameworkId(FRAMEWORK_ID);
        store.clearFrameworkId();
        store.fetchFrameworkId(); // throws
    }

    @Test
    public void testClearEmptyFrameworkId() throws Exception {
        store.clearFrameworkId();
    }

    // task

    @Test
    public void testStoreFetchTask() throws Exception {
        Protos.TaskInfo testTask = createTask(TASK_NAME);
        store.storeTasks(Arrays.asList(testTask));
        assertEquals(testTask, store.fetchTask(TASK_NAME));
        Collection<Protos.TaskInfo> outTasks = store.fetchTasks();
        assertEquals(1, outTasks.size());
        assertEquals(testTask, outTasks.iterator().next());
    }

    @Test(expected=StateStoreException.class)
    public void testFetchMissingTask() throws Exception {
        store.fetchTask(TASK_NAME);
    }

    @Test
    public void testFetchEmptyTasks() throws Exception {
        assertTrue(store.fetchTasks().isEmpty());
    }

    @Test
    public void testRepeatedStoreTask() throws Exception {
        Collection<Protos.TaskInfo> tasks = createTasks(TASK_NAME);
        store.storeTasks(tasks);
        assertEquals(tasks.iterator().next(), store.fetchTask(TASK_NAME));

        tasks = createTasks(TASK_NAME);
        store.storeTasks(tasks);
        assertEquals(tasks.iterator().next(), store.fetchTask(TASK_NAME));

        Collection<String> taskNames = store.fetchTaskNames();
        assertEquals(1, taskNames.size());
        assertEquals(tasks, store.fetchTasks());
    }

    @Test
    public void testStoreClearTask() throws Exception {
        store.storeTasks(createTasks(TASK_NAME));
        store.clearTask(TASK_NAME);
    }

    @Test(expected=StateStoreException.class)
    public void testStoreClearFetchTask() throws Exception {
        store.storeTasks(createTasks(TASK_NAME));
        store.clearTask(TASK_NAME);
        store.fetchTask(TASK_NAME);
    }

    @Test
    public void testClearMissingTask() throws Exception {
        store.clearTask(TASK_NAME);
    }

    @Test
    public void testFetchEmptyTaskNames() throws Exception {
        assertTrue(store.fetchTaskNames().isEmpty());
    }

    @Test
    public void testFetchTaskNames() throws Exception {
        String testTaskNamePrefix = "test-executor";
        String testTaskName0 = testTaskNamePrefix + "-0";
        String testTaskName1 = testTaskNamePrefix + "-1";

        store.storeTasks(createTasks(testTaskName0, testTaskName1));
        Collection<String> taskNames = store.fetchTaskNames();
        assertEquals(2, taskNames.size());

        Iterator<String> iter =  taskNames.iterator();
        assertEquals(testTaskName1, iter.next());
        assertEquals(testTaskName0, iter.next());
    }

    @Test
    public void testFetchTaskNamesWithFrameworkIdSet() throws Exception {
        String testTaskNamePrefix = "test-executor";
        String testTaskName0 = testTaskNamePrefix + "-0";
        String testTaskName1 = testTaskNamePrefix + "-1";

        store.storeFrameworkId(FRAMEWORK_ID);
        store.storeTasks(createTasks(testTaskName0, testTaskName1));
        Collection<String> taskNames = store.fetchTaskNames();
        assertEquals(2, taskNames.size()); // framework id set above mustn't be included

        Iterator<String> iter =  taskNames.iterator();
        assertEquals(testTaskName1, iter.next());
        assertEquals(testTaskName0, iter.next());
    }

    @Test
    public void testMultipleTasks() throws Exception {
        assertTrue(store.fetchTaskNames().isEmpty());
        assertTrue(store.fetchTasks().isEmpty());
        assertTrue(store.fetchStatuses().isEmpty());

        Protos.TaskInfo taskInfoA = createTask("a");
        store.storeTasks(Arrays.asList(taskInfoA));

        assertEquals(taskInfoA, store.fetchTask("a"));
        assertEquals(1, store.fetchTaskNames().size());
        assertEquals("a", store.fetchTaskNames().iterator().next());
        assertEquals(1, store.fetchTasks().size());
        assertEquals(taskInfoA, store.fetchTasks().iterator().next());
        assertTrue(store.fetchStatuses().isEmpty());

        Protos.TaskInfo taskInfoB = createTask("b");
        store.storeTasks(Arrays.asList(taskInfoB));

        assertEquals(taskInfoB, store.fetchTask("b"));
        assertEquals(2, store.fetchTaskNames().size());
        assertEquals(2, store.fetchTasks().size());
        assertTrue(store.fetchStatuses().isEmpty());

        store.clearTask("a");

        assertEquals(taskInfoB, store.fetchTask("b"));
        assertEquals(1, store.fetchTaskNames().size());
        assertEquals("b", store.fetchTaskNames().iterator().next());
        assertEquals(1, store.fetchTasks().size());
        assertEquals(taskInfoB, store.fetchTasks().iterator().next());
        assertTrue(store.fetchStatuses().isEmpty());

        store.clearTask("b");

        assertTrue(store.fetchTaskNames().isEmpty());
        assertTrue(store.fetchTasks().isEmpty());
        assertTrue(store.fetchStatuses().isEmpty());
    }

    // status

    @Test(expected=StateStoreException.class)
    public void testStoreFetchStatusWithoutInfo() throws Exception {
        store.storeStatus(TASK_STATUS);
    }


    @Test(expected=StateStoreException.class)
    public void testStoreFetchStatusInfoUuidMismatch() throws Exception {
        store.storeTasks(createTasks(TASK_NAME));
        store.storeStatus(TASK_STATUS); // has same TASK_NAME, with different ID
    }

    @Test
    public void testStoreFetchStatusExactMatch() throws Exception {
        Protos.TaskInfo task = createTask(TASK_NAME);
        store.storeTasks(Arrays.asList(task));

        // taskstatus id must exactly match taskinfo id:
        Protos.TaskStatus status = TASK_STATUS.toBuilder().setTaskId(task.getTaskId()).build();
        store.storeStatus(status);
        assertEquals(status, store.fetchStatus(TASK_NAME));
        Collection<Protos.TaskStatus> statuses = store.fetchStatuses();
        assertEquals(1, statuses.size());
        assertEquals(status, statuses.iterator().next());
    }

    @Test(expected=StateStoreException.class)
    public void testFetchMissingStatus() throws Exception {
        store.fetchStatus(TASK_NAME);
    }

    @Test
    public void testFetchEmptyStatuses() throws Exception {
        assertTrue(store.fetchStatuses().isEmpty());
    }

    @Test
    public void testRepeatedStoreStatus() throws Exception {
        Protos.TaskInfo task = createTask(TASK_NAME);
        store.storeTasks(Arrays.asList(task));

        // taskstatus id must exactly match taskinfo id:
        Protos.TaskStatus status = TASK_STATUS.toBuilder().setTaskId(task.getTaskId()).build();
        store.storeStatus(status);
        assertEquals(status, store.fetchStatus(TASK_NAME));

        store.storeStatus(status);
        assertEquals(status, store.fetchStatus(TASK_NAME));

        Collection<String> taskNames = store.fetchTaskNames();
        assertEquals(1, taskNames.size());
        Collection<Protos.TaskStatus> statuses = store.fetchStatuses();
        assertEquals(1, statuses.size());
        assertEquals(status, statuses.iterator().next());
    }

    @Test
    public void testMultipleStatuses() throws Exception {
        assertTrue(store.fetchTaskNames().isEmpty());
        assertTrue(store.fetchTasks().isEmpty());
        assertTrue(store.fetchStatuses().isEmpty());

        // must have TaskInfos first:
        Protos.TaskInfo taskA = createTask("a");
        Protos.TaskInfo taskB = createTask("b");
        store.storeTasks(Arrays.asList(taskA, taskB));

        assertEquals(2, store.fetchTaskNames().size());
        assertTrue(store.fetchStatuses().isEmpty());
        assertEquals(2, store.fetchTasks().size());

        Protos.TaskStatus taskStatusA = createTaskStatus(taskA.getTaskId());
        store.storeStatus(taskStatusA);

        assertEquals(taskStatusA, store.fetchStatus("a"));
        assertEquals(2, store.fetchTaskNames().size());
        assertEquals("a", store.fetchTaskNames().iterator().next());
        assertEquals(1, store.fetchStatuses().size());
        assertEquals(taskStatusA, store.fetchStatuses().iterator().next());
        assertEquals(2, store.fetchTasks().size());

        Protos.TaskStatus taskStatusB = createTaskStatus(taskB.getTaskId());
        store.storeStatus(taskStatusB);

        assertEquals(taskStatusB, store.fetchStatus("b"));
        assertEquals(2, store.fetchTaskNames().size());
        assertEquals(2, store.fetchStatuses().size());
        assertEquals(2, store.fetchTasks().size());

        store.clearTask("a");

        assertEquals(taskStatusB, store.fetchStatus("b"));
        assertEquals(1, store.fetchTaskNames().size());
        assertEquals("b", store.fetchTaskNames().iterator().next());
        assertEquals(1, store.fetchStatuses().size());
        assertEquals(taskStatusB, store.fetchStatuses().iterator().next());
        assertEquals(1, store.fetchTasks().size());

        store.clearTask("b");

        assertTrue(store.fetchTaskNames().isEmpty());
        assertTrue(store.fetchTasks().isEmpty());
        assertTrue(store.fetchStatuses().isEmpty());
    }

    @Test
    public void testStoreStatusSucceedsOnUUIDChangeWithTaskInfoUpdate() throws Exception {
        Protos.TaskInfo task = createTask(TASK_NAME);
        store.storeTasks(Arrays.asList(task));

        // taskstatus id must exactly match taskinfo id:
        Protos.TaskStatus status = TASK_STATUS.toBuilder().setTaskId(task.getTaskId()).build();
        store.storeStatus(status);
        assertEquals(status, store.fetchStatus(TASK_NAME));

        // change the taskinfo id:
        Protos.TaskInfo taskNewId = createTask(TASK_NAME);
        store.storeTasks(Arrays.asList(taskNewId));
        // send a new status whose id matches the updated taskinfo id:
        Protos.TaskStatus statusNewId = TASK_STATUS.toBuilder().setTaskId(taskNewId.getTaskId()).build();
        store.storeStatus(statusNewId);
        assertEquals(statusNewId, store.fetchStatus(TASK_NAME));
    }

    @Test(expected=StateStoreException.class)
    public void testStoreStatusFailsOnUUIDChangeWithoutTaskInfoUpdate() throws Exception {
        Protos.TaskInfo task = createTask(TASK_NAME);
        store.storeTasks(Arrays.asList(task));

        // taskstatus id must exactly match taskinfo id:
        Protos.TaskStatus status = TASK_STATUS.toBuilder().setTaskId(task.getTaskId()).build();
        store.storeStatus(status);
        assertEquals(status, store.fetchStatus(TASK_NAME));

        // change the taskinfo id:
        Protos.TaskInfo taskNewId = createTask(TASK_NAME);
        store.storeTasks(Arrays.asList(taskNewId));
        // send a new status whose id doesn't match the updated taskinfo id:
        store.storeStatus(status);
    }

    // taskid is required and cannot be unset, so lets try the next best thing
    @Test(expected=StateStoreException.class)
    public void testStoreStatusEmptyTaskId() throws Exception {
        store.storeStatus(createTaskStatus(
                Protos.TaskID.newBuilder().setValue("").build()));
    }

    @Test(expected=StateStoreException.class)
    public void testStoreStatusBadTaskId() throws Exception {
        store.storeStatus(createTaskStatus(
                Protos.TaskID.newBuilder().setValue("bad-test-id").build()));
    }

    @Test
    public void testStoreFetchTaskAndStatus() throws Exception {
        Protos.TaskInfo testTask = createTask(TASK_NAME);
        store.storeTasks(Arrays.asList(testTask));
        Collection<Protos.TaskInfo> outTasks = store.fetchTasks();
        assertEquals(1, outTasks.size());
        assertEquals(testTask, outTasks.iterator().next());

        Protos.TaskStatus testStatus = createTaskStatus(testTask.getTaskId());
        store.storeStatus(testStatus);
        assertEquals(testStatus, store.fetchStatus(TASK_NAME));
    }

    @Test
    public void testStoreInfoThenStatus() throws Exception {
        assertTrue(store.fetchTaskNames().isEmpty());
        assertTrue(store.fetchTasks().isEmpty());
        assertTrue(store.fetchStatuses().isEmpty());

        Protos.TaskInfo taskInfoA = createTask("a");
        store.storeTasks(Arrays.asList(taskInfoA));

        assertEquals(taskInfoA, store.fetchTask("a"));
        assertEquals(1, store.fetchTaskNames().size());
        assertEquals("a", store.fetchTaskNames().iterator().next());
        assertTrue(store.fetchStatuses().isEmpty());
        assertEquals(1, store.fetchTasks().size());
        assertEquals(taskInfoA, store.fetchTasks().iterator().next());

        // task id must exactly match:
        Protos.TaskStatus taskStatusA = createTaskStatus(taskInfoA.getTaskId());
        store.storeStatus(taskStatusA);

        assertEquals(taskStatusA, store.fetchStatus("a"));
        assertEquals(1, store.fetchTaskNames().size());
        assertEquals("a", store.fetchTaskNames().iterator().next());
        assertEquals(1, store.fetchStatuses().size());
        assertEquals(taskStatusA, store.fetchStatuses().iterator().next());
        assertEquals(1, store.fetchTasks().size());
        assertEquals(taskInfoA, store.fetchTasks().iterator().next());

        store.clearTask("a");

        assertTrue(store.fetchTaskNames().isEmpty());
        assertTrue(store.fetchTasks().isEmpty());
        assertTrue(store.fetchStatuses().isEmpty());
    }

    @Test
    public void testProperties() {
        store.storeProperty("hey", "DC/OS".getBytes(StandardCharsets.UTF_8));
        final byte[] bytes = store.fetchProperty("hey");
        assertEquals("DC/OS", new String(bytes, StandardCharsets.UTF_8));
        final Collection<String> keys = store.listPropertyKeys();
        assertTrue(keys.size() == 1);
        assertEquals(keys.iterator().next(), "hey");
    }

    @Test(expected = StateStoreException.class)
    public void testPropertiesSlashStore() {
        store.storeProperty("hey/hi", "DC/OS".getBytes(StandardCharsets.UTF_8));
    }

    @Test(expected = StateStoreException.class)
    public void testPropertiesSlashFetch() {
        store.fetchProperty("hey/hi");
    }

    private static Protos.TaskStatus createTaskStatus(Protos.TaskID taskId) {
        return TASK_STATUS.toBuilder().setTaskId(taskId).build();
    }

    private static Collection<Protos.TaskInfo> createTasks(String... taskNames) {
        List<Protos.TaskInfo> taskInfos = new ArrayList<>();
        for (String taskName : taskNames) {
            taskInfos.add(createTask(taskName));
        }
        return taskInfos;
    }

    private static Protos.TaskInfo createTask(String taskName) {
        return Protos.TaskInfo.newBuilder()
                .setName(taskName)
                .setTaskId(TaskUtils.toTaskId(taskName))
                .setSlaveId(SlaveID.newBuilder().setValue("ignored")) // proto field required
                .build();
    }
}
