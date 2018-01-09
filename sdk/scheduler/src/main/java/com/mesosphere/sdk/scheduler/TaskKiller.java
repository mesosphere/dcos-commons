package com.mesosphere.sdk.scheduler;

import org.apache.mesos.Protos.TaskID;
import org.apache.mesos.SchedulerDriver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This class is a default implementation of the TaskKiller interface.
 */
public class TaskKiller {
    private final Logger logger = LoggerFactory.getLogger(getClass());
    private final SchedulerDriver driver;

    public TaskKiller(SchedulerDriver driver) {
        this.driver = driver;
    }

    public void killTask(TaskID taskId) {
        // In order to update a podinstance its normal to kill all tasks in a pod.
        // Sometimes a task hasn't been launched ever but it has been recorded for
        // resource reservation footprint reasons, and therefore doesn't have a TaskID yet.
        if (taskId.getValue().isEmpty()) {
            logger.warn("Attempted to kill empty TaskID.");
            return;
        }

        logger.info("Killing task: {}", taskId.getValue());
        driver.killTask(taskId);
    }
}
