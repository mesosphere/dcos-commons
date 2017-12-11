package com.mesosphere.sdk.executor;

import com.mesosphere.sdk.offer.TaskException;
import com.mesosphere.sdk.offer.taskdata.ExecutorTaskLabelReader;
import com.mesosphere.sdk.offer.taskdata.TaskPackingUtils;

import org.apache.mesos.Executor;
import org.apache.mesos.ExecutorDriver;
import org.apache.mesos.Protos;

import com.google.protobuf.TextFormat;
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
    private static final ScheduledExecutorService HEALTH_CHECK_THREAD_POOL =
            Executors.newScheduledThreadPool(HEALTH_CHECK_THREAD_POOL_SIZE);

    private static final int RUNNING_TASK_POLL_INTERVAL_MS = 5000;

    private final Object launchedTasksLock = new Object();
    /**
     * A mapping of all tasks which have ever been launched by this Executor.
     * Tasks which have since exited will have {@link LaunchedTask#isDone()} == {@code true}.
     */
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

        executorService.submit(new TasksRunningMonitor());
    }

    /**
     * Periodically checks that launched tasks are still running.
     * If all tasks have exited (as indicated by {@link LaunchedTask#isDone()}, the executor process is destroyed.
     */
    private class TasksRunningMonitor implements Runnable {
        @Override
        public void run() {
            while (true) {
                synchronized (launchedTasksLock) {
                    if (shouldExit()) {
                        LOGGER.info("Executor exiting: All {} launched tasks have exited, nothing left to do.",
                                launchedTasks.size());
                        System.exit(0);
                    }
                }
                try {
                    Thread.sleep(RUNNING_TASK_POLL_INTERVAL_MS);
                } catch (InterruptedException e) {
                    // ignore
                }
            }
        }

        private boolean shouldExit() {
            if (launchedTasks.isEmpty()) {
                return false; // no tasks have been launched yet.
            }
            long doneTasks = launchedTasks.values().stream().filter(lt -> lt.isDone()).count();
            return doneTasks == launchedTasks.size();
        }
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
            Protos.TaskInfo unpackedTaskInfo = TaskPackingUtils.unpack(task);
            LOGGER.info("Unpacked task: {}", TextFormat.shortDebugString(unpackedTaskInfo));
            LOGGER.info("Unpacked command: {}", TextFormat.shortDebugString(unpackedTaskInfo.getCommand()));
            final ExecutorTask taskToExecute = executorTaskFactory.createTask(unpackedTaskInfo, driver);

            LaunchedTask launchedTask = new LaunchedTask(taskToExecute, executorService);
            synchronized (launchedTasksLock) {
                launchedTasks.put(unpackedTaskInfo.getTaskId(), launchedTask);
            }
            scheduleHealthCheck(driver, unpackedTaskInfo, launchedTask);
            scheduleReadinessCheck(driver, unpackedTaskInfo, launchedTask);
        } catch (Throwable t) {
            LOGGER.error(String.format("Error launching task: %s", TextFormat.shortDebugString(task)), t);

            TaskStatusUtils.sendStatus(
                    driver,
                    Protos.TaskState.TASK_FAILED,
                    task.getTaskId(),
                    task.getSlaveId(),
                    task.getExecutor().getExecutorId(),
                    String.format(
                            "Exception launching task %s",
                            t.getMessage()),
                    false);
        }
    }

    private void scheduleHealthCheck(
            ExecutorDriver executorDriver,
            Protos.TaskInfo taskInfo,
            LaunchedTask launchedTask) {

        if (!taskInfo.hasHealthCheck()) {
            return;
        }

        scheduleCheck(executorDriver, taskInfo, taskInfo.getHealthCheck(), launchedTask, "Health");
    }

    private void scheduleReadinessCheck(
            ExecutorDriver executorDriver,
            Protos.TaskInfo taskInfo,
            LaunchedTask launchedTask) {

        Optional<Protos.HealthCheck> readinessCheckOptional = Optional.empty();
        try {
            readinessCheckOptional = new ExecutorTaskLabelReader(taskInfo).getReadinessCheck();
        } catch (TaskException e) {
            LOGGER.error(String.format(
                    "Failed to extract readiness check from task: %s", taskInfo.getTaskId().getValue()), e);
            return;
        }

        if (!readinessCheckOptional.isPresent()) {
            return;
        }

        scheduleCheck(executorDriver, taskInfo, readinessCheckOptional.get(), launchedTask, "Readiness");
    }

    private void scheduleCheck(
            ExecutorDriver executorDriver,
            Protos.TaskInfo taskInfo,
            Protos.HealthCheck check,
            LaunchedTask launchedTask,
            String checkType) {

        try {
            CheckMonitor healthCheckMonitor =
                    new CheckMonitor(
                            check,
                            CheckHandler.create(
                                    executorDriver,
                                    taskInfo,
                                    launchedTask,
                                    check,
                                    HEALTH_CHECK_THREAD_POOL,
                                    new CheckStats(taskInfo.getName()),
                                    checkType),
                            launchedTask,
                            checkType);
            LOGGER.info("Submitting {} check monitor.", checkType);
            Future<Optional<CheckStats>> futureOptionalHealthCheckStats =
                    executorService.submit(healthCheckMonitor);

            executorService.submit(new Runnable() {
                @Override
                public void run() {
                    try {
                        Optional<CheckStats> optionalHealthCheckStats = futureOptionalHealthCheckStats.get();
                        if (optionalHealthCheckStats.isPresent()) {
                            LOGGER.info("{} check exited with statistics: {}",
                                    checkType, optionalHealthCheckStats.get());
                        }
                    } catch (InterruptedException | ExecutionException e) {
                        LOGGER.error(String.format("Failed to get %s check stats with exception: ", checkType), e);
                    }
                }
            });
        } catch (CheckHandler.CheckValidationException ex) {
            LOGGER.error(String.format("Task did not generate a %s check with exception: ", checkType), ex);
        }

    }

    @Override
    public void killTask(ExecutorDriver driver, Protos.TaskID taskId) {
        try {
            final LaunchedTask launchedTask;
            synchronized (launchedTasks) {
                launchedTask = launchedTasks.get(taskId);
            }
            if (launchedTask == null) {
                LOGGER.error("Unable to kill unknown TaskID: {}", taskId.getValue());
                return;
            }
            LOGGER.info("Stopping task as part of killTask: {}", taskId.getValue());
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
        synchronized (launchedTasks) {
            for (Map.Entry<Protos.TaskID, LaunchedTask> entry : launchedTasks.entrySet()) {
                final Protos.TaskID taskId = entry.getKey();
                try {
                    LOGGER.info("Stopping task as part of executor shutdown: {}", taskId.getValue());
                    entry.getValue().stop();
                } catch (Throwable t) {
                    LOGGER.error(String.format("Error stopping task %s", taskId.getValue()), t);
                }
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
