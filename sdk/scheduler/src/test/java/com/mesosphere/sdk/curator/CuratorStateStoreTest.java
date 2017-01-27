package com.mesosphere.sdk.curator;

import org.apache.curator.retry.ExponentialBackoffRetry;
import org.apache.curator.test.TestingServer;
import org.apache.mesos.Protos;
import org.apache.mesos.Protos.SlaveID;
import com.mesosphere.sdk.offer.CommonTaskUtils;
import com.mesosphere.sdk.state.StateStore;
import com.mesosphere.sdk.state.StateStoreException;
import com.mesosphere.sdk.testutils.CuratorTestUtils;
import org.junit.*;

import java.nio.charset.StandardCharsets;
import java.util.*;

import static org.junit.Assert.*;

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
            .setTaskId(CommonTaskUtils.toTaskId(TASK_NAME))
            .setState(TASK_STATE)
            .build();
    public static final String PROPERTY_VALUE = "DC/OS";
    public static final String GOOD_PROPERTY_KEY = "hey";
    public static final String WHITESPACE_PROPERTY_KEY = "            ";
    public static final String SLASH_PROPERTY_KEY = "hey/hi";

    private static TestingServer testZk;
    private StateStore store;

    @BeforeClass
    public static void beforeAll() throws Exception {
        testZk = new TestingServer();
    }

    @Before
    public void beforeEach() throws Exception {
        CuratorTestUtils.clear(testZk);
        store = new CuratorStateStore(ROOT_ZK_PATH, testZk.getConnectString());
        // Check that schema version was created in the correct location:
        CuratorPersister curator = new CuratorPersister(
                testZk.getConnectString(), new ExponentialBackoffRetry(1000, 3));
        assertNotEquals(0, curator.get("/dcos-service-test-root-path/SchemaVersion").length);
    }

    @After
    public void afterEach() {
        ((CuratorStateStore) store).closeForTesting();
    }

    @Test
    public void testStoreFetchFrameworkId() throws Exception {
        store.storeFrameworkId(FRAMEWORK_ID);
        assertEquals(FRAMEWORK_ID, store.fetchFrameworkId().get());
    }

    @Test
    public void testRootPathMapping() throws Exception {
        store.storeFrameworkId(FRAMEWORK_ID);
        CuratorPersister curator = new CuratorPersister(
                testZk.getConnectString(), new ExponentialBackoffRetry(1000, 3));
        assertNotEquals(0, curator.get("/dcos-service-test-root-path/FrameworkID").length);
    }

    @Test
    public void testFetchEmptyFrameworkId() throws Exception {
        assertFalse(store.fetchFrameworkId().isPresent());
    }

    @Test
    public void testStoreClearFrameworkId() throws Exception {
        store.storeFrameworkId(FRAMEWORK_ID);
        store.clearFrameworkId();
    }

    @Test
    public void testStoreClearFetchFrameworkId() throws Exception {
        store.storeFrameworkId(FRAMEWORK_ID);
        store.clearFrameworkId();
        assertFalse(store.fetchFrameworkId().isPresent());
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
        assertEquals(testTask, store.fetchTask(TASK_NAME).get());
        Collection<Protos.TaskInfo> outTasks = store.fetchTasks();
        assertEquals(1, outTasks.size());
        assertEquals(testTask, outTasks.iterator().next());
    }

    @Test
    public void testFetchMissingTask() throws Exception {
        assertFalse(store.fetchTask(TASK_NAME).isPresent());
    }

    @Test
    public void testFetchEmptyTasks() throws Exception {
        assertTrue(store.fetchTasks().isEmpty());
    }

    @Test
    public void testRepeatedStoreTask() throws Exception {
        Collection<Protos.TaskInfo> tasks = createTasks(TASK_NAME);
        store.storeTasks(tasks);
        assertEquals(tasks.iterator().next(), store.fetchTask(TASK_NAME).get());

        tasks = createTasks(TASK_NAME);
        store.storeTasks(tasks);
        assertEquals(tasks.iterator().next(), store.fetchTask(TASK_NAME).get());

        Collection<String> taskNames = store.fetchTaskNames();
        assertEquals(1, taskNames.size());
        assertEquals(tasks, store.fetchTasks());
    }

    @Test
    public void testStoreClearTask() throws Exception {
        store.storeTasks(createTasks(TASK_NAME));
        store.clearTask(TASK_NAME);
    }

    @Test
    public void testStoreClearFetchTask() throws Exception {
        store.storeTasks(createTasks(TASK_NAME));
        store.clearTask(TASK_NAME);
        assertFalse(store.fetchTask(TASK_NAME).isPresent());
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

        assertEquals(taskInfoA, store.fetchTask("a").get());
        assertEquals(1, store.fetchTaskNames().size());
        assertEquals("a", store.fetchTaskNames().iterator().next());
        assertEquals(1, store.fetchTasks().size());
        assertEquals(taskInfoA, store.fetchTasks().iterator().next());
        assertTrue(store.fetchStatuses().isEmpty());

        Protos.TaskInfo taskInfoB = createTask("b");
        store.storeTasks(Arrays.asList(taskInfoB));

        assertEquals(taskInfoB, store.fetchTask("b").get());
        assertEquals(2, store.fetchTaskNames().size());
        assertEquals(2, store.fetchTasks().size());
        assertTrue(store.fetchStatuses().isEmpty());

        store.clearTask("a");

        assertEquals(taskInfoB, store.fetchTask("b").get());
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

    // TODO(nickbp): Remove this test once CuratorStateStore no longer speculatively unpacks all stored TaskInfos
    @Test
    public void testStorePackedTask() throws Exception {
        Protos.TaskInfo.Builder origInfoBuilder = createTask("foo").toBuilder();
        origInfoBuilder.getExecutorBuilder().getExecutorIdBuilder().setValue("hi");
        Protos.TaskInfo origInfo = origInfoBuilder.build();

        Protos.TaskInfo packedInfo = CommonTaskUtils.packTaskInfo(origInfo);
        assertFalse(packedInfo.hasCommand());
        assertTrue(packedInfo.hasExecutor());
        assertTrue(packedInfo.hasData());
        store.storeTasks(Arrays.asList(packedInfo));

        // result shold be unpacked automatically:
        Protos.TaskInfo retrievedInfo = store.fetchTask("foo").get();
        assertEquals(origInfo, retrievedInfo);
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
        assertEquals(status, store.fetchStatus(TASK_NAME).get());
        Collection<Protos.TaskStatus> statuses = store.fetchStatuses();
        assertEquals(1, statuses.size());
        assertEquals(status, statuses.iterator().next());
    }

    @Test
    public void testFetchMissingStatus() throws Exception {
        assertTrue(!store.fetchStatus(TASK_NAME).isPresent());
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
        assertEquals(status, store.fetchStatus(TASK_NAME).get());

        store.storeStatus(status);
        assertEquals(status, store.fetchStatus(TASK_NAME).get());

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

        assertEquals(taskStatusA, store.fetchStatus("a").get());
        assertEquals(2, store.fetchTaskNames().size());
        assertEquals("a", store.fetchTaskNames().iterator().next());
        assertEquals(1, store.fetchStatuses().size());
        assertEquals(taskStatusA, store.fetchStatuses().iterator().next());
        assertEquals(2, store.fetchTasks().size());

        Protos.TaskStatus taskStatusB = createTaskStatus(taskB.getTaskId());
        store.storeStatus(taskStatusB);

        assertEquals(taskStatusB, store.fetchStatus("b").get());
        assertEquals(2, store.fetchTaskNames().size());
        assertEquals(2, store.fetchStatuses().size());
        assertEquals(2, store.fetchTasks().size());

        store.clearTask("a");

        assertEquals(taskStatusB, store.fetchStatus("b").get());
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
        assertEquals(status, store.fetchStatus(TASK_NAME).get());

        // change the taskinfo id:
        Protos.TaskInfo taskNewId = createTask(TASK_NAME);
        store.storeTasks(Arrays.asList(taskNewId));
        // send a new status whose id matches the updated taskinfo id:
        Protos.TaskStatus statusNewId = TASK_STATUS.toBuilder().setTaskId(taskNewId.getTaskId()).build();
        store.storeStatus(statusNewId);
        assertEquals(statusNewId, store.fetchStatus(TASK_NAME).get());
    }

    @Test(expected=StateStoreException.class)
    public void testStoreStatusFailsOnUUIDChangeWithoutTaskInfoUpdate() throws Exception {
        Protos.TaskInfo task = createTask(TASK_NAME);
        store.storeTasks(Arrays.asList(task));

        // taskstatus id must exactly match taskinfo id:
        Protos.TaskStatus status = TASK_STATUS.toBuilder().setTaskId(task.getTaskId()).build();
        store.storeStatus(status);
        assertEquals(status, store.fetchStatus(TASK_NAME).get());

        // change the taskinfo id:
        Protos.TaskInfo taskNewId = createTask(TASK_NAME);
        store.storeTasks(Arrays.asList(taskNewId));
        // send a new status whose id doesn't match the updated taskinfo id:
        store.storeStatus(status);
    }

    // taskid is required and cannot be unset, so lets try the next best thing
    @Test(expected=StateStoreException.class)
    public void testStoreStatusEmptyTaskId() throws Exception {
        store.storeStatus(createTaskStatus(CommonTaskUtils.emptyTaskId()));
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
        assertEquals(testStatus, store.fetchStatus(TASK_NAME).get());
    }

    @Test
    public void testStoreInfoThenStatus() throws Exception {
        assertTrue(store.fetchTaskNames().isEmpty());
        assertTrue(store.fetchTasks().isEmpty());
        assertTrue(store.fetchStatuses().isEmpty());

        Protos.TaskInfo taskInfoA = createTask("a");
        store.storeTasks(Arrays.asList(taskInfoA));

        assertEquals(taskInfoA, store.fetchTask("a").get());
        assertEquals(1, store.fetchTaskNames().size());
        assertEquals("a", store.fetchTaskNames().iterator().next());
        assertTrue(store.fetchStatuses().isEmpty());
        assertEquals(1, store.fetchTasks().size());
        assertEquals(taskInfoA, store.fetchTasks().iterator().next());

        // task id must exactly match:
        Protos.TaskStatus taskStatusA = createTaskStatus(taskInfoA.getTaskId());
        store.storeStatus(taskStatusA);

        assertEquals(taskStatusA, store.fetchStatus("a").get());
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
    public void testPropertiesStoreFetchListClear() {
        store.storeProperty(GOOD_PROPERTY_KEY, PROPERTY_VALUE.getBytes(StandardCharsets.UTF_8));

        final byte[] bytes = store.fetchProperty(GOOD_PROPERTY_KEY);
        assertEquals(PROPERTY_VALUE, new String(bytes, StandardCharsets.UTF_8));

        final Collection<String> keys = store.fetchPropertyKeys();
        assertEquals(1, keys.size());
        assertEquals(GOOD_PROPERTY_KEY, keys.iterator().next());

        store.clearProperty(GOOD_PROPERTY_KEY);
        assertTrue(store.fetchPropertyKeys().isEmpty());
    }

    @Test
    public void testPropertiesListEmpty() {
        assertTrue(store.fetchPropertyKeys().isEmpty());
    }

    @Test
    public void testPropertiesClearEmpty() {
        store.clearProperty(GOOD_PROPERTY_KEY);
    }

    @Test(expected = StateStoreException.class)
    public void testPropertiesStoreWhitespace() {
        store.storeProperty(
                WHITESPACE_PROPERTY_KEY, PROPERTY_VALUE.getBytes(StandardCharsets.UTF_8));
    }

    @Test(expected = StateStoreException.class)
    public void testPropertiesFetchWhitespace() {
        store.fetchProperty(WHITESPACE_PROPERTY_KEY);
    }

    @Test(expected = StateStoreException.class)
    public void testPropertiesClearWhitespace() {
        store.clearProperty(WHITESPACE_PROPERTY_KEY);
    }

    @Test(expected = StateStoreException.class)
    public void testPropertiesStoreSlash() {
        store.storeProperty(SLASH_PROPERTY_KEY, PROPERTY_VALUE.getBytes(StandardCharsets.UTF_8));
    }

    @Test(expected = StateStoreException.class)
    public void testPropertiesFetchSlash() {
        store.fetchProperty(SLASH_PROPERTY_KEY);
    }

    @Test(expected = StateStoreException.class)
    public void testPropertiesClearSlash() {
        store.clearProperty(SLASH_PROPERTY_KEY);
    }

    @Test(expected = StateStoreException.class)
    public void testPropertiesStoreNullValue() {
        store.storeProperty(GOOD_PROPERTY_KEY, null);
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
                .setTaskId(CommonTaskUtils.toTaskId(taskName))
                .setSlaveId(SlaveID.newBuilder().setValue("ignored")) // proto field required
                .build();
    }
}
