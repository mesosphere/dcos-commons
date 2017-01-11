package com.mesosphere.sdk.scheduler;

import org.apache.mesos.Protos.TaskID;
import org.apache.mesos.SchedulerDriver;

import com.mesosphere.sdk.scheduler.recovery.TaskFailureListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This class is a default implementation of the TaskKiller interface.
 */
public class DefaultTaskKiller implements TaskKiller {
    private final Logger logger = LoggerFactory.getLogger(getClass());
    private final TaskFailureListener taskFailureListener;
    private final SchedulerDriver driver;

    public DefaultTaskKiller(TaskFailureListener taskFailureListener, SchedulerDriver driver) {
        this.taskFailureListener = taskFailureListener;
        this.driver = driver;
    }

    @Override
    public void killTask(TaskID taskId, boolean destructive) {
        // In order to update a podinstance its normal to kill all tasks in a pod.
        // Sometimes a task hasn't been launched ever but it has been recorded for
        // resource reservation footprint reasons, and therefore doesn't have a TaskID yet.
        if (taskId.getValue().isEmpty()) {
            logger.warn("Attempted to kill empty TaskID.");
            return;
        }

        logger.info("Scheduling task {} to be killed {}",
                taskId.getValue(), destructive ? "destructively" : "non-destructively");
        if (destructive) {
            taskFailureListener.taskFailed(taskId);
        }
        driver.killTask(taskId);
    }
}
