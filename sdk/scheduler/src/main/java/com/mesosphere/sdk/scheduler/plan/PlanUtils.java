package com.mesosphere.sdk.scheduler.plan;

import com.mesosphere.sdk.offer.CommonIdUtils;
import com.mesosphere.sdk.offer.LoggingUtils;
import com.mesosphere.sdk.offer.TaskUtils;
import com.mesosphere.sdk.scheduler.plan.backoff.Delay;
import com.mesosphere.sdk.scheduler.plan.backoff.ExponentialBackOff;

import org.slf4j.Logger;

import java.util.Collection;
import java.util.Collections;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Common utility methods for {@link Plan} elements.
 */
public final class PlanUtils {

  private static final Logger LOGGER = LoggingUtils.getLogger(PlanUtils.class);

  private PlanUtils() {
  }

  /**
   * Determines whether the specified asset refers to the same pod instance and tasks other assets.
   *
   * @param asset       The asset of interest.
   * @param dirtyAssets Other assets which may conflict with the {@code asset}
   */
  public static boolean assetConflicts(
      PodInstanceRequirement asset,
      Collection<PodInstanceRequirement> dirtyAssets)
  {
    return dirtyAssets.stream().anyMatch(asset::conflictsWith);
  }

  public static Set<String> getLaunchableTasks(Collection<Plan> plans) {
    return plans.stream()
        .flatMap(plan -> plan.getChildren().stream())
        .flatMap(phase -> phase.getChildren().stream())
        .filter(step -> step.getPodInstanceRequirement().isPresent())
        .map(step -> step.getPodInstanceRequirement().get())
        .flatMap(podInstanceRequirement ->
            TaskUtils.getTaskNames(
                podInstanceRequirement.getPodInstance(),
                podInstanceRequirement.getTasksToLaunch()
            ).stream()
        )
        .collect(Collectors.toSet());
  }

  public static Set<PodInstanceRequirement> getDirtyAssets(Plan plan) {
    if (plan == null) {
      return Collections.emptySet();
    }
    LOGGER.info(">>>>>>> plan {}", plan.getName());
    plan.getChildren().forEach(x -> {
      LOGGER.info(">>>> phase {}", x.getName());
      x.getChildren().forEach(y -> LOGGER.info(">> step {}", y.getDisplayStatus()));
    });

    return plan.getChildren().stream()
        .flatMap(phase -> phase.getChildren().stream())
        .filter(step -> (step.isPrepared() || step.isStarting())
            && step.getPodInstanceRequirement().isPresent())
        .map(step -> step.getPodInstanceRequirement().get())
        .collect(Collectors.toSet());
  }

  /**
   * Returns whether the provided Plan {@link Element} is eligible for work.
   *
   * @param element     the element to be checked
   * @param dirtyAssets list of current dirty assets which are already being worked on
   * @return whether this element may proceed with work
   */
  public static boolean isEligible(
      Element element,
      Collection<PodInstanceRequirement> dirtyAssets)
  {
    if (element.isComplete() || element.hasErrors()) {
      return false;
    }
    if (element instanceof Interruptible && ((Interruptible) element).isInterrupted()) {
      return false;
    }
    /* TODO@kjoshi: return false here in case where our current delay hasn't been met.
    @takirala: here's where the logic for determining if we're in backoff gets excercised.
    */
    if (element instanceof Step && ((Step) element).getPodInstanceRequirement().isPresent()) {
      PodInstanceRequirement podInstanceRequirement =
              ((Step) element).getPodInstanceRequirement().get();
      /*
      Implementation detail: Step will have a expiry time set. Compare expiry time with currentTime
      and decide if we've expired or not, include in workset if post expiry.
      */
      return podInstanceRequirement.getTasksToLaunch().stream().allMatch(name -> {
        String taskName = CommonIdUtils.getTaskInstanceName(
                podInstanceRequirement.getPodInstance(), name);
        Delay delay = ExponentialBackOff.getInstance().getDelay(taskName);
        boolean res = delay == null || delay.isOver();
        LOGGER.info("never ever happens -> {}", res);
        return res;
      }) && !PlanUtils.assetConflicts(podInstanceRequirement, dirtyAssets);
    }
    return true;
  }

