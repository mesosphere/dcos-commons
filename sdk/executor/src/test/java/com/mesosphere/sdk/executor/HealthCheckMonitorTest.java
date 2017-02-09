package com.mesosphere.sdk.executor;

import org.apache.mesos.ExecutorDriver;
import org.apache.mesos.Protos;
import org.apache.mesos.Protos.CommandInfo;
import org.apache.mesos.Protos.HealthCheck;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Optional;
import java.util.concurrent.*;

import static org.mockito.Mockito.*;

/**
 * This class tests the HealthCheckMonitor.
 */
public class HealthCheckMonitorTest {

    private static final double SHORT_INTERVAL_S = 0.001;
    private static final double SHORT_DELAY_S = 0.002;
    private static final double SHORT_GRACE_PERIOD_S = 0.003;
    private static final double TIMEOUT_S = 456;
    private static final int MAX_FAILURES = 1;
    private static final String COMMAND = "SOME COMMAND";

    private static final int HEALTH_CHECK_THREAD_POOL_SIZE = 50;
    private static final ScheduledExecutorService scheduledExecutorService =
            Executors.newScheduledThreadPool(HEALTH_CHECK_THREAD_POOL_SIZE);
    private static final ExecutorService executorService = Executors.newCachedThreadPool();

    @Mock private HealthCheckHandler.ProcessRunner mockProcessRunner;
    @Mock private LaunchedTask mockLaunchedTask;
    @Mock private ExecutorTask mockExecutorTask;
    @Mock private ExecutorDriver executorDriver;
    private Protos.TaskInfo taskInfo = Protos.TaskInfo.getDefaultInstance();

    @Before
    public void beforeEach() {
        MockitoAnnotations.initMocks(this);
        when(mockLaunchedTask.getExecutorTask()).thenReturn(mockExecutorTask);
    }

    @Test
    public void testStopHealthCheckMonitorForFailedHealthCheck() throws Exception {
        final HealthCheck healthCheck = HealthCheck.newBuilder()
                .setIntervalSeconds(SHORT_INTERVAL_S)
                .setDelaySeconds(SHORT_DELAY_S)
                .setGracePeriodSeconds(SHORT_GRACE_PERIOD_S)
                .setTimeoutSeconds(TIMEOUT_S)
                .setConsecutiveFailures(MAX_FAILURES)
                .setCommand(CommandInfo.newBuilder().setValue(COMMAND).build())
                .build();
        final HealthCheckHandler healthCheckHandler = new HealthCheckHandler(
                executorDriver,
                taskInfo,
                mockProcessRunner,
                healthCheck,
                scheduledExecutorService,
                new HealthCheckStats("test"));
        when(mockProcessRunner.run(any(), anyDouble())).thenReturn(1); // return failure

        HealthCheckMonitor healthCheckMonitor = new HealthCheckMonitor(
                healthCheck,
                healthCheckHandler,
                mockLaunchedTask);

        Future<Optional<HealthCheckStats>> futureStats = executorService.submit(healthCheckMonitor);
        Optional<HealthCheckStats> optionalStats;
        try {
            optionalStats = futureStats.get();
        } catch (Exception e) {
            Assert.assertTrue(e.getCause().getMessage(), false);
            return;
        }
        Assert.assertTrue(optionalStats.isPresent());

        HealthCheckStats healthCheckStats = optionalStats.get();
        Assert.assertEquals(1, healthCheckStats.getTotalFailures());
        Assert.assertEquals(0, healthCheckStats.getTotalSuccesses());
        Assert.assertEquals(1, healthCheckStats.getConsecutiveFailures());
        Assert.assertEquals(0, healthCheckStats.getConsecutiveSuccesses());

        executorService.shutdown();
        executorService.awaitTermination(10, TimeUnit.SECONDS);
    }
}
