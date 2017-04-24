package com.mesosphere.sdk.executor;

import com.mesosphere.sdk.testutils.TestConstants;
import org.apache.mesos.ExecutorDriver;
import org.apache.mesos.Protos;
import org.awaitility.Awaitility;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.concurrent.*;

import static org.awaitility.Awaitility.to;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.mockito.Mockito.*;

/**
 * Tests for {@link CheckHandler}
 */
public class CheckHandlerTest {

    private static final double SHORT_INTERVAL_S = 0.001;
    private static final double SHORT_DELAY_S = 0.002;
    private static final double SHORT_GRACE_PERIOD_S = 0.003;
    private static final double TIMEOUT_S = 456;

    private ScheduledExecutorService scheduledExecutorService;
    @Mock private CheckHandler.ProcessRunner mockProcessRunner;
    @Mock private ExecutorDriver executorDriver;
    @Captor private ArgumentCaptor<Protos.TaskStatus> taskStatusCaptor;
    private Protos.ExecutorInfo executorInfo = Protos.ExecutorInfo.newBuilder()
            .setExecutorId(TestConstants.EXECUTOR_ID)
            .setCommand(TestConstants.COMMAND_INFO)
            .build();
    private Protos.TaskInfo taskInfo = Protos.TaskInfo.newBuilder()
            .setName(TestConstants.TASK_NAME)
            .setTaskId(TestConstants.TASK_ID)
            .setSlaveId(TestConstants.AGENT_ID)
            .setExecutor(executorInfo)
            .build();

    @Before
    public void beforeEach() {
        MockitoAnnotations.initMocks(this);
        scheduledExecutorService = Executors.newScheduledThreadPool(1);
    }

    @After
    public void afterEach() throws InterruptedException {
        scheduledExecutorService.shutdown();
        scheduledExecutorService.awaitTermination(5, TimeUnit.SECONDS);
    }

    @Test
    public void testSingleFailure() throws Exception {
        int maxConsecutiveFailures = 1;
        CheckStats healthCheckStats = new CheckStats("test");
        CheckHandler healthCheckHandler = new CheckHandler(
                executorDriver,
                taskInfo,
                mockProcessRunner,
                getHealthCheck(maxConsecutiveFailures),
                scheduledExecutorService,
                healthCheckStats,
                "test");

        when(mockProcessRunner.run(any(), anyDouble())).thenReturn(1);

        ScheduledFuture<?> future = healthCheckHandler.start();
        try {
            future.get();
        } catch (Throwable t) {
            Assert.assertTrue(t instanceof ExecutionException);
        }

        Assert.assertEquals(maxConsecutiveFailures, healthCheckStats.getTotalFailures());
        Assert.assertEquals(maxConsecutiveFailures, healthCheckStats.getConsecutiveFailures());
        Assert.assertEquals(0, healthCheckStats.getTotalSuccesses());
        Assert.assertEquals(0, healthCheckStats.getConsecutiveSuccesses());

        verify(mockProcessRunner, times(1)).run(any(), eq(TIMEOUT_S));
    }

    @Test
    public void testThriceFailure() throws Exception {
        int maxConsecutiveFailures = 3;
        CheckStats healthCheckStats = new CheckStats("test");
        CheckHandler healthCheckHandler = new CheckHandler(
                executorDriver,
                taskInfo,
                mockProcessRunner,
                getHealthCheck(maxConsecutiveFailures),
                scheduledExecutorService,
                healthCheckStats,
                "test");

        when(mockProcessRunner.run(any(), anyDouble())).thenReturn(1);

        ScheduledFuture<?> future = healthCheckHandler.start();
        try {
            future.get();
        } catch (Throwable t) {
            Assert.assertTrue(t instanceof ExecutionException);
        }

        Assert.assertEquals(maxConsecutiveFailures, healthCheckStats.getTotalFailures());
        Assert.assertEquals(maxConsecutiveFailures, healthCheckStats.getConsecutiveFailures());
        Assert.assertEquals(0, healthCheckStats.getTotalSuccesses());
        Assert.assertEquals(0, healthCheckStats.getConsecutiveSuccesses());

        verify(mockProcessRunner, times(maxConsecutiveFailures)).run(any(), eq(TIMEOUT_S));
    }

