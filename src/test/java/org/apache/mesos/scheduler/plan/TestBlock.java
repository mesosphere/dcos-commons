package org.apache.mesos.scheduler.plan;

import org.apache.mesos.Protos;
import org.apache.mesos.offer.OfferRequirement;

import java.util.UUID;

/**
 * This class is an implementation of the Block interface for test purposes.
 */
public class TestBlock implements Block {

    private final UUID id;
    private Status status = Status.Pending;

    public TestBlock() {
        this.id = UUID.randomUUID();
    }

    @Override
    public Status getStatus() {
        return status;
    }

    @Override
    public void setStatus(Status newStatus) {
        status = newStatus;
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
        return null;
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
