package org.apache.mesos.scheduler;

import org.apache.mesos.SchedulerDriver;

/**
 * Created by gabriel on 8/25/16.
 */
public interface TaskKiller {
    void killTask(String taskName, boolean destructive);
    void process(SchedulerDriver driver);
}
