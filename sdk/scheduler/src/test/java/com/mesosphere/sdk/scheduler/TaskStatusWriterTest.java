package com.mesosphere.sdk.scheduler;

import org.apache.mesos.Protos;
import org.apache.mesos.Scheduler;
import org.apache.mesos.SchedulerDriver;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

public class TaskStatusWriterTest {

    @Mock
    Scheduler mockScheduler;
    @Mock
    SchedulerDriver mockSchedulerDriver;

    @Before
    public void beforeEach() {
        MockitoAnnotations.initMocks(this);
    }

    @Test
    public void writeStatusTest() throws Exception {
        TaskStatusWriter taskStatusWriter = new TaskStatusWriter(mockScheduler, mockSchedulerDriver);
        taskStatusWriter.writeTaskStatus(Protos.TaskID.newBuilder().setValue("testid").build(),
                Protos.TaskState.TASK_KILLED,
                "This is a test.");

        verify(mockScheduler).statusUpdate(mockSchedulerDriver, Protos.TaskStatus.newBuilder()
            .setTaskId(Protos.TaskID.newBuilder().setValue("testid").build())
            .setState(Protos.TaskState.TASK_KILLED)
            .setMessage("This is a test.")
            .build());
        verifyNoMoreInteractions(mockScheduler);
    }

}
