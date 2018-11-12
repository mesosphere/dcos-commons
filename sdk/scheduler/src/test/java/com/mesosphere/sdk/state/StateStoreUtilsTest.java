package com.mesosphere.sdk.state;

import com.google.common.collect.ImmutableList;
import com.mesosphere.sdk.offer.CommonIdUtils;
import com.mesosphere.sdk.storage.MemPersister;
import com.mesosphere.sdk.storage.Persister;
import com.mesosphere.sdk.testutils.TestConstants;

import org.apache.mesos.Protos;
import org.junit.Before;
import org.junit.Test;

import java.nio.charset.StandardCharsets;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.junit.Assert.assertThat;

/**
 * Tests for {@link StateStoreUtils}.
 */
public class StateStoreUtilsTest {

    private Persister persister;
    private StateStore stateStore;

    @Before
    public void beforeEach() throws Exception {
        this.persister = MemPersister.newBuilder().build();
        this.stateStore = new StateStore(this.persister);
    }

    @Test
    public void testEmptyStateStoreReturnsEmptyArray() {
        byte[] result = StateStoreUtils.fetchPropertyOrEmptyArray(stateStore, "UNDEFINED");

        assertThat(result.length, is(0));
    }

    @Test
    public void testUnmatchedKeyReturnsEmptyArray() {
        stateStore.storeProperty("DEFINED", "VALUE".getBytes(StandardCharsets.UTF_8));

        byte[] result = StateStoreUtils.fetchPropertyOrEmptyArray(stateStore, "UNDEFINED");

        assertThat(result.length, is(0));
    }

