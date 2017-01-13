package com.mesosphere.sdk.executor;

import org.apache.mesos.ExecutorDriver;
import org.apache.mesos.Protos;
import org.apache.mesos.Protos.TaskInfo;

import com.github.mustachejava.DefaultMustacheFactory;
import com.github.mustachejava.Mustache;
import com.github.mustachejava.MustacheFactory;
import com.mesosphere.sdk.offer.CommonTaskUtils;
import com.mesosphere.sdk.specification.ConfigFileSpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.StringReader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;

import java.time.Duration;
import java.util.Map;
import java.util.TreeMap;
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

    private boolean exitOnTermination;

    // TODO(mohit): Remove this when KillPolicy is available.
    private static final Duration TERMINATE_TIMEOUT = Duration.ofSeconds(10);

    public static ProcessTask create(ExecutorDriver executorDriver, Protos.TaskInfo taskInfo) {
        return create(executorDriver, taskInfo, true);
    }

    public static ProcessTask create(
            ExecutorDriver executorDriver,
            Protos.TaskInfo taskInfo,
            boolean exitOnTermination) {
        return create(executorDriver, taskInfo, CommonTaskUtils.getProcess(taskInfo), exitOnTermination);
    }

    public static ProcessTask create(
            ExecutorDriver executorDriver,
            Protos.TaskInfo taskInfo,
            ProcessBuilder processBuilder,
            boolean exitOnTermination) {
        return new ProcessTask(executorDriver, taskInfo, processBuilder, exitOnTermination);
    }

    public ProcessBuilder getProcessBuilder() {
        return processBuilder;
    }

    protected ProcessTask(
            ExecutorDriver executorDriver,
            Protos.TaskInfo taskInfo,
            ProcessBuilder processBuilder,
            boolean exitOnTermination) {
        this.driver = executorDriver;
        this.taskInfo = taskInfo;
        this.processBuilder = processBuilder;
        this.exitOnTermination = exitOnTermination;
    }

    public void preStart() {
        // NOOP
    }

    @Override
    public void run() {
        try {

            setupConfigFiles(taskInfo);

            preStart();

            LOGGER.info("Executing command: {}", processBuilder.command());
            LOGGER.info("With Environment: {}", processBuilder.environment());

            if (processBuilder.command().isEmpty()) {
                final String errorMessage = "Empty command found for: " + taskInfo.getName();
                CommonTaskUtils.sendStatus(
                        driver,
                        Protos.TaskState.TASK_ERROR,
                        taskInfo.getTaskId(),
                        taskInfo.getSlaveId(),
                        taskInfo.getExecutor().getExecutorId(),
                        errorMessage);
                return;
            }

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
                if (exitValue == 0) {
                    driver.stop();
                } else {
                    driver.abort();
                }
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
                    e.getMessage());
            if (exitOnTermination) {
                driver.abort();
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

    /**
     * Sets up the config files on the executor side.
     *
     * @param taskInfo The {@link TaskInfo} to extract the config file data from
     * @throws IOException if the data in the taskInfo is not valid or the config can't be written to disk
     */
    private static void setupConfigFiles(TaskInfo taskInfo) throws IOException {
        // When populating config template, use taskinfo env + container env (taskinfo gets priority)
        final Map<String, String> environment = new TreeMap<>();
        environment.putAll(System.getenv());
        environment.putAll(CommonTaskUtils.fromEnvironmentToMap(taskInfo.getCommand().getEnvironment()));
        for (ConfigFileSpec configFileSpec : CommonTaskUtils.getConfigFiles(taskInfo)) {
            writeConfigFile(
                    configFileSpec.getRelativePath(),
                    configFileSpec.getTemplateContent(),
                    environment);
        }
    }

    /**
     * Injects the proper data into the given config template and writes the populated template to disk.
     *
     * @param relativePath    The path to write the file
     * @param templateContent The content of the config template
     * @param environment     The environment from which to extract the injection data
     * @throws IOException if the data can't be written to disk
     */
    private static void writeConfigFile(
            String relativePath,
            String templateContent,
            Map<String, String> environment) throws IOException {
        LOGGER.info("Writing config file: {} ({} bytes)", relativePath, templateContent.length());

        File configFile = new File(relativePath);
        if (!configFile.exists()) {
            try {
                configFile.createNewFile();
            } catch (IOException e) {
                throw new IOException(String.format("Can't create config file %s: %s", relativePath, e));
            }
        }

        Writer writer = null;
        try {
            writer = new BufferedWriter(new OutputStreamWriter(
                    new FileOutputStream(relativePath), StandardCharsets.UTF_8));
            MustacheFactory mf = new DefaultMustacheFactory();
            Mustache mustache = mf.compile(new StringReader(templateContent), "configTemplate");
            mustache.execute(writer, environment);
            writer.close();
        } catch (IOException e) {
            if (writer != null) {
                writer.close();
            }
            throw new IOException(String.format("Can't write to file %s: %s", relativePath, e));
        }
    }
}
