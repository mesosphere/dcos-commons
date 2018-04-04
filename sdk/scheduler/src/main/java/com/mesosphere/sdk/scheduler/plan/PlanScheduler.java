package com.mesosphere.sdk.scheduler.plan;

import com.mesosphere.sdk.framework.TaskKiller;
import com.mesosphere.sdk.offer.*;
import com.mesosphere.sdk.offer.evaluate.OfferEvaluator;
import com.mesosphere.sdk.specification.TaskSpec;
import com.mesosphere.sdk.state.StateStore;

import org.apache.mesos.Protos;
import org.slf4j.Logger;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Attempts to start {@link Step}s, while fulfilling any {@link PodInstanceRequirement}s they provide.
 */
public class PlanScheduler {

    private static final Logger LOGGER = LoggingUtils.getLogger(PlanScheduler.class);

    private final OfferEvaluator offerEvaluator;
    private final StateStore stateStore;

    public PlanScheduler(OfferEvaluator offerEvaluator, StateStore stateStore) {
        this.offerEvaluator = offerEvaluator;
        this.stateStore = stateStore;
    }

    /**
     * Processes the provided {@code Offer}s against the provided pending {@link Step}s.
     *
     * @return a list of zero or more of the provided offers which were accepted to fulfill offer
     *         requirements returned by the {@link Step}
     */
    public List<OfferRecommendation> resourceOffers(
            final Collection<Protos.Offer> offers, final Collection<? extends Step> steps) {
        List<OfferRecommendation> allRecommendations = new ArrayList<>();
        List<Protos.Offer> availableOffers = new ArrayList<>(offers);

        for (Step step : steps) {
            List<OfferRecommendation> stepRecommendations = resourceOffers(availableOffers, step);
            allRecommendations.addAll(stepRecommendations);

            // Remove the consumed offers from the list of available offers
            Set<Protos.OfferID> usedOfferIds = stepRecommendations.stream()
                    .map(rec -> rec.getOffer().getId())
                    .collect(Collectors.toSet());
            availableOffers = availableOffers.stream()
                    .filter(offer -> !usedOfferIds.contains(offer.getId()))
                    .collect(Collectors.toList());
        }

        return allRecommendations;
    }

    private List<OfferRecommendation> resourceOffers(List<Protos.Offer> offers, Step step) {
        if (!(step.isPending() || step.isPrepared())) {
            LOGGER.info("Ignoring resource offers for step: {} status: {}", step.getName(), step.getStatus());
            return Collections.emptyList();
        }

        LOGGER.info("Processing resource offers for step: {}", step.getName());
        Optional<PodInstanceRequirement> podInstanceRequirementOptional = step.start();
        if (!podInstanceRequirementOptional.isPresent()) {
            LOGGER.info("No PodInstanceRequirement for step: {}", step.getName());
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
            LOGGER.error("Failed generate OfferRecommendations.", e);
            return Collections.emptyList();
        }

        if (recommendations.isEmpty()) {
            // Log that we're not finding suitable offers, possibly due to insufficient resources.
            LOGGER.warn(
                    "Unable to find any offers which fulfill requirement provided by step {}: {}",
                    step.getName(), podInstanceRequirement);
            step.updateOfferStatus(Collections.emptyList());
            return Collections.emptyList();
        }

        // Notify step of offer outcome:
        // If no Operations occurred it may still be of interest to the Step.  For example it may want to set its state
        // to Pending to ensure it will be reattempted on the next Offer cycle.
        step.updateOfferStatus(getNonTransientRecommendations(recommendations));

        return recommendations;
    }

    private void killTasks(PodInstanceRequirement podInstanceRequirement) {
        Map<String, Protos.TaskInfo> taskInfoMap = new HashMap<>();
        stateStore.fetchTasks().forEach(taskInfo -> taskInfoMap.put(taskInfo.getName(), taskInfo));
        LOGGER.info("Killing tasks for pod instance requirement: {}:{}",
                podInstanceRequirement.getPodInstance().getName(),
                podInstanceRequirement.getTasksToLaunch());

        List<TaskSpec> taskSpecsToLaunch = podInstanceRequirement.getPodInstance().getPod().getTasks().stream()
                .filter(taskSpec -> podInstanceRequirement.getTasksToLaunch().contains(taskSpec.getName()))
                .collect(Collectors.toList());
        LOGGER.info("TaskSpecs to launch: {}",
                taskSpecsToLaunch.stream().map(taskSpec -> taskSpec.getName()).collect(Collectors.toList()));

        List<String> resourceSetsToConsume = taskSpecsToLaunch.stream()
                .map(taskSpec -> taskSpec.getResourceSet())
                .map(resourceSet -> resourceSet.getId())
                .collect(Collectors.toList());
        LOGGER.info("Resource sets to consume: {}",
                podInstanceRequirement.getPodInstance().getName(),
                resourceSetsToConsume);

        List<String> tasksToKill = podInstanceRequirement.getPodInstance().getPod().getTasks().stream()
                .filter(taskSpec -> resourceSetsToConsume.contains(taskSpec.getResourceSet().getId()))
                .map(taskSpec -> TaskSpec.getInstanceName(podInstanceRequirement.getPodInstance(), taskSpec))
                .collect(Collectors.toList());
        LOGGER.info("Tasks to kill: {}", tasksToKill);

        for (String taskName : tasksToKill) {
            Protos.TaskInfo taskInfo = taskInfoMap.get(taskName);
            if (taskInfo != null) {
                Optional<Protos.TaskStatus> taskStatusOptional = stateStore.fetchStatus(taskInfo.getName());

                Protos.TaskState state = Protos.TaskState.TASK_RUNNING;
                if (taskStatusOptional.isPresent()) {
                    state = taskStatusOptional.get().getState();
                }

                if (!TaskUtils.isTerminal(state)) {
                    TaskKiller.killTask(taskInfo.getTaskId());
                }
            }
        }
    }

    /**
     * Returns all non-transient recommendations which will actually be executed by Mesos.
     */
    private static Collection<OfferRecommendation> getNonTransientRecommendations(
            Collection<OfferRecommendation> recommendations) {

        List<OfferRecommendation> filteredRecommendations = new ArrayList<>();

        for (OfferRecommendation recommendation : recommendations) {
            if (recommendation instanceof LaunchOfferRecommendation)  {
                LaunchOfferRecommendation launchOfferRecommendation = (LaunchOfferRecommendation) recommendation;
                if (!launchOfferRecommendation.shouldLaunch()) {
                    continue;
                }
            }

            filteredRecommendations.add(recommendation);
        }

        return filteredRecommendations;
    }
}
