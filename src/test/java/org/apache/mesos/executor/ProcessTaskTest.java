package org.apache.mesos.executor;

import org.apache.mesos.ExecutorDriver;
import org.apache.mesos.Protos;
import org.apache.mesos.Protos.SlaveID;
import org.apache.mesos.offer.TaskUtils;
import org.apache.mesos.protobuf.EnvironmentBuilder;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mockito;

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
                .setExecutorId(ExecutorUtils.toExecutorId(EXECUTOR_NAME))
                .setCommand(Protos.CommandInfo.newBuilder().setValue("ls")).build();
        final Protos.TaskInfo taskInfo = Protos.TaskInfo
                .newBuilder()
                .setName(TASK_NAME)
                .setTaskId(TaskUtils.toTaskId(TASK_NAME))
                .setSlaveId(SlaveID.newBuilder().setValue("ignored"))
                .setExecutor(executorInfo)
                .setData(Protos.CommandInfo
                        .newBuilder()
                        .setValue("sleep 5")
                        .setEnvironment(Protos.Environment
                                .newBuilder()
                                .addVariables(EnvironmentBuilder.createEnvironment(DcosTaskConstants.TASK_TYPE, "TEST")))
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
                .setName(EXECUTOR_NAME)
                .setExecutorId(ExecutorUtils.toExecutorId(EXECUTOR_NAME))
                .setCommand(Protos.CommandInfo.newBuilder().setValue("ls")).build();
        final Protos.TaskInfo taskInfo = Protos.TaskInfo
                .newBuilder()
                .setName(TASK_NAME)
                .setTaskId(TaskUtils.toTaskId(TASK_NAME))
                .setSlaveId(SlaveID.newBuilder().setValue("ignored"))
                .setExecutor(executorInfo)
                .setData(Protos.CommandInfo
                        .newBuilder()
                        .setValue("sleep 5")
                        .setEnvironment(Protos.Environment
                                .newBuilder()
                                .addVariables(EnvironmentBuilder.createEnvironment(DcosTaskConstants.TASK_TYPE, "TEST")))
                        .build()
                        .toByteString())
                .build();
        final FailingProcessTask failingProcessTask = new FailingProcessTask(mockExecutorDriver, taskInfo, false);
        final ExecutorService executorService = Executors.newCachedThreadPool();
        executorService.submit(failingProcessTask);

        Mockito.verify(mockExecutorDriver, timeout(1000)).sendStatusUpdate(Mockito.any());
    }
}
