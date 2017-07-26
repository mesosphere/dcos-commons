package com.mesosphere.sdk.state;

import com.google.common.collect.ImmutableList;
import com.mesosphere.sdk.config.ConfigStore;
import com.mesosphere.sdk.config.ConfigStoreException;
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
        this.stateStore = new DefaultStateStore(this.persister);
    }

    @Test
    public void emptyStateStoreReturnsEmptyArray() {
        byte[] result = StateStoreUtils.fetchPropertyOrEmptyArray(stateStore, "UNDEFINED");

        assertThat(result.length, is(0));
    }

    @Test
    public void unmatchedKeyReturnsEmptyArray() {
        stateStore.storeProperty("DEFINED", "VALUE".getBytes());

        byte[] result = StateStoreUtils.fetchPropertyOrEmptyArray(stateStore, "UNDEFINED");

        assertThat(result.length, is(0));
    }

    @Test
    public void matchedKeyReturnsValue() {
        stateStore.storeProperty("DEFINED", "VALUE".getBytes());

        byte[] result = StateStoreUtils.fetchPropertyOrEmptyArray(stateStore, "DEFINED");

        assertThat(result, is("VALUE".getBytes()));
    }

    @Test(expected = StateStoreException.class)
    public void nullValueIsInvalid() {
        StateStoreUtils.validateValue(null);
    }

    @Test(expected = StateStoreException.class)
    public void veryLargeValueIsInvalid() {
        byte[] largeArray = new byte[1024 * 1024 + 1];
        StateStoreUtils.validateValue(largeArray);
    }

    @Test
    public void largeValueIsValid() {
        byte[] largeArray = new byte[1024 * 1024];
        StateStoreUtils.validateValue(largeArray);
    }

    @Test
    public void emptyValueIsValid() {
        byte[] largeArray = new byte[0];
        StateStoreUtils.validateValue(largeArray);
    }

    @Test(expected = StateStoreException.class)
    public void emptyStringIsIsInvalidKey() {
        StateStoreUtils.validateKey("");
    }

    @Test(expected = StateStoreException.class)
    public void nullIsIsInvalidKey() {
        StateStoreUtils.validateKey(null);
    }

    @Test(expected = StateStoreException.class)
    public void blankStringIsInvaldiKey() {
        StateStoreUtils.validateKey("    ");
    }

    @Test(expected = StateStoreException.class)
    public void stringWithLeadingSlashIsInvalidKey() {
        StateStoreUtils.validateKey("/key");
    }

    @Test(expected = StateStoreException.class)
    public void stringWithTrailingSlashIsInvalidKey() {
        StateStoreUtils.validateKey("key/");
    }

    @Test(expected = StateStoreException.class)
    public void stringWithEmbeddedSlashIsInvalidKey() {
        StateStoreUtils.validateKey("key/value");
    }

    @Test(expected = StateStoreException.class)
    public void emptyStateStoreRaisesErrorOnTaskInfo() {
        StateStoreUtils.getTaskInfo(stateStore, null);
    }

    @Test
    public void stateStoreWithSingleStateReturnsTaskInfo() {
        // Create task info
        TaskInfo taskInfo = newTaskInfoBuilder("test-task").build();

        // Add a task to the state store
        stateStore.storeTasks(ImmutableList.of(taskInfo));

        TaskStatus taskStatus = newTaskStatus(taskInfo, TaskState.TASK_UNKNOWN);

        assertThat(StateStoreUtils.getTaskInfo(stateStore, taskStatus), is(taskInfo));
    }


    @Test(expected = StateStoreException.class)
    public void stateStoreWithDuplicateIdsRaisesErrorOnStatus() {
        // Create task info
        TaskInfo taskInfo = newTaskInfoBuilder("task_1").build();
        TaskInfo secondTask = newTaskInfoBuilder("task_2", taskInfo.getTaskId()).build();

        // Add a task to the state store
        stateStore.storeTasks(ImmutableList.of(taskInfo, secondTask));

        TaskStatus taskStatus = newTaskStatus(taskInfo, TaskState.TASK_UNKNOWN);

        assertThat(stateStore.fetchTasks().size(), is(2));
        StateStoreUtils.getTaskInfo(stateStore, taskStatus);
    }

    @Test(expected = StateStoreException.class)
    public void stateStoreWithMismatchedIdRaisesErrorOnStatus() {
        final String taskName = "test-task";

        // Create task info
        TaskInfo taskInfo = newTaskInfoBuilder(taskName).build();

        // Add a task to the state store
        stateStore.storeTasks(ImmutableList.of(taskInfo));

        TaskStatus taskStatus = newTaskStatus(CommonIdUtils.toTaskId("not-" + taskName), TaskState.TASK_UNKNOWN);

        assertThat(taskInfo.getTaskId(), is(not(taskStatus.getTaskId())));
        StateStoreUtils.getTaskInfo(stateStore, taskStatus);
    }


    @Test
    public void emptyStateStoreHasNoTasksNeedingRecovery() throws TaskException {
        assertThat(StateStoreUtils.fetchTasksNeedingRecovery(stateStore, null), is(empty()));
    }


    @Test
    public void taskWithNoStatusDoesNotNeedRecovery() throws TaskException {
        // Create task info
        TaskInfo taskInfo = newTaskInfoBuilder().build();

        // Add a task to the state store
        stateStore.storeTasks(ImmutableList.of(taskInfo));

        assertThat(StateStoreUtils.fetchTasksNeedingRecovery(stateStore, null), is(empty()));

    }

    @Test
    public void runningTaskDoesNotNeedRecoveryIfRunning() throws Exception {

        ConfigStore<ServiceSpec> configStore = newConfigStore(persister);

        // Create task info
        TaskInfo taskInfo = newTaskInfoBuilder("name-0-node", configStore).build();

        // Add a task to the state store
        stateStore.storeTasks(ImmutableList.of(taskInfo));

        TaskStatus taskStatus = newTaskStatus(taskInfo, TaskState.TASK_RUNNING);
        stateStore.storeStatus(taskStatus);

        assertThat(StateStoreUtils.fetchTasksNeedingRecovery(stateStore, configStore), is(empty()));
    }

    @Test
    public void runningTaskNeedsRecoveryIfFailed() throws Exception {

        ConfigStore<ServiceSpec> configStore = newConfigStore(persister);

        // Create task info
        TaskInfo taskInfo = newTaskInfoBuilder("name-0-node", configStore).build();

        // Add a task to the state store
        stateStore.storeTasks(ImmutableList.of(taskInfo));

        TaskStatus taskStatus = newTaskStatus(taskInfo, TaskState.TASK_FAILED);
        stateStore.storeStatus(taskStatus);

        assertThat(StateStoreUtils.fetchTasksNeedingRecovery(stateStore, configStore),
                is(ImmutableList.of(taskInfo)));
    }


    @Test(expected = TaskException.class)
    public void nonPresentTaskRaisesError() throws Exception {

        ConfigStore<ServiceSpec> configStore = newConfigStore(persister);

        // Create task info
        TaskInfo taskInfo = newTaskInfoBuilder("name-0-not-present", configStore).build();

        // Add a task to the state store
        stateStore.storeTasks(ImmutableList.of(taskInfo));

        TaskStatus taskStatus = newTaskStatus(taskInfo, TaskState.TASK_RUNNING);
        stateStore.storeStatus(taskStatus);

        StateStoreUtils.fetchTasksNeedingRecovery(stateStore, configStore);
    }

    @Test
    public void taskInfoWithNoStatusRequiresNoRecovery() throws Exception {

        ConfigStore<ServiceSpec> configStore = newConfigStore(persister);

        // Create task info
        TaskInfo taskInfo = newTaskInfoBuilder("name-0-not-present", configStore).build();

        // Add a task to the state store
        stateStore.storeTasks(ImmutableList.of(taskInfo));

        assertThat(StateStoreUtils.fetchTasksNeedingRecovery(stateStore, configStore), is(empty()));
    }


    @Test
    public void finishedTaskDoesNotNeedRecoveryIfFailed() throws Exception {

        ConfigStore<ServiceSpec> configStore = newConfigStore(persister);

        // Create task info
        TaskInfo taskInfo = newTaskInfoBuilder("name-0-format", configStore).build();

        // Add a task to the state store
        stateStore.storeTasks(ImmutableList.of(taskInfo));

        TaskStatus taskStatus = newTaskStatus(taskInfo, TaskState.TASK_FAILED);
        stateStore.storeStatus(taskStatus);

        assertThat(StateStoreUtils.fetchTasksNeedingRecovery(stateStore, configStore),
                is(empty()));
    }

    @Test
    public void finishedTaskDoesNotNeedRecoveryIfFinished() throws Exception {


        ConfigStore<ServiceSpec> configStore = newConfigStore(persister);

        // Create task info
        TaskInfo taskInfo = newTaskInfoBuilder("name-0-format", configStore).build();

        // Add a task to the state store
        stateStore.storeTasks(ImmutableList.of(taskInfo));

        TaskStatus taskStatus = newTaskStatus(taskInfo, TaskState.TASK_FINISHED);
        stateStore.storeStatus(taskStatus);

        assertThat(StateStoreUtils.fetchTasksNeedingRecovery(stateStore, configStore),
                is(empty()));
    }

    @Test
    public void finishedTaskDoesNotNeedRecoveryIfRunning() throws Exception {

        ConfigStore<ServiceSpec> configStore = newConfigStore(persister);

        // Create task info
        TaskInfo taskInfo = newTaskInfoBuilder("name-0-format", configStore).build();

        // Add a task to the state store
        stateStore.storeTasks(ImmutableList.of(taskInfo));


        TaskStatus taskStatus = newTaskStatus(taskInfo, TaskState.TASK_RUNNING);
        stateStore.storeStatus(taskStatus);

        assertThat(StateStoreUtils.fetchTasksNeedingRecovery(stateStore, configStore),
                is(empty()));
    }


    private static TaskInfo.Builder newTaskInfoBuilder(final String taskName,
                                                       final ConfigStore<ServiceSpec> configStore)
            throws ConfigStoreException {
        TaskInfo.Builder taskInfoBuilder = newTaskInfoBuilder(taskName);

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

        return taskInfoBuilder;
    }

    private static TaskInfo.Builder newTaskInfoBuilder() {
        return newTaskInfoBuilder("test-task");
    }

    private static TaskInfo.Builder newTaskInfoBuilder(final String taskName) {
        final TaskID taskID = CommonIdUtils.toTaskId(taskName);

        return newTaskInfoBuilder(taskName, taskID);
    }

    private static TaskInfo.Builder newTaskInfoBuilder(final String taskName, final TaskID taskID) {
        return TaskInfo.newBuilder()
                .setName(taskName)
                .setTaskId(taskID)
                .setSlaveId(SlaveID.newBuilder()
                        .setValue("proto-field-required")
                );
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

        ConfigStore<ServiceSpec> configStore = new DefaultConfigStore(
                DefaultServiceSpec.getConfigurationFactory(serviceSpec), persister);
        // At startup, the the service spec must be stored, and the target config must be set to the
        // stored spec.
        UUID targetConfig = configStore.store(serviceSpec);
        configStore.setTargetConfig(targetConfig);

        return configStore;
    }

}
