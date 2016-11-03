package org.apache.mesos.state;

import static org.junit.Assert.assertEquals;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;

import org.apache.mesos.Protos;
import org.apache.mesos.testutils.TaskTestUtils;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import static org.mockito.Mockito.when;

/**
 * Tests for {@link StateStoreUtils}.
 */
public class StateStoreUtilsTest {

    @Mock
    private StateStore mockStateStore;

    @Before
    public void beforeEach() throws Exception {
        MockitoAnnotations.initMocks(this);
    }

    @Test
    public void testTaskLostNeedsRecovery() {
        Protos.TaskInfo testTask = TaskTestUtils.getTaskInfo(Collections.emptyList());
        when(mockStateStore.fetchTasks()).thenReturn(Arrays.asList(testTask));
        mockStateStore.storeTasks(Arrays.asList(testTask));
        assertEquals(0, StateStoreUtils.fetchTasksNeedingRecovery(mockStateStore).size());
        when(mockStateStore.fetchStatuses()).thenReturn(Arrays.asList(
                Protos.TaskStatus.newBuilder()
                        .setTaskId(testTask.getTaskId())
                        .setState(Protos.TaskState.TASK_LOST)
                        .build()));
        Collection<Protos.TaskInfo> recoveryTasks =
                StateStoreUtils.fetchTasksNeedingRecovery(mockStateStore);
        assertEquals(1, recoveryTasks.size());
        assertEquals(testTask, recoveryTasks.iterator().next());
    }
}
