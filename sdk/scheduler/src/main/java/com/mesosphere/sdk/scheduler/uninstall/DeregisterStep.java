package com.mesosphere.sdk.scheduler.uninstall;

import com.mesosphere.sdk.offer.OfferRecommendation;
import com.mesosphere.sdk.scheduler.plan.AbstractStep;
import com.mesosphere.sdk.scheduler.plan.PodInstanceRequirement;
import com.mesosphere.sdk.scheduler.plan.Status;
import com.mesosphere.sdk.state.StateStore;
import org.apache.mesos.Protos;
import org.apache.mesos.SchedulerDriver;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * Step which implements the deregistering of a framework.
 */
public class DeregisterStep extends AbstractStep {

    private SchedulerDriver schedulerDriver;
    private StateStore stateStore;

    /**
     * Creates a new instance with initial {@code status}. The {@link SchedulerDriver} must be
     * set separately.
     */
    DeregisterStep(Status status, StateStore stateStore) {
        super("deregister", status);
        this.stateStore = stateStore;
    }

    /**
     *
     * @param schedulerDriver Must be set before call to {@link #start()}
     */
    void setSchedulerDriver(SchedulerDriver schedulerDriver) {
        this.schedulerDriver = schedulerDriver;
    }

    @Override
    public Optional<PodInstanceRequirement> start() {
        logger.info("Stopping SchedulerDriver...");
        // Remove the framework ID before unregistering
        stateStore.clearFrameworkId();
        // Unregisters the framework in addition to stopping the SchedulerDriver thread:
        schedulerDriver.stop(true);
        logger.info("Deleting service root path for framework...");
        stateStore.clearAllData();
        logger.info("Finished deleting service root path for framework");
        setStatus(Status.COMPLETE);
        return Optional.empty();
    }

    @Override
    public Optional<PodInstanceRequirement> getPodInstanceRequirement() {
        return Optional.empty();
    }

    @Override
    public void updateOfferStatus(Collection<OfferRecommendation> recommendations) {
    }

    @Override
    public Optional<PodInstanceRequirement> getAsset() {
        return getPodInstanceRequirement();
    }

    @Override
    public List<String> getErrors() {
        return Collections.emptyList();
    }

    @Override
    public void update(Protos.TaskStatus status) {
    }

}
