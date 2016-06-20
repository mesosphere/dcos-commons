package org.apache.mesos.state;

import static org.junit.Assert.*;

import org.apache.curator.test.TestingServer;
import org.apache.mesos.Protos;
import org.junit.Before;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;

/**
 * Tests to validate the operation of the {@link CuratorStateStore}.
 */
public class CuratorStateStoreTest {
    private static final String FRAMEWORK_ID = "test-framework-id";
    private static final String TASK_NAME = "test-task-name";
    private static final String TASK_ID = "test-task-id";
    private static final String SLAVE_ID = "test-slave-id";
    private static final String EXECUTOR_NAME = "test-executor-name";
    private static final String ROOT_ZK_PATH = "/test-root-path";

    private TestingServer testZk;
    private CuratorStateStore store;

    @Before
    public void beforeEach() throws Exception {
        testZk = new TestingServer();
        store = getTestStateStore();
    }

    @Test
    public void testStoreFetchFrameworkId() throws Exception {
        Protos.FrameworkID fwkId = getTestFrameworkId();
        store.storeFrameworkId(fwkId);
        assertEquals(fwkId, store.fetchFrameworkId());
    }

    @Test(expected=StateStoreException.class)
    public void testFetchEmptyFrameworkId() throws Exception {
        store.fetchFrameworkId();
    }

    @Test
    public void testStoreClearFrameworkId() throws Exception {
        Protos.FrameworkID fwkId = getTestFrameworkId();
        store.storeFrameworkId(fwkId);
        store.clearFrameworkId();
    }

    @Test(expected=StateStoreException.class)
    public void testStoreClearFetchFrameworkId() throws Exception {
        store.storeFrameworkId(getTestFrameworkId());
        store.clearFrameworkId();
        store.fetchFrameworkId(); // throws
    }

    @Test
    public void testClearEmptyFrameworkId() throws Exception {
        store.clearFrameworkId();
    }

    @Test
    public void testStoreFetchTask() throws Exception {
        store.storeTasks(getTestTasks(), getTestExecutorName());
        Collection<Protos.TaskInfo> outTasks = store.fetchTasks(getTestExecutorName());
        assertEquals(1, outTasks.size());
        assertEquals(getTestTask(), outTasks.iterator().next());
    }

    @Test(expected=StateStoreException.class)
    public void testFetchEmptyTask() throws Exception {
        store.fetchTasks(getTestExecutorName());
    }

    @Test
    public void testRepeatedStoreTask() throws Exception {
        store.storeTasks(getTestTasks(), getTestExecutorName());
        store.storeTasks(getTestTasks(), getTestExecutorName());
    }

    @Test
    public void testStoreClearExecutor() throws Exception {
        store.storeTasks(getTestTasks(), getTestExecutorName());
        store.clearExecutor(getTestExecutorName());
    }

    @Test(expected=StateStoreException.class)
    public void testStoreClearFetchExecutor() throws Exception {
        store.storeTasks(getTestTasks(), getTestExecutorName());
        store.clearExecutor(getTestExecutorName());
        store.fetchTasks(getTestExecutorName());
    }

    @Test
    public void testClearEmptyExecutor() throws Exception {
        store.clearExecutor(getTestExecutorName());
    }

    @Test
    public void testFetchEmptyExecutors() throws Exception {
        Collection<String> execNames = store.fetchExecutorNames();
        assertEquals(0, execNames.size());
    }

    @Test
    public void testFetchExecutors() throws Exception {
        String testExecutorNamePrefix = "test-executor";
        String testExecutorName0 = testExecutorNamePrefix + "-0";
        String testExecutorName1 = testExecutorNamePrefix + "-1";

        store.storeTasks(getTestTasks(), testExecutorName0);
        store.storeTasks(getTestTasks(), testExecutorName1);
        Collection<String> execNames = store.fetchExecutorNames();
        assertEquals(2, execNames.size());

        Iterator<String> iter =  execNames.iterator();
        assertEquals(testExecutorName1, iter.next());
        assertEquals(testExecutorName0, iter.next());
    }

