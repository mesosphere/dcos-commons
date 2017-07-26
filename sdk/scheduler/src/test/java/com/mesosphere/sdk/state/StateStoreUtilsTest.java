package com.mesosphere.sdk.state;

import com.google.common.collect.ImmutableList;
import com.mesosphere.sdk.config.ConfigStore;
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
import org.junit.Test;
import org.mockito.Mock;

import java.io.File;
import java.util.UUID;

import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;

/**
 * Tests for {@link StateStoreUtils}.
 */
public class StateStoreUtilsTest {

    @Mock
    private ServiceSpec mockServiceSpec;

    @Test
    public void emptyStateStoreReturnsEmptyArray() {
        final StateStore stateStore = new DefaultStateStore(new MemPersister());

        byte[] result = StateStoreUtils.fetchPropertyOrEmptyArray(stateStore, "UNDEFINED");

        assertThat(result.length, is(0));
    }

    @Test
    public void unmatchedKeyReturnsEmptyArray() {
        final StateStore stateStore = new DefaultStateStore(new MemPersister());
        stateStore.storeProperty("DEFINED", "VALUE".getBytes());

        byte[] result = StateStoreUtils.fetchPropertyOrEmptyArray(stateStore, "UNDEFINED");

        assertThat(result.length, is(0));
    }

    @Test
    public void matchedKeyReturnsValue() {
        final StateStore stateStore = new DefaultStateStore(new MemPersister());
        stateStore.storeProperty("DEFINED", "VALUE".getBytes());

        byte[] result = StateStoreUtils.fetchPropertyOrEmptyArray(stateStore, "DEFINED");

        assertThat(result, is("VALUE".getBytes()));
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
        final StateStore stateStore = new DefaultStateStore(new MemPersister());

        StateStoreUtils.getTaskInfo(stateStore, null);
    }

    @Test
    public void stateStoreWithSingleStateReturnsTaskInfo() {
        final StateStore stateStore = new DefaultStateStore(new MemPersister());

        final String taskName = "test-task";
        final TaskID taskID = CommonIdUtils.toTaskId(taskName);

        // Create task info
        TaskInfo taskInfo = TaskInfo.newBuilder()
                .setName(taskName)
                .setTaskId(taskID)
                .setSlaveId(SlaveID.newBuilder().setValue("ignored")) // proto field required
                .build();

        // Add a task to the state store
        stateStore.storeTasks(ImmutableList.of(taskInfo));

        TaskStatus taskStatus = TaskStatus.newBuilder().setTaskId(taskID).setState(TaskState.TASK_UNKNOWN).build();

        assertThat(StateStoreUtils.getTaskInfo(stateStore, taskStatus), is(taskInfo));
    }


    @Test(expected = StateStoreException.class)
    public void stateStoreWithDuplicateIdsRaisesErrorOnStatus() {
        final StateStore stateStore = new DefaultStateStore(new MemPersister());

        final String taskName = "test-task";
        final TaskID taskID = CommonIdUtils.toTaskId(taskName);

        // Create task info
        TaskInfo firstTask = TaskInfo.newBuilder()
                .setName(taskName + "1")
                .setTaskId(taskID)
                .setSlaveId(SlaveID.newBuilder().setValue("ignored")) // proto field required
                .build();

        TaskInfo secondTask = TaskInfo.newBuilder()
                .setName(taskName + "2")
                .setTaskId(taskID)
                .setSlaveId(SlaveID.newBuilder().setValue("ignored")) // proto field required
                .build();


        // Add a task to the state store
        stateStore.storeTasks(ImmutableList.of(firstTask, secondTask));

        TaskStatus taskStatus = TaskStatus.newBuilder().setTaskId(taskID).setState(TaskState.TASK_UNKNOWN).build();

        assertThat(stateStore.fetchTasks().size(), is(2));
        StateStoreUtils.getTaskInfo(stateStore, taskStatus);
    }

