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
 * This class is a default implementation of the TaskKiller interface.
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
        if (taskInfo == null) {
            logger.warn("Encountered unexpected 'null' TaskInfo. NOT scheduling kill operation.");
            return;
        }

        Optional<Protos.TaskStatus> taskState = stateStore.fetchStatus(taskName);
        if (!taskState.isPresent()) {
            logger.warn("Attempted to kill unknown task: " + taskName);
            return;
        }

        if (!taskState.get().getState().equals(Protos.TaskState.TASK_RUNNING)) {
            logger.warn(String.format("No need to kill: '%s', task state: '%s'", taskName, taskState));
            return;
        }

        if (destructive) {
            synchronized (rescheduleLock) {
                logger.info("Scheduling task to be killed destructively: " + taskInfo);
                tasksToReschedule.add(taskInfo);
            }
        } else {
            synchronized (restartLock) {
                logger.info("Scheduling task to be killed with intention to restart: " + taskInfo);
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
                logger.info("Restarting task: " + taskInfo.getTaskId().getValue());
                driver.killTask(taskInfo.getTaskId());
            }

            tasksToRestart = new ArrayList<>();
        }
    }

    private void processTasksToReschedule(SchedulerDriver driver) {
        synchronized (rescheduleLock) {
            for (Protos.TaskInfo taskInfo : tasksToReschedule) {
                logger.info("Rescheduling task: " + taskInfo.getTaskId().getValue());
                taskFailureListener.taskFailed(taskInfo.getTaskId());
                driver.killTask(taskInfo.getTaskId());
            }

            tasksToReschedule = new ArrayList<>();
        }
    }
}
