package com.mesosphere.sdk.scheduler.uninstall;

import com.mesosphere.sdk.scheduler.TaskKiller;
import com.mesosphere.sdk.scheduler.plan.Status;
import org.apache.mesos.Protos;


/**
 * Step which issues a kill command for a given task.
 */
public class TaskKillStep extends UninstallStep {

    private final TaskKiller taskKiller;
    private final Protos.TaskID taskID;

    public TaskKillStep(Protos.TaskID taskID, TaskKiller taskKiller) {
        super("kill-task-" + taskID.getValue(), Status.PENDING);
        this.taskKiller = taskKiller;
        this.taskID = taskID;
    }

    @Override
    public void start() {
        setStatus(Status.IN_PROGRESS);
        taskKiller.killTask(taskID);
        setStatus(Status.COMPLETE);
    }
}
