package com.mesosphere.sdk.executor;

import com.mesosphere.sdk.offer.TaskException;
import org.apache.mesos.Executor;
import org.apache.mesos.ExecutorDriver;
import org.apache.mesos.Protos;

import com.google.protobuf.TextFormat;
import com.mesosphere.sdk.offer.CommonTaskUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.*;

/**
 * An {@link Executor} implementation that supports execution of long-running tasks and supporting short-lived tasks.
 */
public class CustomExecutor implements Executor {
    private static final Logger LOGGER = LoggerFactory.getLogger(CustomExecutor.class);
    private static final int HEALTH_CHECK_THREAD_POOL_SIZE = 10;
    private static final ScheduledExecutorService scheduledExecutorService =
            Executors.newScheduledThreadPool(HEALTH_CHECK_THREAD_POOL_SIZE);

    private final Map<Protos.TaskID, LaunchedTask> launchedTasks = new HashMap<>();
    private final ExecutorService executorService;
    private final ExecutorTaskFactory executorTaskFactory;

    private volatile Protos.SlaveInfo slaveInfo;

    public CustomExecutor(
            final ExecutorService executorService,
            ExecutorTaskFactory executorTaskFactory) {
        this.executorService = executorService;
        this.executorTaskFactory = executorTaskFactory;
    }

    @Override
    public void registered(
            ExecutorDriver driver,
            Protos.ExecutorInfo executorInfo,
            Protos.FrameworkInfo frameworkInfo,
            Protos.SlaveInfo slaveInfo) {
        LOGGER.info("Registered executor: {}", TextFormat.shortDebugString(executorInfo));
        this.slaveInfo = slaveInfo;
    }

    @Override
    public void reregistered(ExecutorDriver driver, Protos.SlaveInfo slaveInfo) {
        LOGGER.info("Re-registered on slave: {}", slaveInfo.getId().getValue());
        this.slaveInfo = slaveInfo;
    }

    @Override
    public void disconnected(ExecutorDriver driver) {
        LOGGER.info("Executor disconnected");
    }

    @Override
    public void launchTask(final ExecutorDriver driver, final Protos.TaskInfo task) {
        LOGGER.info("Launching task: {}", TextFormat.shortDebugString(task));

        try {
            Protos.TaskInfo unpackedTaskInfo = CommonTaskUtils.unpackTaskInfo(task);
            LOGGER.info("Unpacked task: {}", TextFormat.shortDebugString(unpackedTaskInfo));
            LOGGER.info("Unpacked command: {}", TextFormat.shortDebugString(unpackedTaskInfo.getCommand()));
            final ExecutorTask taskToExecute = executorTaskFactory.createTask(unpackedTaskInfo, driver);

            Future<?> future = executorService.submit(taskToExecute);
            LaunchedTask launchedTask = new LaunchedTask(taskToExecute, future);
            launchedTasks.put(unpackedTaskInfo.getTaskId(), launchedTask);
            scheduleHealthCheck(driver, unpackedTaskInfo, launchedTask);
            scheduleReadinessCheck(driver, unpackedTaskInfo, launchedTask);
        } catch (Throwable t) {
            LOGGER.error(String.format("Error launching task: %s", TextFormat.shortDebugString(task)), t);

            CommonTaskUtils.sendStatus(
                    driver,
                    Protos.TaskState.TASK_FAILED,
                    task.getTaskId(),
                    task.getSlaveId(),
                    task.getExecutor().getExecutorId(),
                    String.format("Exception launching task %s",
                            t.getMessage()));
        }
    }

    private void scheduleHealthCheck(
            ExecutorDriver executorDriver,
            Protos.TaskInfo taskInfo,
            LaunchedTask launchedTask) {

        if (!taskInfo.hasHealthCheck()) {
            LOGGER.info("No health check for task: {}", taskInfo.getName());
            return;
        }

        scheduleCheck(executorDriver, taskInfo, taskInfo.getHealthCheck(), launchedTask);
    }

