package com.mesosphere.sdk.state;

import com.google.common.collect.ImmutableList;
import com.mesosphere.sdk.config.ConfigStore;
import com.mesosphere.sdk.config.ConfigStoreException;
import com.mesosphere.sdk.config.StringConfiguration;
import com.mesosphere.sdk.offer.CommonIdUtils;
import com.mesosphere.sdk.offer.TaskException;
import com.mesosphere.sdk.specification.DefaultServiceSpec;
import com.mesosphere.sdk.specification.ServiceSpec;
import com.mesosphere.sdk.storage.MemPersister;
import com.mesosphere.sdk.storage.Persister;
import net.jcip.annotations.ThreadSafe;
import org.apache.mesos.Protos;
import org.apache.mesos.Protos.Labels;
import org.apache.mesos.Protos.SlaveID;
import org.apache.mesos.Protos.TaskID;
import org.apache.mesos.Protos.TaskInfo;
import org.apache.mesos.Protos.TaskState;
import org.apache.mesos.Protos.TaskStatus;
import org.junit.Test;
import org.mockito.Mock;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import static org.hamcrest.Matchers.empty;
import static org.junit.Assert.assertThat;
import static org.hamcrest.Matchers.is;

/**
 * Tests for {@link StateStoreUtils}.
 */
public class StateStoreUtilsTest {

    @Mock private ServiceSpec mockServiceSpec;

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

}
