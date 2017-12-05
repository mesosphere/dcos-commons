package com.mesosphere.sdk.scheduler.decommission;

import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.mesosphere.sdk.scheduler.plan.PodInstanceRequirement;
import com.mesosphere.sdk.scheduler.plan.Status;
import com.mesosphere.sdk.scheduler.uninstall.UninstallStep;
import com.mesosphere.sdk.state.StateStore;

/**
 * A step which erases a specified task's state from zookeeper.
 */
public class EraseTaskStateStep extends UninstallStep {
    private static final Logger LOGGER = LoggerFactory.getLogger(EraseTaskStateStep.class);

    private final StateStore stateStore;
    private final String taskName;

    public EraseTaskStateStep(StateStore stateStore, String taskName) {
        super("erase-" + taskName, Status.PENDING);
        this.stateStore = stateStore;
        this.taskName = taskName;
    }

    @Override
    public Optional<PodInstanceRequirement> start() {
        LOGGER.info("Deleting remnants of decommissioned task from state store: {}", taskName);
        stateStore.clearTask(taskName);
        setStatus(Status.COMPLETE);
        return Optional.empty();
    }
}
