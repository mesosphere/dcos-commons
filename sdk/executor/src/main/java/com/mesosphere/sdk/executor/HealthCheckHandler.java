package com.mesosphere.sdk.executor;

import com.google.common.annotations.VisibleForTesting;
import com.mesosphere.sdk.offer.CommonTaskUtils;
import org.apache.mesos.ExecutorDriver;
import org.apache.mesos.Protos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
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
    private static final Logger LOGGER = LoggerFactory.getLogger(HealthCheckHandler.class);

    private final Protos.HealthCheck healthCheck;
    private final ScheduledExecutorService scheduledExecutorService;
    private final HealthCheckRunner healthCheckRunner;

    public static HealthCheckHandler create(
            ExecutorDriver executorDriver,
            Protos.TaskInfo taskInfo,
            Protos.HealthCheck healthCheck,
            ScheduledExecutorService scheduledExecutorService,
            HealthCheckStats healthCheckStats)
            throws HealthCheckValidationException {
        return new HealthCheckHandler(
                executorDriver,
                taskInfo,
                new ProcessRunner(),
                healthCheck,
                scheduledExecutorService,
                healthCheckStats);
    }

    /**
     * Allows providing a custom {@link ProcessRunner} for testing.
     */
    @VisibleForTesting
    HealthCheckHandler(
            ExecutorDriver executorDriver,
            Protos.TaskInfo taskInfo,
            ProcessRunner processRunner,
            Protos.HealthCheck healthCheck,
            ScheduledExecutorService scheduledExecutorService,
            HealthCheckStats healthCheckStats)
            throws HealthCheckValidationException {
        validate(healthCheck);
        this.healthCheck = healthCheck;
        this.scheduledExecutorService = scheduledExecutorService;
        this.healthCheckRunner = new HealthCheckRunner(
                executorDriver,
                taskInfo,
                processRunner,
                healthCheck,
                healthCheckStats);
    }

    public ScheduledFuture<?> start() {
        double interval = healthCheck.getIntervalSeconds();
        double delay = healthCheck.getDelaySeconds() +
                healthCheck.getGracePeriodSeconds();

        long intervalMs = (long) (interval * 1000);
        long delayMs = (long) (delay * 1000);

        LOGGER.info("Scheduling health check every {}ms following an initial {}ms delay.",
                intervalMs, delayMs);
        return scheduledExecutorService.scheduleAtFixedRate(
                healthCheckRunner,
                delayMs,
                intervalMs,
                TimeUnit.MILLISECONDS);
    }

    /**
     * Custom Executors are responsible for implementing HealthChecks.  This Custom Executor implements a subset of
     * all possible Health Checks describable by a HealthCheckInfo object.
     * <p>
     * They must be command HealthChecks as HTTP is not recommended by Mesos yet and is unneeded from a completeness
     * perspective.  `curl -f` normally is sufficient.  Since all HealthChecks as currently implemented by this Custom
     * Executor are excuted as sub-processes we further require that the HealthCheck specifies that it is a "shell"
     * command to avoid unexpected behavior.
     *
     * @param healthCheck The HealthCheck to be executed
     * @throws HealthCheckValidationException when a HealthCheck does not adhere to the HealthChecks supported
     *                                        by this Custom Executor
     */
    private void validate(Protos.HealthCheck healthCheck) throws HealthCheckValidationException {
        // Validate HealthCheck
        if (healthCheck.hasHttp()) {
            throw new HealthCheckValidationException(
                    "The following health check contains an unsupported HTTP configuration: " + healthCheck);
        }

        if (!healthCheck.hasCommand()) {
            throw new HealthCheckValidationException(
                    "The following health check does not contain a Command: " + healthCheck);
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
        private static final Logger LOGGER = LoggerFactory.getLogger(HealthCheckRunner.class);

        private final ExecutorDriver executorDriver;
        private final Protos.TaskInfo taskInfo;
        private final ProcessRunner processRunner;
        private final Protos.HealthCheck healthCheck;
        private final HealthCheckStats healthCheckStats;

        private HealthCheckRunner(
                ExecutorDriver executorDriver,
                Protos.TaskInfo taskInfo,
                ProcessRunner processRunner,
                Protos.HealthCheck healthCheck,
                HealthCheckStats healthCheckStats) {
            this.executorDriver = executorDriver;
            this.taskInfo = taskInfo;
            this.processRunner = processRunner;
            this.healthCheck = healthCheck;
            this.healthCheckStats = healthCheckStats;
        }

        @Override
        public void run() {
            Protos.CommandInfo commandInfo = healthCheck.getCommand();
            try {
                final Map<String, String> envMap = CommonTaskUtils.fromEnvironmentToMap(commandInfo.getEnvironment());
                List<String> command = new ArrayList<>();
                command.add("/bin/bash");
                command.add("-c");
                command.add(commandInfo.getValue());

                ProcessBuilder processBuilder = new ProcessBuilder(command);
                processBuilder.inheritIO();
                processBuilder.environment().putAll(envMap);

                LOGGER.info("Running health check process: {}", command);
                int exitValue = processRunner.run(processBuilder, healthCheck.getTimeoutSeconds());

                if (exitValue != 0) {
                    healthCheckStats.failed();
                    LOGGER.error("Health check failed with exit code {}: {}", exitValue, commandInfo);
                } else {
                    LOGGER.info("Health check succeeded: {}", commandInfo);
                    healthCheckStats.succeeded();
                }

                LOGGER.debug("Health check stats: {}", healthCheckStats);
            } catch (Throwable t) {
                LOGGER.error(String.format("Check failed with exception: %s", commandInfo), t);
                healthCheckStats.failed();
            }

            // Health checks have a positive consecutive failure count, readiness
            // checks do not.
            if (healthCheck.getConsecutiveFailures() > 0) {
                handleHealthCheck();
            } else {
                handleReadinessCheck();
            }
        }

        private void handleHealthCheck() {
            if (healthCheckStats.getConsecutiveFailures() >= healthCheck.getConsecutiveFailures()) {
                throw new HealthCheckRuntimeException(
                        "Health check exceeded its maximum consecutive failures.",
                        healthCheckStats);
            }
        }

        private void handleReadinessCheck() {
            if (healthCheckStats.getTotalSuccesses() > 0) {
                Protos.Labels labels = Protos.Labels.newBuilder().build();
                labels = CommonTaskUtils.withLabelSet(
                        labels,
                        CommonTaskUtils.READINESS_CHECK_PASSED_KEY,
                        "true").build();
                CommonTaskUtils.sendStatus(
                        executorDriver,
                        Protos.TaskState.TASK_RUNNING,
                        taskInfo.getTaskId(),
                        taskInfo.getSlaveId(),
                        taskInfo.getExecutor().getExecutorId(),
                        "Readiness check passed",
                        labels,
                        null);
                throw new HealthCheckRuntimeException("Readiness check passed.", healthCheckStats);
            }
        }
    }

    /**
     * Runs the provided process and returns an exit value. This is broken out into a separate
     * function to allow mockery in tests.
     */
    @VisibleForTesting
    static class ProcessRunner {
        public int run(ProcessBuilder processBuilder, double timeoutSeconds)
                throws IOException, InterruptedException {
            Process process = processBuilder.start();
            synchronized (process) {
                process.wait((long) (timeoutSeconds * 1000));
            }
            return process.exitValue();
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
