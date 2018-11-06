package com.mesosphere.sdk.scheduler.plan;

import com.mesosphere.sdk.framework.TaskKiller;
import com.mesosphere.sdk.offer.InvalidRequirementException;
import com.mesosphere.sdk.offer.LoggingUtils;
import com.mesosphere.sdk.offer.OfferRecommendation;
import com.mesosphere.sdk.offer.TaskUtils;
import com.mesosphere.sdk.offer.evaluate.OfferEvaluator;
import com.mesosphere.sdk.specification.TaskSpec;
import com.mesosphere.sdk.state.StateStore;
import org.apache.mesos.Protos;
import org.slf4j.Logger;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Attempts to start {@link Step}s, while fulfilling any {@link PodInstanceRequirement}s they provide.
 */
public class PlanScheduler {

  private final Logger logger;

  private final OfferEvaluator offerEvaluator;

  private final StateStore stateStore;

  public PlanScheduler(OfferEvaluator offerEvaluator, StateStore stateStore, Optional<String> namespace) {
    this.logger = LoggingUtils.getLogger(getClass(), namespace);
    this.offerEvaluator = offerEvaluator;
    this.stateStore = stateStore;
  }

  /**
   * Processes the provided {@code Offer}s against the provided pending {@link Step}s.
   *
   * @return a list of zero or more of the provided offers which were accepted to fulfill offer
   * requirements returned by the {@link Step}
   */
  public List<OfferRecommendation> resourceOffers(
      final Collection<Protos.Offer> offers, final Collection<? extends Step> steps)
  {
    List<OfferRecommendation> allRecommendations = new ArrayList<>();
    List<Protos.Offer> availableOffers = new ArrayList<>(offers);

    for (Step step : steps) {
      List<OfferRecommendation> stepRecommendations = resourceOffers(availableOffers, step);
      allRecommendations.addAll(stepRecommendations);

      // Remove the consumed offers from the list of available offers
      Set<Protos.OfferID> usedOfferIds = stepRecommendations.stream()
          .map(rec -> rec.getOfferId())
          .collect(Collectors.toSet());
      availableOffers = availableOffers.stream()
          .filter(offer -> !usedOfferIds.contains(offer.getId()))
          .collect(Collectors.toList());
    }

    return allRecommendations;
  }

  private List<OfferRecommendation> resourceOffers(List<Protos.Offer> offers, Step step) {
    if (!(step.isPending() || step.isPrepared())) {
      logger.info("Ignoring resource offers for step: {} status: {}", step.getName(), step.getStatus());
      return Collections.emptyList();
    }

    logger.info("Processing resource offers for step: {}", step.getName());
    step.start();
    Optional<PodInstanceRequirement> podInstanceRequirementOptional = step.getPodInstanceRequirement();
    if (!podInstanceRequirementOptional.isPresent()) {
      logger.info("No PodInstanceRequirement for step: {}", step.getName());
      step.updateOfferStatus(Collections.emptyList());
      return Collections.emptyList();
    }

    PodInstanceRequirement podInstanceRequirement = podInstanceRequirementOptional.get();
    // It is harmless to attempt to kill tasks which have never been launched.  This call attempts to Kill all Tasks
    // with a Task name which is equivalent to that expressed by the OfferRequirement.  If no such Task is currently
    // running no operation occurs.
    killTasks(podInstanceRequirement);

    // Step has returned an OfferRequirement to process. Find offers which match the
    // requirement and accept them, if any are found:
    final List<OfferRecommendation> recommendations;
    try {
      recommendations = offerEvaluator.evaluate(podInstanceRequirement, offers);
    } catch (InvalidRequirementException | IOException e) {
      logger.error("Failed generate OfferRecommendations.", e);
      return Collections.emptyList();
    }

    if (recommendations.isEmpty()) {
      // Log that we're not finding suitable offers, possibly due to insufficient resources.
      logger.warn(
          "Unable to find any offers which fulfill requirement provided by step {}: {}",
          step.getName(), podInstanceRequirement);
      step.updateOfferStatus(Collections.emptyList());
      return Collections.emptyList();
    }

    // Notify step of offer outcome:
    // If no Operations occurred it may still be of interest to the Step.  For example it may want to set its state
    // to Pending to ensure it will be reattempted on the next Offer cycle.
    step.updateOfferStatus(recommendations.stream()
        // Only include recommendations for operations that are actually sent to Mesos.
        .filter(rec -> rec.getOperation().isPresent())
        .collect(Collectors.toList()));

    return recommendations;
  }

  private void killTasks(PodInstanceRequirement podInstanceRequirement) {

    Set<String> resourceSetsToConsume = podInstanceRequirement.getPodInstance().getPod().getTasks().stream()
        .filter(taskSpec -> podInstanceRequirement.getTasksToLaunch().contains(taskSpec.getName()))
        .map(taskSpec -> taskSpec.getResourceSet().getId())
        .collect(Collectors.toSet());
    List<String> tasksToKill = podInstanceRequirement.getPodInstance().getPod().getTasks().stream()
        .filter(taskSpec -> resourceSetsToConsume.contains(taskSpec.getResourceSet().getId()))
        .map(taskSpec -> TaskSpec.getInstanceName(podInstanceRequirement.getPodInstance(), taskSpec))
        .collect(Collectors.toList());
    logger.info("Killing {} for pod instance requirement {}:{}, with resource sets to consume {}",
        tasksToKill,
        podInstanceRequirement.getPodInstance().getName(),
        podInstanceRequirement.getTasksToLaunch(),
        resourceSetsToConsume,
        tasksToKill);

    for (String taskName : tasksToKill) {
      Optional<Protos.TaskInfo> taskInfo = stateStore.fetchTask(taskName);
      if (!taskInfo.isPresent()) {
        // No TaskInfo at all. This should (only) be the case when the service is first being deployed.
        // Avoid sending out kill requests for tasks that never existed: there's no ID regardless
        logger.info("Skipping kill request for {}: no TaskInfo found, new task?", taskName);
        continue;
      }

      Optional<Protos.TaskStatus> taskStatusOptional = stateStore.fetchStatus(taskName);
      if (!taskStatusOptional.isPresent()) {
        // Couldn't find status, which shouldn't happen in practice. Just issue a kill request regardless.
        TaskKiller.killTask(taskInfo.get().getTaskId());
      } else if (TaskUtils.isTerminal(taskStatusOptional.get())) {
        logger.info("Skipping kill request for {}: already in terminal state {}",
            taskName, taskStatusOptional.get().getState());
      } else {
        // Task isn't already in terminal state. Issue a kill request.
        TaskKiller.killTask(taskInfo.get().getTaskId());
      }
    }
  }
}
