package com.mesosphere.sdk.executor;

import org.apache.mesos.ExecutorDriver;
import org.apache.mesos.Protos;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.UUID;
import java.util.concurrent.Executors;

public class CustomExecutorTest {
    private static final String TASK_TYPE = "TASK_TYPE";
    private static final String TEST = "TEST";

    @Mock ExecutorDriver mockExecutorDriver;
    @Mock Runnable mockExitCallback;

    private CustomExecutor customExecutor;

    @Before
    public void beforeEach() {
        MockitoAnnotations.initMocks(this);
        customExecutor = getTestExecutor();
    }

    @Test
    public void testSimple() {
        final Protos.ExecutorInfo executorInfo = getTestExecutorInfo();

        customExecutor.registered(mockExecutorDriver, executorInfo, null, null);

        final Protos.TaskInfo taskInfo = Protos.TaskInfo
                .newBuilder()
                .setName(TEST)
                .setTaskId(Protos.TaskID.newBuilder().setValue(UUID.randomUUID().toString()))
                .setSlaveId(Protos.SlaveID.newBuilder().setValue(UUID.randomUUID().toString()))
                .setExecutor(executorInfo)
                .setData(createCommandInfo().toByteString())
                .build();


        customExecutor.launchTask(mockExecutorDriver, taskInfo);
    }

    @Test
    public void testSimpleKill() {
        final Protos.ExecutorInfo executorInfo = getTestExecutorInfo();

        customExecutor.registered(mockExecutorDriver, executorInfo, null, null);

        final Protos.TaskInfo taskInfo = Protos.TaskInfo
                .newBuilder()
                .setName(TEST)
                .setTaskId(Protos.TaskID.newBuilder().setValue(UUID.randomUUID().toString()))
                .setSlaveId(Protos.SlaveID.newBuilder().setValue(UUID.randomUUID().toString()))
                .setExecutor(executorInfo)
                .setData(createCommandInfo().toByteString())
                .build();


        customExecutor.launchTask(mockExecutorDriver, taskInfo);

        customExecutor.killTask(mockExecutorDriver, taskInfo.getTaskId());
    }

    @Test
    public void testRegisterAndReRegister() {
        final Protos.ExecutorInfo executorInfo = getTestExecutorInfo();

        final String localhost = "localhost";
        final Protos.SlaveInfo oldSlaveInfo = Protos.SlaveInfo
                .newBuilder()
                .setId(Protos.SlaveID.newBuilder().setValue(UUID.randomUUID().toString()))
                .setHostname(localhost)
                .build();

        customExecutor.registered(mockExecutorDriver, executorInfo, null, oldSlaveInfo);

        final Protos.SlaveInfo newSlaveInfo = Protos.SlaveInfo
                .newBuilder()
                .setId(Protos.SlaveID.newBuilder().setValue(UUID.randomUUID().toString()))
                .setHostname(localhost)
                .build();

        customExecutor.reregistered(mockExecutorDriver, newSlaveInfo);
    }

    @Test
    public void testSimpleShutdown() {
        final Protos.ExecutorInfo executorInfo = getTestExecutorInfo();
        customExecutor.registered(mockExecutorDriver, executorInfo, null, null);

        final Protos.TaskInfo taskInfo = Protos.TaskInfo
                .newBuilder()
                .setName(TEST)
                .setTaskId(Protos.TaskID.newBuilder().setValue(UUID.randomUUID().toString()))
                .setSlaveId(Protos.SlaveID.newBuilder().setValue(UUID.randomUUID().toString()))
                .setExecutor(executorInfo)
                .setData(createCommandInfo().toByteString())
                .build();


        customExecutor.launchTask(mockExecutorDriver, taskInfo);

        customExecutor.shutdown(mockExecutorDriver);
    }

    private static Protos.CommandInfo createCommandInfo() {
        Protos.CommandInfo.Builder commandInfoBuilder = Protos.CommandInfo.newBuilder()
                .setValue("date");
        commandInfoBuilder.getEnvironmentBuilder().addVariablesBuilder().setName(TASK_TYPE).setValue(TEST);
        return commandInfoBuilder.build();
    }

    private static Protos.ExecutorInfo getTestExecutorInfo() {
        return Protos.ExecutorInfo
                .newBuilder()
                .setName("TEST_EXECUTOR")
                .setExecutorId(Protos.ExecutorID.newBuilder().setValue(UUID.randomUUID().toString()))
                .setCommand(Protos.CommandInfo.newBuilder().setValue("ls"))
                .build();
    }

    private CustomExecutor getTestExecutor() {
        return new CustomExecutor(
                Executors.newCachedThreadPool(),
                new ExecutorTaskFactory() {
                    @Override
                    public ExecutorTask createTask(Protos.TaskInfo task, ExecutorDriver driver) {
                        return new TestExecutorTask(task, driver);
                    }
                },
                mockExitCallback);
    }
}
