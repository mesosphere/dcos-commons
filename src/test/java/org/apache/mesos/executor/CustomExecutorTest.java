package org.apache.mesos.executor;

import org.apache.mesos.ExecutorDriver;
import org.apache.mesos.Protos;
import org.apache.mesos.protobuf.EnvironmentBuilder;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.contrib.java.lang.system.ExpectedSystemExit;
import org.mockito.Mockito;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.mockito.Mockito.times;

public class CustomExecutorTest {
    @Rule
    public final ExpectedSystemExit exit = ExpectedSystemExit.none();

    @Test
    public void testSimple() {
        final CustomExecutor customExecutor = getTestExecutor();
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
                                .addVariables(EnvironmentBuilder.createEnvironment(DcosTaskConstants.TASK_TYPE, "TEST")))
                        .build()
                        .toByteString())
                .build();


        customExecutor.launchTask(mockExecutorDriver, taskInfo);
    }

    @Test
    public void testSimpleKill() {
        final CustomExecutor customExecutor = getTestExecutor();
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
        final CustomExecutor customExecutor = getTestExecutor();

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
                                .addVariables(EnvironmentBuilder.createEnvironment(DcosTaskConstants.TASK_TYPE, "TEST")))
                        .build()
                        .toByteString())
                .build();


        customExecutor.launchTask(mockExecutorDriver, taskInfo);

        customExecutor.shutdown(mockExecutorDriver);
    }

    @Test
    public void testNoTaskData() {
        final CustomExecutor customExecutor = getTestExecutor();
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
        final CustomExecutor customExecutor = getTestExecutor();

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

    @Test
    public void testRegistrationTasksLaunch() {
        final ExecutorDriver mockExecutorDriver = Mockito.mock(ExecutorDriver.class);
        final Protos.TaskStatus taskStatus = getTestTaskStatus();

        final List<TimedExecutorTask> onRegisteredTasks = Arrays.asList(
                new TestTimedExecutorTask(
                        Duration.ofMillis(100),
                        Duration.ofMinutes(1),
                        Protos.TaskStatus.newBuilder()
                        .setTaskId(Protos.TaskID.newBuilder().setValue("test-task-id"))
                        .setState(Protos.TaskState.TASK_FINISHED)
                        .build(),
                        mockExecutorDriver));

        final CustomExecutor customExecutor = getTestExecutor(
                onRegisteredTasks,
                null);

        customExecutor.registered(mockExecutorDriver, getTestExecutorInfo(), null, null);
        Mockito.verify(mockExecutorDriver, times(1)).sendStatusUpdate(taskStatus);
    }

    @Test
    public void testReregistrationTasksLaunch() {
        final ExecutorDriver mockExecutorDriver = Mockito.mock(ExecutorDriver.class);
        final Protos.TaskStatus taskStatus = getTestTaskStatus();

        final List<TimedExecutorTask> onRegisteredTasks = Arrays.asList(
                new TestTimedExecutorTask(
                        Duration.ofMillis(100),
                        Duration.ofMinutes(1),
                        Protos.TaskStatus.newBuilder()
                                .setTaskId(Protos.TaskID.newBuilder().setValue("test-task-id"))
                                .setState(Protos.TaskState.TASK_FINISHED)
                                .build(),
                        mockExecutorDriver));

        final CustomExecutor customExecutor = getTestExecutor(
                null,
                onRegisteredTasks);

        customExecutor.registered(mockExecutorDriver, getTestExecutorInfo(), null, null);
        customExecutor.reregistered(mockExecutorDriver, null);
        Mockito.verify(mockExecutorDriver, times(1)).sendStatusUpdate(taskStatus);
    }

    @Test
    public void testAllRegistrationTasksLaunch() {
        final ExecutorDriver mockExecutorDriver = Mockito.mock(ExecutorDriver.class);
        final Protos.TaskStatus taskStatus = getTestTaskStatus();

        final List<TimedExecutorTask> onAllRegisteredTasks = Arrays.asList(
                new TestTimedExecutorTask(
                        Duration.ofMillis(100),
                        Duration.ofMinutes(1),
                        Protos.TaskStatus.newBuilder()
                                .setTaskId(Protos.TaskID.newBuilder().setValue("test-task-id"))
                                .setState(Protos.TaskState.TASK_FINISHED)
                                .build(),
                        mockExecutorDriver));

        final CustomExecutor customExecutor = getTestExecutor(
                onAllRegisteredTasks,
                onAllRegisteredTasks);

        customExecutor.registered(mockExecutorDriver, getTestExecutorInfo(), null, null);
        customExecutor.reregistered(mockExecutorDriver, null);
        Mockito.verify(mockExecutorDriver, times(2)).sendStatusUpdate(taskStatus);
    }

    @Test
    public void testRegistrationTasksTimeout() {
        final ExecutorDriver mockExecutorDriver = Mockito.mock(ExecutorDriver.class);

        final List<TimedExecutorTask> onRegisteredTasks = Arrays.asList(
                new TestTimedExecutorTask(
                        Duration.ofSeconds(10),
                        Duration.ZERO,
                        Protos.TaskStatus.newBuilder()
                                .setTaskId(Protos.TaskID.newBuilder().setValue("test-task-id"))
                                .setState(Protos.TaskState.TASK_FINISHED)
                                .build(),
                        mockExecutorDriver));

        final CustomExecutor customExecutor = getTestExecutor(
                onRegisteredTasks,
                null);

        exit.expectSystemExitWithStatus(ExecutorErrorCode.ON_REGISTERED_TASK_FAILURE.ordinal());
        customExecutor.registered(mockExecutorDriver, getTestExecutorInfo(), null, null);
    }

    @Test
    public void testReregistrationTasksTimeout() {
        final ExecutorDriver mockExecutorDriver = Mockito.mock(ExecutorDriver.class);

        final List<TimedExecutorTask> onReregisteredTasks = Arrays.asList(
                new TestTimedExecutorTask(
                        Duration.ofSeconds(10),
                        Duration.ZERO,
                        Protos.TaskStatus.newBuilder()
                                .setTaskId(Protos.TaskID.newBuilder().setValue("test-task-id"))
                                .setState(Protos.TaskState.TASK_FINISHED)
                                .build(),
                        mockExecutorDriver));

        final CustomExecutor customExecutor = getTestExecutor(
                null,
                onReregisteredTasks);

        customExecutor.registered(mockExecutorDriver, getTestExecutorInfo(), null, null);
        exit.expectSystemExitWithStatus(ExecutorErrorCode.ON_REREGISTERED_TASK_FAILURE.ordinal());
        customExecutor.reregistered(mockExecutorDriver, null);
    }

    private CustomExecutor getTestExecutor() {
        final ExecutorService executorService = Executors.newCachedThreadPool();
        final TestExecutorTaskFactory testExecutorTaskFactory = new TestExecutorTaskFactory();
        return new CustomExecutor(executorService, testExecutorTaskFactory);
    }

    private CustomExecutor getTestExecutor(
            List<TimedExecutorTask> onRegisteredTasks,
            List<TimedExecutorTask> onReregisteredTasks) {
        final ExecutorService executorService = Executors.newCachedThreadPool();
        final TestExecutorTaskFactory testExecutorTaskFactory = new TestExecutorTaskFactory();

        return new CustomExecutor(
                executorService,
                testExecutorTaskFactory,
                onRegisteredTasks,
                onReregisteredTasks);
    }

    private Protos.ExecutorInfo getTestExecutorInfo() {
        return Protos.ExecutorInfo
                .newBuilder()
                .setName("TEST_EXECUTOR")
                .setExecutorId(Protos.ExecutorID.newBuilder().setValue(UUID.randomUUID().toString()))
                .setCommand(Protos.CommandInfo.newBuilder().setValue("ls"))
                .build();
    }

    private Protos.TaskStatus getTestTaskStatus(String taskId) {
        return Protos.TaskStatus.newBuilder()
                .setTaskId(Protos.TaskID.newBuilder().setValue(taskId))
                .setState(Protos.TaskState.TASK_FINISHED)
                .build();
    }

    private Protos.TaskStatus getTestTaskStatus() {
        return getTestTaskStatus("test-task-id");
    }
}
