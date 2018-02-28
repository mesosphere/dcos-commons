package com.mesosphere.sdk.scheduler;

import com.mesosphere.sdk.testutils.TestConstants;
import org.apache.mesos.Protos;
import org.apache.mesos.SchedulerDriver;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import static org.mockito.Mockito.*;

/**
 * This class tests the {@link TaskKiller} class.
 */
public class TaskKillerTest {
    @Mock private SchedulerDriver driver;

    @BeforeClass
    public static void beforeAll() throws InterruptedException {
        TaskKiller.reset(false); // disable background executor to avoid unexpected calls
    }

    @AfterClass
    public static void afterAll() throws InterruptedException {
        TaskKiller.reset(true); // reenable background executor to return to default behavior
    }

    @Before
    public void beforeEach() throws InterruptedException {
        MockitoAnnotations.initMocks(this);
        Driver.setDriver(driver);
    }

    @Test
    public void emptyTaskId() {
        verify(driver, never()).killTask(TestConstants.TASK_ID);

        TaskKiller.killTask(Protos.TaskID.newBuilder().setValue("").build());
        verify(driver, never()).killTask(TestConstants.TASK_ID);

        TaskKiller.killAllTasks();
        verify(driver, never()).killTask(TestConstants.TASK_ID);
    }

    @Test
    public void delayedDriverSet() throws InterruptedException {
        // Enqueue a task to kill, but it shouldn't be killed since no driver exists
        Driver.setDriver(null);
        TaskKiller.killTask(TestConstants.TASK_ID);
        verify(driver, never()).killTask(TestConstants.TASK_ID);

        // Re-add driver and perform an iteration of task killing. Should happen now.
        Driver.setDriver(driver);
        TaskKiller.killAllTasks();
        verify(driver, times(1)).killTask(TestConstants.TASK_ID);

        completeKilling(1);
    }

    @Test
    public void normalDriverSet() {
        verify(driver, never()).killTask(TestConstants.TASK_ID);

        // Enqueue a task to kill, and it should have a kill call issued immediately
        TaskKiller.killTask(TestConstants.TASK_ID);
        verify(driver, times(1)).killTask(TestConstants.TASK_ID);

        completeKilling(1);
    }

    @Test
    public void multipleKillAttempts() {
        verify(driver, never()).killTask(TestConstants.TASK_ID);

        // Enqueue a task to kill, and it should have a kill call issued immediately
        TaskKiller.killTask(TestConstants.TASK_ID);
        verify(driver, times(1)).killTask(TestConstants.TASK_ID);

        // Validate the next killing interation attempts to kill the task again since we haven't
        // received a status update yet.
        TaskKiller.killAllTasks();
        verify(driver, times(2)).killTask(TestConstants.TASK_ID);

        completeKilling(2);
    }

    @Test
    public void multipleKillAttemptsWithNonTerminalStatus() {
        verify(driver, never()).killTask(TestConstants.TASK_ID);

        // Enqueue a task to kill, and it should have a kill call issued immediately
        TaskKiller.killTask(TestConstants.TASK_ID);
        verify(driver, times(1)).killTask(TestConstants.TASK_ID);

        // Validate the next killing interation attempts to kill the task again since we haven't
        // received a status update yet.
        TaskKiller.killAllTasks();
        verify(driver, times(2)).killTask(TestConstants.TASK_ID);

        // Sent a status update that doesn't stop killing
        TaskKiller.update(
                TestConstants.TASK_STATUS.toBuilder()
                        .setState(Protos.TaskState.TASK_RUNNING)
                        .build());

        // Validate AGAIN that the next killing interation attempts to kill the task again since we haven't
        // received a status update yet.
        TaskKiller.killAllTasks();
        verify(driver, times(3)).killTask(TestConstants.TASK_ID);

        completeKilling(3);
    }

    private void completeKilling(int count) {
        // Remove the task from the queue by reporting it as killed
        TaskKiller.update(
                TestConstants.TASK_STATUS.toBuilder()
                        .setState(Protos.TaskState.TASK_KILLED)
                        .build());

        TaskKiller.killAllTasks();
        verify(driver, times(count)).killTask(TestConstants.TASK_ID);
    }
}
