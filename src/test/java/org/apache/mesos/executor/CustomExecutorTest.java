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

public class CustomExecutorTest {
    @Test
    public void testSimple() {
        final ExecutorService executorService = Executors.newCachedThreadPool();
        final TestExecutorTaskFactory testExecutorTaskFactory = new TestExecutorTaskFactory();
        final CustomExecutor customExecutor = new CustomExecutor(executorService, testExecutorTaskFactory);
        final Protos.ExecutorInfo executorInfo = Protos.ExecutorInfo
                .newBuilder()
                .setName("TEST_EXECUTOR")
                .setExecutorId(Protos.ExecutorID.newBuilder().setValue(UUID.randomUUID().toString()))
                .setCommand(Protos.CommandInfo.newBuilder().setValue("ls")).build();
        final ExecutorDriver mockExecutorDriver = Mockito.mock(ExecutorDriver.class);

        customExecutor.registered(mockExecutorDriver, executorInfo, null, null);

        final Protos.TaskInfo taskInfo = Protos.TaskInfo
                .newBuilder()
                .setName("TEST")
                .setTaskId(Protos.TaskID.newBuilder().setValue(UUID.randomUUID().toString()))
                .setSlaveId(Protos.SlaveID.newBuilder().setValue(UUID.randomUUID().toString()))
                .setExecutor(executorInfo)
                .setData(Protos.CommandInfo
                        .newBuilder()
                        .setValue("date")
                        .setEnvironment(Protos.Environment
                                .newBuilder()
                                .addVariables(EnvironmentBuilder.createEnvironment(ExecutorTask.TASK_TYPE, "TEST")))
                        .build()
                        .toByteString())
                .build();


        customExecutor.launchTask(mockExecutorDriver, taskInfo);
    }

    @Test
    public void testSimpleKill() {
        final ExecutorService executorService = Executors.newCachedThreadPool();
        final TestExecutorTaskFactory testExecutorTaskFactory = new TestExecutorTaskFactory();
        final CustomExecutor customExecutor = new CustomExecutor(executorService, testExecutorTaskFactory);
        final Protos.ExecutorInfo executorInfo = Protos.ExecutorInfo
                .newBuilder()
                .setName("TEST_EXECUTOR")
                .setExecutorId(Protos.ExecutorID.newBuilder().setValue(UUID.randomUUID().toString()))
                .setCommand(Protos.CommandInfo.newBuilder().setValue("ls")).build();
        final ExecutorDriver mockExecutorDriver = Mockito.mock(ExecutorDriver.class);

        customExecutor.registered(mockExecutorDriver, executorInfo, null, null);

        final Protos.TaskInfo taskInfo = Protos.TaskInfo
                .newBuilder()
                .setName("TEST")
                .setTaskId(Protos.TaskID.newBuilder().setValue(UUID.randomUUID().toString()))
                .setSlaveId(Protos.SlaveID.newBuilder().setValue(UUID.randomUUID().toString()))
                .setExecutor(executorInfo)
                .setData(Protos.CommandInfo
                        .newBuilder()
                        .setValue("date")
                        .setEnvironment(Protos.Environment
                                .newBuilder()
                                .addVariables(EnvironmentBuilder.createEnvironment(ExecutorTask.TASK_TYPE, "TEST")))
                        .build()
                        .toByteString())
                .build();


        customExecutor.launchTask(mockExecutorDriver, taskInfo);

        customExecutor.killTask(mockExecutorDriver, taskInfo.getTaskId());
    }

    @Test
    public void testRegisterAndReRegister() {
        final ExecutorService executorService = Executors.newCachedThreadPool();
        final TestExecutorTaskFactory testExecutorTaskFactory = new TestExecutorTaskFactory();
        final CustomExecutor customExecutor = new CustomExecutor(executorService, testExecutorTaskFactory);
        final ExecutorDriver mockExecutorDriver = Mockito.mock(ExecutorDriver.class);

        final Protos.ExecutorInfo executorInfo = Protos.ExecutorInfo
                .newBuilder()
                .setName("TEST_EXECUTOR")
                .setExecutorId(Protos.ExecutorID.newBuilder().setValue(UUID.randomUUID().toString()))
                .setCommand(Protos.CommandInfo.newBuilder().setValue("ls")).build();

        final String localhost = "localhost";
        final Protos.SlaveInfo oldSlaveInfo = Protos.SlaveInfo
                .newBuilder()
                .setId(Protos.SlaveID.newBuilder().setValue(UUID.randomUUID().toString()))
                .setHostname(localhost)
                .build();

        customExecutor.registered(mockExecutorDriver, executorInfo, null, oldSlaveInfo);

        Assert.assertTrue(customExecutor.getSlaveInfo().isPresent());
        Assert.assertTrue(customExecutor.getSlaveInfo().get().equals(oldSlaveInfo));

        final Protos.SlaveInfo newSlaveInfo = Protos.SlaveInfo
                .newBuilder()
                .setId(Protos.SlaveID.newBuilder().setValue(UUID.randomUUID().toString()))
                .setHostname(localhost)
                .build();

        customExecutor.reregistered(mockExecutorDriver, newSlaveInfo);
        Assert.assertTrue(customExecutor.getSlaveInfo().isPresent());
        Assert.assertTrue(customExecutor.getSlaveInfo().get().equals(newSlaveInfo));
    }