    @Test(expected = StateStoreException.class)
    public void stateStoreWithMismatchedIdRaisesErrorOnStatus() {
        final StateStore stateStore = new DefaultStateStore(new MemPersister());

        final String taskName = "test-task";
        final TaskID taskID = CommonIdUtils.toTaskId(taskName);

        // Create task info
        TaskInfo firstTask = TaskInfo.newBuilder()
                .setName(taskName)
                .setTaskId(taskID)
                .setSlaveId(SlaveID.newBuilder().setValue("ignored")) // proto field required
                .build();

        // Add a task to the state store
        stateStore.storeTasks(ImmutableList.of(firstTask));

        TaskStatus taskStatus = TaskStatus.newBuilder().setTaskId(CommonIdUtils.toTaskId("not-" + taskName)).setState(TaskState.TASK_UNKNOWN).build();

        StateStoreUtils.getTaskInfo(stateStore, taskStatus);
    }


    @Test
    public void emptyStateStoreHasNoTasksNeedingRecovery() throws TaskException {
        final StateStore stateStore = new DefaultStateStore(new MemPersister());
        assertThat(StateStoreUtils.fetchTasksNeedingRecovery(stateStore, null), is(empty()));
    }


    @Test
    public void taskWithNoStatusDoesNotNeedRecovery() throws TaskException {
        final StateStore stateStore = new DefaultStateStore(new MemPersister());

        final String taskName = "test-task";
        final TaskID taskID = CommonIdUtils.toTaskId(taskName);

        // Create task info
        TaskInfo taskInfo = TaskInfo.newBuilder()
                .setName(taskName)
                .setTaskId(taskID)
                .setSlaveId(SlaveID.newBuilder().setValue("ignored")) // proto field required
                .build();

        // Add a task to the state store
        stateStore.storeTasks(ImmutableList.of(taskInfo));

        assertThat(StateStoreUtils.fetchTasksNeedingRecovery(stateStore, null), is(empty()));

    }

    @Test
    public void runningTaskDoesNotNeedRecoveryIfRunning() throws Exception {

        final Persister persister = new MemPersister();

        ConfigStore<ServiceSpec> configStore = newConfigStore(persister);

        final StateStore stateStore = new DefaultStateStore(persister);

        //
        final String taskName = "name-0-node";
        final TaskID taskID = CommonIdUtils.toTaskId(taskName);

        // Create task info
        TaskInfo.Builder taskInfoBuilder = TaskInfo.newBuilder()
                .setName(taskName)
                .setTaskId(taskID)
                .setSlaveId(SlaveID.newBuilder().setValue("ignored")) // proto field required
                ;

        // create default labels:
        taskInfoBuilder.setLabels(new TaskLabelWriter(taskInfoBuilder)
                .setTargetConfiguration(configStore.getTargetConfig())
                .setType("name")
                .setIndex(0)
                .toProto());

        TaskInfo taskInfo = taskInfoBuilder.build();

        // Add a task to the state store
        stateStore.storeTasks(ImmutableList.of(taskInfo));

        TaskStatus taskStatus = TaskStatus.newBuilder()
                .setTaskId(taskID)
                .setState(TaskState.TASK_RUNNING).build();
        stateStore.storeStatus(taskStatus);

        configStore.getTargetConfig();

        assertThat(StateStoreUtils.fetchTasksNeedingRecovery(stateStore, configStore), is(empty()));
    }

    @Test
    public void runningTaskNeedsRecoveryIfFailed() throws Exception {
        final Persister persister = new MemPersister();

        ConfigStore<ServiceSpec> configStore = newConfigStore(persister);

        final StateStore stateStore = new DefaultStateStore(persister);

        //
        final String taskName = "name-0-node";
        final TaskID taskID = CommonIdUtils.toTaskId(taskName);

        // Create task info
        TaskInfo.Builder taskInfoBuilder = TaskInfo.newBuilder()
                .setName(taskName)
                .setTaskId(taskID)
                .setSlaveId(SlaveID.newBuilder().setValue("ignored")) // proto field required
                ;

        // create default labels:
        taskInfoBuilder.setLabels(new TaskLabelWriter(taskInfoBuilder)
                .setTargetConfiguration(configStore.getTargetConfig())
                .setType("name")
                .setIndex(0)
                .toProto());

        TaskInfo taskInfo = taskInfoBuilder.build();

        // Add a task to the state store
        stateStore.storeTasks(ImmutableList.of(taskInfo));

        TaskStatus taskStatus = TaskStatus.newBuilder()
                .setTaskId(taskID)
                .setState(TaskState.TASK_FAILED).build();
        stateStore.storeStatus(taskStatus);

        configStore.getTargetConfig();


        assertThat(StateStoreUtils.fetchTasksNeedingRecovery(stateStore, configStore),
                is(ImmutableList.of(taskInfo)));
    }


