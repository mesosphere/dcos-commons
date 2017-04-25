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
public class CheckMonitor implements Callable<Optional<CheckStats>> {
    private final Logger logger = LoggerFactory.getLogger(getClass());

    private final CheckHandler healthCheckHandler;
    private final LaunchedTask launchedTask;
    private final Protos.HealthCheck healthCheck;
    private final String checkType;

    public CheckMonitor(
            Protos.HealthCheck healthCheck,
            CheckHandler healthCheckHandler,
            LaunchedTask launchedTask,
            String checkType) {
        this.healthCheck = healthCheck;
        this.healthCheckHandler = healthCheckHandler;
        this.launchedTask = launchedTask;
        this.checkType = checkType;
    }

    @Override
    public Optional<CheckStats> call() throws Exception {
        Future<?> healthCheck = healthCheckHandler.start();
        Optional<CheckStats> healthCheckStats = Optional.empty();

        try {
            healthCheck.get();
        } catch (Throwable t) {

            if (t.getCause() instanceof CheckHandler.CheckRuntimeException) {
                CheckHandler.CheckRuntimeException healthCheckRuntimeException =
                        (CheckHandler.CheckRuntimeException) t.getCause();
                healthCheckStats = Optional.of(healthCheckRuntimeException.getHealthCheckStats());
            } else {
                logger.error(String.format("Waiting for %s check failed with exception: ", checkType), t);
            }
        }

        if (this.healthCheck.getConsecutiveFailures() > 0) {
            launchedTask.getExecutorTask().stop(launchedTask.getFuture());
        }
        return healthCheckStats;
    }
}
