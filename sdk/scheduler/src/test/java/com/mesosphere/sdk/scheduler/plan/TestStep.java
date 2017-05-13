package com.mesosphere.sdk.scheduler.plan;

import com.google.common.annotations.VisibleForTesting;

import com.mesosphere.sdk.offer.OfferRecommendation;
import org.apache.mesos.Protos;

import java.util.*;

/**
 * This class is an implementation of the Step interface for test purposes.
 */
public class TestStep extends AbstractStep {

    private PodInstanceRequirement podInstanceRequirement;

    public TestStep() {
        super("test-step", Status.PENDING);
    }

    public TestStep(String name, PodInstanceRequirement podInstanceRequirement) {
        super(name, Status.PENDING);
        this.podInstanceRequirement = podInstanceRequirement;
    }

    @Override
    public Optional<PodInstanceRequirement> start() {
        setStatus(Status.PREPARED);
        return getPodInstanceRequirement();
    }

    @Override
    public Optional<PodInstanceRequirement> getPodInstanceRequirement() {
        return Optional.ofNullable(podInstanceRequirement);
    }

    @Override
    public void updateOfferStatus(Collection<OfferRecommendation> recommendations) {
        if (recommendations.isEmpty()) {
            setStatus(Status.PREPARED);
        } else {
            setStatus(Status.STARTING);
        }
    }

    @Override
    public Optional<PodInstanceRequirement> getAsset() {
        return Optional.ofNullable(podInstanceRequirement);
    }

    @Override
    public void update(Protos.TaskStatus status) {
        // Left intentionally empty
    }

    @Override
    public String getMessage() {
        return "test-message";
    }

    @Override
    public List<String> getErrors() {
        return Collections.emptyList();
    }

    @Override
    public void restart() {
        setStatus(Status.PENDING);
    }

    @Override
    public void forceComplete() {
        setStatus(Status.COMPLETE);
    }

    @VisibleForTesting
    public void setStatus(Status status) {
        super.setStatus(status);
    }

    @Override
    public String toString() {
        return String.format("TestStep[%s:%s]", getName(), getStatus());
    }
}