    @Test(expected = TaskException.class)
    public void nonPresentTaskRaisesError() throws Exception {
        final Persister persister = new MemPersister();

        ConfigStore<ServiceSpec> configStore = newConfigStore(persister);

        final StateStore stateStore = new DefaultStateStore(persister);

        //
        final String taskName = "name-0-not-present";
        final TaskID taskID = CommonIdUtils.toTaskId(taskName);

        // Create task info
        TaskInfo.Builder taskInfoBuilder = TaskInfo.newBuilder()
                .setName(taskName)
                .setTaskId(taskID)
                .setSlaveId(SlaveID.newBuilder().setValue("ignored")) // proto field required
                ;

        // create default labels:
        taskInfoBuilder.setLabels(new TaskLabelWriter(taskInfoBuilder)
                .setTargetConfiguration(configStore.getTargetConfig())
                .setType("name")
                .setIndex(0)
                .toProto());

        TaskInfo taskInfo = taskInfoBuilder.build();

        // Add a task to the state store
        stateStore.storeTasks(ImmutableList.of(taskInfo));

        TaskStatus taskStatus = TaskStatus.newBuilder()
                .setTaskId(taskID)
                .setState(TaskState.TASK_RUNNING).build();
        stateStore.storeStatus(taskStatus);

        StateStoreUtils.fetchTasksNeedingRecovery(stateStore, configStore);
    }

    @Test
    public void taskInfoWithNoStatusRequiresNoRecovery() throws Exception {
        final Persister persister = new MemPersister();

        ConfigStore<ServiceSpec> configStore = newConfigStore(persister);

        final StateStore stateStore = new DefaultStateStore(persister);

        //
        final String taskName = "name-0-not-present";
        final TaskID taskID = CommonIdUtils.toTaskId(taskName);

        // Create task info
        TaskInfo.Builder taskInfoBuilder = TaskInfo.newBuilder()
                .setName(taskName)
                .setTaskId(taskID)
                .setSlaveId(SlaveID.newBuilder().setValue("ignored")) // proto field required
                ;

        // create default labels:
        taskInfoBuilder.setLabels(new TaskLabelWriter(taskInfoBuilder)
                .setTargetConfiguration(configStore.getTargetConfig())
                .setType("name")
                .setIndex(0)
                .toProto());

        TaskInfo taskInfo = taskInfoBuilder.build();

        // Add a task to the state store
        stateStore.storeTasks(ImmutableList.of(taskInfo));

        assertThat(StateStoreUtils.fetchTasksNeedingRecovery(stateStore, configStore), is(empty()));
    }


    @Test
    public void finishedTaskDoesNotNeedRecoveryIfFailed() throws Exception {
        final Persister persister = new MemPersister();

        ConfigStore<ServiceSpec> configStore = newConfigStore(persister);

        final StateStore stateStore = new DefaultStateStore(persister);

        //
        final String taskName = "name-0-format";
        final TaskID taskID = CommonIdUtils.toTaskId(taskName);

        // Create task info
        TaskInfo.Builder taskInfoBuilder = TaskInfo.newBuilder()
                .setName(taskName)
                .setTaskId(taskID)
                .setSlaveId(SlaveID.newBuilder().setValue("ignored")) // proto field required
                ;

        // create default labels:
        taskInfoBuilder.setLabels(new TaskLabelWriter(taskInfoBuilder)
                .setTargetConfiguration(configStore.getTargetConfig())
                .setType("name")
                .setIndex(0)
                .toProto());

        TaskInfo taskInfo = taskInfoBuilder.build();

        // Add a task to the state store
        stateStore.storeTasks(ImmutableList.of(taskInfo));

        TaskStatus taskStatus = TaskStatus.newBuilder()
                .setTaskId(taskID)
                .setState(TaskState.TASK_FAILED).build();
        stateStore.storeStatus(taskStatus);

        configStore.getTargetConfig();


        assertThat(StateStoreUtils.fetchTasksNeedingRecovery(stateStore, configStore),
                is(empty()));
    }

