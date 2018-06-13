package com.mesosphere.sdk.scheduler.decommission;

import java.util.Optional;

import com.mesosphere.sdk.scheduler.plan.PodInstanceRequirement;
import com.mesosphere.sdk.scheduler.plan.Status;
import com.mesosphere.sdk.scheduler.uninstall.UninstallStep;
import com.mesosphere.sdk.state.StateStore;

/**
 * A step which erases a specified task's state from zookeeper.
 */
public class EraseTaskStateStep extends UninstallStep {

    private final StateStore stateStore;
    private final String taskName;

    public EraseTaskStateStep(StateStore stateStore, String taskName, Optional<String> namespace) {
        super("erase-" + taskName, namespace);
        this.stateStore = stateStore;
        this.taskName = taskName;
    }

    @Override
    public Optional<PodInstanceRequirement> start() {
        logger.info("Deleting remnants of decommissioned task from state store: {}", taskName);
        stateStore.clearTask(taskName);
        setStatus(Status.COMPLETE);
        return Optional.empty();
    }
}
