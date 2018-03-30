package com.mesosphere.sdk.scheduler.uninstall;

import com.mesosphere.sdk.framework.Driver;
import com.mesosphere.sdk.scheduler.plan.PodInstanceRequirement;
import com.mesosphere.sdk.scheduler.plan.Status;
import com.mesosphere.sdk.state.FrameworkStore;
import com.mesosphere.sdk.state.StateStore;
import com.mesosphere.sdk.storage.PersisterException;
import com.mesosphere.sdk.storage.PersisterUtils;

import org.apache.mesos.SchedulerDriver;

import java.util.Optional;

/**
 * Step which implements the deregistering of a framework.
 */
public class DeregisterStep extends UninstallStep {

    private final FrameworkStore frameworkStore;
    private final StateStore stateStore;

    /**
     * Creates a new instance with initial {@code status}. The {@link SchedulerDriver} must be
     * set separately.
     */
    DeregisterStep(FrameworkStore frameworkStore, StateStore stateStore) {
        super("deregister", Status.PENDING);
        this.frameworkStore = frameworkStore;
        this.stateStore = stateStore;
    }

    @Override
    public Optional<PodInstanceRequirement> start() {
        logger.info("Stopping SchedulerDriver...");
        // Remove the framework ID before unregistering
        frameworkStore.clearFrameworkId();
        // Unregisters the framework in addition to stopping the SchedulerDriver thread:
        // Calling with failover == false causes Mesos to teardown the framework.
        // This call will cause DefaultService's schedulerDriver.run() call to return DRIVER_STOPPED.
        Optional<SchedulerDriver> driver = Driver.getDriver();
        if (driver.isPresent()) {
            driver.get().stop(false);
            logger.info("Deleting service root path for framework...");
            try {
                PersisterUtils.clearAllData(stateStore.getPersister());
            } catch (PersisterException e) {
                // Best effort.
                logger.error("Failed to clear all data", e);
            }
            logger.info("### UNINSTALL IS COMPLETE! ###");
            logger.info("Scheduler should be cleaned up shortly...");
            setStatus(Status.COMPLETE);
        } else {
            logger.error("No driver is present for deregistering the framework.");

            // The state should already be PENDING, but we do this out of an abundance of caution.
            setStatus(Status.PENDING);
        }

        return Optional.empty();
    }
}
