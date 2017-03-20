package com.mesosphere.sdk.scheduler.plan;

import com.google.inject.Inject;
import com.mesosphere.sdk.offer.*;
import com.mesosphere.sdk.offer.evaluate.OfferEvaluator;
import com.mesosphere.sdk.scheduler.TaskKiller;
import com.mesosphere.sdk.specification.TaskSpec;
import com.mesosphere.sdk.state.StateStore;

import org.apache.mesos.Protos;
import org.apache.mesos.Protos.Offer;
import org.apache.mesos.Protos.OfferID;
import org.apache.mesos.Protos.TaskInfo;
import org.apache.mesos.SchedulerDriver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Default deployment scheduler. See docs in {@link PlanScheduler} interface.
 */
public class DefaultPlanScheduler implements PlanScheduler {

    private static final Logger logger = LoggerFactory.getLogger(DefaultPlanScheduler.class);

    private final OfferAccepter offerAccepter;
    private final OfferEvaluator offerEvaluator;
    private final StateStore stateStore;
    private final TaskKiller taskKiller;

    @Inject
    public DefaultPlanScheduler(
            OfferAccepter offerAccepter,
            OfferEvaluator offerEvaluator,
            StateStore stateStore,
            TaskKiller taskKiller) {
        this.offerAccepter = offerAccepter;
        this.offerEvaluator = offerEvaluator;
        this.stateStore = stateStore;
        this.taskKiller = taskKiller;
    }

    @Override
    public Collection<OfferID> resourceOffers(
            final SchedulerDriver driver,
            final List<Offer> offers,
            final Collection<? extends Step> steps) {
        if (driver == null || offers == null || steps == null) {
            logger.error("Unexpected null argument(s) encountered: driver='{}' offers='{}', steps='{}'",
                    driver, offers, steps);
            return Collections.emptyList();
        }

        List<OfferID> acceptedOfferIds = new ArrayList<>();
        List<Offer> availableOffers = new ArrayList<>(offers);

        for (Step step : steps) {
            acceptedOfferIds.addAll(resourceOffers(driver, availableOffers, step));
            availableOffers = PlanUtils.filterAcceptedOffers(availableOffers, acceptedOfferIds);
        }

        return acceptedOfferIds;
    }

    private Collection<OfferID> resourceOffers(
            SchedulerDriver driver,
            List<Offer> offers,
            Step step) {

        if (driver == null || offers == null) {
            logger.error("Unexpected null argument encountered: driver='{}' offers='{}'", driver, offers);
            return Collections.emptyList();
        }

        if (step == null) {
            logger.info("Ignoring resource offers for null step.");
            return Collections.emptyList();
        }

        if (!(step.isPending() || step.isPrepared())) {
            logger.info("Ignoring resource offers for step: {} status: {}", step.getName(), step.getStatus());
            return Collections.emptyList();
        }

        logger.info("Processing resource offers for step: {}", step.getName());
        Optional<PodInstanceRequirement> podInstanceRequirementOptional = step.start();
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
        List<OfferRecommendation> recommendations = null;
        try {
            recommendations = offerEvaluator.evaluate(podInstanceRequirement, offers);
        } catch (InvalidRequirementException e) {
            logger.error("Failed generate OfferRequirement.", e);
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

        List<OfferID> acceptedOffers = offerAccepter.accept(driver, recommendations);

        // Notify step of offer outcome:
        if (acceptedOffers.isEmpty()) {
            // If no Operations occurred it may be of interest to the Step.  For example it may want to set its state
            // to Pending to ensure it will be reattempted on the next Offer cycle.
            step.updateOfferStatus(Collections.emptyList());
        } else {
            step.updateOfferStatus(getNonTransientRecommendations(recommendations));
        }

        return acceptedOffers;
    }

    private void killTasks(PodInstanceRequirement podInstanceRequirement) {
        Map<String, TaskInfo> taskInfoMap = new HashMap<>();
        stateStore.fetchTasks().forEach(taskInfo -> taskInfoMap.put(taskInfo.getName(), taskInfo));
        logger.info("Killing tasks for pod instance requirement: {}:{}",
                podInstanceRequirement.getPodInstance().getName(),
                podInstanceRequirement.getTasksToLaunch());

        List<TaskSpec> taskSpecsToLaunch = podInstanceRequirement.getPodInstance().getPod().getTasks().stream()
                .filter(taskSpec -> podInstanceRequirement.getTasksToLaunch().contains(taskSpec.getName()))
                .collect(Collectors.toList());
        logger.info("TaskSpecs to launch: {}",
                taskSpecsToLaunch.stream().map(taskSpec -> taskSpec.getName()).collect(Collectors.toList()));

        List<String> resourceSetsToConsume = taskSpecsToLaunch.stream()
                .map(taskSpec -> taskSpec.getResourceSet())
                .map(resourceSet -> resourceSet.getId())
                .collect(Collectors.toList());
        logger.info("Resource sets to consume: {}",
                podInstanceRequirement.getPodInstance().getName(),
                resourceSetsToConsume);

        List<String> tasksToKill = podInstanceRequirement.getPodInstance().getPod().getTasks().stream()
                .filter(taskSpec -> resourceSetsToConsume.contains(taskSpec.getResourceSet().getId()))
                .map(taskSpec -> TaskSpec.getInstanceName(podInstanceRequirement.getPodInstance(), taskSpec))
                .collect(Collectors.toList());
        logger.info("Tasks to kill: {}", tasksToKill);

        for (String taskName : tasksToKill) {
            TaskInfo taskInfo = taskInfoMap.get(taskName);
            if (taskInfo != null) {
                Optional<Protos.TaskStatus> taskStatusOptional = stateStore.fetchStatus(taskInfo.getName());

                Protos.TaskState state = Protos.TaskState.TASK_RUNNING;
                if (taskStatusOptional.isPresent()) {
                    state = taskStatusOptional.get().getState();
                }

                if (!CommonTaskUtils.isTerminal(state)) {
                    taskKiller.killTask(taskInfo.getTaskId(), false);
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
                if (launchOfferRecommendation.isTransient()) {
                    continue;
                }
            }

            filteredRecommendations.add(recommendation);
        }

        return filteredRecommendations;
    }
}
