package com.mesosphere.sdk.scheduler.uninstall;

import com.mesosphere.sdk.framework.TaskKiller;
import com.mesosphere.sdk.scheduler.plan.PodInstanceRequirement;
import com.mesosphere.sdk.scheduler.plan.Status;
import org.apache.mesos.Protos;

import java.util.Optional;

/**
 * Step which issues a kill command for a given task.
 */
public class TaskKillStep extends UninstallStep {

    private final Protos.TaskID taskID;

    public TaskKillStep(Protos.TaskID taskID, Optional<String> namespace) {
        super("kill-task-" + taskID.getValue(), namespace);
        this.taskID = taskID;
    }

    @Override
    public Optional<PodInstanceRequirement> start() {
        setStatus(Status.IN_PROGRESS);
        TaskKiller.killTask(taskID);
        setStatus(Status.COMPLETE);

        return getPodInstanceRequirement();
    }
}
