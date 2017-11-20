package com.mesosphere.sdk.scheduler.plan;

import com.google.common.annotations.VisibleForTesting;
import com.google.protobuf.TextFormat;
import com.mesosphere.sdk.offer.*;
import com.mesosphere.sdk.offer.taskdata.TaskLabelReader;
import com.mesosphere.sdk.specification.GoalState;
import com.mesosphere.sdk.specification.TaskSpec;
import com.mesosphere.sdk.state.GoalStateOverride;
import com.mesosphere.sdk.state.StateStore;

import org.apache.mesos.Protos;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

/**
 * Step which implements the deployment of a pod.
 */
public class DeploymentStep extends AbstractStep {

    protected final StateStore stateStore;
    protected final PodInstanceRequirement podInstanceRequirement;

    private final List<String> errors = new ArrayList<>();
    private final Map<String, String> parameters = new HashMap<>();
    private Map<Protos.TaskID, TaskStatusPair> tasks = new HashMap<>();
    private final AtomicBoolean prepared = new AtomicBoolean(false);

    /**
     * Creates a new instance with the provided {@code name}, initial {@code status}, associated pod instance required
     * by the step, and any {@code errors} to be displayed to the user.
     */
    public DeploymentStep(
            String name, PodInstanceRequirement podInstanceRequirement, StateStore stateStore) {
        super(name, Status.PENDING);
        this.podInstanceRequirement = podInstanceRequirement;
        this.stateStore = stateStore;
        updateStatus();
    }

    /**
     * Sets the step to an {@code ERROR} state with the provided error message.
     *
     * @return this
     */
    public DeploymentStep addError(String error) {
        this.errors.add(error);
        updateStatus();
        return this;
    }

    /**
     * Sets an initial status (other than {@code PENDING}) for the step. This status may later be updated as
     * Offers/TaskStatuses are received.
     *
     * @return this
     */
    public DeploymentStep updateInitialStatus(Status status) {
        super.setStatus(status);
        return this;
    }

    @Override
    public void updateParameters(Map<String, String> parameters) {
        this.parameters.clear();
        this.parameters.putAll(parameters);
    }

    @Override
    public Optional<PodInstanceRequirement> start() {
        return getPodInstanceRequirement();
    }

    @Override
    public Optional<PodInstanceRequirement> getPodInstanceRequirement() {
        return Optional.of(
                PodInstanceRequirement.newBuilder(podInstanceRequirement)
                        .environment(parameters)
                        .build());
    }

    private static Set<Protos.TaskID> getTaskIds(Collection<OfferRecommendation> recommendations) {
        return recommendations.stream()
                .filter(recommendation -> recommendation instanceof LaunchOfferRecommendation)
                .map(recommendation -> ((LaunchOfferRecommendation) recommendation).getStoreableTaskInfo())
                .filter(taskInfo -> !taskInfo.getTaskId().getValue().equals(""))
                .map(taskInfo -> taskInfo.getTaskId())
                .collect(Collectors.toSet());
    }

    /**
     * Synchronized to ensure consistency between this and {@link #update(Protos.TaskStatus)}.
     */
    @Override
    public synchronized void updateOfferStatus(Collection<OfferRecommendation> recommendations) {
        // log a bulleted list of operations, with each operation on one line:
        logger.info("Updated step '{} [{}]' to reflect {} recommendation{}: {}",
                getName(),
                getId(),
                recommendations.size(),
                recommendations.size() == 1 ? "" : "s",
                recommendations.stream().map(r -> r.getOperation().getType()));

        tasks.clear();

        for (OfferRecommendation recommendation : recommendations) {
            if (!(recommendation instanceof LaunchOfferRecommendation)) {
                continue;
            }
            Protos.TaskInfo taskInfo = ((LaunchOfferRecommendation) recommendation).getStoreableTaskInfo();
            if (!taskInfo.getTaskId().getValue().equals("")) {
                tasks.put(taskInfo.getTaskId(), new TaskStatusPair(taskInfo, Status.PREPARED));
            }
        }

        logger.info("Step '{} [{}]' is now waiting for updates for task IDs: {}", getName(), getId(), tasks.keySet());

        if (recommendations.isEmpty()) {
            tasks.keySet().forEach(id -> setTaskStatus(id, Status.PREPARED));
        } else {
            getTaskIds(recommendations).forEach(id -> setTaskStatus(id, Status.STARTING));
        }

        prepared.set(true);
        updateStatus();
    }

