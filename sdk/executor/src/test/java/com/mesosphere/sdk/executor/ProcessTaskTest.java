package com.mesosphere.sdk.executor;

import com.mesosphere.sdk.offer.CommonIdUtils;
import com.mesosphere.sdk.offer.ProcessBuilderUtils;

import org.apache.mesos.ExecutorDriver;
import org.apache.mesos.Protos;
import org.apache.mesos.Protos.SlaveID;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mockito;

import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.mockito.Mockito.timeout;

public class ProcessTaskTest {
    private static final String EXECUTOR_NAME = "TEST_EXECUTOR";
    private static final String TASK_NAME = "TEST_TASK";


    @Test
    public void testSimple() throws Exception {
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

        final ProcessTask processTask = ProcessTask.create(
                mockExecutorDriver,
                taskInfo,
                false);

        Assert.assertFalse(processTask.isAlive());
        final ExecutorService executorService = Executors.newCachedThreadPool();
        executorService.submit(processTask);

        // Wait for processTask to run
        Thread.sleep(1000);

        processTask.stop(null);
        Assert.assertFalse(processTask.isAlive());
    }

    @Test
    public void testFailingTask() throws Exception {
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
                .setCommand(Protos.CommandInfo.newBuilder().setValue("exit 0"))
                .setExecutor(executorInfo)
                .build();

        final FailingProcessTask failingProcessTask = new FailingProcessTask(
                mockExecutorDriver,
                taskInfo,
                ProcessBuilderUtils.buildProcess(taskInfo.getCommand()),
                false);
        final ExecutorService executorService = Executors.newCachedThreadPool();
        executorService.submit(failingProcessTask);

        Mockito.verify(mockExecutorDriver, timeout(1000)).sendStatusUpdate(Mockito.any());
    }

    @Test
    public void testExitOnCompletion() throws Exception {
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

        final ProcessTask processTask = ProcessTask.create(
                mockExecutorDriver,
                taskInfo,
                true);


        Assert.assertFalse(processTask.isAlive());
        final ExecutorService executorService = Executors.newCachedThreadPool();
        executorService.submit(processTask);

        // Wait for processTask to run
        Mockito.verify(mockExecutorDriver, timeout(1000)).stop();
        Assert.assertFalse(processTask.isAlive());
    }

    @Test
    public void testExitOnFailure() throws Exception {
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

        final FailingProcessTask processTask = new FailingProcessTask(
                mockExecutorDriver,
                taskInfo,
                ProcessBuilderUtils.buildProcess(taskInfo.getCommand()),
                true);


        Assert.assertFalse(processTask.isAlive());
        final ExecutorService executorService = Executors.newCachedThreadPool();
        executorService.submit(processTask);

        Mockito.verify(mockExecutorDriver, timeout(1000)).abort();
        Assert.assertFalse(processTask.isAlive());
    }

    public static class FailingProcessTask extends ProcessTask {
        protected FailingProcessTask(
                ExecutorDriver executorDriver,
                Protos.TaskInfo taskInfo,
                ProcessBuilder processBuilder,
                boolean exitOnTermination) throws IOException {
            super(executorDriver, taskInfo, processBuilder, exitOnTermination);
        }

        @Override
        public void preStart() {
            throw new RuntimeException("Error");
        }
    }

}