    @Test
    public void testMatchedKeyReturnsValue() {
        stateStore.storeProperty("DEFINED", "VALUE".getBytes(StandardCharsets.UTF_8));

        byte[] result = StateStoreUtils.fetchPropertyOrEmptyArray(stateStore, "DEFINED");

        assertThat(result, is("VALUE".getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    public void testEmptyStateStoreHasNoLastCompletedUpdateType() {
        assertThat(StateStoreUtils.getDeploymentWasCompleted(stateStore), is(false));
    }

    @Test
    public void testLastCompletedUpdateTypeGetsSet() {
        StateStoreUtils.setDeploymentWasCompleted(stateStore);
        assertThat(StateStoreUtils.getDeploymentWasCompleted(stateStore), is(true));
    }

    @Test
    public void testEmptyStateStoreHasNoTaskStatusAsProperty() {
        assertThat(StateStoreUtils.getTaskStatusFromProperty(stateStore, "test-task").isPresent(), is(false));
    }

    @Test
    public void testMismatchInTaskNameReturnsNoTaskStatusAsProperty() {
        final Protos.TaskInfo taskInfo = newTaskInfo("test-task");
        final Protos.TaskStatus taskStatus = newTaskStatus(taskInfo, Protos.TaskState.TASK_UNKNOWN);
        StateStoreUtils.storeTaskStatusAsProperty(stateStore, "test-task", taskStatus);
        assertThat(StateStoreUtils.getTaskStatusFromProperty(stateStore, "not-test-task").isPresent(), is(false));
    }

    @Test
    public void testTaskStatusAsPropertyIsSetAndReturned() {
        final Protos.TaskInfo taskInfo = newTaskInfo("test-task");
        final Protos.TaskStatus taskStatus = newTaskStatus(taskInfo, Protos.TaskState.TASK_UNKNOWN);
        StateStoreUtils.storeTaskStatusAsProperty(stateStore, "test-task", taskStatus);

        assertThat(StateStoreUtils.getTaskStatusFromProperty(stateStore, "test-task").get(), is(taskStatus));
    }

    @Test
    public void testStateStoreWithSingleStateReturnsTaskInfo() {
        // Create task info
        Protos.TaskInfo taskInfo = newTaskInfo();

        // Add a task to the state store
        stateStore.storeTasks(ImmutableList.of(taskInfo));

        Protos.TaskStatus taskStatus = newTaskStatus(taskInfo, Protos.TaskState.TASK_UNKNOWN);

        assertThat(StateStoreUtils.fetchTaskInfo(stateStore, taskStatus), is(taskInfo));
    }

    @Test(expected = StateStoreException.class)
    public void testEmptyStateStoreRaisesErrorOnTaskName() {
        StateStoreUtils.fetchTaskInfo(stateStore, newTaskStatus(
                CommonIdUtils.toTaskId(TestConstants.SERVICE_NAME, TestConstants.TASK_NAME),
                Protos.TaskState.TASK_UNKNOWN));
    }

    @Test(expected = StateStoreException.class)
    public void testStateStoreWithMismatchedIdRaisesErrorOnStatus() {

        // Create task info
        Protos.TaskInfo taskInfo = newTaskInfo(TestConstants.TASK_NAME);

        // Add a task to the state store
        stateStore.storeTasks(ImmutableList.of(taskInfo));

        Protos.TaskStatus taskStatus = newTaskStatus(
                CommonIdUtils.toTaskId(TestConstants.SERVICE_NAME, "not-" + TestConstants.TASK_NAME),
                Protos.TaskState.TASK_UNKNOWN);

        assertThat(taskInfo.getTaskId(), is(not(taskStatus.getTaskId())));
        StateStoreUtils.fetchTaskInfo(stateStore, taskStatus);
    }

    @Test
    public void testRepairTaskIdsNothingNeeded() {
        Protos.TaskInfo taskInfo = newTaskInfo(TestConstants.TASK_NAME);
        stateStore.storeTasks(ImmutableList.of(taskInfo));
        Protos.TaskStatus taskStatus = newTaskStatus(taskInfo.getTaskId(), Protos.TaskState.TASK_UNKNOWN);
        stateStore.storeStatus(TestConstants.TASK_NAME, taskStatus);

        StateStoreUtils.repairTaskIDs(stateStore);

        assertThat(stateStore.fetchTask(TestConstants.TASK_NAME).get(), is(taskInfo));
        assertThat(stateStore.fetchStatus(TestConstants.TASK_NAME).get(), is(taskStatus));
    }

    @Test
    public void testRepairTaskIdsMissingStatus() {
        Protos.TaskInfo taskInfo = newTaskInfo(TestConstants.TASK_NAME);
        stateStore.storeTasks(ImmutableList.of(taskInfo));

        StateStoreUtils.repairTaskIDs(stateStore);

        assertThat(stateStore.fetchTask(TestConstants.TASK_NAME).get(), is(taskInfo));
        Protos.TaskStatus status = stateStore.fetchStatus(TestConstants.TASK_NAME).get();
        assertThat(status.getState(), is(Protos.TaskState.TASK_FAILED));
        assertThat(status.getTaskId(), is(taskInfo.getTaskId()));
    }

    @Test
    public void testRepairTaskIdsMismatchedIds() {
        Protos.TaskInfo taskInfo = newTaskInfo(TestConstants.TASK_NAME);
        stateStore.storeTasks(ImmutableList.of(taskInfo));
        Protos.TaskID taskID = CommonIdUtils.toTaskId(TestConstants.SERVICE_NAME, "not-" + TestConstants.TASK_NAME);
        Protos.TaskStatus taskStatus = newTaskStatus(taskID, Protos.TaskState.TASK_UNKNOWN);
        stateStore.storeStatus(TestConstants.TASK_NAME, taskStatus);

        StateStoreUtils.repairTaskIDs(stateStore);

        // Protos.TaskInfo was updated to match Status' id:
        assertThat(stateStore.fetchTask(TestConstants.TASK_NAME).get().getTaskId(), is(taskID));
        // Protos.TaskStatus was updated to have failed status:
        Protos.TaskStatus status = stateStore.fetchStatus(TestConstants.TASK_NAME).get();
        assertThat(status.getState(), is(Protos.TaskState.TASK_FAILED));
        assertThat(status.getTaskId(), is(taskID));
    }

    @Test
    public void testRepairTaskIdsMismatchedIdsEmptyId() {
        Protos.TaskInfo taskInfo = newTaskInfo(TestConstants.TASK_NAME);
        stateStore.storeTasks(ImmutableList.of(taskInfo));
        Protos.TaskID taskID = Protos.TaskID.newBuilder().setValue("").build();
        Protos.TaskStatus taskStatus = newTaskStatus(taskID, Protos.TaskState.TASK_UNKNOWN);
        stateStore.storeStatus(TestConstants.TASK_NAME, taskStatus);

        StateStoreUtils.repairTaskIDs(stateStore);

        // Protos.TaskInfo was updated to match Status' id:
        assertThat(stateStore.fetchTask(TestConstants.TASK_NAME).get().getTaskId(), is(taskID));
        // Protos.TaskStatus state was not updated due to empty id:
        Protos.TaskStatus status = stateStore.fetchStatus(TestConstants.TASK_NAME).get();
        assertThat(status.getState(), is(Protos.TaskState.TASK_UNKNOWN));
        assertThat(status.getTaskId(), is(taskID));
    }

    @Test
    public void testEmptyStateStoreIsNotUninstalling() {
        assertThat(StateStoreUtils.isUninstalling(stateStore), is(false));
    }

    @Test
    public void testSetUninstalling() {
        StateStoreUtils.setUninstalling(stateStore);
        assertThat(StateStoreUtils.isUninstalling(stateStore), is(true));
    }

    @Test
    public void testEmptyStateStoreValueIsFalse() {
        final String key = "empty_value";
        stateStore.storeProperty(key, new byte[0]);
        assertThat(StateStoreUtils.fetchBooleanProperty(stateStore, key), is(false));
    }

    @Test
    public void testFalseIsFalse() {
        final String key = "false";
        stateStore.storeProperty(key, key.getBytes(StandardCharsets.UTF_8));
        assertThat(StateStoreUtils.fetchBooleanProperty(stateStore, key), is(false));
    }

    @Test
    public void testTrueIsTrue() {
        final String key = "true";
        stateStore.storeProperty(key, key.getBytes(StandardCharsets.UTF_8));
        assertThat(StateStoreUtils.fetchBooleanProperty(stateStore, key), is(true));
    }

    @Test(expected = StateStoreException.class)
    public void testInvalidBooleanRaisesError() {
        final String key = "invalid_value";
        stateStore.storeProperty(key, "horses".getBytes(StandardCharsets.UTF_8));
        StateStoreUtils.fetchBooleanProperty(stateStore, key);
    }

    private static Protos.TaskInfo newTaskInfo() {
        return newTaskInfo("test-task");
    }

    private static Protos.TaskInfo newTaskInfo(final String taskName) {
        return newTaskInfo(taskName, CommonIdUtils.toTaskId(TestConstants.SERVICE_NAME, taskName));
    }

    private static Protos.TaskInfo newTaskInfo(final String taskName, final Protos.TaskID taskID) {
        Protos.TaskInfo.Builder taskBuilder = Protos.TaskInfo.newBuilder()
                .setName(taskName)
                .setTaskId(taskID);
        taskBuilder.getSlaveIdBuilder().setValue("proto-field-required");
        return taskBuilder.build();
    }

    public static Protos.TaskStatus newTaskStatus(final Protos.TaskInfo taskInfo, final Protos.TaskState taskState) {
        return newTaskStatus(taskInfo.getTaskId(), taskState);
    }

    private static Protos.TaskStatus newTaskStatus(final Protos.TaskID taskID, final Protos.TaskState taskState) {
        return Protos.TaskStatus.newBuilder()
                .setTaskId(taskID)
                .setState(taskState)
                .build();
    }
}
