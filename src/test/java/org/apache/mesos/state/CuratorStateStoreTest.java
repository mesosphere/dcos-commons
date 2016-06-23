package org.apache.mesos.state;

import static org.junit.Assert.*;

import org.apache.curator.test.TestingServer;
import org.apache.mesos.Protos;
import org.apache.mesos.executor.ExecutorUtils;
import org.apache.mesos.offer.TaskUtils;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;

/**
 * Tests to validate the operation of the {@link CuratorStateStore}.
 */
public class CuratorStateStoreTest {
    private static final Protos.FrameworkID FRAMEWORK_ID =
            Protos.FrameworkID.newBuilder().setValue("test-framework-id").build();
    private static final String TASK_NAME = "test-task-name";
    private static final String EXECUTOR_NAME = "test-executor-name";
    private static final String ROOT_ZK_PATH = "/test-root-path";
    private static final Protos.TaskState TASK_STATE = Protos.TaskState.TASK_STAGING;
    private static final Protos.TaskStatus TASK_STATUS = Protos.TaskStatus.newBuilder()
            .setTaskId(TaskUtils.toTaskId(TASK_NAME))
            .setExecutorId(ExecutorUtils.toExecutorId(EXECUTOR_NAME))
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

    @Test
    public void testStoreFetchTask() throws Exception {
        Protos.TaskInfo testTask = getTestTask(EXECUTOR_NAME);
        store.storeTasks(Arrays.asList(testTask));
        Collection<Protos.TaskInfo> outTasks = store.fetchTasks(EXECUTOR_NAME);
        assertEquals(1, outTasks.size());
        assertEquals(testTask, outTasks.iterator().next());
    }

    @Test(expected=StateStoreException.class)
    public void testFetchEmptyTasks() throws Exception {
        store.fetchTasks(EXECUTOR_NAME);
    }

    @Test
    public void testStoreFetchCustomExecutorTask() throws Exception {
        Protos.TaskInfo testTask = getTestTask(EXECUTOR_NAME);
        store.storeTasks(Arrays.asList(testTask));
        Protos.TaskInfo outTask = store.fetchTask(TASK_NAME, EXECUTOR_NAME);
        assertEquals(testTask, outTask);
    }
    @Test
    public void testStoreFetchCommandExecutorTask() throws Exception {
        Protos.TaskInfo testTask = getTestTask(EXECUTOR_NAME).toBuilder()
                .clearExecutor()
                .setCommand(Protos.CommandInfo.newBuilder())
                .build();
        store.storeTasks(Arrays.asList(testTask));
        // uses task name as fallback for executor name:
        assertEquals(testTask, store.fetchTask(TASK_NAME, TASK_NAME));
        Collection<String> executors = store.fetchExecutorNames();
        assertEquals(1, executors.size());
        assertEquals(TASK_NAME, executors.iterator().next());
    }

    @Test(expected=StateStoreException.class)
    public void testStoreTaskMissingExecutorName() throws Exception {
        Protos.TaskInfo testTask = getTestTask(EXECUTOR_NAME);
        testTask = testTask.toBuilder()
                .setExecutor(testTask.getExecutor().toBuilder().clearName())
                .build();
        store.storeTasks(Arrays.asList(testTask));
    }

    @Test(expected=StateStoreException.class)
    public void testStoreTaskEmptyExecutorName() throws Exception {
        Protos.TaskInfo testTask = getTestTask(EXECUTOR_NAME);
        testTask = testTask.toBuilder()
                .setExecutor(testTask.getExecutor().toBuilder().setName(""))
                .build();
        store.storeTasks(Arrays.asList(testTask));
    }

    @Test(expected=StateStoreException.class)
    public void testStoreTaskMissingExecutorAndCommand() throws Exception {
        Protos.TaskInfo testTask = getTestTask(EXECUTOR_NAME).toBuilder()
                .clearExecutor()
                .clearCommand()
                .build();
        store.storeTasks(Arrays.asList(testTask));
    }

    @Test(expected=StateStoreException.class)
    public void testFetchEmptyTask() throws Exception {
        store.fetchTask(TASK_NAME, EXECUTOR_NAME);
    }

    @Test
    public void testRepeatedStoreTask() throws Exception {
        store.storeTasks(getTestTasks(EXECUTOR_NAME));
        store.storeTasks(getTestTasks(EXECUTOR_NAME));
    }

