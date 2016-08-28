package org.apache.mesos.scheduler.plan;

import org.apache.mesos.Protos;
import org.apache.mesos.offer.OfferRequirement;
import org.apache.mesos.specification.TaskSpecification;
import org.apache.zookeeper.data.Stat;

import java.util.Optional;
import java.util.UUID;

/**
 * Created by gabriel on 8/27/16.
 */
public class DefaultBlock implements Block {
    private final String name;
    private final Optional<OfferRequirement> offerRequirementOptional;
    private Status status;

    public DefaultBlock(String name) {
        this(name, Optional.empty(), Status.COMPLETE);
    }

    public DefaultBlock(String name, OfferRequirement offerRequirement, Status status) {
        this(name, Optional.of(offerRequirement), status);
    }

    private DefaultBlock(String name, Optional<OfferRequirement> offerRequirementOptional, Status status) {
        this.name = name;
        this.offerRequirementOptional = offerRequirementOptional;
        this.status = status;

    }

    boolean isStatus(Status status) {
        return status.equals(this.status);
    }

    @Override
    public boolean isComplete() {
        return isStatus(Status.COMPLETE);
    }

    @Override
    public boolean isPending() {
        return isStatus(Status.PENDING);
    }

    @Override
    public boolean isInProgress() {
        return isStatus(Status.IN_PROGRESS);
    }

    @Override
    public Optional<OfferRequirement> start() {
        return offerRequirementOptional;
    }

    @Override
    public void updateOfferStatus(boolean accepted) {

    }

    @Override
    public void restart() {

    }

    @Override
    public void forceComplete() {

    }

    @Override
    public void update(Protos.TaskStatus status) {

    }

    @Override
    public UUID getId() {
        return null;
    }

    @Override
    public String getMessage() {
        return null;
    }

    @Override
    public String getName() {
        return null;
    }
}
