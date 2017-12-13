package com.mesosphere.sdk.executor;

import java.util.HashMap;
import java.util.Map;

import org.apache.mesos.Protos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.annotations.VisibleForTesting;

/**
 * Storage of tasks that are being run by this executor.
 */
public class LaunchedTaskStore {
    private static final Logger LOGGER = LoggerFactory.getLogger(LaunchedTaskStore.class);
    private static final int RUNNING_TASK_POLL_INTERVAL_MS = 5000;

    private final Runnable monitor;
    private final int exitPollIntervalMs;

    private final Object launchedTasksLock = new Object();
    /**
     * A mapping of all tasks which have ever been launched by this Executor.
     * Tasks which have since exited will have {@link LaunchedTask#isDone()} == {@code true}.
     */
    private final Map<Protos.TaskID, LaunchedTask> launchedTasks = new HashMap<>();

    public LaunchedTaskStore(Runnable exitCallback) {
        this(exitCallback, RUNNING_TASK_POLL_INTERVAL_MS);
    }

    @VisibleForTesting
    LaunchedTaskStore(Runnable exitCallback, int exitPollIntervalMs) {
        this.monitor = new TasksRunningMonitor(exitCallback);
        this.exitPollIntervalMs = exitPollIntervalMs;
    }

    /**
     * Returns the monitor process to be run once the executor has registered.
     */
    public Runnable getMonitor() {
        return monitor;
    }

    /**
     * Adds a launched task.
     */
    public void put(Protos.TaskID taskId, LaunchedTask launchedTask) {
        synchronized (launchedTasksLock) {
            launchedTasks.put(taskId, launchedTask);
        }
    }

    /**
     * Kills a previously added task, or does nothing if the specified task is already killed or could not be found.
     */
    public void kill(Protos.TaskID taskId) {
        final LaunchedTask launchedTask;
        synchronized (launchedTasks) {
            launchedTask = launchedTasks.get(taskId);
        }
        if (launchedTask == null) {
            LOGGER.error("Unable to kill unknown TaskID: {}", taskId.getValue());
            return;
        }
        LOGGER.info("Stopping task as part of killTask: {}", taskId.getValue());
        try {
            launchedTask.stop();
        } catch (Throwable t) {
            LOGGER.error(String.format("Error killing task %s", taskId.getValue()), t);
        }
    }

    /**
     * Kills any previously added tasks that are still running.
     */
    public void killAll() {
        synchronized (launchedTasks) {
            for (Map.Entry<Protos.TaskID, LaunchedTask> entry : launchedTasks.entrySet()) {
                final Protos.TaskID taskId = entry.getKey();
                LOGGER.info("Stopping task as part of shutdown: {}", taskId.getValue());
                try {
                    entry.getValue().stop();
                } catch (Throwable t) {
                    LOGGER.error(String.format("Error stopping task %s", taskId.getValue()), t);
                }
            }
        }
    }

    /**
     * Periodically checks that launched tasks are still running.
     * If all tasks have exited (as indicated by {@link LaunchedTask#isDone()}, the executor process is destroyed.
     * This ensures that we aren't leaving zombie executors behind in the cluster.
     */
    private class TasksRunningMonitor implements Runnable {
        private final Runnable exitCallback;

        public TasksRunningMonitor(Runnable exitCallback) {
            this.exitCallback = exitCallback;
        }

        @Override
        public void run() {
            while (true) {
                exitIfAllDone();
                try {
                    Thread.sleep(exitPollIntervalMs);
                } catch (InterruptedException e) {
                    // ignore
                }
            }
        }

        /**
         * Calls {@link LaunchedTaskStore#executorExit()} if no tasks are still running.
         */
        private void exitIfAllDone() {
            synchronized (launchedTasksLock) {
                if (launchedTasks.isEmpty()) {
                    return; // no tasks have been launched yet.
                }
                long doneTasks = launchedTasks.values().stream().filter(lt -> lt.isDone()).count();
                if (doneTasks == launchedTasks.size()) {
                    LOGGER.info("Shutting down executor: All {} launched tasks have exited, nothing left to do.",
                            launchedTasks.size());
                    exitCallback.run();
                } else {
                    LOGGER.debug("{} of {} launched tasks have exited", doneTasks, launchedTasks.size());
                }
            }
        }
    }

    /**
     * Kills the executor process. Broken out into a separate object to allow mocking in tests.
     */
    public static class ExitCallback implements Runnable {
        @Override
        public void run() {
            exitProcess();
        }
    }

    /**
     * FindBugs refuses to acknowledge @SuppressWarnings in the above {@link ExitCallback#run()} function, but this
     * works, so...
     */
    @SuppressWarnings("DM_EXIT")
    private static void exitProcess() {
        System.exit(0);
    }
}
