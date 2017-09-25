package com.mesosphere.sdk.scheduler;

import org.apache.mesos.Protos.TaskID;
import org.apache.mesos.SchedulerDriver;

import com.mesosphere.sdk.scheduler.recovery.RecoveryType;

/**
 * This interface should be implemented to allow components to request the killing of Mesos Tasks.  This is a normal
 * part of restarting a Task, which is a normal part of updating the Configuration of a Task.  This is also useful for
 * allowing end-users to mitigate problems with Tasks when they manually determine that a Task should be restarted or
 * permanently replaced.
 */
public interface TaskKiller {

    /**
     * Configures this instance with the {@link SchedulerDriver} to be invoked when killing tasks.
     * This must be called at least once before {@link #killTask(TaskID, RecoveryType)} is invoked.
     *
     * @return this
     */
    TaskKiller setSchedulerDriver(SchedulerDriver driver);

    /**
     * This method should accept Tasks which the caller wishes to kill.  The kill may be destructive (for restarting at
     * a new location) or it may be killed with the intention of later being restarted (for in-place restart).  This
     * method does not synchronously kill the Task.  Mesos will periodically provide a SchedulerDriver which may be used
     * to process requested Task kills.
     *
     * @param taskId ID of the task to be restarted
     * @param recoveryType A flag indicating the type of kill to perform, {@code TRANSIENT} or {@code PERMANENT}
     */
    void killTask(TaskID taskId, RecoveryType recoveryType);
}
