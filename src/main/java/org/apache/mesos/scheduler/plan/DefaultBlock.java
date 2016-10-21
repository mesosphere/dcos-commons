package org.apache.mesos.scheduler.plan;

import org.apache.commons.lang3.builder.ReflectionToStringBuilder;
import org.apache.mesos.Protos;
import org.apache.mesos.offer.OfferRequirement;
import org.apache.mesos.scheduler.DefaultObservable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * This class is a default implementation of the Block interface.
 */
public class DefaultBlock extends DefaultObservable implements Block {
    private final Logger logger = LoggerFactory.getLogger(getClass());
    private final String name;
    private final Optional<OfferRequirement> offerRequirementOptional;
    private final UUID id = UUID.randomUUID();
    private Status status;
    private Map<Protos.TaskID, Status> tasks = new HashMap<>();

    public DefaultBlock(String name, OfferRequirement offerRequirement, Status status) {
        this(name, Optional.of(offerRequirement), status);
    }

    private DefaultBlock(String name, Optional<OfferRequirement> offerRequirementOptional, Status status) {
        this.name = name;
        this.offerRequirementOptional = offerRequirementOptional;
        this.status = status;
    }

    private boolean isStatus(Status status) {
        return status.equals(this.status);
    }

    private void setTaskIds(Collection <Protos.Offer.Operation> operations) {
        tasks.clear();

        for (Protos.Offer.Operation operation : operations) {
            if (operation.getType().equals(Protos.Offer.Operation.Type.LAUNCH)) {
                for (Protos.TaskInfo taskInfo : operation.getLaunch().getTaskInfosList()) {
                    tasks.put(taskInfo.getTaskId(), Status.IN_PROGRESS);
                }
            }
        }
    }

    private void setStatus(Status newStatus) {
        Status oldStatus = status;
        status = newStatus;
        logger.info(getName() + ": changed status from: " + oldStatus + " to: " + newStatus);

        if (!oldStatus.equals(newStatus)) {
            notifyObservers();
        }
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
    public void updateOfferStatus(Collection<Protos.Offer.Operation> operations) {
        if (!operations.isEmpty()) {
            setTaskIds(operations);
            setStatus(Status.IN_PROGRESS);
        }
    }

    @Override
    public void restart() {
        logger.warn("Restarting block: " + getName());
        setStatus(Status.PENDING);
    }

    @Override
    public void forceComplete() {
        logger.warn("Forcing completion of block: " + getName());
        setStatus(Status.COMPLETE);
    }

    @Override
    public void update(Protos.TaskStatus status) {
        if (!tasks.containsKey(status.getTaskId())) {
            logger.info(getName() + " ignoring irrelevant TaskStatus: " + status);
            return;
        }

        if (isComplete()) {
            logger.warn(getName() + " ignoring due to being Complete, TaskStatus: " + status);
            return;
        }

        switch (status.getState()) {
            case TASK_RUNNING:
                tasks.replace(status.getTaskId(), Status.COMPLETE);
                break;
            case TASK_ERROR:
            case TASK_FAILED:
            case TASK_FINISHED:
            case TASK_KILLED:
            case TASK_KILLING:
                tasks.replace(status.getTaskId(), Status.ERROR);
                // Retry the block because something failed.
                setStatus(Status.PENDING);
                break;
            case TASK_STAGING:
            case TASK_STARTING:
                tasks.replace(status.getTaskId(), Status.IN_PROGRESS);
                break;
            default:
                logger.warn("Failed to process unexpected state: " + status.getState());
        }

        for (Status taskStatus : tasks.values()) {
            if (!taskStatus.equals(Status.COMPLETE)) {
                // Keep and log current status
                setStatus(this.status);
                return;
            }
        }

        setStatus(Status.COMPLETE);
    }

    @Override
    public UUID getId() {
        return id;
    }

    @Override
    public String getMessage() {
        return "Block: " + name;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public String toString() {
        return ReflectionToStringBuilder.toString(this);
    }
}
