package com.mesosphere.sdk.scheduler.uninstall;

import com.google.protobuf.TextFormat;
import com.mesosphere.sdk.scheduler.TaskKiller;
import com.mesosphere.sdk.scheduler.plan.PodInstanceRequirement;
import com.mesosphere.sdk.scheduler.plan.Status;
import com.mesosphere.sdk.scheduler.recovery.RecoveryType;
import org.apache.mesos.Protos;

import java.util.Optional;

/**
 * Step which issues kill commands to all .
 */
public class TaskKillStep extends UninstallStep {

    private TaskKiller taskKiller;
    private Protos.TaskID taskID;

    public TaskKillStep(Status status, Protos.TaskID taskID) {
        super("kill-task-"+taskID.getValue(), status);
        this.taskID = taskID;
    }

    public void setTaskKiller(TaskKiller taskKiller) {
        this.taskKiller = taskKiller;
    }

    @Override
    public Optional<PodInstanceRequirement> start() {
        setStatus(Status.IN_PROGRESS);
        taskKiller.killTask(taskID, RecoveryType.TRANSIENT);
        setStatus(Status.COMPLETE);

        return getPodInstanceRequirement();
    }
}
