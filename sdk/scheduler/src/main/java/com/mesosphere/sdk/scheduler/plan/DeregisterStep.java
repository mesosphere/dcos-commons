package com.mesosphere.sdk.scheduler.plan;

import com.mesosphere.sdk.offer.OfferRecommendation;
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

    /**
     * Creates a new instance with initial {@code status}. The {@link SchedulerDriver} must be
     * set separately.
     */
    public DeregisterStep(Status status) {
        super("deregister", status);
    }

    /**
     *
     * @param schedulerDriver Must be set before call to {@link #start()}
     */
    public void setSchedulerDriver(SchedulerDriver schedulerDriver) {
        this.schedulerDriver = schedulerDriver;
    }

    @Override
    public Optional<PodInstanceRequirement> start() {
        logger.info("Stopping SchedulerDriver...");
        schedulerDriver.stop(true);
        setStatus(Status.COMPLETE);
        return Optional.empty();
    }

    @Override
    public void updateOfferStatus(Collection<OfferRecommendation> recommendations) {
    }

    @Override
    public Optional<String> getAsset() {
        return Optional.empty();
    }

    @Override
    public List<String> getErrors() {
        return Collections.emptyList();
    }

    @Override
    public void update(Protos.TaskStatus status) {
    }

}
