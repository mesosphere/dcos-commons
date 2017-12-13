package com.mesosphere.sdk.executor;

import com.mesosphere.sdk.offer.CommonIdUtils;
import org.apache.mesos.ExecutorDriver;
import org.apache.mesos.Protos;
import org.apache.mesos.Protos.SlaveID;
import org.awaitility.Awaitility;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.awaitility.Awaitility.to;
import static org.hamcrest.Matchers.equalTo;
import static org.mockito.Mockito.times;

public class ProcessTaskTest {
    private static final String EXECUTOR_NAME = "TEST_EXECUTOR";
    private static final String TASK_NAME = "TEST_TASK";

    @Test
    public void testTaskStopped() throws Exception {
        final ExecutorDriver mockExecutorDriver = Mockito.mock(ExecutorDriver.class);
        final Protos.ExecutorInfo executorInfo = Protos.ExecutorInfo
                .newBuilder()
                .setName(EXECUTOR_NAME)
                .setExecutorId(CommonIdUtils.toExecutorId(EXECUTOR_NAME))
                .setCommand(Protos.CommandInfo.newBuilder().setValue("")).build();

        final Protos.TaskInfo taskInfo = Protos.TaskInfo
                .newBuilder()
                .setName(TASK_NAME)
                .setTaskId(CommonIdUtils.toTaskId(TASK_NAME))
                .setSlaveId(SlaveID.newBuilder().setValue("ignored"))
                .setExecutor(executorInfo)
                .setCommand(Protos.CommandInfo.newBuilder().setValue("exit 0"))
                .build();

        final ProcessTask processTask = ProcessTask.create(mockExecutorDriver, taskInfo);

        Assert.assertFalse(processTask.isAlive());
        Executors.newCachedThreadPool().submit(processTask);

        processTask.stop();

        Awaitility.await().atMost(5, TimeUnit.SECONDS).untilCall(to(processTask).isAlive(), equalTo(false));
        Assert.assertFalse(processTask.isAlive());
    }

    @Test
    public void testFailingTask() throws Exception {
        final ExecutorDriver mockExecutorDriver = Mockito.mock(ExecutorDriver.class);

        final Protos.ExecutorInfo executorInfo = Protos.ExecutorInfo.newBuilder()
                .setName(EXECUTOR_NAME)
                .setExecutorId(CommonIdUtils.toExecutorId(EXECUTOR_NAME))
                .setCommand(Protos.CommandInfo.newBuilder().setValue(""))
                .build();

        final Protos.TaskInfo taskInfo = Protos.TaskInfo.newBuilder()
                .setName(TASK_NAME)
                .setTaskId(CommonIdUtils.toTaskId(TASK_NAME))
                .setSlaveId(SlaveID.newBuilder().setValue("ignored"))
                .setCommand(Protos.CommandInfo.newBuilder().setValue("exit 1"))
                .setExecutor(executorInfo)
                .build();

        final ProcessTask processTask = ProcessTask.create(mockExecutorDriver, taskInfo);
        Assert.assertFalse(processTask.isAlive());
        processTask.run();
        Assert.assertFalse(processTask.isAlive());

        // Wait for processTask to run: TASK_RUNNING + TASK_FAILED
        ArgumentCaptor<Protos.TaskStatus> captor = ArgumentCaptor.forClass(Protos.TaskStatus.class);
        Mockito.verify(mockExecutorDriver, times(2)).sendStatusUpdate(captor.capture());
        List<Protos.TaskStatus> statuses = captor.getAllValues();
        Assert.assertEquals(2, statuses.size());
        Assert.assertEquals(Protos.TaskState.TASK_RUNNING, statuses.get(0).getState());
        Assert.assertEquals(Protos.TaskState.TASK_FAILED, statuses.get(1).getState());
        Assert.assertFalse(processTask.isAlive());
    }

    @Test
    public void testFinishingTask() throws Exception {
        final ExecutorDriver mockExecutorDriver = Mockito.mock(ExecutorDriver.class);

        final Protos.ExecutorInfo executorInfo = Protos.ExecutorInfo.newBuilder()
                .setName(EXECUTOR_NAME)
                .setExecutorId(CommonIdUtils.toExecutorId(EXECUTOR_NAME))
                .setCommand(Protos.CommandInfo.newBuilder().setValue(""))
                .build();

        final Protos.TaskInfo taskInfo = Protos.TaskInfo.newBuilder()
                .setName(TASK_NAME)
                .setTaskId(CommonIdUtils.toTaskId(TASK_NAME))
                .setSlaveId(SlaveID.newBuilder().setValue("ignored"))
                .setExecutor(executorInfo)
                .setCommand(Protos.CommandInfo.newBuilder().setValue("exit 0"))
                .build();

        final ProcessTask processTask = ProcessTask.create(mockExecutorDriver, taskInfo);
        Assert.assertFalse(processTask.isAlive());
        processTask.run();
        Assert.assertFalse(processTask.isAlive());

        // Wait for processTask to run: TASK_RUNNING + TASK_FINISHED
        ArgumentCaptor<Protos.TaskStatus> captor = ArgumentCaptor.forClass(Protos.TaskStatus.class);
        Mockito.verify(mockExecutorDriver, times(2)).sendStatusUpdate(captor.capture());
        List<Protos.TaskStatus> statuses = captor.getAllValues();
        Assert.assertEquals(2, statuses.size());
        Assert.assertEquals(Protos.TaskState.TASK_RUNNING, statuses.get(0).getState());
        Assert.assertEquals(Protos.TaskState.TASK_FINISHED, statuses.get(1).getState());
        Assert.assertFalse(processTask.isAlive());
    }

    @Test
    public void testKilledTask() throws Exception {
        final ExecutorDriver mockExecutorDriver = Mockito.mock(ExecutorDriver.class);
        final Protos.ExecutorInfo executorInfo = Protos.ExecutorInfo
                .newBuilder()
                .setName(EXECUTOR_NAME)
                .setExecutorId(CommonIdUtils.toExecutorId(EXECUTOR_NAME))
                .setCommand(Protos.CommandInfo.newBuilder().setValue("")).build();

        final Protos.TaskInfo taskInfo = Protos.TaskInfo
                .newBuilder()
                .setName(TASK_NAME)
                .setTaskId(CommonIdUtils.toTaskId(TASK_NAME))
                .setSlaveId(SlaveID.newBuilder().setValue("ignored"))
                .setExecutor(executorInfo)
                .setCommand(Protos.CommandInfo.newBuilder().setValue("exit 137"))
                .build();

        final ProcessTask processTask = ProcessTask.create(mockExecutorDriver, taskInfo);
        Assert.assertFalse(processTask.isAlive());
        processTask.run();
        Assert.assertFalse(processTask.isAlive());

        // Wait for processTask to run: TASK_RUNNING + TASK_KILLED
        ArgumentCaptor<Protos.TaskStatus> captor = ArgumentCaptor.forClass(Protos.TaskStatus.class);
        Mockito.verify(mockExecutorDriver, times(2)).sendStatusUpdate(captor.capture());
        List<Protos.TaskStatus> statuses = captor.getAllValues();
        Assert.assertEquals(2, statuses.size());
        Assert.assertEquals(Protos.TaskState.TASK_RUNNING, statuses.get(0).getState());
        Assert.assertEquals(Protos.TaskState.TASK_KILLED, statuses.get(1).getState());
        Assert.assertFalse(processTask.isAlive());
    }
}
