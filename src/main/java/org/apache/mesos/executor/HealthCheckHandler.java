package org.apache.mesos.executor;

import org.apache.mesos.Protos;
import org.apache.mesos.offer.TaskUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Each HealthCheckHandler is responsible for executing a single HealthCheck defined for a TaskInfo.
 * The start() method returns a Future which can be waited upon.  It will only complete if the
 * HealthCheck has failed, which is another way of saying that it has reached its maximum consecutive
 * failures limit. Health checks are not run during the grace period as their result would be ignored
 * in any case.
 */
public class HealthCheckHandler {
    private final Protos.TaskInfo taskInfo;
    private final ScheduledExecutorService scheduledExecutorService;
    private final HealthCheckStats healthCheckStats;
    private final Logger logger = LoggerFactory.getLogger(getClass());

    public static HealthCheckHandler create(
            Protos.TaskInfo taskInfo,
            ScheduledExecutorService scheduledExecutorService,
            HealthCheckStats healthCheckStats)
            throws HealthCheckValidationException {
        return new HealthCheckHandler(
                taskInfo,
                scheduledExecutorService,
                healthCheckStats);
    }

    private HealthCheckHandler(
            Protos.TaskInfo taskInfo,
            ScheduledExecutorService scheduledExecutorService,
            HealthCheckStats healthCheckStats)
            throws HealthCheckValidationException {
        validateTask(taskInfo);
        this.taskInfo = taskInfo;
        this.scheduledExecutorService = scheduledExecutorService;
        this.healthCheckStats = healthCheckStats;
    }

    public ScheduledFuture<?> start() {
        double interval = taskInfo.getHealthCheck().getIntervalSeconds();
        double delay = taskInfo.getHealthCheck().getDelaySeconds() +
                taskInfo.getHealthCheck().getGracePeriodSeconds();

        long intervalMs = (long) (interval * 1000);
        long delayMs = (long) (delay * 1000);

        logger.info("Scheduling health check.");
        return scheduledExecutorService.scheduleAtFixedRate(
                new HealthCheckRunner(taskInfo.getHealthCheck(), healthCheckStats),
                delayMs,
                intervalMs,
                TimeUnit.MILLISECONDS);
    }

    private void validateTask(Protos.TaskInfo taskInfo) throws HealthCheckValidationException {
        // Validate TaskInfo
        if (!taskInfo.hasHealthCheck()) {
            throw new HealthCheckValidationException("The following task does not contain a HealthCheck: " + taskInfo);
        }

        // Validate HealthCheck
        Protos.HealthCheck healthCheck = taskInfo.getHealthCheck();
        if (healthCheck.hasHttp()) {
            throw new HealthCheckValidationException(
                    "The following task contains an unsupported HTTP HealthCheck: " + taskInfo);
        }

        if (!healthCheck.hasCommand()) {
            throw new HealthCheckValidationException(
                    "The following task does not contain a Command for its Healthcheck: " + taskInfo);
        }

        // Validate Command
        Protos.CommandInfo commandInfo = healthCheck.getCommand();
        if (!Boolean.valueOf(commandInfo.getShell())) {
            throw new HealthCheckValidationException(
                    "Only shell based health checks are supported for health check commmand: " + commandInfo);
        }
    }

    /**
     * This class spawns a subprocess for each invocation of a health check.  It records statistics
     * regarding successes and failures.
     */
    private static class HealthCheckRunner implements Runnable {
        private final Logger logger = LoggerFactory.getLogger(getClass());
        private final Protos.HealthCheck healthCheck;
        private final HealthCheckStats healthCheckStats;

        public HealthCheckRunner(Protos.HealthCheck healthCheck, HealthCheckStats healthCheckStats) {
            this.healthCheck = healthCheck;
            this.healthCheckStats = healthCheckStats;
            logger.info("Created health check runner.");
        }

        @Override
        public void run() {
            logger.info("Running health check");
            Protos.CommandInfo commandInfo = healthCheck.getCommand();

            try {
                final Map<String, String> envMap = TaskUtils.fromEnvironmentToMap(commandInfo.getEnvironment());
                List<String> command = new ArrayList<>();
                command.add("/bin/bash");
                command.add("-c");
                command.add(commandInfo.getValue());

                String[] cmdArray = new String[command.size()];
                cmdArray = command.toArray(cmdArray);
                ProcessBuilder processBuilder = new ProcessBuilder(cmdArray);
                processBuilder.inheritIO();
                processBuilder.environment().putAll(envMap);

                logger.info("Starting health check process: " + command);
                Process process = processBuilder.start();
                synchronized (process) {
                    process.wait((long) (healthCheck.getTimeoutSeconds() * 1000));
                }

                if (process.exitValue() != 0) {
                    healthCheckStats.failed();
                    logger.error(
                            "Failed to run health check: " + commandInfo + " with exit code: " + process.exitValue());
                } else {
                    logger.info("Health check: " + commandInfo + " succeeded.");
                    healthCheckStats.succeeded();
                }
            } catch (Throwable t) {
                logger.error("Failed to run health check: " + commandInfo + " with throwable: ", t);
                healthCheckStats.failed();
            }

            if (healthCheckStats.getConsecutiveFailures() >= healthCheck.getConsecutiveFailures()) {
                throw new HealthCheckRuntimeException(
                        "Health check exceeded its maximum consecutive failures.",
                        healthCheckStats);
            }
        }
    }

    /**
     * This class encapsulates Exceptions associated with health check validation.
     */
    public static class HealthCheckValidationException extends Exception {
        public HealthCheckValidationException(String msg) {
            super(msg);
        }
    }

    /**
     * This class encapsulates Exceptions associated with health check execution failures.  It encapsulates the
     * statistics associated with the running of a health check.
     */
    public static class HealthCheckRuntimeException extends RuntimeException {
        private HealthCheckStats healthCheckStats;

        public HealthCheckRuntimeException(String msg, HealthCheckStats healthCheckStats) {
            super(msg);
            this.healthCheckStats = healthCheckStats;
        }

        public HealthCheckStats getHealthCheckStats() {
            return healthCheckStats;
        }
    }
}
