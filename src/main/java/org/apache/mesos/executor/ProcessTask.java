package org.apache.mesos.executor;

import org.apache.commons.lang3.SerializationUtils;
import org.apache.mesos.ExecutorDriver;
import org.apache.mesos.Protos;
import org.apache.mesos.offer.TaskUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Paths;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Generic process task, that can be spawned using {@code CustomExecutor}.
 */
public class ProcessTask extends ExecutorTask {
    private static final Logger LOGGER = LoggerFactory.getLogger(ProcessTask.class);

    private ExecutorDriver driver;
    private Protos.TaskInfo task;
    private volatile Process process;
    private final CompletableFuture<Boolean> initialized =
            new CompletableFuture<>();
    private final CompletableFuture<Integer> exit =
            new CompletableFuture<>();

    // TODO(mohit): Remove this when KillPolicy is available.
    private static final Duration TERMINATE_TIMEOUT = Duration.ofSeconds(10);

    public ProcessTask(ExecutorDriver executorDriver, Protos.TaskInfo task) {
        this.driver = executorDriver;
        this.task = task;
    }

    public void preStart() {

    }

    @Override
    public void start() {
        try {
            preStart();

            final Protos.CommandInfo taskData = Protos.CommandInfo.parseFrom(task.getData());
            final Map<String, String> envMap = TaskUtils.fromEnvironmentToMap(taskData.getEnvironment());
            final String taskType = envMap.get(ExecutorTask.TASK_TYPE);
            final String command = taskData.getValue();

            ProcessBuilder builder = new ProcessBuilder("/bin/sh", "-c", command)
                    .redirectOutput(Paths.get(taskType + ".stdout.log").toFile())
                    .redirectError(Paths.get(taskType + ".stderr.log").toFile());
            builder.environment().putAll(envMap);

            LOGGER.info("Executing command: {}", builder.command());
            LOGGER.info("With Environment: {}", builder.environment());

            this.process = builder.start();

            final String startMessage = "Launched Process of type: " + taskType;
            TaskUtils.sendStatus(
                    driver,
                    Protos.TaskState.TASK_RUNNING,
                    task.getTaskId(),
                    task.getSlaveId(),
                    task.getExecutor().getExecutorId(),
                    startMessage);
            initialized.complete(true);

            LOGGER.info(startMessage);

            waitUninterruptably(process);

            final int exitValue = process.exitValue();

            String exitMessage = "Process of type: "
                    + taskType + " exited with code: ";

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

            TaskUtils.sendStatus(
                    driver,
                    taskState,
                    task.getTaskId(),
                    task.getSlaveId(),
                    task.getExecutor().getExecutorId(),
                    exitMessage);

            System.exit(0);
        } catch (Throwable e) {
            LOGGER.error("Process task failed.", e);
            initialized.complete(false);
            exit.complete(1);
            TaskUtils.sendStatus(
                    driver,
                    Protos.TaskState.TASK_FAILED,
                    task.getTaskId(),
                    task.getSlaveId(),
                    task.getExecutor().getExecutorId(),
                    e.getMessage(),
                    SerializationUtils.serialize(e));
            System.exit(1);
        }
    }

    @Override
    public boolean checkHealth() {
        return false;
    }

    @Override
    public void stop() {
        if (process != null) {
            LOGGER.info("Terminating process: task = {}", task);

            if (terminate(TERMINATE_TIMEOUT)) {
                LOGGER.info("Terminated process: task = {}", task.getTaskId());
            } else {
                LOGGER.warn("Failed to terminate process: task = {}",
                        task.getTaskId());
                LOGGER.info("Killing process task = {}", task.getTaskId());
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
        return (process != null) ? process.isAlive() : false;
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
        LOGGER.info("Killing process: name = {}", task.getName());
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
