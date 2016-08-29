package org.apache.mesos.scheduler;

import org.apache.mesos.Protos;
import org.apache.mesos.SchedulerDriver;
import org.apache.mesos.scheduler.recovery.TaskFailureListener;
import org.apache.mesos.state.StateStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Created by gabriel on 8/20/16.
 */
public class DefaultTaskKiller implements TaskKiller {
    private final Logger logger = LoggerFactory.getLogger(getClass());
    private final StateStore stateStore;
    private final TaskFailureListener taskFailureListener;

    private final Object restartLock = new Object();
    private List<Protos.TaskInfo> tasksToRestart = new ArrayList<>();
    private final Object rescheduleLock = new Object();
    private List<Protos.TaskInfo> tasksToReschedule = new ArrayList<>();

    public DefaultTaskKiller(StateStore stateStore, TaskFailureListener taskFailureListener) {
        this.stateStore = stateStore;
        this.taskFailureListener = taskFailureListener;
    }

    @Override
    public void killTask(String taskName, boolean destructive) {
        Optional<Protos.TaskInfo> taskInfoOptional = stateStore.fetchTask(taskName);
        if (!taskInfoOptional.isPresent()) {
            logger.warn("Attempted to kill unknown task: " + taskName);
            return;
        }

        Protos.TaskInfo taskInfo = taskInfoOptional.get();
        if (!stateStore.fetchStatus(taskName).getState().equals(Protos.TaskState.TASK_RUNNING)) {
            logger.warn("Task is not running, no need to kill: " + taskInfo);
            return;
        }

        if (destructive) {
            synchronized (rescheduleLock) {
                logger.info("Killing task destructively: " + taskInfo);
                tasksToReschedule.add(taskInfo);
            }
        } else {
            synchronized (restartLock) {
                logger.info("Killing task: " + taskInfo);
                tasksToRestart.add(taskInfo);
            }
        }
    }

    @Override
    public void process(SchedulerDriver driver) {
        processTasksToRestart(driver);
        processTasksToReschedule(driver);
    }

    private void processTasksToRestart(SchedulerDriver driver) {
        synchronized (restartLock) {
            for (Protos.TaskInfo taskInfo : tasksToRestart) {
                if (taskInfo != null) {
                    logger.info("Restarting task: " + taskInfo.getTaskId().getValue());
                    driver.killTask(taskInfo.getTaskId());
                } else {
                    logger.warn("Asked to restart null task.");
                }
            }

            tasksToRestart = new ArrayList<>();
        }
    }

    private void processTasksToReschedule(SchedulerDriver driver) {
        synchronized (rescheduleLock) {
            for (Protos.TaskInfo taskInfo : tasksToReschedule) {
                if (taskInfo != null) {
                    logger.info("Rescheduling task: " + taskInfo.getTaskId().getValue());
                    taskFailureListener.taskFailed(taskInfo.getTaskId());
                    driver.killTask(taskInfo.getTaskId());
                } else {
                    logger.warn("Asked to reschedule null task.");
                }
            }

            tasksToReschedule = new ArrayList<>();
        }
    }
}