  /**
   * Returns the overall status to display by a parent element of the provided children.
   *
   * @param parentName        the name of the parent element, used for logging
   * @param childStatuses     the statuses of the parent element's children
   * @param candidateStatuses the statuses of the parent element's candidate children, as selected by the parent
   *                          element's Strategy
   * @param errors            any errors from the parent element
   * @param isInterrupted     whether the parent element is interrupted
   * @return the Status to display for the parent element
   */
  @SuppressWarnings({
      "checkstyle:CyclomaticComplexity",
      "checkstyle:LineLength",
      "checkstyle:MultipleStringLiterals"
  })
  public static Status getAggregateStatus(
      String parentName,
      Collection<Status> childStatuses,
      Collection<Status> candidateStatuses,
      Collection<String> errors,
      boolean isInterrupted)
  {
    // Ordering matters throughout this method.  Modify with care.
    // Also note that this function MUST NOT call parent.getStatus() as that creates a circular call.
    Status result;

    if (!errors.isEmpty() || anyMatch(Status.ERROR, childStatuses)) {
      result = Status.ERROR;
      LOGGER.debug("({} status={}) One or more children contain errors.", parentName, result);
    } else if (allMatch(Status.COMPLETE, childStatuses)) {
      result = Status.COMPLETE;
      LOGGER.debug("({} status={}) All children have status: {}", parentName, result, Status.COMPLETE);
    } else if (allMatch(Status.DELAYED, candidateStatuses) && allMatch(Status.DELAYED, childStatuses)) {
      result = Status.DELAYED;
      LOGGER.debug("({} status={}) All candidates and children are {}", parentName, result, Status.DELAYED);
    } else if (isInterrupted) {
      result = Status.WAITING;
      LOGGER.debug("({} status={}) Parent element is interrupted", parentName, result);
    } else if (anyMatch(Status.PREPARED, childStatuses)) {
      result = Status.IN_PROGRESS;
      LOGGER.debug("({} status={}) At least one child has status: {}", parentName, result, Status.PREPARED);
    } else if (anyMatch(Status.WAITING, candidateStatuses)) {
      result = Status.WAITING;
      LOGGER.debug("({} status={}) At least one candidate has status: {}", parentName, result, Status.WAITING);
    } else if (anyMatch(Status.IN_PROGRESS, candidateStatuses)) {
      result = Status.IN_PROGRESS;
      LOGGER.debug("({} status={}) At least one candidate has status: {}",
          parentName, result, Status.IN_PROGRESS);
    } else if (anyMatch(Status.COMPLETE, childStatuses) && anyMatch(Status.PENDING, candidateStatuses)) {
      result = Status.IN_PROGRESS;
      LOGGER.debug("({} status={}) At least one child has status '{}' and at least one candidate has status '{}'",
          parentName, result, Status.COMPLETE, Status.PENDING);
    } else if (anyMatch(Status.COMPLETE, childStatuses) && anyMatch(Status.STARTING, candidateStatuses)) {
      result = Status.IN_PROGRESS;
      LOGGER.debug("({} status={}) At least one child has status '{}' and at least one candidate has status '{}'",
          parentName, result, Status.COMPLETE, Status.STARTING);
    } else if (anyMatch(Status.COMPLETE, childStatuses) && anyMatch(Status.STARTED, candidateStatuses)) {
      result = Status.IN_PROGRESS;
      LOGGER.debug("({} status={}) At least one child has status '{}' and at least one candidate has status '{}'",
          parentName, result, Status.COMPLETE, Status.STARTED);
    } else if (anyMatch(Status.PENDING, candidateStatuses)) {
      result = Status.PENDING;
      LOGGER.debug("({} status={}) At least one candidate has status: {}", parentName, result, Status.PENDING);
    } else if (anyMatch(Status.WAITING, childStatuses)) {
      result = Status.WAITING;
      LOGGER.debug("({} status={}) At least one child has status: {}", parentName, result, Status.WAITING);
    } else if (anyMatch(Status.STARTING, candidateStatuses)) {
      result = Status.STARTING;
      LOGGER.debug("({} status={}) At least one candidate has status '{}'", parentName, result, Status.STARTING);
    } else if (anyMatch(Status.STARTED, candidateStatuses)) {
      result = Status.STARTED;
      LOGGER.debug("({} status={}) At least one candidate has status '{}'", parentName, result, Status.STARTED);
    } else {
      result = Status.ERROR;
      LOGGER.warn("({} status={}) Unexpected state. Children: {} Candidates: {}",
          parentName, result, childStatuses, candidateStatuses);
    }

    return result;
  }

  private static boolean allMatch(Status status, Collection<Status> statuses) {
    return statuses.stream().allMatch(s -> s == status);
  }

  private static boolean anyMatch(Status status, Collection<Status> statuses) {
    return statuses.stream().anyMatch(s -> s == status);
  }
}
