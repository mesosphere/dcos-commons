package com.mesosphere.sdk.scheduler;

import com.google.common.annotations.VisibleForTesting;
import org.apache.mesos.Protos;
import org.apache.mesos.Protos.TaskID;
import org.apache.mesos.SchedulerDriver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.HashSet;
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

    private static final Duration killInterval = Duration.ofSeconds(5);
    private static final Set<TaskID> tasksToKill = new HashSet<>();
    private static final Object lock = new Object();

    private static ScheduledExecutorService executor;
    private static TaskKiller taskKiller = new TaskKiller();
    private static SchedulerDriver driver;

    private TaskKiller() {
        startScheduling();
    }

    @VisibleForTesting
    static void shutdownScheduling() throws InterruptedException {
        executor.shutdownNow();
        executor.awaitTermination(killInterval.toMillis(), TimeUnit.MILLISECONDS);
    }

    @VisibleForTesting
    static void startScheduling() {
        executor = Executors.newSingleThreadScheduledExecutor();
        executor.scheduleAtFixedRate(
                new Runnable() {
                    @Override
                    public void run() {
                        killAllTasks();
                    }
                },
                killInterval.toMillis(),
                killInterval.toMillis(),
                TimeUnit.MILLISECONDS);
    }

    public static void setDriver(SchedulerDriver schedulerDriver) {
        driver = schedulerDriver;
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

        synchronized (lock) {
            LOGGER.info("Enqueueing kill of task: {}", taskId.getValue());
            tasksToKill.add(taskId);
        }

        killTaskInternal(taskId);
    }

    public static void update(Protos.TaskStatus taskStatus) {
        if (isDead(taskStatus)) {
            synchronized (lock) {
                if (tasksToKill.remove(taskStatus.getTaskId())) {
                    LOGGER.info("Completed killing: {}, remaining tasks to kill: {}",
                            taskStatus.getTaskId().getValue(),
                            tasksToKill.stream().map(t -> t.getValue()).collect(Collectors.toList()));
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
        synchronized (lock) {
            copy = new HashSet<>(tasksToKill);
        }

        for (TaskID taskId : copy) {
            killTaskInternal(taskId);
        }
    }

    private static void killTaskInternal(TaskID taskId) {
        if (driver != null) {
            LOGGER.info("Killing task: {}", taskId.getValue());
            driver.killTask(taskId);
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