    @Test
    public void testStoreClearExecutor() throws Exception {
        store.storeTasks(getTestTasks(EXECUTOR_NAME));
        store.clearExecutor(EXECUTOR_NAME);
    }

    @Test(expected=StateStoreException.class)
    public void testStoreClearFetchExecutor() throws Exception {
        store.storeTasks(getTestTasks(EXECUTOR_NAME));
        store.clearExecutor(EXECUTOR_NAME);
        store.fetchTasks(EXECUTOR_NAME);
    }

    @Test
    public void testClearEmptyExecutor() throws Exception {
        store.clearExecutor(EXECUTOR_NAME);
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

        store.storeTasks(getTestTasks(testExecutorName0));
        store.storeTasks(getTestTasks(testExecutorName1));
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

        store.storeFrameworkId(FRAMEWORK_ID);
        store.storeTasks(getTestTasks(testExecutorName0));
        store.storeTasks(getTestTasks(testExecutorName1));
        Collection<String> execNames = store.fetchExecutorNames();
        assertEquals(2, execNames.size()); // framework id set above mustn't be included

        Iterator<String> iter =  execNames.iterator();
        assertEquals(testExecutorName1, iter.next());
        assertEquals(testExecutorName0, iter.next());
    }

    @Test
    public void testStoreFetchStatus() throws Exception {
        store.storeStatus(TASK_STATUS);
        assertEquals(TASK_STATUS, store.fetchStatus(TASK_NAME, EXECUTOR_NAME));
    }

    @Test(expected=StateStoreException.class)
    public void testFetchEmptyStatus() throws Exception {
        store.fetchStatus(TASK_NAME, EXECUTOR_NAME);
    }

    @Test
    public void testStoreFetchStatuses() throws Exception {
        store.storeStatus(TASK_STATUS);
        Collection<Protos.TaskStatus> statuses = store.fetchStatuses(EXECUTOR_NAME);
        assertEquals(TASK_STATUS, statuses.iterator().next());
    }

    @Test(expected=StateStoreException.class)
    public void testFetchEmptyStatuses() throws Exception {
        store.fetchStatuses(EXECUTOR_NAME);
    }

    @Test
    public void testRepeatedStoreStatus() throws Exception {
        store.storeStatus(TASK_STATUS);

        assertEquals(TASK_STATUS, store.fetchStatus(TASK_NAME, EXECUTOR_NAME));

        store.storeStatus(TASK_STATUS);

        assertEquals(TASK_STATUS, store.fetchStatus(TASK_NAME, EXECUTOR_NAME));
        Collection<String> execNames = store.fetchExecutorNames();
        assertEquals(1, execNames.size());
        Collection<Protos.TaskStatus> statuses = store.fetchStatuses(execNames.iterator().next());
        assertEquals(1, statuses.size());
        assertEquals(TASK_STATUS, statuses.iterator().next());
    }

    @Test
    public void testMultipleStoreStatus() throws Exception {
        Protos.TaskStatus taskStatusA = getTestTaskStatus("a");
        Protos.TaskStatus taskStatusB = getTestTaskStatus("b");

        store.storeStatus(taskStatusA);

        assertEquals(taskStatusA, store.fetchStatus("a", EXECUTOR_NAME));

        store.storeStatus(taskStatusB);

        assertEquals(taskStatusB, store.fetchStatus("b", EXECUTOR_NAME));
        Collection<String> execNames = store.fetchExecutorNames();
        assertEquals(1, execNames.size());
        Collection<Protos.TaskStatus> statuses = store.fetchStatuses(execNames.iterator().next());
        assertEquals(2, statuses.size());
        store.clearExecutor(EXECUTOR_NAME);
        assertEquals(true, store.fetchExecutorNames().isEmpty());
    }

    @Test
    public void testStoreStatusReusesExecutorId() throws Exception {
        store.storeStatus(TASK_STATUS);
        store.storeStatus(TASK_STATUS.toBuilder()
                .clearExecutorId()
                .build());
        assertEquals(TASK_STATUS, store.fetchStatus(TASK_NAME, EXECUTOR_NAME));
    }

