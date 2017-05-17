package com.mesosphere.sdk.executor;

import com.google.common.annotations.VisibleForTesting;
import com.google.protobuf.TextFormat;
import com.mesosphere.sdk.offer.ProcessBuilderUtils;
import com.mesosphere.sdk.offer.taskdata.ExecutorLabelWriter;

import org.apache.mesos.ExecutorDriver;
import org.apache.mesos.Protos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Each {@link CheckHandler} is responsible for executing a single HealthCheck defined for a TaskInfo.
 * The start() method returns a Future which can be waited upon.  It will only complete if the
 * HealthCheck has failed, which is another way of saying that it has reached its maximum consecutive
 * failures limit. Health checks are not run during the grace period as their result would be ignored
 * in any case.
 */
public class CheckHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(CheckHandler.class);

    private final Protos.HealthCheck healthCheck;
    private final ScheduledExecutorService scheduledExecutorService;
    private final CheckRunner healthCheckRunner;
    private final String checkType;

    public static CheckHandler create(
            ExecutorDriver executorDriver,
            Protos.TaskInfo taskInfo,
            Protos.HealthCheck healthCheck,
            ScheduledExecutorService scheduledExecutorService,
            CheckStats healthCheckStats,
            String checkType)
            throws CheckValidationException {
        return new CheckHandler(
                executorDriver,
                taskInfo,
                new ProcessRunner(),
                healthCheck,
                scheduledExecutorService,
                healthCheckStats,
                checkType);
    }

    /**
     * Allows providing a custom {@link ProcessRunner} for testing.
     */
    @VisibleForTesting
    CheckHandler(
            ExecutorDriver executorDriver,
            Protos.TaskInfo taskInfo,
            ProcessRunner processRunner,
            Protos.HealthCheck healthCheck,
            ScheduledExecutorService scheduledExecutorService,
            CheckStats healthCheckStats,
            String checkType)
            throws CheckValidationException {
        validate(healthCheck);
        this.healthCheck = healthCheck;
        this.scheduledExecutorService = scheduledExecutorService;
        this.healthCheckRunner = new CheckRunner(
                executorDriver,
                taskInfo,
                processRunner,
                healthCheck,
                healthCheckStats,
                checkType);
        this.checkType = checkType;
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
     * @throws CheckValidationException when a HealthCheck does not adhere to the HealthChecks supported
     *                                        by this Custom Executor
     */
    private void validate(Protos.HealthCheck healthCheck) throws CheckValidationException {
        // Validate HealthCheck
        if (healthCheck.hasHttp()) {
            throw new CheckValidationException(String.format(
                    "The following %s check contains an unsupported HTTP configuration: %s", checkType, healthCheck));
        }

        if (!healthCheck.hasCommand()) {
            throw new CheckValidationException(String.format(
                    "The following %s check does not contain a Command: %s", checkType, healthCheck));
        }

        // Validate Command
        Protos.CommandInfo commandInfo = healthCheck.getCommand();
        if (!Boolean.valueOf(commandInfo.getShell())) {
            throw new CheckValidationException(String.format(
                    "Only shell based %s checks are supported for commmand: %s", checkType, commandInfo));
        }
    }

    /**
     * This class spawns a subprocess for each invocation of a health check.  It records statistics
     * regarding successes and failures.
     */
    private static class CheckRunner implements Runnable {
        private static final Logger LOGGER = LoggerFactory.getLogger(CheckRunner.class);

        private final ExecutorDriver executorDriver;
        private final Protos.TaskInfo taskInfo;
        private final ProcessRunner processRunner;
        private final Protos.HealthCheck healthCheck;
        private final CheckStats healthCheckStats;
        private final String checkType;

        private CheckRunner(
                ExecutorDriver executorDriver,
                Protos.TaskInfo taskInfo,
                ProcessRunner processRunner,
                Protos.HealthCheck healthCheck,
                CheckStats healthCheckStats,
                String checkType) {
            this.executorDriver = executorDriver;
            this.taskInfo = taskInfo;
            this.processRunner = processRunner;
            this.healthCheck = healthCheck;
            this.healthCheckStats = healthCheckStats;
            this.checkType = checkType;
        }

        @Override
        public void run() {
            Protos.CommandInfo commandInfo = healthCheck.getCommand();
            try {
                LOGGER.info("Running {} check process: {}", checkType, commandInfo.getValue());
                int exitValue = processRunner.run(
                        ProcessBuilderUtils.buildProcess(commandInfo), healthCheck.getTimeoutSeconds());

                if (exitValue != 0) {
                    healthCheckStats.failed();
                    LOGGER.error("{} check failed with exit code {}: {}",
                            checkType, exitValue, commandInfo.getValue());
                } else {
                    LOGGER.info("{} check succeeded: {}", checkType, commandInfo.getValue());
                    healthCheckStats.succeeded();
                }

                LOGGER.debug("{} check stats: {}", checkType, healthCheckStats);
            } catch (Throwable t) {
                LOGGER.error(String.format(
                        "%s check failed with exception: %s", checkType, TextFormat.shortDebugString(commandInfo)),
                        t);
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
                TaskStatusUtils.sendStatus(
                        executorDriver,
                        Protos.TaskState.TASK_FAILED,
                        taskInfo.getTaskId(),
                        taskInfo.getSlaveId(),
                        taskInfo.getExecutor().getExecutorId(),
                        "Health check failed",
                        false);
                throw new CheckRuntimeException(
                        "Health check exceeded its maximum consecutive failures.",
                        healthCheckStats);
            } else if (healthCheckStats.getConsecutiveSuccesses() == 1) {
                TaskStatusUtils.sendStatus(
                        executorDriver,
                        Protos.TaskState.TASK_RUNNING,
                        taskInfo.getTaskId(),
                        taskInfo.getSlaveId(),
                        taskInfo.getExecutor().getExecutorId(),
                        "Health check passed",
                        true);
            }
        }

        private void handleReadinessCheck() {
            if (healthCheckStats.getTotalSuccesses() > 0) {
                TaskStatusUtils.sendStatus(
                        executorDriver,
                        Protos.TaskState.TASK_RUNNING,
                        taskInfo.getTaskId(),
                        taskInfo.getSlaveId(),
                        taskInfo.getExecutor().getExecutorId(),
                        "Readiness check passed",
                        true,
                        new ExecutorLabelWriter().setReadinessCheckPassed().toProto(),
                        null);
                throw new CheckRuntimeException("Readiness check passed", healthCheckStats);
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
    public static class CheckValidationException extends Exception {
        public CheckValidationException(String msg) {
            super(msg);
        }
    }

    /**
     * This class encapsulates Exceptions associated with health check execution failures.  It encapsulates the
     * statistics associated with the running of a health check.
     */
    public static class CheckRuntimeException extends RuntimeException {
        private CheckStats healthCheckStats;

        public CheckRuntimeException(String msg, CheckStats healthCheckStats) {
            super(msg);
            this.healthCheckStats = healthCheckStats;
        }

        public CheckStats getHealthCheckStats() {
            return healthCheckStats;
        }
    }
}
