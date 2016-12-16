package com.mesosphere.sdk.scheduler;

import org.apache.mesos.Protos.TaskID;

/**
 * This interface should be implemented to allow components to request the killing of Mesos Tasks.  This is a normal
 * part of restarting a Task, which is a normal part of updating the Configuration of a Task.  This is also useful for
 * allowing end-users to mitigate problems with Tasks when they manually determine that a Task should be restarted or
 * permanently replaced.
 */
public interface TaskKiller {

    /**
     * This method should accept Tasks which the caller wishes to kill.  The kill may be destructive (for restarting at
     * a new location) or it may be killed with the intention of later being restarted (for in-place restart).  This
     * method does not synchronously kill the Task.  Mesos will periodically provide a SchedulerDriver which may be used
     * to process requested Task kills.
     *
     * @param taskId ID of the task to be restarted
     * @param destructive A flag indicating whether the Task should be killed permanently and destructively or
     *     anticipates a future restart
     */
    void killTask(TaskID taskId, boolean destructive);
}
