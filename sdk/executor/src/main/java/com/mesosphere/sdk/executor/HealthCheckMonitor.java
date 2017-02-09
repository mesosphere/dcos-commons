package com.mesosphere.sdk.executor;

import org.apache.mesos.Protos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.Future;

/**
 * This class reacts to the failure of a health check by calling the stop() method of the ExecutorTask.
 */
public class HealthCheckMonitor implements Callable<Optional<HealthCheckStats>> {
    private final HealthCheckHandler healthCheckHandler;
    private final LaunchedTask launchedTask;
    private final Logger logger = LoggerFactory.getLogger(getClass());
    private final Protos.HealthCheck healthCheck;

    public HealthCheckMonitor(
            Protos.HealthCheck healthCheck,
            HealthCheckHandler healthCheckHandler,
            LaunchedTask launchedTask) {
        this.healthCheck = healthCheck;
        this.healthCheckHandler = healthCheckHandler;
        this.launchedTask = launchedTask;
    }

    @Override
    public Optional<HealthCheckStats> call() throws Exception {
        Future<?> healthCheck = healthCheckHandler.start();
        Optional<HealthCheckStats> healthCheckStats = Optional.empty();

        try {
            healthCheck.get();
        } catch (Throwable t) {

            if (t.getCause() instanceof HealthCheckHandler.HealthCheckRuntimeException) {
                HealthCheckHandler.HealthCheckRuntimeException healthCheckRuntimeException =
                        (HealthCheckHandler.HealthCheckRuntimeException) t.getCause();
                healthCheckStats = Optional.of(healthCheckRuntimeException.getHealthCheckStats());
            } else {
                logger.error("Waiting for health check failed with exception: ", t);
            }
        }

        if (this.healthCheck.getConsecutiveFailures() > 0) {
            launchedTask.getExecutorTask().stop(launchedTask.getFuture());
        }
        return healthCheckStats;
    }
}
