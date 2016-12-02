package org.apache.mesos.scheduler.plan;

import com.google.common.annotations.VisibleForTesting;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.ReflectionToStringBuilder;
import org.apache.mesos.Protos;
import org.apache.mesos.offer.OfferRequirement;
import org.apache.mesos.offer.TaskException;
import org.apache.mesos.offer.TaskUtils;
import org.apache.mesos.scheduler.DefaultObservable;
import org.apache.mesos.scheduler.plan.strategy.ParallelStrategy;
import org.apache.mesos.scheduler.plan.strategy.Strategy;
import org.apache.mesos.specification.PodInstance;
import org.apache.mesos.specification.TaskSpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * This class is a default implementation of the Step interface.
 */
public class DefaultStep extends DefaultObservable implements Step {
    /** Non-static to ensure that we inherit the names of subclasses. */
    private final Logger logger = LoggerFactory.getLogger(getClass());

    private final String name;
    private final Optional<OfferRequirement> offerRequirementOptional;
    private final UUID id = UUID.randomUUID();
    private final List<String> errors;
    private final Strategy<Step> strategy = new ParallelStrategy<>();
    private final Object statusLock = new Object();
    private final PodInstance podInstance;
    private Status status;
    private Map<Protos.TaskID, Status> tasks = new HashMap<>();

    public DefaultStep(
            String name,
            Optional<OfferRequirement> offerRequirementOptional,
            Status status,
            PodInstance podInstance,
            List<String> errors) {
        this.name = name;
        this.offerRequirementOptional = offerRequirementOptional;
        this.podInstance = podInstance;
        this.errors = errors;

        setStatus(status); // Log initial status
    }

    @Override
    public Optional<OfferRequirement> start() {
        return offerRequirementOptional;
    }

    /**
     * Synchronized to ensure consistency between this and {@link #update(Protos.TaskStatus)}.
     */
    public synchronized void updateOfferStatus(Collection<Protos.Offer.Operation> operations) {
        tasks.clear();
        tasks.putAll(toTaskStatuses(operations));
        logger.info("Updated with {} operations: '{}' task IDs: '{}'", operations.size(), operations, tasks);
        if (!operations.isEmpty()) {
            setStatus(Status.IN_PROGRESS);
        }
    }

    @Override
    public void restart() {
        logger.warn("Restarting step: '{} [{}]'", getName(), getId());
        setStatus(Status.PENDING);
    }

    @Override
    public void forceComplete() {
        logger.warn("Forcing completion of step: '{} [{}]'", getName(), getId());
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
    public Status getStatus() {
        synchronized (statusLock) {
            return status;
        }
    }

    @Override
    public List<Element<?>> getChildren() {
        return Collections.emptyList();
    }

    @Override
    public Strategy<Step> getStrategy() {
        return strategy;
    }

    /**
     * Synchronized to ensure consistency between this and {@link #updateOfferStatus(Collection)}.
     */
    @Override
    public synchronized void update(Protos.TaskStatus status) {
        if (!tasks.containsKey(status.getTaskId())) {
            logger.info(getName() + " ignoring irrelevant TaskStatus: " + status);
            return;
        }

        if (isComplete()) {
            logger.warn(getName() + " ignoring due to being Complete, TaskStatus: " + status);
            return;
        }

        switch (status.getState()) {
            case TASK_ERROR:
            case TASK_FAILED:
            case TASK_KILLED:
            case TASK_KILLING:
                tasks.replace(status.getTaskId(), Status.ERROR);
                // Retry the step because something failed.
                setStatus(Status.PENDING);
                break;
            case TASK_STAGING:
            case TASK_STARTING:
                tasks.replace(status.getTaskId(), Status.IN_PROGRESS);
                break;
            case TASK_RUNNING:
                try {
                    if (TaskUtils.getGoalState(
                            podInstance,
                            TaskUtils.toTaskName(status.getTaskId())).equals(TaskSpec.GoalState.RUNNING)) {
                        tasks.replace(status.getTaskId(), Status.COMPLETE);
                    } else {
                        tasks.replace(status.getTaskId(), Status.IN_PROGRESS);
                    }
                } catch (TaskException e) {
                    logger.error("Failed to update status.", e);
                }
                break;
            case TASK_FINISHED:
                try {
                    if (TaskUtils.getGoalState(
                            podInstance,
                            TaskUtils.toTaskName(status.getTaskId())).equals(TaskSpec.GoalState.RUNNING)) {
                        tasks.replace(status.getTaskId(), Status.COMPLETE);
                    } else {
                        tasks.replace(status.getTaskId(), Status.PENDING);
                    }
                } catch (TaskException e) {
                    logger.error("Failed to update status.", e);
                }
                break;
            default:
                logger.warn("Failed to process unexpected state: " + status.getState());
        }

        setStatus(getStatus(tasks));
    }

    private static Status getStatus(Map<Protos.TaskID, Status> tasks) {
        if (tasks.isEmpty()) {
            return Status.PENDING;
        }

        for (Status taskStatus : tasks.values()) {
            if (!taskStatus.equals(Status.COMPLETE)) {
                // Keep and log current status
                return taskStatus;
            }
        }

        return Status.COMPLETE;
    }

    private static Map<Protos.TaskID, Status> toTaskStatuses(Collection<Protos.Offer.Operation> operations) {
        Map<Protos.TaskID, Status> tasks = new HashMap<>();
        for (Protos.Offer.Operation operation : operations) {
            if (operation.getType().equals(Protos.Offer.Operation.Type.LAUNCH)) {
                for (Protos.TaskInfo taskInfo : operation.getLaunch().getTaskInfosList()) {
                    tasks.put(taskInfo.getTaskId(), Status.IN_PROGRESS);
                }
            }
        }
        return tasks;
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

    @VisibleForTesting
    public Map<Protos.TaskID, Status> getExpectedTasks() {
        return tasks;
    }

    /**
     * Updates the status setting and logs the outcome. Should only be called either by tests, by
     * {@code this}, or by subclasses.
     *
     * @param newStatus the new status to be set
     */
    @VisibleForTesting
    void setStatus(Status newStatus) {
        Status oldStatus;
        synchronized (statusLock) {
            oldStatus = status;
            status = newStatus;
            logger.info(getName() + ": changed status from: " + oldStatus + " to: " + newStatus);
        }

        if (!Objects.equals(oldStatus, newStatus)) {
            notifyObservers();
        }
    }
}
