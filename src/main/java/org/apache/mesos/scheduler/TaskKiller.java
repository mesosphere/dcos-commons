package org.apache.mesos.scheduler;

import org.apache.mesos.SchedulerDriver;

/**
 * This interface should be implemented to allow components to request the killing of Mesos Tasks.  This is a normal
 * part of restarting a Task, which is a normal part of updating the Configuration of a Task.  This is also useful for
 * allowing end-users to mitigate problems with Tasks when they manually determine that a Task should be restarted or
 * permanently replaced.
 */
public interface TaskKiller {
    /**
     * This method should accept Tasks which a component wishes to kill.  The kill may be destructive or it may be
     * killed with the intention of later being restarted.  This method does not synchronously kill the Task.  Mesos
     * will periodically provide a SchedulerDriver which may be used to process requested Task kills.
     * @param taskName The name of the Task to be killed.
     * @param destructive A flag indicating whether the Task should be killed permanently and destructively or
     *                    anticipates a future restart.
     */
    void killTask(String taskName, boolean destructive);


    /**
     * This method is called to provide a Scheduler driver so previously requested Task kills may be processed.
     * @param driver The SchedulerDriver provided by Mesos.
     */
    void process(SchedulerDriver driver);
}
