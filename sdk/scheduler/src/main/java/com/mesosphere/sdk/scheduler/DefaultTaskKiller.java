package com.mesosphere.sdk.scheduler;

import org.apache.mesos.Protos;
import org.apache.mesos.SchedulerDriver;
import com.mesosphere.sdk.scheduler.recovery.TaskFailureListener;
import com.mesosphere.sdk.state.StateStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;

/**
 * This class is a default implementation of the TaskKiller interface.
 */
public class DefaultTaskKiller implements TaskKiller {
    private final Logger logger = LoggerFactory.getLogger(getClass());
    private final StateStore stateStore;
    private final TaskFailureListener taskFailureListener;
    private final SchedulerDriver driver;

    public DefaultTaskKiller(StateStore stateStore, TaskFailureListener taskFailureListener, SchedulerDriver driver) {
        this.stateStore = stateStore;
        this.taskFailureListener = taskFailureListener;
        this.driver = driver;
    }

    @Override
    public boolean killTask(String taskName, boolean destructive) {
        Optional<Protos.TaskInfo> taskInfoOptional = stateStore.fetchTask(taskName);
        if (!taskInfoOptional.isPresent()) {
            logger.warn("Attempted to kill unknown task: " + taskName);
            return false;
        }

        Protos.TaskInfo taskInfo = taskInfoOptional.get();
        if (taskInfo == null) {
            logger.warn("Encountered unexpected 'null' TaskInfo. NOT scheduling kill operation.");
            return false;
        }

        Optional<Protos.TaskStatus> taskState = stateStore.fetchStatus(taskName);
        if (!taskState.isPresent()) {
            logger.warn("Attempted to kill unknown task: " + taskName);
            return false;
        }

        if (destructive) {
            taskFailureListener.taskFailed(taskInfo.getTaskId());
        }

        logger.info(String.format(
                "Scheduling task to be killed %s: %s",
                destructive ? "destructively" : "non-destructively",
                taskInfo));

        driver.killTask(taskInfo.getTaskId());
        return true;
    }
}
