package org.apache.mesos.executor;

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
    private final ExecutorTask executorTask;
    private final Logger logger = LoggerFactory.getLogger(getClass());

    public HealthCheckMonitor(
            HealthCheckHandler healthCheckHandler,
            ExecutorTask executorTask) {
        this.healthCheckHandler = healthCheckHandler;
        this.executorTask = executorTask;
    }

    @Override
    public Optional<HealthCheckStats> call() throws Exception {
        Future<?> healthCheck = healthCheckHandler.start();
        Optional<HealthCheckStats> healthCheckStats = Optional.empty();

        try {
            healthCheck.get();
        } catch (Throwable t) {
            logger.error("Waiting for failed health check failed with exception: ", t);
            if (t.getCause() instanceof HealthCheckHandler.HealthCheckRuntimeException) {
                HealthCheckHandler.HealthCheckRuntimeException healthCheckRuntimeException =
                        (HealthCheckHandler.HealthCheckRuntimeException) t.getCause();
                healthCheckStats = Optional.of(healthCheckRuntimeException.getHealthCheckStats());
            }
        }

        executorTask.stop();
        return healthCheckStats;
    }
}
