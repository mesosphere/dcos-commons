package com.mesosphere.sdk.scheduler;

import com.mesosphere.sdk.state.StateStore;
import com.mesosphere.sdk.testutils.TestConstants;
import org.apache.mesos.Protos;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Arrays;
import java.util.UUID;

import static org.mockito.Matchers.any;
import static org.mockito.Mockito.*;

/**
 * This class test the {@link TaskCleaner}.
 */
public class TaskCleanerTest {
    @Mock TaskKiller taskKiller;
    @Mock StateStore stateStore;
    private TaskCleaner taskCleaner;

    @Before
    public void beforeEach() {
        MockitoAnnotations.initMocks(this);
        taskCleaner = new TaskCleaner(stateStore, taskKiller, false);
    }

    @Test
    public void ignoreTerminalState() {
        taskCleaner.statusUpdate(getTerminalStatus());
        verify(taskKiller, never()).killTask(any());
    }

    @Test
    public void killTaskEmptyStateStore() {
        taskCleaner.statusUpdate(getNonTerminalStatus());
        verify(taskKiller, times(1)).killTask(any());
    }

    @Test
    public void dontKillExpectedTask() {
        when(stateStore.fetchTasks()).thenReturn(Arrays.asList(TestConstants.TASK_INFO));

        taskCleaner.statusUpdate(getNonTerminalStatus());
        verify(taskKiller, never()).killTask(any());
    }

    @Test
    public void killTaskNonEmptyStateStore() {
        Protos.TaskInfo taskInfo = TestConstants.TASK_INFO.toBuilder()
                .setTaskId(Protos.TaskID.newBuilder().setValue(UUID.randomUUID().toString()))
                .build();
        when(stateStore.fetchTasks()).thenReturn(Arrays.asList(taskInfo));

        taskCleaner.statusUpdate(getNonTerminalStatus());
        verify(taskKiller, times(1)).killTask(any());
    }

    private Protos.TaskStatus getTerminalStatus() {
        return TestConstants.TASK_STATUS.toBuilder()
                .setState(Protos.TaskState.TASK_FAILED)
                .build();
    }

    private Protos.TaskStatus getNonTerminalStatus() {
        return TestConstants.TASK_STATUS;
    }
}
