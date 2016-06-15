package org.apache.mesos.scheduler.plan;

import org.apache.mesos.Protos;
import org.apache.mesos.offer.OfferRequirement;

import java.util.UUID;

/**
 * This class is an implementation of the Block interface for test purposes.
 */
public class TestBlock implements Block {

    private final UUID id = UUID.randomUUID();
    private Status status = Status.Pending;

    public void setStatus(Status newStatus) {
        status = newStatus;
    }

    @Override
    public void restart() {
        setStatus(Status.Pending);
    }

    @Override
    public void forceComplete() {
        setStatus(Status.Complete);
    }

    @Override
    public boolean isPending() {
        return status.equals(Status.Pending);
    }

    @Override
    public boolean isInProgress() {
        return status.equals(Status.InProgress);
    }

    @Override
    public boolean isComplete() {
        return status.equals(Status.Complete);
    }

    @Override
    public OfferRequirement start() {
        setStatus(Status.InProgress);
        return null; // no requirements
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

    @Override
    public void updateOfferStatus(boolean accepted) {
        if (!accepted) {
            setStatus(Status.Pending);
        }
    }
}