    private void scheduleReadinessCheck(
            ExecutorDriver executorDriver,
            Protos.TaskInfo taskInfo,
            LaunchedTask launchedTask) {

        Optional<Protos.HealthCheck> readinessCheckOptional = Optional.empty();
        try {
            readinessCheckOptional = CommonTaskUtils.getReadinessCheck(taskInfo);
        } catch (TaskException e) {
            LOGGER.error("Failed to extract readiness check.", e);
            return;
        }

        if (!readinessCheckOptional.isPresent()){
            LOGGER.info("No readiness check for task: {}", taskInfo.getName());
            return;
        }

        scheduleCheck(executorDriver, taskInfo, readinessCheckOptional.get(), launchedTask);
    }

    private void scheduleCheck(
            ExecutorDriver executorDriver,
            Protos.TaskInfo taskInfo,
            Protos.HealthCheck check,
            LaunchedTask launchedTask) {

        try {
            HealthCheckMonitor healthCheckMonitor =
                    new HealthCheckMonitor(
                            check,
                            HealthCheckHandler.create(
                                    executorDriver,
                                    taskInfo,
                                    check,
                                    scheduledExecutorService,
                                    new HealthCheckStats(taskInfo.getName())),
                            launchedTask);
            LOGGER.info("Submitting check monitor.");
            Future<Optional<HealthCheckStats>> futureOptionalHealthCheckStats =
                    executorService.submit(healthCheckMonitor);

            executorService.submit(new Runnable() {
                @Override
                public void run() {
                    try {
                        Optional<HealthCheckStats> optionalHealthCheckStats = futureOptionalHealthCheckStats.get();
                        if (optionalHealthCheckStats.isPresent()) {
                            LOGGER.error("Check exited with statistics: {}", optionalHealthCheckStats.get());
                        }
                    } catch (InterruptedException | ExecutionException e) {
                        LOGGER.error("Failed to get check stats with exception: ", e);
                    }
                }
            });
        } catch (HealthCheckHandler.HealthCheckValidationException ex) {
            LOGGER.error("Task did not generate a check with exception: ", ex);
        }

    }

    @Override
    public void killTask(ExecutorDriver driver, Protos.TaskID taskId) {
        // TODO(mohit): Implement SIGKILL shutdown. Currently only perform SIGTERM.
        try {
            if (!launchedTasks.containsKey(taskId)) {
                LOGGER.error("Unable to kill unknown TaskID: {}", taskId.getValue());
                return;
            }
            LOGGER.info("Stopping task as part of killTask: {}", taskId.getValue());
            final LaunchedTask launchedTask = launchedTasks.get(taskId);
            launchedTask.stop();
        } catch (Throwable t) {
            LOGGER.error(String.format("Error killing task %s", taskId.getValue()), t);
        }
    }

    @Override
    public void frameworkMessage(ExecutorDriver driver, byte[] data) {
        LOGGER.error("Received unsupported framework message (%d bytes).", data.length);
    }

    @Override
    public void shutdown(ExecutorDriver driver) {
        LOGGER.info("Shutting down now.");

        // Shutdown all tasks
        for (Map.Entry<Protos.TaskID, LaunchedTask> entry : launchedTasks.entrySet()) {
            final Protos.TaskID taskId = entry.getKey();
            try {
                LOGGER.info("Stopping task as part of executor shutdown: {}", taskId.getValue());
                killTask(driver, taskId);
            } catch (Throwable t) {
                LOGGER.error(String.format("Error stopping task %s", taskId.getValue()), t);
            }
        }
    }

    @Override
    public void error(ExecutorDriver driver, String message) {
        LOGGER.error("Received error: {}", message);
    }

    Optional<Protos.SlaveInfo> getSlaveInfo() {
        return Optional.ofNullable(slaveInfo);
    }
}