    @Test
    public void testFetchExecutorsWithFrameworkIdSet() throws Exception {
        String testExecutorNamePrefix = "test-executor";
        String testExecutorName0 = testExecutorNamePrefix + "-0";
        String testExecutorName1 = testExecutorNamePrefix + "-1";

        store.storeFrameworkId(getTestFrameworkId());
        store.storeTasks(getTestTasks(), testExecutorName0);
        store.storeTasks(getTestTasks(), testExecutorName1);
        Collection<String> execNames = store.fetchExecutorNames();
        assertEquals(2, execNames.size()); // framework id set above mustn't be included

        Iterator<String> iter =  execNames.iterator();
        assertEquals(testExecutorName1, iter.next());
        assertEquals(testExecutorName0, iter.next());
    }

    @Test
    public void testStoreFetchStatus() throws Exception {
        store.storeTasks(getTestTasks(), getTestExecutorName());
        store.storeStatus(getTestTaskStatus(), TASK_NAME);
        assertEquals(getTestTaskStatus(), store.fetchStatus(TASK_NAME, getTestExecutorName()));
    }

    @Test(expected=StateStoreException.class)
    public void testFetchEmptyStatus() throws Exception {
        store.fetchStatus(TASK_NAME, getTestExecutorName());
    }

    @Test
    public void testRepeatedStoreStatus() throws Exception {
        store.storeTasks(getTestTasks(), getTestExecutorName());
        store.storeStatus(getTestTaskStatus(), TASK_NAME);
        store.storeStatus(getTestTaskStatus(), TASK_NAME);
    }

    @Test(expected=StateStoreException.class)
    public void testStoreIncorrectStatus() throws Exception {
        store.storeTasks(getTestTasks(), getTestExecutorName());
        Protos.TaskStatus badTaskStatus = Protos.TaskStatus.newBuilder()
                .setTaskId(Protos.TaskID.newBuilder().setValue("bad-test-id"))
                .setState(getTestTaskState())
                .build();

        store.storeStatus(badTaskStatus, TASK_NAME);
    }

    @Test(expected=StateStoreException.class)
    public void testStoreStatusWithoutTaskInfo() throws Exception {
        store.storeStatus(getTestTaskStatus(), TASK_NAME);
    }

    @Test
    public void testStoreFetchTaskAndStatus() throws Exception {
        store.storeTasks(getTestTasks(), getTestExecutorName());
        Collection<Protos.TaskInfo> outTasks = store.fetchTasks(getTestExecutorName());
        assertEquals(1, outTasks.size());
        assertEquals(getTestTask(), outTasks.iterator().next());

        store.storeStatus(getTestTaskStatus(), TASK_NAME);
        assertEquals(getTestTaskStatus(), store.fetchStatus(TASK_NAME, getTestExecutorName()));
    }

    private static Protos.FrameworkID getTestFrameworkId() {
        return Protos.FrameworkID.newBuilder().setValue(FRAMEWORK_ID).build();
    }

    private static Protos.TaskStatus getTestTaskStatus() {
        return Protos.TaskStatus.newBuilder()
                .setTaskId(getTestTaskId())
                .setState(getTestTaskState())
                .build();
    }

    private static Protos.TaskID getTestTaskId() {
        return Protos.TaskID.newBuilder().setValue(TASK_ID).build();
    }

    private static Collection<Protos.TaskInfo> getTestTasks() {
        return Arrays.asList(getTestTask());
    }

    private static Protos.TaskInfo getTestTask() {
        return Protos.TaskInfo.newBuilder()
                .setName(TASK_NAME)
                .setTaskId(getTestTaskId())
                .setSlaveId(Protos.SlaveID.newBuilder().setValue(SLAVE_ID))
                .build();
    }

    private static String getTestExecutorName() {
        return EXECUTOR_NAME;
    }

    private CuratorStateStore getTestStateStore() {
        return new CuratorStateStore(ROOT_ZK_PATH, testZk.getConnectString());
    }

    public static Protos.TaskState getTestTaskState() {
        return Protos.TaskState.TASK_STAGING;
    }
}