    @Test
    public void testStoreStatusOnlyMatchesExactTask() throws Exception {
        // write initial versions with executor id present:

        // original:
        store.storeStatus(TASK_STATUS); // uses EXECUTOR_ID
        // different executor name, same task name:
        Protos.ExecutorID otherExecutorId = ExecutorUtils.toExecutorId("other-executor");
        Protos.TaskStatus otherExecutorStatus = TASK_STATUS.toBuilder()
                .setTaskId(TaskUtils.toTaskId(TASK_NAME)) // same name, different UUID
                .setExecutorId(otherExecutorId)
                .build();
        store.storeStatus(otherExecutorStatus);
        // same executor name, different task name:
        Protos.TaskID otherTaskId = TaskUtils.toTaskId("other-task");
        Protos.TaskStatus otherTaskStatus = TASK_STATUS.toBuilder()
                .setTaskId(otherTaskId)
                .build();
        store.storeStatus(otherTaskStatus);

        // write versions of each with executor id removed:
        store.storeStatus(TASK_STATUS.toBuilder().clearExecutorId().build());
        store.storeStatus(otherExecutorStatus.toBuilder().clearExecutorId().build());
        store.storeStatus(otherTaskStatus.toBuilder().clearExecutorId().build());

        // returned values line up:
        assertEquals(TASK_STATUS, store.fetchStatus(TASK_NAME, EXECUTOR_NAME));
        assertEquals(otherExecutorStatus, store.fetchStatus(TASK_NAME, "other-executor"));
        assertEquals(otherTaskStatus, store.fetchStatus("other-task", EXECUTOR_NAME));
    }

    // exact taskid match is required
    @Test(expected=StateStoreException.class)
    public void testStoreStatusExecutorIdFailsOnUUIDChange() throws Exception {
        store.storeStatus(TASK_STATUS);
        store.storeStatus(TASK_STATUS.toBuilder()
                .setTaskId(TaskUtils.toTaskId(TASK_NAME)) // new UUID should trigger mismatch
                .clearExecutorId()
                .build());
    }

    // taskid is required and cannot be unset, so lets try the next best thing
    @Test(expected=StateStoreException.class)
    public void testStoreStatusEmptyTaskId() throws Exception {
        store.storeStatus(TASK_STATUS.toBuilder()
                .setTaskId(Protos.TaskID.newBuilder().setValue(""))
                .build());
    }

    @Test(expected=StateStoreException.class)
    public void testStoreStatusBadTaskId() throws Exception {
        store.storeStatus(TASK_STATUS.toBuilder()
                .setTaskId(Protos.TaskID.newBuilder().setValue("bad-test-id"))
                .build());
    }

    @Test(expected=StateStoreException.class)
    public void testStoreStatusMissingExecutorId() throws Exception {
        store.storeStatus(TASK_STATUS.toBuilder()
                .clearExecutorId()
                .build());
    }

    @Test(expected=StateStoreException.class)
    public void testStoreStatusBadExecutorId() throws Exception {
        store.storeStatus(TASK_STATUS.toBuilder()
                .setExecutorId(Protos.ExecutorID.newBuilder().setValue("bad-executor-id"))
                .build());
    }

    @Test
    public void testStoreFetchTaskAndStatus() throws Exception {
        Protos.TaskInfo testTask = getTestTask(EXECUTOR_NAME);
        store.storeTasks(Arrays.asList(testTask));
        Collection<Protos.TaskInfo> outTasks = store.fetchTasks(EXECUTOR_NAME);
        assertEquals(1, outTasks.size());
        assertEquals(testTask, outTasks.iterator().next());

        store.storeStatus(TASK_STATUS);
        assertEquals(TASK_STATUS, store.fetchStatus(TASK_NAME, EXECUTOR_NAME));
    }

    /**
     * Note: this regenerates the task_id UUID each time it's called, even if taskName is the same
     */
    private static Protos.TaskStatus getTestTaskStatus(String taskName) {
        return TASK_STATUS.toBuilder().setTaskId(TaskUtils.toTaskId(taskName)).build();
    }

    private static Collection<Protos.TaskInfo> getTestTasks(String... executorNames) {
        List<Protos.TaskInfo> taskInfos = new ArrayList<>();
        for (String executorName : executorNames) {
            taskInfos.add(getTestTask(executorName));
        }
        return taskInfos;
    }

    private static Protos.TaskInfo getTestTask(String executorName) {
        return Protos.TaskInfo.newBuilder()
                .setName(TASK_NAME)
                .setTaskId(TASK_STATUS.getTaskId())
                .setExecutor(Protos.ExecutorInfo.newBuilder()
                        .setExecutorId(ExecutorUtils.toExecutorId(executorName))
                        .setName(executorName)
                        .setCommand(Protos.CommandInfo.newBuilder())) // ignored
                .setSlaveId(Protos.SlaveID.newBuilder().setValue("ignored")) // ignored
                .build();
    }
}