    @Test
    public void testSingleException() throws Exception {
        int maxConsecutiveFailures = 1;
        CheckStats healthCheckStats = new CheckStats("test");
        CheckHandler healthCheckHandler = new CheckHandler(
                executorDriver,
                taskInfo,
                mockProcessRunner,
                getHealthCheck(maxConsecutiveFailures),
                scheduledExecutorService,
                healthCheckStats,
                "test");

        when(mockProcessRunner.run(any(), anyDouble())).thenThrow(new IllegalArgumentException("hello"));

        ScheduledFuture<?> future = healthCheckHandler.start();
        try {
            future.get();
        } catch (Throwable t) {
            Assert.assertTrue(t instanceof ExecutionException);
        }

        Assert.assertEquals(maxConsecutiveFailures, healthCheckStats.getTotalFailures());
        Assert.assertEquals(maxConsecutiveFailures, healthCheckStats.getConsecutiveFailures());
        Assert.assertEquals(0, healthCheckStats.getTotalSuccesses());
        Assert.assertEquals(0, healthCheckStats.getConsecutiveSuccesses());

        verify(mockProcessRunner, times(1)).run(any(), eq(TIMEOUT_S));
    }

    @Test
    public void testThriceException() throws Exception {
        int maxConsecutiveFailures = 3;
        CheckStats healthCheckStats = new CheckStats("test");
        CheckHandler healthCheckHandler = new CheckHandler(
                executorDriver,
                taskInfo,
                mockProcessRunner,
                getHealthCheck(maxConsecutiveFailures),
                scheduledExecutorService,
                healthCheckStats,
                "test");

        when(mockProcessRunner.run(any(), anyDouble())).thenThrow(new IllegalArgumentException("hello"));

        ScheduledFuture<?> future = healthCheckHandler.start();
        try {
            future.get();
        } catch (Throwable t) {
            Assert.assertTrue(t instanceof ExecutionException);
        }

        Assert.assertEquals(maxConsecutiveFailures, healthCheckStats.getTotalFailures());
        Assert.assertEquals(maxConsecutiveFailures, healthCheckStats.getConsecutiveFailures());
        Assert.assertEquals(0, healthCheckStats.getTotalSuccesses());
        Assert.assertEquals(0, healthCheckStats.getConsecutiveSuccesses());

        verify(mockProcessRunner, times(maxConsecutiveFailures)).run(any(), eq(TIMEOUT_S));
    }

    @Test
    public void testSuccess() throws Exception {
        CheckStats healthCheckStats = new CheckStats("test");
        CheckHandler healthCheckHandler = new CheckHandler(
                executorDriver,
                taskInfo,
                mockProcessRunner,
                getHealthCheck(1),
                scheduledExecutorService,
                healthCheckStats,
                "test");

        when(mockProcessRunner.run(any(), anyDouble())).thenReturn(0);

        healthCheckHandler.start();
        Awaitility.await().atMost(5, TimeUnit.SECONDS).untilCall(to(healthCheckStats).getTotalSuccesses(), greaterThan(1L));

        Assert.assertEquals(0, healthCheckStats.getTotalFailures());
        Assert.assertEquals(0, healthCheckStats.getConsecutiveFailures());
        long consecutiveSuccesses = healthCheckStats.getConsecutiveSuccesses();
        Assert.assertTrue("Found consecutive successes: " + consecutiveSuccesses, consecutiveSuccesses >= 1);

        verify(mockProcessRunner, atLeast((int)consecutiveSuccesses)).run(any(), eq(TIMEOUT_S));
    }

    @Test(expected=CheckHandler.CheckValidationException.class)
    public void testFailHasHealthCheckValidation() throws CheckHandler.CheckValidationException {
        Protos.TaskInfo taskInfo = getTask().toBuilder()
                .clearHealthCheck()
                .build();

        CheckHandler.create(
                executorDriver,
                taskInfo,
                taskInfo.getHealthCheck(),
                scheduledExecutorService,
                new CheckStats("test"),
                "test");
    }

    @Test(expected=CheckHandler.CheckValidationException.class)
    public void testFailHttpHealthCheckValidation() throws CheckHandler.CheckValidationException {
        Protos.TaskInfo taskInfo = getTask().toBuilder()
                .setHealthCheck(Protos.HealthCheck.newBuilder()
                        .setHttp(Protos.HealthCheck.HTTPCheckInfo.newBuilder().setPort(2))
                        .build())
                .build();

        CheckHandler.create(
                executorDriver,
                taskInfo,
                taskInfo.getHealthCheck(),
                scheduledExecutorService,
                new CheckStats("test"),
                "test");
    }

