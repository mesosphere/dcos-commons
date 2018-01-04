package com.mesosphere.sdk.executor;

import org.apache.mesos.ExecutorDriver;
import org.apache.mesos.Protos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.mesosphere.sdk.offer.taskdata.ProcessBuilderUtils;

import java.time.Duration;
import java.util.concurrent.*;

/**
 * Generic process task, that can be spawned using {@code CustomExecutor}.
 */
public class ProcessTask implements ExecutorTask {
    private static final Logger LOGGER = LoggerFactory.getLogger(ProcessTask.class);
    private final ProcessBuilder processBuilder;
    private final ExecutorDriver driver;
    private final Protos.TaskInfo taskInfo;
    private final CompletableFuture<Boolean> initialized =
            new CompletableFuture<>();
    private final CompletableFuture<Integer> exit =
            new CompletableFuture<>();
    private volatile Process process;

    private static final Duration noGracePeriod = Duration.ZERO;
    private final Duration taskKillGracePeriod;

    public static ProcessTask create(ExecutorDriver executorDriver, Protos.TaskInfo taskInfo) {
        return new ProcessTask(executorDriver, taskInfo, ProcessBuilderUtils.buildProcess(taskInfo.getCommand()));
    }

    protected ProcessTask(
            ExecutorDriver executorDriver,
            Protos.TaskInfo taskInfo,
            ProcessBuilder processBuilder) {
        this.driver = executorDriver;
        this.taskInfo = taskInfo;
        this.processBuilder = processBuilder;
        this.taskKillGracePeriod = getTaskKillGracePeriod(taskInfo);
    }

    private static Duration getTaskKillGracePeriod(Protos.TaskInfo taskInfo) {
        if (!taskInfo.hasKillPolicy()) {
            return noGracePeriod;
        }
        Protos.KillPolicy killPolicy = taskInfo.getKillPolicy();
        if (!killPolicy.hasGracePeriod()) {
            return noGracePeriod;
        }
        Protos.DurationInfo gracePeriod = killPolicy.getGracePeriod();
        return Duration.ofNanos(gracePeriod.getNanoseconds());
    }

    @Override
    public void run() {
        try {
            LOGGER.info("Executing command: {}", processBuilder.command());
            LOGGER.info("With Environment: {}", processBuilder.environment());

            if (processBuilder.command().isEmpty()) {
                TaskStatusUtils.sendStatus(
                        driver,
                        Protos.TaskState.TASK_FAILED,
                        taskInfo.getTaskId(),
                        taskInfo.getSlaveId(),
                        taskInfo.getExecutor().getExecutorId(),
                        String.format("Empty command found for: %s", taskInfo.getName()),
                        false);
                return;
            }

            this.process = processBuilder.start();

            final String startMessage = String.format(
                    "Launching Task: %s (%s)", taskInfo.getName(), taskInfo.getTaskId().getValue());
            LOGGER.info(startMessage);
            TaskStatusUtils.sendStatus(
                    driver,
                    Protos.TaskState.TASK_RUNNING,
                    taskInfo.getTaskId(),
                    taskInfo.getSlaveId(),
                    taskInfo.getExecutor().getExecutorId(),
                    startMessage,
                    true);
            initialized.complete(true);

            waitUninterruptably(process);
            final int exitValue = process.exitValue();
            exit.complete(exitValue);
            Protos.TaskState taskState;

            boolean isHealthy = true;
            String exitValueStr = String.valueOf(exitValue);
            if (exitValue == 0) {
                taskState = Protos.TaskState.TASK_FINISHED;
            } else if (exitValue > 128) {
                taskState = Protos.TaskState.TASK_KILLED;
                // Fatal error: 128 + N (e.g. kill -9 results in 137 == 128+9)
                exitValueStr = String.format("%d => killed(%d)", exitValue, exitValue - 128);
                isHealthy = false;
            } else {
                taskState = Protos.TaskState.TASK_FAILED;
                isHealthy = false;
            }

            String exitMessage = String.format(
                    "Task: %s (%s) exited with code: %s",
                    taskInfo.getName(), taskInfo.getTaskId().getValue(), exitValueStr);
            TaskStatusUtils.sendStatus(
                    driver,
                    taskState,
                    taskInfo.getTaskId(),
                    taskInfo.getSlaveId(),
                    taskInfo.getExecutor().getExecutorId(),
                    exitMessage,
                    isHealthy);

            LOGGER.info(exitMessage);
        } catch (Throwable e) {
            LOGGER.error(String.format("Task: %s (%s) failed", taskInfo.getName(), taskInfo.getTaskId().getValue()), e);
            initialized.complete(false);
            exit.complete(1);
            TaskStatusUtils.sendStatus(
                    driver,
                    Protos.TaskState.TASK_FAILED,
                    taskInfo.getTaskId(),
                    taskInfo.getSlaveId(),
                    taskInfo.getExecutor().getExecutorId(),
                    e.getMessage(),
                    false);
        }
    }

    @Override
    public void stop() {
        if ((process != null) &&
                (!terminate(this.taskKillGracePeriod))) {
            kill();
        }
    }

    private static void waitUninterruptably(final Process process) {
        while (true) {
            try {
                process.waitFor();
                return;
            } catch (InterruptedException ex) {
                // don't log anything: this loop is run throughout the task's lifecycle
            }
        }
    }

    boolean isAlive() {
        return process != null && process.isAlive();
    }

    private boolean waitInit() {
        while (true) {
            try {
                return initialized.get();
            } catch (InterruptedException | ExecutionException e) {
                LOGGER.warn(String.format("Resuming wait for process to initialize: %s (%s)",
                        taskInfo.getName(), taskInfo.getTaskId().getValue()), e);
            }
        }
    }

    private boolean terminate(Duration timeout) {
        if (timeout == null || timeout.toMillis() == 0L) {
            LOGGER.info("Skipping SIGTERM for process: {} ({})",
                    taskInfo.getName(), taskInfo.getTaskId().getValue());
            return false;
        }

        if (waitInit() && isAlive()) {
            LOGGER.info("Sending SIGTERM to process: {} ({}), waiting {}ms",
                    taskInfo.getName(), taskInfo.getTaskId().getValue(), timeout.toMillis());
            process.destroy();
        }
        while (true) {
            try {
                exit.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
                LOGGER.info("Process has exited following SIGTERM: {} ({})",
                        taskInfo.getName(), taskInfo.getTaskId().getValue());
                return true;
            } catch (InterruptedException | ExecutionException e) {
                LOGGER.warn(String.format("Resuming wait for process to exit following SIGTERM: %s (%s)",
                        taskInfo.getName(), taskInfo.getTaskId().getValue()), e);
            } catch (TimeoutException e) {
                LOGGER.warn("Process did not exit in {}ms following SIGTERM: {} ({})",
                        timeout.toMillis(), taskInfo.getName(), taskInfo.getTaskId().getValue());
                return false;
            }
        }
    }

    private void kill() {
        if (waitInit() && isAlive()) {
            LOGGER.info("Sending SIGKILL to process: {} ({})", taskInfo.getName(), taskInfo.getTaskId().getValue());
            process.destroyForcibly();
        }
        while (true) {
            try {
                exit.get();
                return;
            } catch (InterruptedException | ExecutionException e) {
                LOGGER.warn(String.format("Resuming wait for process to exit following SIGKILL: %s (%s)",
                        taskInfo.getName(), taskInfo.getTaskId().getValue()), e);
            }
        }
    }
}
