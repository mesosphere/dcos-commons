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

    private final Object killLock = new Object();
    private List<Protos.TaskInfo> tasksToKill = new ArrayList<>();

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

        if (destructive) {
            taskFailureListener.taskFailed(taskInfo.getTaskId());
        }

        logger.info(String.format(
                "Scheduling task to be killed %s: %s",
                destructive ? "destructively" : "non-destructively",
                taskInfo));

        synchronized (killLock) {
            tasksToKill.add(taskInfo);
        }
    }

    @Override
    public void process(SchedulerDriver driver) {
        for (Protos.TaskInfo taskInfo : tasksToKill) {
            logger.info("Killing task: " + taskInfo.getTaskId().getValue());
            driver.killTask(taskInfo.getTaskId());
        }

        tasksToKill = new ArrayList<>();
    }
}
