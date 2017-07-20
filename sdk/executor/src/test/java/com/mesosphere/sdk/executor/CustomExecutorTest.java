package com.mesosphere.sdk.executor;

import org.apache.mesos.ExecutorDriver;
import org.apache.mesos.Protos;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.contrib.java.lang.system.ExpectedSystemExit;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class CustomExecutorTest {
    private static final String TASK_TYPE = "TASK_TYPE";
    private static final String TEST = "TEST";

    @Rule
    public final ExpectedSystemExit exit = ExpectedSystemExit.none();

    @Mock
    ExecutorDriver mockExecutorDriver;

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

    private static CustomExecutor getTestExecutor() {
        final ExecutorService executorService = Executors.newCachedThreadPool();
        final TestExecutorTaskFactory testExecutorTaskFactory = new TestExecutorTaskFactory();
        return new CustomExecutor(executorService, testExecutorTaskFactory);
    }
}
