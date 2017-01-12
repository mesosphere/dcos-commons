package com.mesosphere.sdk.scheduler.plan;

import com.google.common.annotations.VisibleForTesting;
import com.google.protobuf.TextFormat;
import com.mesosphere.sdk.offer.CommonTaskUtils;
import com.mesosphere.sdk.offer.TaskException;
import com.mesosphere.sdk.offer.TaskUtils;
import com.mesosphere.sdk.scheduler.DefaultObservable;
import com.mesosphere.sdk.scheduler.plan.strategy.ParallelStrategy;
import com.mesosphere.sdk.scheduler.plan.strategy.Strategy;
import com.mesosphere.sdk.specification.GoalState;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.ReflectionToStringBuilder;
import org.apache.mesos.Protos;
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
    private final UUID id = UUID.randomUUID();
    private final List<String> errors;
    private final Strategy<Step> strategy = new ParallelStrategy<>();
    private final Object statusLock = new Object();
    private final PodInstanceRequirement podInstanceRequirement;
    private Status status;
    private Map<Protos.TaskID, TaskStatusPair> tasks = new HashMap<>();

    /**
     * Creates a new instance with the provided {@code name}, initial {@code status}, associated pod instance required
     * by the step, and any {@code errors} to be displayed to the user.
     */
    public DefaultStep(
            String name,
            Status status,
            PodInstanceRequirement podInstanceRequirement,
            List<String> errors) {
        this.name = name;
        this.status = status;
        this.podInstanceRequirement = podInstanceRequirement;
        this.errors = errors;

        setStatus(status); // Log initial status
    }

    /**
     * This method may be triggered by external components via the {@link #updateOfferStatus(Collection)} method in
     * particular, so it is synchronized to avoid inconsistent expectations regarding what TaskIDs are relevant to it.
     *
     * @param operations The Operations which were performed in response to the {@link PodInstanceRequirement} provided
     *                   by {@link #start()}
     */
    private synchronized void setTaskIds(Collection <Protos.Offer.Operation> operations) {
        tasks.clear();

        for (Protos.Offer.Operation operation : operations) {
            if (operation.getType().equals(Protos.Offer.Operation.Type.LAUNCH)) {
                for (Protos.TaskInfo taskInfo : operation.getLaunch().getTaskInfosList()) {
                    if (!taskInfo.getTaskId().getValue().equals("")) {
                        tasks.put(taskInfo.getTaskId(), new TaskStatusPair(taskInfo, Status.PREPARED));
                    }
                }
            }
        }

        logger.info("Step '{} [{}]' is now waiting for updates for task IDs: {}", getName(), getId(), tasks);
    }

    @Override
    public Optional<PodInstanceRequirement> start() {
        return Optional.of(podInstanceRequirement);
    }

    @Override
    public void updateOfferStatus(Collection<Protos.Offer.Operation> operations) {
        // log a bulleted list of operations, with each operation on one line:
        logger.info("Updated step '{} [{}]' with {} operations:", getName(), getId(), operations.size());
        for (Protos.Offer.Operation operation : operations) {
            logger.info("  {}", TextFormat.shortDebugString(operation));
        }
        setTaskIds(operations);

        if (operations.isEmpty()) {
            setStatus(Status.PREPARED);
        } else {
            setStatus(Status.STARTING);
        }
    }

    @Override
    public Optional<String> getAsset() {
        return Optional.of(podInstanceRequirement.getPodInstance().getName());
    }

    @Override
    public boolean isAssetDirty() {
        return isInProgress();
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
            if (status == Status.PENDING && getStrategy().isInterrupted()) {
                return Status.WAITING;
            }
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
        logger.info("Step {} received status: {}", getName(), TextFormat.shortDebugString(status));

        if (!tasks.containsKey(status.getTaskId())) {
            logger.debug("Step {} ignoring irrelevant TaskStatus: {}",
                    getName(), TextFormat.shortDebugString(status));
            return;
        }

        if (isComplete()) {
            logger.debug("Step {} ignoring TaskStatus due to being Complete: {}",
                    getName(), TextFormat.shortDebugString(status));
            return;
        }

        GoalState goalState = null;
        try {
            goalState = TaskUtils.getGoalState(
                    podInstanceRequirement.getPodInstance(),
                    CommonTaskUtils.toTaskName(status.getTaskId()));
        } catch (TaskException e) {
            logger.error(String.format("Failed to update status for step %s", getName()), e);
            setStatus(getStatus()); // Log status
            return;
        }

        logger.info("Goal state for: {} is: {}", status.getTaskId().getValue(), goalState.name());

        switch (status.getState()) {
            case TASK_ERROR:
            case TASK_FAILED:
            case TASK_KILLED:
            case TASK_KILLING:
                setTaskStatus(status.getTaskId(), Status.PENDING);
                // Retry the step because something failed.
                setStatus(Status.PENDING);
                break;
            case TASK_STAGING:
            case TASK_STARTING:
                setTaskStatus(status.getTaskId(), Status.STARTING);
                break;
            case TASK_RUNNING:
                Protos.TaskInfo taskInfo = tasks.get(status.getTaskId()).getTaskInfo();
                if (goalState.equals(GoalState.RUNNING)
                        && CommonTaskUtils.readinessCheckSucceeded(taskInfo, status)) {
                    setTaskStatus(status.getTaskId(), Status.COMPLETE);
                } else {
                    setTaskStatus(status.getTaskId(), Status.STARTING);
                }
                break;
            case TASK_FINISHED:
                if (goalState.equals(GoalState.FINISHED)) {
                    setTaskStatus(status.getTaskId(), Status.COMPLETE);
                } else {
                    setTaskStatus(status.getTaskId(), Status.PENDING);
                }
                break;
            default:
                logger.warn("Failed to process unexpected state: " + status.getState());
        }

        setStatus(getStatus(tasks));
    }

    private void setTaskStatus(Protos.TaskID taskID, Status status) {
        if (tasks.containsKey(taskID)) {
            TaskStatusPair taskStatusPair = new TaskStatusPair(tasks.get(taskID).getTaskInfo(), status);
            tasks.replace(taskID, taskStatusPair);
            logger.info("Status for: {} is: {}", taskID.getValue(), status);
        }
    }

    private Status getStatus(Map<Protos.TaskID, TaskStatusPair> tasks) {
        if (tasks.isEmpty()) {
            return Status.PENDING;
        }

        for (Map.Entry<Protos.TaskID, TaskStatusPair> entry : tasks.entrySet()) {
            String taskId = entry.getKey().getValue();
            Status status = entry.getValue().getStatus();
            logger.info("TaskId: {} has status: {}", taskId, status);
            if (!status.equals(Status.COMPLETE)) {
                // Keep and log current status
                return status;
            }
        }

        return Status.COMPLETE;
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

    private static class TaskStatusPair {
        private final Protos.TaskInfo taskInfo;
        private final Status status;

        public TaskStatusPair(Protos.TaskInfo taskInfo, Status status) {
            this.taskInfo = taskInfo;
            this.status = status;
        }

        public Protos.TaskInfo getTaskInfo() {
            return taskInfo;
        }

        public Status getStatus() {
            return status;
        }
    }
}
