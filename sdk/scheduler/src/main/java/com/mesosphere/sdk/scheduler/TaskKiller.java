package com.mesosphere.sdk.scheduler;

import com.google.common.annotations.VisibleForTesting;
import org.apache.mesos.Protos;
import org.apache.mesos.Protos.TaskID;
import org.apache.mesos.SchedulerDriver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * This class implements reliable task killing in conjunction with the {@link TaskCleaner}.  Mesos does not provide
 * reliable task killing.  This class repeatedly attempts to kill a task until Mesos declares it has been killed or that
 * Mesos doesn't know anything about this task.
 */
public final class TaskKiller {
    private static final Logger LOGGER = LoggerFactory.getLogger(TaskKiller.class);

    private static final Duration KILL_INTERVAL = Duration.ofSeconds(5);
    private static final Set<TaskID> TASKS_TO_KILL = new HashSet<>();
    private static final Object LOCK = new Object();

    /**
     * After the first task kill, this executor will be created and will periodically reissue kill invocations for any
     * tasks which haven't produced a dead or unknown status. We do this because the Mesos kill command is best-effort.
     */
    private static ScheduledExecutorService executor;

    /**
     * Whether the above executor should be running. Only disabled for tests.
     */
    private static boolean executorEnabled = true;

    private TaskKiller() {
        // Do not instantiate
    }

    /**
     * Resets the {@link TaskKiller}'s internal state for tests.
     *
     * @param executorEnabled whether the background kill executor should be enabled, should only be disabled in tests
     */
    @VisibleForTesting
    public static void reset(boolean executorEnabled) throws InterruptedException {
        synchronized (LOCK) {
            if (executor != null) {
                executor.shutdownNow();
                executor.awaitTermination(KILL_INTERVAL.toMillis(), TimeUnit.MILLISECONDS);
            }
            executor = null;

            TASKS_TO_KILL.clear();

            TaskKiller.executorEnabled = executorEnabled;
        }
    }

    /**
     * Calling this method will cause the referenced TaskID to be reliably killed.  An attempt to kill the task will be
     * immediately made.  Continued periodic attempts will be made to kill the task until Mesos indicates that it has
     * killed the Task or doesn't recongize the task.
     *
     * This is still not a guarantee that a task has been killed.  Mesos may not know about a particular TaskID at any
     * given time.  See the {@link TaskCleaner} for the rest of the task killing system.
     * @param taskId the TaskID of the Task to be killed.
     */
    public static void killTask(TaskID taskId) {
        // In order to update a podinstance its normal to kill all tasks in a pod.
        // Sometimes a task hasn't been launched ever but it has been recorded for
        // resource reservation footprint reasons, and therefore doesn't have a TaskID yet.
        if (taskId.getValue().isEmpty()) {
            LOGGER.warn("Attempted to kill empty TaskID.");
            return;
        }

        synchronized (LOCK) {
            TASKS_TO_KILL.add(taskId);
            LOGGER.info("Enqueued kill of task: {}, {} tasks to kill: {}",
                    taskId.getValue(),
                    TASKS_TO_KILL.size(),
                    TASKS_TO_KILL.stream().map(t -> t.getValue()).collect(Collectors.toList()));

            // Initialize the executor if enabled and not already running.
            if (executor == null && executorEnabled) {
                LOGGER.info("Initializing scheduled executor with an interval of {}s", KILL_INTERVAL.getSeconds());
                executor = Executors.newSingleThreadScheduledExecutor();
                executor.scheduleAtFixedRate(
                        new Runnable() {
                            @Override
                            public void run() {
                                killAllTasks();
                            }
                        },
                        KILL_INTERVAL.toMillis(),
                        KILL_INTERVAL.toMillis(),
                        TimeUnit.MILLISECONDS);
            }
        }

        // Finally, try invoking the task kill (if driver is set).
        killTaskInternal(taskId);
    }

    public static void update(Protos.TaskStatus taskStatus) {
        if (isDead(taskStatus)) {
            synchronized (LOCK) {
                if (TASKS_TO_KILL.remove(taskStatus.getTaskId())) {
                    LOGGER.info("Completed killing: {}, {} remaining tasks to kill: {}",
                            taskStatus.getTaskId().getValue(),
                            TASKS_TO_KILL.size(),
                            TASKS_TO_KILL.stream().map(t -> t.getValue()).collect(Collectors.toList()));
                } else {
                    LOGGER.warn(
                            "Attempted to complete killing of unexpected task: {}",
                            taskStatus.getTaskId().getValue());
                }
            }
        }
    }

    @VisibleForTesting
    static void killAllTasks() {
        Set<TaskID> copy;
        synchronized (LOCK) {
            copy = new HashSet<>(TASKS_TO_KILL);
        }

        for (TaskID taskId : copy) {
            killTaskInternal(taskId);
        }
    }

    private static void killTaskInternal(TaskID taskId) {
        Optional<SchedulerDriver> driver = Driver.getDriver();

        if (driver.isPresent()) {
            LOGGER.info("Killing task: {}", taskId.getValue());
            driver.get().killTask(taskId);
        } else {
            LOGGER.warn("Can't kill '{}', driver not yet set.", taskId.getValue());
        }
    }

    private static boolean isDead(Protos.TaskStatus taskStatus) {
        switch (taskStatus.getState()) {
            case TASK_KILLING:
            case TASK_RUNNING:
            case TASK_STAGING:
            case TASK_STARTING:
                return false;
            default:
                return true;
        }
    }
}
