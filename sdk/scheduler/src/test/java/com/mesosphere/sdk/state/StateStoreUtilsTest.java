package com.mesosphere.sdk.state;

import com.google.common.collect.ImmutableList;
import com.mesosphere.sdk.config.ConfigurationUpdater.UpdateResult.DeploymentType;
import com.mesosphere.sdk.offer.CommonIdUtils;
import com.mesosphere.sdk.offer.TaskException;
import com.mesosphere.sdk.offer.taskdata.TaskLabelWriter;
import com.mesosphere.sdk.scheduler.SchedulerFlags;
import com.mesosphere.sdk.specification.DefaultServiceSpec;
import com.mesosphere.sdk.specification.ServiceSpec;
import com.mesosphere.sdk.specification.yaml.RawServiceSpec;
import com.mesosphere.sdk.storage.MemPersister;
import com.mesosphere.sdk.storage.Persister;
import com.mesosphere.sdk.testutils.OfferRequirementTestUtils;
import org.apache.mesos.Protos.SlaveID;
import org.apache.mesos.Protos.TaskID;
import org.apache.mesos.Protos.TaskInfo;
import org.apache.mesos.Protos.TaskState;
import org.apache.mesos.Protos.TaskStatus;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.hamcrest.Matchers.empty;
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
        this.persister = new MemPersister();
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

    @Test(expected = StateStoreException.class)
    public void testEmptyStateStoreRaisesErrorOnTaskInfo() {
        StateStoreUtils.getTaskInfo(stateStore, null);
    }


    @Test
    public void testEmptyStateStoreHasNoLastCompletedUpdateType() {
        assertThat(StateStoreUtils.getLastCompletedUpdateType(stateStore), is(DeploymentType.NONE));
    }


    @Test
    public void testLastCompletedUpdateTypeGetsSet() {
        StateStoreUtils.setLastCompletedUpdateType(stateStore, DeploymentType.DEPLOY);
        assertThat(StateStoreUtils.getLastCompletedUpdateType(stateStore), is(DeploymentType.DEPLOY));
    }


    @Test
    public void testEmptyStateStoreHasNoTaskStatusAsProperty() {
        assertThat(StateStoreUtils.getTaskStatusFromProperty(stateStore, "test-task").isPresent(), is(false));
    }


    @Test
    public void testMismatchInTaskNameReturnsNoTaskStatusAsProperty() {
        final TaskInfo taskInfo = newTaskInfo("test-task");
        final TaskStatus taskStatus = newTaskStatus(taskInfo, TaskState.TASK_UNKNOWN);
        StateStoreUtils.storeTaskStatusAsProperty(stateStore, "test-task", taskStatus);
        assertThat(StateStoreUtils.getTaskStatusFromProperty(stateStore, "not-test-task").isPresent(), is(false));
    }


    @Test
    public void testTaskStatusAsPropertyIsSetAndReturned() {
        final TaskInfo taskInfo = newTaskInfo("test-task");
        final TaskStatus taskStatus = newTaskStatus(taskInfo, TaskState.TASK_UNKNOWN);
        StateStoreUtils.storeTaskStatusAsProperty(stateStore, "test-task", taskStatus);

        assertThat(StateStoreUtils.getTaskStatusFromProperty(stateStore, "test-task").get(), is(taskStatus));
    }


    @Test
    public void testStateStoreWithSingleStateReturnsTaskInfo() {
        // Create task info
        TaskInfo taskInfo = newTaskInfo();

        // Add a task to the state store
        stateStore.storeTasks(ImmutableList.of(taskInfo));

        TaskStatus taskStatus = newTaskStatus(taskInfo, TaskState.TASK_UNKNOWN);

        assertThat(StateStoreUtils.getTaskInfo(stateStore, taskStatus), is(taskInfo));
    }


    @Test(expected = StateStoreException.class)
    public void testStateStoreWithDuplicateIdsRaisesErrorOnStatus() {
        // Create task info
        TaskInfo taskInfo = newTaskInfo("task_1");
        TaskInfo secondTask = newTaskInfo("task_2", taskInfo.getTaskId());

        // Add a task to the state store
        stateStore.storeTasks(ImmutableList.of(taskInfo, secondTask));

        TaskStatus taskStatus = newTaskStatus(taskInfo, TaskState.TASK_UNKNOWN);

        assertThat(stateStore.fetchTasks().size(), is(2));
        StateStoreUtils.getTaskInfo(stateStore, taskStatus);
    }

    @Test(expected = StateStoreException.class)
    public void testStateStoreWithMismatchedIdRaisesErrorOnStatus() {
        final String taskName = "test-task";

        // Create task info
        TaskInfo taskInfo = newTaskInfo(taskName);

        // Add a task to the state store
        stateStore.storeTasks(ImmutableList.of(taskInfo));

        TaskStatus taskStatus = newTaskStatus(CommonIdUtils.toTaskId("not-" + taskName), TaskState.TASK_UNKNOWN);

        assertThat(taskInfo.getTaskId(), is(not(taskStatus.getTaskId())));
        StateStoreUtils.getTaskInfo(stateStore, taskStatus);
    }


    @Test
    public void testEmptyStateStoreHasNoTasksNeedingRecovery() throws TaskException {
        assertThat(StateStoreUtils.fetchTasksNeedingRecovery(stateStore, null), is(empty()));
    }


    @Test
    public void testTaskWithNoStatusDoesNotNeedRecovery() throws TaskException {
        // Create task info
        TaskInfo taskInfo = newTaskInfo();

        // Add a task to the state store
        stateStore.storeTasks(ImmutableList.of(taskInfo));

        assertThat(StateStoreUtils.fetchTasksNeedingRecovery(stateStore, null), is(empty()));

    }

    @Test
    public void testRunningTaskDoesNotNeedRecoveryIfRunning() throws Exception {

        ConfigStore<ServiceSpec> configStore = newConfigStore(persister);

        // Create task info
        TaskInfo taskInfo = newTaskInfo("name-0-node", configStore);

        // Add a task to the state store
        stateStore.storeTasks(ImmutableList.of(taskInfo));

        TaskStatus taskStatus = newTaskStatus(taskInfo, TaskState.TASK_RUNNING);
        stateStore.storeStatus(taskStatus);

        assertThat(StateStoreUtils.fetchTasksNeedingRecovery(stateStore, configStore), is(empty()));
    }

    @Test
    public void testRunningTaskNeedsRecoveryIfFailed() throws Exception {

        ConfigStore<ServiceSpec> configStore = newConfigStore(persister);

        // Create task info
        TaskInfo taskInfo = newTaskInfo("name-0-node", configStore);

        // Add a task to the state store
        stateStore.storeTasks(ImmutableList.of(taskInfo));

        TaskStatus taskStatus = newTaskStatus(taskInfo, TaskState.TASK_FAILED);
        stateStore.storeStatus(taskStatus);

        assertThat(StateStoreUtils.fetchTasksNeedingRecovery(stateStore, configStore),
                is(ImmutableList.of(taskInfo)));
    }


    @Test(expected = TaskException.class)
    public void testNonPresentTaskRaisesError() throws Exception {

        ConfigStore<ServiceSpec> configStore = newConfigStore(persister);

        // Create task info
        TaskInfo taskInfo = newTaskInfo("name-0-not-present", configStore);

        // Add a task to the state store
        stateStore.storeTasks(ImmutableList.of(taskInfo));

        TaskStatus taskStatus = newTaskStatus(taskInfo, TaskState.TASK_RUNNING);
        stateStore.storeStatus(taskStatus);

        StateStoreUtils.fetchTasksNeedingRecovery(stateStore, configStore);
    }

    @Test
    public void testTaskInfoWithNoStatusRequiresNoRecovery() throws Exception {

        ConfigStore<ServiceSpec> configStore = newConfigStore(persister);

        // Create task info
        TaskInfo taskInfo = newTaskInfo("name-0-not-present", configStore);

        // Add a task to the state store
        stateStore.storeTasks(ImmutableList.of(taskInfo));

        assertThat(StateStoreUtils.fetchTasksNeedingRecovery(stateStore, configStore), is(empty()));
    }


    @Test
    public void testFinishedTaskDoesNotNeedRecoveryIfFailed() throws Exception {

        ConfigStore<ServiceSpec> configStore = newConfigStore(persister);

        // Create task info
        TaskInfo taskInfo = newTaskInfo("name-0-format", configStore);

        // Add a task to the state store
        stateStore.storeTasks(ImmutableList.of(taskInfo));

        TaskStatus taskStatus = newTaskStatus(taskInfo, TaskState.TASK_FAILED);
        stateStore.storeStatus(taskStatus);

        assertThat(StateStoreUtils.fetchTasksNeedingRecovery(stateStore, configStore),
                is(empty()));
    }

    @Test
    public void testFinishedTaskDoesNotNeedRecoveryIfFinished() throws Exception {


        ConfigStore<ServiceSpec> configStore = newConfigStore(persister);

        // Create task info
        TaskInfo taskInfo = newTaskInfo("name-0-format", configStore);

        // Add a task to the state store
        stateStore.storeTasks(ImmutableList.of(taskInfo));

        TaskStatus taskStatus = newTaskStatus(taskInfo, TaskState.TASK_FINISHED);
        stateStore.storeStatus(taskStatus);

        assertThat(StateStoreUtils.fetchTasksNeedingRecovery(stateStore, configStore),
                is(empty()));
    }

    @Test
    public void testFinishedTaskDoesNotNeedRecoveryIfRunning() throws Exception {

        ConfigStore<ServiceSpec> configStore = newConfigStore(persister);

        // Create task info
        TaskInfo taskInfo = newTaskInfo("name-0-format", configStore);

        // Add a task to the state store
        stateStore.storeTasks(ImmutableList.of(taskInfo));


        TaskStatus taskStatus = newTaskStatus(taskInfo, TaskState.TASK_RUNNING);
        stateStore.storeStatus(taskStatus);

        assertThat(StateStoreUtils.fetchTasksNeedingRecovery(stateStore, configStore),
                is(empty()));
    }


    @Test
    public void testEmptyStateStoreIsNotSuppressed() {
        assertThat(StateStoreUtils.isSuppressed(stateStore), is(false));
    }

    @Test
    public void testToggleSuppressed() {
        StateStoreUtils.setSuppressed(stateStore, true);
        assertThat(StateStoreUtils.isSuppressed(stateStore), is(true));

        StateStoreUtils.setSuppressed(stateStore, false);
        assertThat(StateStoreUtils.isSuppressed(stateStore), is(false));
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


    private static TaskInfo newTaskInfo(final String taskName,
                                        final ConfigStore<ServiceSpec> configStore)
            throws ConfigStoreException {
        TaskInfo.Builder taskInfoBuilder = newTaskInfo(taskName).toBuilder();

        // POD type
        final UUID targetConfig = configStore.getTargetConfig();
        final int podIndex = 0;
        final String podType = configStore.fetch(targetConfig).getPods().get(podIndex).getType();

        // create default labels:
        taskInfoBuilder.setLabels(new TaskLabelWriter(taskInfoBuilder)
                .setTargetConfiguration(targetConfig)
                .setType(podType)
                .setIndex(podIndex)
                .toProto());

        return taskInfoBuilder.build();
    }

    private static TaskInfo newTaskInfo() {
        return newTaskInfo("test-task");
    }

    private static TaskInfo newTaskInfo(final String taskName) {
        final TaskID taskID = CommonIdUtils.toTaskId(taskName);

        return newTaskInfo(taskName, taskID);
    }

    private static TaskInfo newTaskInfo(final String taskName, final TaskID taskID) {
        return TaskInfo.newBuilder()
                .setName(taskName)
                .setTaskId(taskID)
                .setSlaveId(SlaveID.newBuilder()
                        .setValue("proto-field-required")
                ).build();
    }


    private static TaskStatus newTaskStatus(final TaskInfo taskInfo, final TaskState taskState) {
        return newTaskStatus(taskInfo.getTaskId(), taskState);
    }

    private static TaskStatus newTaskStatus(final TaskID taskID, final TaskState taskState) {
        return TaskStatus.newBuilder()
                .setTaskId(taskID)
                .setState(taskState)
                .build();
    }


    private ConfigStore<ServiceSpec> newConfigStore(final Persister persister) throws Exception {
        final SchedulerFlags flags = OfferRequirementTestUtils.getTestSchedulerFlags();

        ClassLoader classLoader = StateStoreUtilsTest.class.getClassLoader();
        File file = new File(classLoader.getResource("resource-set-seq.yml").getFile());
        ServiceSpec serviceSpec = DefaultServiceSpec.newGenerator(RawServiceSpec.newBuilder(file).build(), flags)
                .build();

        ConfigStore<ServiceSpec> configStore = new ConfigStore<>(
                DefaultServiceSpec.getConfigurationFactory(serviceSpec), persister);
        // At startup, the the service spec must be stored, and the target config must be set to the
        // stored spec.
        UUID targetConfig = configStore.store(serviceSpec);
        configStore.setTargetConfig(targetConfig);

        return configStore;
    }

}
