package org.apache.mesos.scheduler.plan;

import org.apache.mesos.Protos;
import org.apache.mesos.offer.OfferRequirement;

import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

/**
 * This class is an implementation of the Block interface for test purposes.
 */
public class TestBlock implements Block {

    private final UUID id = UUID.randomUUID();
    private Status status = Status.PENDING;

    public TestBlock setStatus(Status newStatus) {
        status = newStatus;
        return this;
    }

    @Override
    public void restart() {
        setStatus(Status.PENDING);
    }

    @Override
    public void forceComplete() {
        setStatus(Status.COMPLETE);
    }

    @Override
    public boolean isPending() {
        return status.equals(Status.PENDING);
    }

    @Override
    public boolean isInProgress() {
        return status.equals(Status.IN_PROGRESS);
    }

    @Override
    public boolean isComplete() {
        return status.equals(Status.COMPLETE);
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
    public String getName() {
        return "test-block";
    }
}
