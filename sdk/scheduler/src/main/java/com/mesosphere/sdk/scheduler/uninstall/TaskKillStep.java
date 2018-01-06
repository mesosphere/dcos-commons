package com.mesosphere.sdk.scheduler.uninstall;

import com.mesosphere.sdk.scheduler.TaskKiller;
import com.mesosphere.sdk.scheduler.plan.PodInstanceRequirement;
import com.mesosphere.sdk.scheduler.plan.Status;
import org.apache.mesos.Protos;

import java.util.Optional;

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
    public Optional<PodInstanceRequirement> start() {
        setStatus(Status.IN_PROGRESS);
        taskKiller.killTask(taskID);
        setStatus(Status.COMPLETE);

        return getPodInstanceRequirement();
    }
}
