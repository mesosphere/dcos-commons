package org.apache.mesos.executor;

import org.apache.mesos.Protos;
import org.awaitility.Awaitility;
import org.junit.Assert;
import org.junit.Test;

import java.util.concurrent.*;

import static org.awaitility.Awaitility.to;
import static org.hamcrest.Matchers.greaterThan;

/**
 * This class tests the HealthCheckHandler class.
 */
public class HealthCheckHandlerTest {
    private static final double SHORT_INTERVAL_S = 0.001;
    private static final double SHORT_DELAY_S = 0.001;
    private static final double SHORT_GRACE_PERIOD_S = 0.001;
    private static final ScheduledExecutorService scheduledExecutorService = Executors.newScheduledThreadPool(1);

    @Test
    public void testSingleFailure() throws HealthCheckHandler.HealthCheckValidationException, ExecutionException, InterruptedException {
        int maxConsecutiveFailures = 1;
        HealthCheckStats healthCheckStats = new HealthCheckStats("test");
        HealthCheckHandler healthCheckHandler = HealthCheckHandler.create(
                HealthCheckTestUtils.getFailingTask(maxConsecutiveFailures),
                scheduledExecutorService,
                healthCheckStats);

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
    }

    @Test
    public void testThriceFailure() throws HealthCheckHandler.HealthCheckValidationException, ExecutionException, InterruptedException {
        int maxConsecutiveFailures = 3;
        HealthCheckStats healthCheckStats = new HealthCheckStats("test");
        HealthCheckHandler healthCheckHandler = HealthCheckHandler.create(
                HealthCheckTestUtils.getFailingTask(maxConsecutiveFailures),
                scheduledExecutorService,
                healthCheckStats);

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
    }

    @Test
    public void testSuccess() throws HealthCheckHandler.HealthCheckValidationException, ExecutionException, InterruptedException {
        int maxConsecutiveFailures = 1;
        Protos.TaskInfo failureTask = HealthCheckTestUtils.getSuccesfulTask();
        HealthCheckStats healthCheckStats = new HealthCheckStats("test");
        HealthCheckHandler healthCheckHandler = HealthCheckHandler.create(
                failureTask,
                scheduledExecutorService,
                healthCheckStats);

        healthCheckHandler.start();
        Awaitility.await().atMost(1, TimeUnit.SECONDS).untilCall(to(healthCheckStats).getTotalSuccesses(), greaterThan(1L));

        Assert.assertEquals(0, healthCheckStats.getTotalFailures());
        Assert.assertEquals(0, healthCheckStats.getConsecutiveFailures());
        long consecutiveSuccesses = healthCheckStats.getConsecutiveSuccesses();
        Assert.assertTrue("Found consecutive successes: " + consecutiveSuccesses, consecutiveSuccesses >= 1);
        Assert.assertEquals(healthCheckStats.getTotalSuccesses(), consecutiveSuccesses);
    }

    @Test(expected=HealthCheckHandler.HealthCheckValidationException.class)
    public void testFailHasHealthCheckValidation() throws HealthCheckHandler.HealthCheckValidationException {
        Protos.TaskInfo taskInfo = Protos.TaskInfo.newBuilder(HealthCheckTestUtils.getSuccesfulTask())
                .clearHealthCheck()
                .build();

        HealthCheckHandler.create(
                taskInfo,
                scheduledExecutorService,
                new HealthCheckStats("test"));
    }

    @Test(expected=HealthCheckHandler.HealthCheckValidationException.class)
    public void testFailHttpHealthCheckValidation() throws HealthCheckHandler.HealthCheckValidationException {
        Protos.TaskInfo taskInfo = Protos.TaskInfo.newBuilder(HealthCheckTestUtils.getSuccesfulTask())
                .setHealthCheck(Protos.HealthCheck.newBuilder()
                        .setHttp(Protos.HealthCheck.HTTPCheckInfo.newBuilder().setPort(2))
                        .build())
                .build();

        HealthCheckHandler.create(
                taskInfo,
                scheduledExecutorService,
                new HealthCheckStats("test"));
    }

    @Test(expected=HealthCheckHandler.HealthCheckValidationException.class)
    public void testFailNoCommandHealthCheckValidation() throws HealthCheckHandler.HealthCheckValidationException {
        Protos.TaskInfo taskInfo = Protos.TaskInfo.newBuilder(HealthCheckTestUtils.getSuccesfulTask())
                .setHealthCheck(Protos.HealthCheck.newBuilder(HealthCheckTestUtils.getSuccessfulHealthCheck())
                        .clearCommand()
                        .build())
                .build();

        HealthCheckHandler.create(
                taskInfo,
                scheduledExecutorService,
                new HealthCheckStats("test"));
    }
}
