package org.apache.mesos.scheduler.plan;

import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.ReflectionToStringBuilder;
import org.apache.mesos.Protos;
import org.apache.mesos.offer.OfferRequirement;
import org.apache.mesos.scheduler.DefaultObservable;
import org.apache.mesos.scheduler.plan.strategy.ParallelStrategy;
import org.apache.mesos.scheduler.plan.strategy.Strategy;
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
    private final List<String> errors;
    private final Strategy strategy = new ParallelStrategy();
    private Status status;
    private Map<Protos.TaskID, Status> tasks = new HashMap<>();

    public DefaultBlock(
            String name,
            Optional<OfferRequirement> offerRequirementOptional,
            Status status,
            List<String> errors) {
        this.name = name;
        this.offerRequirementOptional = offerRequirementOptional;
        this.status = status;
        this.errors = errors;
    }

    private synchronized void setTaskIds(Collection <Protos.Offer.Operation> operations) {
        tasks.clear();

        for (Protos.Offer.Operation operation : operations) {
            if (operation.getType().equals(Protos.Offer.Operation.Type.LAUNCH)) {
                for (Protos.TaskInfo taskInfo : operation.getLaunch().getTaskInfosList()) {
                    tasks.put(taskInfo.getTaskId(), Status.IN_PROGRESS);
                }
            }
        }
    }

    public synchronized void setStatus(Status newStatus) {
        Status oldStatus = status;
        status = newStatus;
        logger.info(getName() + ": changed status from: " + oldStatus + " to: " + newStatus);

        if (!oldStatus.equals(newStatus)) {
            notifyObservers();
        }
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
        logger.warn("Restarting block: '{} [{}]'", getName(), getId());
        setStatus(Status.PENDING);
    }

    @Override
    public void forceComplete() {
        logger.warn("Forcing completion of block: '{} [{}]'", getName(), getId());
        setStatus(Status.COMPLETE);
    }

    @Override
    public String getMessage() {
        return PlanUtils.getMessage(this);
    }

    @Override
    public List<String> getErrors() {
        return errors;
    }

    @Override
    public UUID getId() {
        return id;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public synchronized Status getStatus() {
        return status;
    }

    @Override
    public List<Element> getChildren() {
        return Collections.emptyList();
    }

    @Override
    public Strategy<? extends Element> getStrategy() {
        return strategy;
    }

    @Override
    public synchronized void update(Protos.TaskStatus status) {
        if (!tasks.containsKey(status.getTaskId())) {
            logger.warn(getName() + " ignoring irrelevant TaskStatus: " + status);
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
    public String toString() {
        return ReflectionToStringBuilder.toString(this);
    }

    @Override
    public boolean equals(Object o) {
        return EqualsBuilder.reflectionEquals(this, o);
    }

    @Override
    public int hashCode() {
        return Objects.hash(getId());
    }
}
