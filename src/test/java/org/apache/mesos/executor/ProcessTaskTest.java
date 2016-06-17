package org.apache.mesos.executor;

import org.apache.mesos.ExecutorDriver;
import org.apache.mesos.Protos;
import org.apache.mesos.protobuf.EnvironmentBuilder;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mockito;

import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ProcessTaskTest {
    @Test
    public void testSimple() throws Exception {
        final ExecutorDriver mockExecutorDriver = Mockito.mock(ExecutorDriver.class);
        final Protos.ExecutorInfo executorInfo = Protos.ExecutorInfo
                .newBuilder()
                .setName("TEST_EXECUTOR")
                .setExecutorId(Protos.ExecutorID.newBuilder().setValue(UUID.randomUUID().toString()))
                .setCommand(Protos.CommandInfo.newBuilder().setValue("ls")).build();
        final Protos.TaskInfo taskInfo = Protos.TaskInfo
                .newBuilder()
                .setName("TEST")
                .setTaskId(Protos.TaskID.newBuilder().setValue(UUID.randomUUID().toString()))
                .setSlaveId(Protos.SlaveID.newBuilder().setValue(UUID.randomUUID().toString()))
                .setExecutor(executorInfo)
                .setData(Protos.CommandInfo
                        .newBuilder()
                        .setValue("sleep 5")
                        .setEnvironment(Protos.Environment
                                .newBuilder()
                                .addVariables(EnvironmentBuilder.createEnvironment(ExecutorTask.TASK_TYPE, "TEST")))
                        .build()
                        .toByteString())
                .build();
        final ProcessTask processTask = new ProcessTask(mockExecutorDriver, taskInfo, false);

        Assert.assertFalse(processTask.isAlive());
        final ExecutorService executorService = Executors.newCachedThreadPool();
        executorService.submit(processTask);

        // Wait for processTask to run
        Thread.sleep(1000);

        Assert.assertTrue(processTask.isAlive());


        processTask.stop();

        Assert.assertFalse(processTask.isAlive());
    }

    public static class FailingProcessTask extends ProcessTask {
        public FailingProcessTask(ExecutorDriver executorDriver, Protos.TaskInfo task) {
            super(executorDriver, task, true);
        }

        public FailingProcessTask(ExecutorDriver executorDriver, Protos.TaskInfo task, boolean exitOnTermination) {
            super(executorDriver, task, exitOnTermination);
        }

        @Override
        public void preStart() {
            throw new RuntimeException("Error");
        }
    }

    @Test
    public void testFailingTask() throws Exception {
        final ExecutorDriver mockExecutorDriver = Mockito.mock(ExecutorDriver.class);

        final Protos.ExecutorInfo executorInfo = Protos.ExecutorInfo
                .newBuilder()
                .setName("TEST_EXECUTOR")
                .setExecutorId(Protos.ExecutorID.newBuilder().setValue(UUID.randomUUID().toString()))
                .setCommand(Protos.CommandInfo.newBuilder().setValue("ls")).build();
        final Protos.TaskInfo taskInfo = Protos.TaskInfo
                .newBuilder()
                .setName("TEST")
                .setTaskId(Protos.TaskID.newBuilder().setValue(UUID.randomUUID().toString()))
                .setSlaveId(Protos.SlaveID.newBuilder().setValue(UUID.randomUUID().toString()))
                .setExecutor(executorInfo)
                .setData(Protos.CommandInfo
                        .newBuilder()
                        .setValue("sleep 5")
                        .setEnvironment(Protos.Environment
                                .newBuilder()
                                .addVariables(EnvironmentBuilder.createEnvironment(ExecutorTask.TASK_TYPE, "TEST")))
                        .build()
                        .toByteString())
                .build();
        final FailingProcessTask failingProcessTask = new FailingProcessTask(mockExecutorDriver, taskInfo, false);
        final ExecutorService executorService = Executors.newCachedThreadPool();
        executorService.submit(failingProcessTask);

        Thread.sleep(100);
        Mockito.verify(mockExecutorDriver).sendStatusUpdate(Mockito.any());
    }
}