    @Test
    public void testSimpleShutdown() {
        final ExecutorService executorService = Executors.newCachedThreadPool();
        final TestExecutorTaskFactory testExecutorTaskFactory = new TestExecutorTaskFactory();
        final CustomExecutor customExecutor = new CustomExecutor(executorService, testExecutorTaskFactory);
        final Protos.ExecutorInfo executorInfo = Protos.ExecutorInfo
                .newBuilder()
                .setName("TEST_EXECUTOR")
                .setExecutorId(Protos.ExecutorID.newBuilder().setValue(UUID.randomUUID().toString()))
                .setCommand(Protos.CommandInfo.newBuilder().setValue("ls")).build();
        final ExecutorDriver mockExecutorDriver = Mockito.mock(ExecutorDriver.class);

        customExecutor.registered(mockExecutorDriver, executorInfo, null, null);

        final Protos.TaskInfo taskInfo = Protos.TaskInfo
                .newBuilder()
                .setName("TEST")
                .setTaskId(Protos.TaskID.newBuilder().setValue(UUID.randomUUID().toString()))
                .setSlaveId(Protos.SlaveID.newBuilder().setValue(UUID.randomUUID().toString()))
                .setExecutor(executorInfo)
                .setData(Protos.CommandInfo
                        .newBuilder()
                        .setValue("date")
                        .setEnvironment(Protos.Environment
                                .newBuilder()
                                .addVariables(EnvironmentBuilder.createEnvironment(ExecutorTask.TASK_TYPE, "TEST")))
                        .build()
                        .toByteString())
                .build();


        customExecutor.launchTask(mockExecutorDriver, taskInfo);

        customExecutor.shutdown(mockExecutorDriver);
    }

    @Test
    public void testNoTaskData() {
        final ExecutorService executorService = Executors.newCachedThreadPool();
        final TestExecutorTaskFactory testExecutorTaskFactory = new TestExecutorTaskFactory();
        final CustomExecutor customExecutor = new CustomExecutor(executorService, testExecutorTaskFactory);
        final ExecutorDriver mockExecutorDriver = Mockito.mock(ExecutorDriver.class);


        final Protos.ExecutorInfo executorInfo = Protos.ExecutorInfo
                .newBuilder()
                .setName("TEST_EXECUTOR")
                .setExecutorId(Protos.ExecutorID.newBuilder().setValue(UUID.randomUUID().toString()))
                .setCommand(Protos.CommandInfo.newBuilder().setValue("ls")).build();

        customExecutor.registered(mockExecutorDriver, executorInfo, null, null);

        final Protos.TaskInfo taskInfo = Protos.TaskInfo
                .newBuilder()
                .setName("TEST")
                .setTaskId(Protos.TaskID.newBuilder().setValue(UUID.randomUUID().toString()))
                .setSlaveId(Protos.SlaveID.newBuilder().setValue(UUID.randomUUID().toString()))
                .setExecutor(executorInfo)
                .build();


        customExecutor.launchTask(mockExecutorDriver, taskInfo);
        Mockito.verify(mockExecutorDriver).sendStatusUpdate(Mockito.any());
    }

    @Test
    public void testNoTaskType() {
        final ExecutorService executorService = Executors.newCachedThreadPool();
        final TestExecutorTaskFactory testExecutorTaskFactory = new TestExecutorTaskFactory();
        final CustomExecutor customExecutor = new CustomExecutor(executorService, testExecutorTaskFactory);

        final Protos.ExecutorInfo executorInfo = Protos.ExecutorInfo
                .newBuilder()
                .setName("TEST_EXECUTOR")
                .setExecutorId(Protos.ExecutorID.newBuilder().setValue(UUID.randomUUID().toString()))
                .setCommand(Protos.CommandInfo.newBuilder().setValue("ls"))
                .build();
        final ExecutorDriver mockExecutorDriver = Mockito.mock(ExecutorDriver.class);

        customExecutor.registered(mockExecutorDriver, executorInfo, null, null);

        final Protos.TaskInfo taskInfo = Protos.TaskInfo
                .newBuilder()
                .setName("TEST")
                .setTaskId(Protos.TaskID.newBuilder().setValue(UUID.randomUUID().toString()))
                .setSlaveId(Protos.SlaveID.newBuilder().setValue(UUID.randomUUID().toString()))
                .setExecutor(executorInfo)
                .setData(Protos.CommandInfo
                        .newBuilder()
                        .setValue("date")
                        .build()
                        .toByteString())
                .build();


        customExecutor.launchTask(mockExecutorDriver, taskInfo);

        Mockito.verify(mockExecutorDriver).sendStatusUpdate(Mockito.any());
    }

}
