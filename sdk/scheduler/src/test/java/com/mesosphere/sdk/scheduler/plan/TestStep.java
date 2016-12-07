package com.mesosphere.sdk.scheduler.plan;

import org.apache.mesos.Protos;
import com.mesosphere.sdk.offer.OfferRequirement;
import com.mesosphere.sdk.scheduler.DefaultObservable;
import com.mesosphere.sdk.scheduler.plan.strategy.SerialStrategy;
import com.mesosphere.sdk.scheduler.plan.strategy.Strategy;

import com.google.common.annotations.VisibleForTesting;

import java.util.*;

/**
 * This class is an implementation of the Step interface for test purposes.
 */
public class TestStep extends DefaultObservable implements Step {

    private final UUID id = UUID.randomUUID();
    private Status status = Status.PENDING;
    private String name;

    public TestStep() {
        this.name = "test-step";
    }

    public TestStep(String name) {
        this.name = name;
    }

    @Override
    public List<Element<?>> getChildren() {
        return Collections.emptyList();
    }

    @Override
    public Strategy<? extends Step> getStrategy() {
        return new SerialStrategy<>();
    }

    @Override
    public Optional<OfferRequirement> start() {
        setStatus(Status.IN_PROGRESS);
        return Optional.empty();
    }

    @Override
    public void updateOfferStatus(Collection<Protos.Offer.Operation> operations) {
        if (operations.isEmpty()) {
            setStatus(Status.PENDING);
        } else {
            setStatus(Status.IN_PROGRESS);
        }
    }

    @Override
    public void update(Protos.TaskStatus status) {
        // Left intentionally empty
    }

    @Override
    public UUID getId() {
        return id;
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
    public String getName() {
        return name;
    }

    @Override
    public Status getStatus() {
        return status;
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
        this.status = status;
    }
}