    @Test
    public void finishedTaskDoesNotNeedRecoveryIfFinished() throws Exception {
        final Persister persister = new MemPersister();

        ConfigStore<ServiceSpec> configStore = newConfigStore(persister);

        final StateStore stateStore = new DefaultStateStore(persister);

        //
        final String taskName = "name-0-format";
        final TaskID taskID = CommonIdUtils.toTaskId(taskName);

        // Create task info
        TaskInfo.Builder taskInfoBuilder = TaskInfo.newBuilder()
                .setName(taskName)
                .setTaskId(taskID)
                .setSlaveId(SlaveID.newBuilder().setValue("ignored")) // proto field required
                ;

        // create default labels:
        taskInfoBuilder.setLabels(new TaskLabelWriter(taskInfoBuilder)
                .setTargetConfiguration(configStore.getTargetConfig())
                .setType("name")
                .setIndex(0)
                .toProto());

        TaskInfo taskInfo = taskInfoBuilder.build();

        // Add a task to the state store
        stateStore.storeTasks(ImmutableList.of(taskInfo));

        TaskStatus taskStatus = TaskStatus.newBuilder()
                .setTaskId(taskID)
                .setState(TaskState.TASK_FINISHED).build();
        stateStore.storeStatus(taskStatus);

        configStore.getTargetConfig();


        assertThat(StateStoreUtils.fetchTasksNeedingRecovery(stateStore, configStore),
                is(empty()));
    }

    @Test
    public void finishedTaskDoesNotNeedRecoveryIfRunning() throws Exception {
        final Persister persister = new MemPersister();

        ConfigStore<ServiceSpec> configStore = newConfigStore(persister);

        final StateStore stateStore = new DefaultStateStore(persister);

        //
        final String taskName = "name-0-format";
        final TaskID taskID = CommonIdUtils.toTaskId(taskName);

        // Create task info
        TaskInfo.Builder taskInfoBuilder = TaskInfo.newBuilder()
                .setName(taskName)
                .setTaskId(taskID)
                .setSlaveId(SlaveID.newBuilder().setValue("ignored")) // proto field required
                ;

        // create default labels:
        taskInfoBuilder.setLabels(new TaskLabelWriter(taskInfoBuilder)
                .setTargetConfiguration(configStore.getTargetConfig())
                .setType("name")
                .setIndex(0)
                .toProto());

        TaskInfo taskInfo = taskInfoBuilder.build();

        // Add a task to the state store
        stateStore.storeTasks(ImmutableList.of(taskInfo));

        TaskStatus taskStatus = TaskStatus.newBuilder()
                .setTaskId(taskID)
                .setState(TaskState.TASK_RUNNING).build();
        stateStore.storeStatus(taskStatus);

        configStore.getTargetConfig();


        assertThat(StateStoreUtils.fetchTasksNeedingRecovery(stateStore, configStore),
                is(empty()));
    }


    private ConfigStore<ServiceSpec> newConfigStore(final Persister persister) throws Exception {
        final SchedulerFlags flags = OfferRequirementTestUtils.getTestSchedulerFlags();

        ClassLoader classLoader = StateStoreUtilsTest.class.getClassLoader();
        File file = new File(classLoader.getResource("resource-set-seq.yml").getFile());
        DefaultServiceSpec serviceSpec = DefaultServiceSpec.newGenerator(RawServiceSpec.newBuilder(file).build(), flags)
                .build();

        DefaultConfigStore<ServiceSpec> configStore = new DefaultConfigStore(DefaultServiceSpec.getConfigurationFactory(serviceSpec), persister);
        // At startup, the the service spec must be stored, and the target config must be set to the
        // stored spec.
        UUID targetConfig = configStore.store(serviceSpec);
        configStore.setTargetConfig(targetConfig);

        return configStore;
    }

}