    @Override
    public List<String> getErrors() {
        return errors;
    }

    @Override
    public String getDisplayStatus() {
        // NOTE: This is obtained on the fly because it's only effectively needed when someone is actually fetching
        // plan status. Similarly, it can be incorrect for non-recovery deployment steps, as they're only getting
        // updates while they're still deploying.

        // Extract full names of the defined tasks, e.g. "pod-0-task":
        Collection<String> taskFullNames = podInstanceRequirement.getPodInstance().getPod().getTasks().stream()
                .map(taskSpec -> TaskSpec.getInstanceName(podInstanceRequirement.getPodInstance(), taskSpec))
                .collect(Collectors.toList());
        return getDisplayStatus(stateStore, super.getStatus(), taskFullNames);
    }

    /**
     * Synchronized to ensure consistency between this and {@link #updateOfferStatus(Collection)}.
     */
    @Override
    public synchronized void update(Protos.TaskStatus status) {
        logger.debug("Step {} received status: {}", getName(), TextFormat.shortDebugString(status));

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
                    CommonIdUtils.toTaskName(status.getTaskId()));
        } catch (TaskException e) {
            logger.error(String.format("Failed to get goal state for step %s with status %s",
                    getName(), getStatus()), e);
            return;
        }

        logger.info("Goal state for: {} is: {}", status.getTaskId().getValue(), goalState.name());

        switch (status.getState()) {
            case TASK_ERROR:
            case TASK_FAILED:
            case TASK_KILLED:
            case TASK_KILLING:
            case TASK_LOST:
                setTaskStatus(status.getTaskId(), Status.PENDING);
                break;
            case TASK_STAGING:
            case TASK_STARTING:
                setTaskStatus(status.getTaskId(), Status.STARTING);
                break;
            case TASK_RUNNING:
                Protos.TaskInfo taskInfo = tasks.get(status.getTaskId()).getTaskInfo();
                if (goalState.equals(GoalState.RUNNING)
                        && new TaskLabelReader(taskInfo).isReadinessCheckSucceeded(status)) {
                    setTaskStatus(status.getTaskId(), Status.COMPLETE);
                } else {
                    setTaskStatus(status.getTaskId(), Status.STARTED);
                }
                break;
            case TASK_FINISHED:
                if (goalState.equals(GoalState.ONCE) || goalState.equals(GoalState.FINISH)) {
                    setTaskStatus(status.getTaskId(), Status.COMPLETE);
                } else {
                    setTaskStatus(status.getTaskId(), Status.PENDING);
                }
                break;
            default:
                logger.error("Failed to process unexpected state: " + status.getState());
        }

        updateStatus();
    }

    /**
     * WARNING: This is a private method for a reason.  Callers must validate that the taskID is already present in the
     * tasks map.
     */
    private String getTaskName(Protos.TaskID taskID) {
        return tasks.get(taskID).getTaskInfo().getName();
    }

    private void setOverrideStatus(Protos.TaskID taskID, Status status) {
        GoalStateOverride.Status overrideStatus = stateStore.fetchGoalOverrideStatus(getTaskName(taskID));

        logger.info("Goal override status: {}", overrideStatus);
        if (!GoalStateOverride.Progress.COMPLETE.equals(overrideStatus.progress)) {
            GoalStateOverride.Progress progress = GoalStateOverride.Status.translateStatus(status);
            stateStore.storeGoalOverrideStatus(
                    getTaskName(taskID),
                    overrideStatus.target.newStatus(progress));
        }
    }

    private void setTaskStatus(Protos.TaskID taskID, Status status) {
        if (tasks.containsKey(taskID)) {
            // Update the TaskStatusPair with the new status:
            tasks.replace(taskID, new TaskStatusPair(tasks.get(taskID).getTaskInfo(), status));
            logger.info("Status for: {} is: {}", taskID.getValue(), status);
        }

        setOverrideStatus(taskID, status);

        if (isPending()) {
            prepared.set(false);
        }
    }

    private void updateStatus() {
        Set<Status> taskStatuses = tasks.values().stream()
                .map(task -> task.getStatus())
                .collect(Collectors.toSet());
        Optional<Status> status = getStatus(taskStatuses, !getErrors().isEmpty(), prepared.get());
        if (status.isPresent()) {
            super.setStatus(status.get());
        } else {
            logger.warn("The minimum status of the set of task statuses, {}, is not explicitly handled. " +
                    "Leaving current step status as-is: {} {}", taskStatuses, super.getName(), super.getStatus());
        }
    }

    @VisibleForTesting
    static String getDisplayStatus(StateStore stateStore, Status stepStatus, Collection<String> tasksToLaunch) {
        // It is valid for some tasks to be paused and not others, i.e. user specified specific task(s) to paused.
        // Only display a PAUSING/PAUSED state in the plan if ALL the tasks are marked as paused.
        boolean allTasksPaused = !tasksToLaunch.isEmpty() && tasksToLaunch.stream()
                .map(taskName -> stateStore.fetchGoalOverrideStatus(taskName))
                .allMatch(goalOverrideStatus -> goalOverrideStatus.target == GoalStateOverride.PAUSED);
        if (allTasksPaused) {
            // Show a custom display status when the task is in or entering a paused state:
            if (stepStatus.isRunning()) {
                return GoalStateOverride.PAUSED.getTransitioningName();
            } else if (stepStatus == Status.COMPLETE || stepStatus == Status.STARTED) {
                return GoalStateOverride.PAUSED.getSerializedName();
            }
        }
        return stepStatus.toString();
    }

    @VisibleForTesting
    static Optional<Status> getStatus(Set<Status> statuses, boolean hasErrors, boolean isPrepared) {
        // A DeploymentStep should have the "least" status of its consituent tasks.
        // 1 PENDING task and 2 STARTING tasks => PENDING step state
        // 2 STARTING tasks and 1 COMPLETE task=> STARTING step state
        // 3 COMPLETE tasks => COMPLETE step state
        if (hasErrors) {
            return Optional.of(Status.ERROR);
        } else if (statuses.isEmpty()) {
            if (isPrepared) {
                return Optional.of(Status.PREPARED);
            } else {
                return Optional.of(Status.PENDING);
            }
        } else if (statuses.contains(Status.ERROR)) {
            return Optional.of(Status.ERROR);
        } else if (statuses.contains(Status.PENDING)) {
            return Optional.of(Status.PENDING);
        } else if (statuses.contains(Status.PREPARED)) {
            return Optional.of(Status.PREPARED);
        } else if (statuses.contains(Status.STARTING)) {
            return Optional.of(Status.STARTING);
        } else if (statuses.contains(Status.STARTED)) {
            return Optional.of(Status.STARTED);
        } else if (statuses.contains(Status.COMPLETE) && statuses.size() == 1) {
            // If the size of the set statuses == 1, then all tasks have the same status.
            // In this case, the status COMPLETE.
            return Optional.of(Status.COMPLETE);
        }

        // If we don't explicitly handle the new status, we will fall back to the existing parent status.
        return Optional.empty();
    }

    @VisibleForTesting
    static class TaskStatusPair {
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

        @Override
        public String toString() {
            return String.format("%s(%s):%s", taskInfo.getName(), taskInfo.getTaskId().getValue(), status);
        }
    }
}
