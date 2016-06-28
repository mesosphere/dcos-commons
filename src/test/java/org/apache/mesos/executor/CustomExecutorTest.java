package org.apache.mesos.executor;

import org.apache.mesos.ExecutorDriver;
import org.apache.mesos.Protos;
import org.apache.mesos.protobuf.EnvironmentBuilder;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.contrib.java.lang.system.ExpectedSystemExit;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.mockito.Mockito.times;

public class CustomExecutorTest {
    private Duration defaultSleep = Duration.ofMillis(100);
    private Duration defaultTimeout = Duration.ofSeconds(1);
    @Rule
    public final ExpectedSystemExit exit = ExpectedSystemExit.none();

    @Mock
    ExecutorDriver mockExecutorDriver;

    @Before
    public void beforeEach() {
        MockitoAnnotations.initMocks(this);
    }

    @Test
    public void testSimple() {
        final CustomExecutor customExecutor = getTestExecutor();
        final Protos.ExecutorInfo executorInfo = getTestExecutorInfo();

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
                                .addVariables(EnvironmentBuilder.createEnvironment(DcosTaskConstants.TASK_TYPE, "TEST")))
                        .build()
                        .toByteString())
                .build();


        customExecutor.launchTask(mockExecutorDriver, taskInfo);
    }

    @Test
    public void testSimpleKill() {
        final CustomExecutor customExecutor = getTestExecutor();
        final Protos.ExecutorInfo executorInfo = getTestExecutorInfo();

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
                                .addVariables(EnvironmentBuilder.createEnvironment(DcosTaskConstants.TASK_TYPE, "TEST")))
                        .build()
                        .toByteString())
                .build();


        customExecutor.launchTask(mockExecutorDriver, taskInfo);

        customExecutor.killTask(mockExecutorDriver, taskInfo.getTaskId());
    }

    @Test
    public void testRegisterAndReRegister() {
        final CustomExecutor customExecutor = getTestExecutor();
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
        final CustomExecutor customExecutor = getTestExecutor();
        final Protos.ExecutorInfo executorInfo = getTestExecutorInfo();
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
                                .addVariables(EnvironmentBuilder.createEnvironment(DcosTaskConstants.TASK_TYPE, "TEST")))
                        .build()
                        .toByteString())
                .build();


        customExecutor.launchTask(mockExecutorDriver, taskInfo);

        customExecutor.shutdown(mockExecutorDriver);
    }

    @Test
    public void testNoTaskData() {
        final CustomExecutor customExecutor = getTestExecutor(defaultSleep, defaultTimeout, false, false);
        final Protos.ExecutorInfo executorInfo = getTestExecutorInfo();

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
        final CustomExecutor customExecutor = getTestExecutor(defaultSleep, defaultTimeout, false, false);
        final Protos.ExecutorInfo executorInfo = getTestExecutorInfo();

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

    @Test
    public void testRegistrationTasksLaunch() {
        final ExecutorDriver mockExecutorDriver = Mockito.mock(ExecutorDriver.class);
        final CustomExecutor customExecutor = getTestExecutor();

        customExecutor.registered(mockExecutorDriver, getTestExecutorInfo(), null, null);
        Mockito.verify(mockExecutorDriver, times(1))
                .sendStatusUpdate(TestExecutorTaskFactory.getTaskStatus(DcosTaskConstants.ON_REGISTERED_TASK));
    }

    @Test
    public void testReregistrationTasksLaunch() {
        final ExecutorDriver mockExecutorDriver = Mockito.mock(ExecutorDriver.class);
        final CustomExecutor customExecutor = getTestExecutor();

        customExecutor.registered(mockExecutorDriver, getTestExecutorInfo(), null, null);
        customExecutor.reregistered(mockExecutorDriver, null);
        Mockito.verify(mockExecutorDriver, times(1))
                .sendStatusUpdate(TestExecutorTaskFactory.getTaskStatus(DcosTaskConstants.ON_REREGISTERED_TASK));
    }

    @Test
    public void testAllRegistrationTasksLaunch() {
        final ExecutorDriver mockExecutorDriver = Mockito.mock(ExecutorDriver.class);
        final CustomExecutor customExecutor = getTestExecutor();

        customExecutor.registered(mockExecutorDriver, getTestExecutorInfo(), null, null);
        customExecutor.reregistered(mockExecutorDriver, null);
        Mockito.verify(mockExecutorDriver, times(1))
                .sendStatusUpdate(TestExecutorTaskFactory.getTaskStatus(DcosTaskConstants.ON_REGISTERED_TASK));
        Mockito.verify(mockExecutorDriver, times(1))
                .sendStatusUpdate(TestExecutorTaskFactory.getTaskStatus(DcosTaskConstants.ON_REREGISTERED_TASK));
    }

    @Test
    public void testRegistrationTasksTimeout() {
        final ExecutorDriver mockExecutorDriver = Mockito.mock(ExecutorDriver.class);
        final CustomExecutor customExecutor = getTestExecutor(Duration.ofSeconds(10), Duration.ZERO, true, false);

        exit.expectSystemExitWithStatus(ExecutorErrorCode.ON_REGISTERED_TASK_FAILURE.ordinal());
        customExecutor.registered(mockExecutorDriver, getTestExecutorInfo(), null, null);
    }

    @Test
    public void testReregistrationTasksTimeout() {
        final ExecutorDriver mockExecutorDriver = Mockito.mock(ExecutorDriver.class);
        final CustomExecutor customExecutor = getTestExecutor(Duration.ofSeconds(10), Duration.ZERO, false, true);

        customExecutor.registered(mockExecutorDriver, getTestExecutorInfo(), null, null);
        exit.expectSystemExitWithStatus(ExecutorErrorCode.ON_REREGISTERED_TASK_FAILURE.ordinal());
        customExecutor.reregistered(mockExecutorDriver, null);
    }

    private CustomExecutor getTestExecutor() {
        return getTestExecutor(defaultSleep, defaultTimeout, true, true);
    }

    private CustomExecutor getTestExecutor(Duration sleep, Duration timeout, boolean onRegistered, boolean onReregistered) {
        final ExecutorService executorService = Executors.newCachedThreadPool();
        final TestExecutorTaskFactory testExecutorTaskFactory = new TestExecutorTaskFactory(sleep, timeout, onRegistered, onReregistered);
        return new CustomExecutor(executorService, testExecutorTaskFactory);
    }

    private Protos.ExecutorInfo getTestExecutorInfo() {
        return Protos.ExecutorInfo
                .newBuilder()
                .setName("TEST_EXECUTOR")
                .setExecutorId(Protos.ExecutorID.newBuilder().setValue(UUID.randomUUID().toString()))
                .setCommand(Protos.CommandInfo.newBuilder().setValue("ls"))
                .build();
    }

    private List<TimedExecutorTask> getTestRegistrationTasks() {
        return Arrays.asList(
                new TestTimedExecutorTask(
                        Duration.ofSeconds(10),
                        Duration.ZERO,
                        Protos.TaskStatus.newBuilder()
                                .setTaskId(Protos.TaskID.newBuilder().setValue("test-task-id"))
                                .setState(Protos.TaskState.TASK_FINISHED)
                                .build(),
                        mockExecutorDriver));
    }
}
