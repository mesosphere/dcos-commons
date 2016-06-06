package org.apache.mesos.state;

import com.netflix.curator.test.TestingServer;

import org.apache.curator.retry.ExponentialBackoffRetry;
import org.apache.mesos.Protos;
import org.junit.Before;
import org.junit.Test;
import org.junit.Assert;

import java.util.Arrays;
import java.util.Collection;

/**
 * Tests to validate the operation of the CuratorStateStore.
 */
public class CuratorStateStoreTest {
    private String testFrameworkId = "test-framework-id";
    private String testTaskName = "test-task-name";
    private String testTaskId = "test-task-id";
    private String testSlaveId = "test-slave-id";
    private String testExecutorId = "test-executor-id";
    private String testRootZkPath = "/test-root-path";
    private ExponentialBackoffRetry retryPolicy = new ExponentialBackoffRetry(1000, 3);
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
        Assert.assertEquals(fwkId, store.fetchFrameworkId());
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
        Protos.FrameworkID fwkId = getTestFrameworkId();
        store.storeFrameworkId(fwkId);
        store.clearFrameworkId();
        store.fetchFrameworkId();
    }

    @Test
    public void testClearEmptyFrameworkId() throws Exception {
        store.clearFrameworkId();
    }

    @Test
    public void testStoreFetchTask() throws Exception {
        store.storeTasks(getTestTasks(), getTestExecutorId());
        Collection<Protos.TaskInfo> outTasks = store.fetchTasks(getTestExecutorId());
        Assert.assertEquals(1, outTasks.size());
        Assert.assertEquals(getTestTask(), outTasks.iterator().next());
    }

    @Test(expected=StateStoreException.class)
    public void testFetchEmptyTask() throws Exception {
       store.fetchTasks(getTestExecutorId());
    }

    @Test
    public void testStoreClearExecutor() throws Exception {
        store.storeTasks(getTestTasks(), getTestExecutorId());
        store.clearExecutor(getTestExecutorId());
    }

    @Test(expected=StateStoreException.class)
    public void testStoreClearFetchExecutor() throws Exception {
        store.storeTasks(getTestTasks(), getTestExecutorId());
        store.clearExecutor(getTestExecutorId());
        store.fetchTasks(getTestExecutorId());
    }

    @Test
    public void testClearEmptyExecutor() throws Exception {
        store.clearExecutor(getTestExecutorId());
    }

    @Test
    public void testStoreFetchStatus() throws Exception {
        store.storeStatus(getTestTaskStatus(), testTaskName, getTestExecutorId());
        Assert.assertEquals(getTestTaskStatus(), store.fetchStatus(testTaskName, getTestExecutorId()));
    }

    @Test(expected=StateStoreException.class)
    public void testFetchEmptyStatus() throws Exception {
        store.fetchStatus(testTaskName, getTestExecutorId());
    }

    @Test
    public void testStoreFetchTaskAndStatus() throws Exception {
        store.storeTasks(getTestTasks(), getTestExecutorId());
        Collection<Protos.TaskInfo> outTasks = store.fetchTasks(getTestExecutorId());
        Assert.assertEquals(1, outTasks.size());
        Assert.assertEquals(getTestTask(), outTasks.iterator().next());

        store.storeStatus(getTestTaskStatus(), testTaskName, getTestExecutorId());
        Assert.assertEquals(getTestTaskStatus(), store.fetchStatus(testTaskName, getTestExecutorId()));
    }

    private Protos.FrameworkID getTestFrameworkId() {
        return Protos.FrameworkID.newBuilder().setValue(testFrameworkId).build();
    }

    private Protos.TaskStatus getTestTaskStatus() {
        return Protos.TaskStatus.newBuilder()
                .setTaskId(getTestTaskId())
                .setState(getTestTaskState())
                .build();
    }

    private Protos.TaskID getTestTaskId() {
        return Protos.TaskID.newBuilder().setValue(testTaskId).build();

    }

    private Collection<Protos.TaskInfo> getTestTasks() {
        return Arrays.asList(getTestTask());
    }

    private Protos.TaskInfo getTestTask() {
        return Protos.TaskInfo.newBuilder()
                .setName(testTaskName)
                .setTaskId(getTestTaskId())
                .setSlaveId(Protos.SlaveID.newBuilder().setValue(testSlaveId))
                .build();
    }

    private Protos.ExecutorID getTestExecutorId() {
        return Protos.ExecutorID.newBuilder().setValue(testExecutorId).build();
    }

    private CuratorStateStore getTestStateStore() {
        return new CuratorStateStore(testRootZkPath, testZk.getConnectString(), retryPolicy);
    }

    public Protos.TaskState getTestTaskState() {
        return Protos.TaskState.TASK_STAGING;
    }
}
