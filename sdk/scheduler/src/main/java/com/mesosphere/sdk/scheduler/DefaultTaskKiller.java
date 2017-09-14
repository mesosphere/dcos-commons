package com.mesosphere.sdk.scheduler;

import org.apache.mesos.Protos.TaskID;
import org.apache.mesos.SchedulerDriver;

import com.mesosphere.sdk.scheduler.recovery.RecoveryType;
import com.mesosphere.sdk.scheduler.recovery.TaskFailureListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This class is a default implementation of the TaskKiller interface.
 */
public class DefaultTaskKiller implements TaskKiller {
    private final Logger logger = LoggerFactory.getLogger(getClass());
    private final TaskFailureListener taskFailureListener;

    private SchedulerDriver driver;

    public DefaultTaskKiller(TaskFailureListener taskFailureListener) {
        this.taskFailureListener = taskFailureListener;
    }

    @Override
    public TaskKiller setSchedulerDriver(SchedulerDriver driver) {
        this.driver = driver;
        return this;
    }

    @Override
    public void killTask(TaskID taskId, RecoveryType recoveryType) {
        if (driver == null) {
            throw new IllegalStateException(String.format(
                    "killTask(%s) was called without first calling setSchedulerDriver()", taskId.getValue()));
        }

        // In order to update a podinstance its normal to kill all tasks in a pod.
        // Sometimes a task hasn't been launched ever but it has been recorded for
        // resource reservation footprint reasons, and therefore doesn't have a TaskID yet.
        if (taskId.getValue().isEmpty()) {
            logger.warn("Attempted to kill empty TaskID.");
            return;
        }

        logger.info("Scheduling task {} to be killed {}",
                taskId.getValue(), recoveryType == RecoveryType.PERMANENT ? "destructively" : "non-destructively");
        if (recoveryType == RecoveryType.PERMANENT) {
            taskFailureListener.taskFailed(taskId);
        }
        driver.killTask(taskId);
    }
}
