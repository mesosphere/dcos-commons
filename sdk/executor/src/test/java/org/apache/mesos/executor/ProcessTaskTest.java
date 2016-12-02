package org.apache.mesos.executor;

import org.apache.mesos.ExecutorDriver;
import org.apache.mesos.Protos;
import org.apache.mesos.Protos.SlaveID;
import org.apache.mesos.offer.CommonTaskUtils;
import org.apache.mesos.testutils.TaskTestUtils;
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

    private static final Protos.ExecutorInfo cmdPkgExecutorInfo = Protos.ExecutorInfo
            .newBuilder()
            .setExecutorId(Protos.ExecutorID.newBuilder().setValue("dummy_value_unused"))
            .setCommand(Protos.CommandInfo.newBuilder()
                    .setValue("sleep 5")
                    .setEnvironment(Protos.Environment
                            .newBuilder()
                            .addVariables(
                                    TaskTestUtils.createEnvironmentVariable(DcosTaskConstants.TASK_TYPE, "TEST")))
                    .build())
            .build();

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
                .setTaskId(CommonTaskUtils.toTaskId(TASK_NAME))
                .setSlaveId(SlaveID.newBuilder().setValue("ignored"))
                .setExecutor(executorInfo)
                .setData(cmdPkgExecutorInfo.toByteString())
                .build();

        final ProcessTask processTask = ProcessTask.create(
                mockExecutorDriver,
                CommonTaskUtils.unpackTaskInfo(taskInfo),
                false);

        Assert.assertFalse(processTask.isAlive());
        final ExecutorService executorService = Executors.newCachedThreadPool();
        executorService.submit(processTask);

        // Wait for processTask to run
        Thread.sleep(1000);

        Assert.assertTrue(processTask.isAlive());
        processTask.stop(null);
        Assert.assertFalse(processTask.isAlive());
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
                .setTaskId(CommonTaskUtils.toTaskId(TASK_NAME))
                .setSlaveId(SlaveID.newBuilder().setValue("ignored"))
                .setExecutor(executorInfo)
                .setData(cmdPkgExecutorInfo.toByteString())
                .build();

        final FailingProcessTask failingProcessTask = new FailingProcessTask(
                mockExecutorDriver,
                taskInfo,
                CommonTaskUtils.getProcess(taskInfo),
                false);
        final ExecutorService executorService = Executors.newCachedThreadPool();
        executorService.submit(failingProcessTask);

        Mockito.verify(mockExecutorDriver, timeout(1000)).sendStatusUpdate(Mockito.any());
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
