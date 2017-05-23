package com.mesosphere.sdk.scheduler;

import org.apache.mesos.Protos;
import org.apache.mesos.Scheduler;
import org.apache.mesos.SchedulerDriver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Simple writer to manipulate the TaskStatus of a Mesos task (as far as our Scheduler
 * is concerned).
 */
public class TaskStatusWriter {
    private final Logger LOGGER = LoggerFactory.getLogger(this.getClass());
    private final Scheduler scheduler;
    private final SchedulerDriver schedulerDriver;

    public TaskStatusWriter(Scheduler scheduler,
                            SchedulerDriver schedulerDriver) {
        this.scheduler = scheduler;
        this.schedulerDriver = schedulerDriver;
    }

    /**
     * Constructs a TaskStatus from the supplied parameters and sends
     * it to the Scheduler by calling statusUpdate()
     * @param taskID
     * @param taskState
     * @param message
     * @throws Exception
     */
    public void writeTaskStatus(Protos.TaskID taskID,
                                Protos.TaskState taskState,
                                String message)  throws Exception {
        writeTaskStatus(Protos.TaskStatus.newBuilder()
                .setTaskId(taskID)
                .setState(taskState)
                .setMessage(message)
                .build());
    }

    /**
     * Sends the specified TaskStatus to the Scheduler by calling statusUpdate()
     * @param status
     * @throws Exception
     */
    public void writeTaskStatus(Protos.TaskStatus status) throws Exception {
        LOGGER.info("Writing new status for taskId={} state={} message='{}'",
                status.getTaskId().getValue(),
                status.getState().toString(),
                status.getMessage());
        scheduler.statusUpdate(schedulerDriver, status);
    }

}
