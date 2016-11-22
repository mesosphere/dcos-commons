package com.mesosphere.sdk.executor;

import org.apache.commons.lang3.SerializationUtils;
import org.apache.mesos.ExecutorDriver;
import org.apache.mesos.Protos;
import com.mesosphere.sdk.offer.CommonTaskUtils;
import com.mesosphere.sdk.offer.TaskException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.*;

/**
 * Generic process task, that can be spawned using {@code CustomExecutor}.
 */
public class ProcessTask implements ExecutorTask {
    private static final Logger LOGGER = LoggerFactory.getLogger(ProcessTask.class);
    private final String taskType;
    private final ProcessBuilder processBuilder;
    private final ExecutorDriver driver;
    private final Protos.TaskInfo taskInfo;
    private final CompletableFuture<Boolean> initialized =
            new CompletableFuture<>();
    private final CompletableFuture<Integer> exit =
            new CompletableFuture<>();
    private volatile Process process;

    private boolean exitOnTermination;

    // TODO(mohit): Remove this when KillPolicy is available.
    private static final Duration TERMINATE_TIMEOUT = Duration.ofSeconds(10);

    public static ProcessTask create(ExecutorDriver executorDriver, Protos.TaskInfo taskInfo)
            throws IOException {
        return create(executorDriver, taskInfo, true);
    }

    public static ProcessTask create(ExecutorDriver executorDriver, Protos.TaskInfo taskInfo, boolean exitOnTermination)
            throws IOException {
        return create(executorDriver, taskInfo, CommonTaskUtils.getProcess(taskInfo), exitOnTermination);
    }

    public static ProcessTask create(
            ExecutorDriver executorDriver,
            Protos.TaskInfo taskInfo,
            ProcessBuilder processBuilder) throws IOException {
        return create(executorDriver, taskInfo, processBuilder, true);
    }

    public static ProcessTask create(
            ExecutorDriver executorDriver,
            Protos.TaskInfo taskInfo,
            ProcessBuilder processBuilder,
            boolean exitOnTermination) throws IOException {
        return new ProcessTask(executorDriver, taskInfo, processBuilder, exitOnTermination);
    }

    public ProcessBuilder getProcessBuilder() {
        return processBuilder;
    }

    protected ProcessTask(
            ExecutorDriver executorDriver,
            Protos.TaskInfo taskInfo,
            ProcessBuilder processBuilder,
            boolean exitOnTermination) throws IOException {
        this.driver = executorDriver;
        this.taskInfo = taskInfo;
        String taskTypeTest;
        try {
            taskTypeTest = CommonTaskUtils.getType(taskInfo);
        } catch (TaskException e) {
            taskTypeTest = ""; // not found
        }
        this.taskType = taskTypeTest;
        this.processBuilder = processBuilder;
        this.exitOnTermination = exitOnTermination;

        CommonTaskUtils.setupConfigFiles(taskInfo);
    }

    public void preStart() {
        // NOOP
    }

    @Override
    public void run() {
        try {
            preStart();

            LOGGER.info("Executing command: {}", processBuilder.command());
            LOGGER.info("With Environment: {}", processBuilder.environment());

            this.process = processBuilder.start();

            final String startMessage = "Launching Task: " + taskInfo.getName();
            CommonTaskUtils.sendStatus(
                    driver,
                    Protos.TaskState.TASK_RUNNING,
                    taskInfo.getTaskId(),
                    taskInfo.getSlaveId(),
                    taskInfo.getExecutor().getExecutorId(),
                    startMessage);
            initialized.complete(true);

            LOGGER.info(startMessage);
            waitUninterruptably(process);
            final int exitValue = process.exitValue();
            String exitMessage = String.format("Task: %s exited with code: %s", taskInfo.getTaskId(), exitValue);
            exit.complete(exitValue);
            Protos.TaskState taskState;

            if (exitValue == 0) {
                taskState = Protos.TaskState.TASK_FINISHED;
                exitMessage += exitValue;
            } else if (exitValue > 128) {
                taskState = Protos.TaskState.TASK_KILLED;
                exitMessage += (exitValue - 128);
            } else {
                taskState = Protos.TaskState.TASK_ERROR;
                exitMessage += exitValue;
            }

            CommonTaskUtils.sendStatus(
                    driver,
                    taskState,
                    taskInfo.getTaskId(),
                    taskInfo.getSlaveId(),
                    taskInfo.getExecutor().getExecutorId(),
                    exitMessage);

            LOGGER.info(exitMessage);
            if (exitOnTermination) {
                LOGGER.info("Executor is exiting because exitOnTermination: " + exitOnTermination);
                System.exit(ExecutorErrorCode.EXIT_ON_TERMINATION_SUCCESS.ordinal());
            }
        } catch (Throwable e) {
            LOGGER.error("Process task failed.", e);
            initialized.complete(false);
            exit.complete(1);
            CommonTaskUtils.sendStatus(
                    driver,
                    Protos.TaskState.TASK_FAILED,
                    taskInfo.getTaskId(),
                    taskInfo.getSlaveId(),
                    taskInfo.getExecutor().getExecutorId(),
                    e.getMessage(),
                    SerializationUtils.serialize(e));
            if (exitOnTermination) {
                System.exit(ExecutorErrorCode.EXIT_ON_TERMINATION_FAILURE.ordinal());
            }
        }
    }

    @Override
    public void stop(Future<?> future) {
        if (process != null) {
            LOGGER.info("Terminating process: task = {}", taskInfo);

            if (terminate(TERMINATE_TIMEOUT)) {
                LOGGER.info("Terminated process: task = {}", taskInfo.getTaskId());
            } else {
                LOGGER.warn("Failed to terminate process: task = {}",
                        taskInfo.getTaskId());
                LOGGER.info("Killing process task = {}", taskInfo.getTaskId());
                kill();
            }
        }
    }

    protected static void waitUninterruptably(final Process process) {
        while (true) {
            try {
                process.waitFor();
                return;
            } catch (InterruptedException ex) {
            }
        }
    }

    protected boolean isAlive() {
        return process != null && process.isAlive();
    }

    protected void sigTerm() {
        if (isAlive()) {
            LOGGER.info("Sending SIGTERM");
            process.destroy();
        }
    }

    protected void sigKill() {
        if (isAlive()) {
            LOGGER.info("Sending SIGKILL");
            process.destroyForcibly();
        }
    }

    private boolean waitInit() {
        while (true) {
            try {
                return initialized.get();
            } catch (InterruptedException | ExecutionException e) {
                LOGGER.warn("Not yet initialized.", e);
            }
        }
    }

    public boolean terminate(Duration timeout) {
        LOGGER.info("Terminating process");
        if (waitInit() && isAlive()) {
            sigTerm();
        }
        return waitExit(timeout);
    }

    public int kill() {
        LOGGER.info("Killing process: name = {}", taskInfo.getName());
        if (waitInit() && isAlive()) {
            sigKill();
        }
        return waitExit();
    }

    private int waitExit() {
        while (true) {
            try {
                return exit.get();
            } catch (InterruptedException | ExecutionException e) {
                LOGGER.warn("Not exited yet.", e);
            }
        }
    }

    private boolean waitExit(final Duration timeout) {
        while (true) {
            try {
                exit.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
                return true;
            } catch (InterruptedException | ExecutionException e) {
                LOGGER.warn("Not exited yet and timeout didn't expire.", e);
            } catch (TimeoutException e) {
                return false;
            }
        }
    }

}
