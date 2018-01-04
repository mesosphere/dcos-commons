package com.mesosphere.sdk.scheduler.decommission;

import com.mesosphere.sdk.scheduler.TaskKiller;
import com.mesosphere.sdk.scheduler.plan.PodInstanceRequirement;
import com.mesosphere.sdk.scheduler.plan.Status;
import com.mesosphere.sdk.scheduler.uninstall.UninstallStep;
import com.mesosphere.sdk.state.StateStore;

import org.apache.mesos.Protos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;

/**
 * Step which marks a task as being decommissioned and kills it.
 */
public class TriggerDecommissionStep extends UninstallStep {
    private static final Logger LOGGER = LoggerFactory.getLogger(DecommissionPlanFactory.class);

    private final StateStore stateStore;
    private final TaskKiller taskKiller;
    private final Protos.TaskInfo taskInfo;

    public TriggerDecommissionStep(StateStore stateStore, TaskKiller taskKiller, Protos.TaskInfo taskInfo) {
        super("kill-" + taskInfo.getName(), Status.PENDING);
        this.stateStore = stateStore;
        this.taskKiller = taskKiller;
        this.taskInfo = taskInfo;
    }

    @Override
    public Optional<PodInstanceRequirement> start() {
        LOGGER.info("Marking task for decommissioning: {}", taskInfo.getName());
        setStatus(Status.IN_PROGRESS);
        stateStore.storeGoalOverrideStatus(taskInfo.getName(), DecommissionPlanFactory.DECOMMISSIONING_STATUS);
        taskKiller.killTask(taskInfo.getTaskId());
        setStatus(Status.COMPLETE);
        return getPodInstanceRequirement();
    }
}
