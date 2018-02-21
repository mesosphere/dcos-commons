package com.mesosphere.sdk.scheduler;

import com.mesosphere.sdk.state.StateStore;
import com.mesosphere.sdk.testutils.TestConstants;
import org.apache.mesos.Protos;
import org.apache.mesos.SchedulerDriver;
import org.junit.Assert;
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
    @Test
    public void portTestsToDefaultSchedulerTests() {
        Assert.fail("TODO");
    }
    /*
    @Mock StateStore stateStore;
    @Mock SchedulerDriver driver;
    private TaskCleaner taskCleaner;

    @Before
    public void beforeEach() {
        MockitoAnnotations.initMocks(this);
        Driver.setDriver(driver);
        taskCleaner = new TaskCleaner(stateStore, false);
    }

    @Test
    public void ignoreTerminalState() {
        taskCleaner.statusUpdate(getTerminalStatus());
        verify(driver, never()).killTask(any());
    }

    @Test
    public void ignoreTaskLost() {
        Protos.TaskStatus taskLost = TestConstants.TASK_STATUS.toBuilder().setState(Protos.TaskState.TASK_LOST).build();
        taskCleaner.statusUpdate(taskLost);
        verify(driver, never()).killTask(any());
    }

    @Test
    public void killTaskEmptyStateStore() {
        taskCleaner.statusUpdate(getNonTerminalStatus());
        verify(driver, times(1)).killTask(any());
    }

    @Test
    public void dontKillExpectedTask() {
        when(stateStore.fetchTasks()).thenReturn(Arrays.asList(TestConstants.TASK_INFO));

        taskCleaner.statusUpdate(getNonTerminalStatus());
        verify(driver, never()).killTask(any());
    }

    @Test
    public void killTaskNonEmptyStateStore() {
        Protos.TaskInfo taskInfo = TestConstants.TASK_INFO.toBuilder()
                .setTaskId(Protos.TaskID.newBuilder().setValue(UUID.randomUUID().toString()))
                .build();
        when(stateStore.fetchTasks()).thenReturn(Arrays.asList(taskInfo));

        taskCleaner.statusUpdate(getNonTerminalStatus());
        verify(driver, times(1)).killTask(any());
    }

    private Protos.TaskStatus getTerminalStatus() {
        return TestConstants.TASK_STATUS.toBuilder()
                .setState(Protos.TaskState.TASK_FAILED)
                .build();
    }

    private Protos.TaskStatus getNonTerminalStatus() {
        return TestConstants.TASK_STATUS;
    }
    */
}
