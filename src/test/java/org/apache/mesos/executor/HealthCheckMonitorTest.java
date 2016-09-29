package org.apache.mesos.executor;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.testng.Assert;

import java.util.Optional;
import java.util.concurrent.*;

import static org.mockito.Mockito.when;

/**
 * This class tests the HealthCheckMonitor.
 */
public class HealthCheckMonitorTest {
    private static final int HEALTH_CHECK_THREAD_POOL_SIZE = 50;
    private static final ScheduledExecutorService scheduledExecutorService =
            Executors.newScheduledThreadPool(HEALTH_CHECK_THREAD_POOL_SIZE);
    private static final ExecutorService executorService = Executors.newCachedThreadPool();

    @Mock private static LaunchedTask launchedTask;
    @Mock private static ExecutorTask executorTask;

    @Before
    public void beforeEach() {
        MockitoAnnotations.initMocks(this);
        when(launchedTask.getExecutorTask()).thenReturn(executorTask);
    }

    @Test
    public void testStopHealthCheckMonitorForFailedHealthCheck()
            throws HealthCheckHandler.HealthCheckValidationException, InterruptedException, ExecutionException {
        HealthCheckMonitor healthCheckMonitor = new HealthCheckMonitor(
                HealthCheckHandler.create(
                        HealthCheckTestUtils.getFailingTask(1),
                        scheduledExecutorService, new HealthCheckStats("test")),
                launchedTask);
        Future<Optional<HealthCheckStats>> futureStats = executorService.submit(healthCheckMonitor);
        Optional<HealthCheckStats> optionalStats = futureStats.get();
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