    @Test(expected=CheckHandler.CheckValidationException.class)
    public void testFailNoCommandHealthCheckValidation() throws CheckHandler.CheckValidationException {
        Protos.TaskInfo taskInfo = getTask().toBuilder()
                .setHealthCheck(getHealthCheck(1).toBuilder().clearCommand())
                .build();

        CheckHandler.create(
                executorDriver,
                taskInfo,
                taskInfo.getHealthCheck(),
                scheduledExecutorService,
                new CheckStats("test"),
                "test");
    }

    @Test(expected=CheckHandler.CheckValidationException.class)
    public void testFailNoShellHealthCheckValidation() throws CheckHandler.CheckValidationException {
        Protos.HealthCheck.Builder healthCheckBuilder = getHealthCheck(1).toBuilder();
        healthCheckBuilder.getCommandBuilder().setShell(false);
        Protos.TaskInfo taskInfo = Protos.TaskInfo.newBuilder(getTask())
                .setHealthCheck(healthCheckBuilder)
                .build();

        CheckHandler.create(
                executorDriver,
                taskInfo,
                taskInfo.getHealthCheck(),
                scheduledExecutorService,
                new CheckStats("test"),
                "test");
    }

    @Test
    public void testReadinessSuccess() throws Exception {
        CheckStats healthCheckStats = new CheckStats("test");
        CheckHandler healthCheckHandler = new CheckHandler(
                executorDriver,
                taskInfo,
                mockProcessRunner,
                getReadinessCheck(),
                scheduledExecutorService,
                healthCheckStats,
                "test");

        when(mockProcessRunner.run(any(), anyDouble())).thenReturn(0);

        healthCheckHandler.start();
        Awaitility.await().atMost(5, TimeUnit.SECONDS).untilCall(to(healthCheckStats).getTotalSuccesses(), greaterThanOrEqualTo(1L));

        Assert.assertEquals(0, healthCheckStats.getTotalFailures());
        Assert.assertEquals(0, healthCheckStats.getConsecutiveFailures());
        long consecutiveSuccesses = healthCheckStats.getConsecutiveSuccesses();
        Assert.assertTrue("Found consecutive successes: " + consecutiveSuccesses, consecutiveSuccesses >= 1);

        verify(mockProcessRunner, atLeast((int)consecutiveSuccesses)).run(any(), eq(TIMEOUT_S));
        Awaitility.await().atMost(5, TimeUnit.SECONDS).untilCall(to(healthCheckStats).getTotalSuccesses(), greaterThanOrEqualTo(1L));

        verify(executorDriver, atLeastOnce()).sendStatusUpdate(taskStatusCaptor.capture());
        String readinessCheckKey = taskStatusCaptor.getValue().getLabels().getLabels(0).getKey();
        String readinessCheckValue = taskStatusCaptor.getValue().getLabels().getLabels(0).getValue();
        Assert.assertEquals("readiness_check_passed", readinessCheckKey);
        Assert.assertEquals("true", readinessCheckValue);
    }

    private static Protos.TaskInfo getTask() {
        return Protos.TaskInfo.newBuilder()
                .setName("task-health-check")
                .setTaskId(Protos.TaskID.newBuilder().setValue("task-health-check-task-id"))
                .setSlaveId(Protos.SlaveID.newBuilder().setValue("task-health-check-slave-id"))
                .setHealthCheck(getHealthCheck(1))
                .build();
    }

    private static Protos.HealthCheck getHealthCheck(int maxConsecutiveFailures) {
        return Protos.HealthCheck.newBuilder()
                .setIntervalSeconds(SHORT_INTERVAL_S)
                .setDelaySeconds(SHORT_DELAY_S)
                .setGracePeriodSeconds(SHORT_GRACE_PERIOD_S)
                .setTimeoutSeconds(TIMEOUT_S)
                .setConsecutiveFailures(maxConsecutiveFailures)
                .setCommand(Protos.CommandInfo.newBuilder()
                        .setValue("this_command_should_not_be_run")
                        .build())
                .build();
    }

    private static Protos.HealthCheck getReadinessCheck() {
        return getHealthCheck(0);
    }
}
