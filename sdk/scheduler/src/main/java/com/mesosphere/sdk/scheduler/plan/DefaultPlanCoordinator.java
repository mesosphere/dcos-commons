package com.mesosphere.sdk.scheduler.plan;

import com.mesosphere.sdk.offer.TaskUtils;
import org.apache.commons.collections.CollectionUtils;
import org.apache.mesos.Protos.Offer;
import org.apache.mesos.Protos.OfferID;
import org.apache.mesos.SchedulerDriver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Default implementation of PlanCoordinator.
 *
 * A {@link DefaultPlanCoordinator} is an {@link Observable} and will forward updates from its plans.
 */
public class DefaultPlanCoordinator implements PlanCoordinator {
    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultPlanCoordinator.class);

    private final List<PlanManager> planManagers = new LinkedList<>();
    private final PlanScheduler planScheduler;
    private final Set<String> addressedCandidates = new HashSet<>();

    public DefaultPlanCoordinator(
            List<PlanManager> planManagers,
            PlanScheduler planScheduler) {
        if (CollectionUtils.isEmpty(planManagers)) {
            throw new IllegalArgumentException("At least one plan manager is required");
        }
        this.planManagers.addAll(planManagers);
        this.planScheduler = planScheduler;
    }

    @Override
    public List<Step> getCandidates() {
        // Assets that are being actively worked on
        final Set<PodInstanceRequirement> dirtiedAssets = new HashSet<>();

        // Pro-actively determine all known dirty assets. This is used to ensure that PlanManagers that are presented
        // with offers first, does not accidentally schedule an asset that's actively being worked upon by another
        // PlanManager that is presented offers later.
        dirtiedAssets.addAll(planManagers.stream()
                .filter(planManager -> !planManager.getPlan().isInterrupted())
                .flatMap(planManager -> planManager.getDirtyAssets().stream())
                .collect(Collectors.toList()));

        LOGGER.info("Initial dirtied assets: {}", dirtiedAssets);

        List<Step> candidateSteps = new ArrayList<>();
        for (final PlanManager planManager : getPlanManagers()) {
            if (planManager.getPlan().isInterrupted()) {
                LOGGER.info("Skipping interrupted plan: {}", planManager.getPlan().getName());
                continue;
            }

            try {
                Collection<PodInstanceRequirement> relevantDirtyAssets =
                        getRelevantDirtyAssets(planManager, dirtiedAssets);
                LOGGER.info("Getting candidates for plan: '{}' with relevant dirtied assets: {}.",
                        planManager.getPlan().getName(), relevantDirtyAssets);

                // Get candidate steps to be scheduled
                candidateSteps.addAll(planManager.getCandidates(relevantDirtyAssets));

                // Collect known dirtied assets
                dirtiedAssets.addAll(planManager.getDirtyAssets());
                LOGGER.info("Updated dirtied assets: {}", dirtiedAssets);
            } catch (Throwable t) {
                LOGGER.error(String.format("Error with plan manager: %s.", planManager), t);
            }
        }

        LOGGER.info("Candidate steps: {}", candidateSteps);
        return candidateSteps;
    }

    @Override
    public synchronized Collection<OfferID> processOffers(
            final SchedulerDriver driver,
            final List<Offer> offersToProcess) {
        // Offers that have already been used
        final Set<OfferID> dirtiedOffers = new HashSet<>();

        // Assets that are being actively worked on
        final Set<PodInstanceRequirement> dirtiedAssets = new HashSet<>();

        // Offers that are available for scheduling (copy original list to allow modification below)
        final List<Offer> offers = new ArrayList<>(offersToProcess);

        List<Step> candidates = getCandidates();
        for (Step step : candidates) {
            // Try scheduling candidate steps using the available offers
            Collection<OfferID> usedOffers = planScheduler.resourceOffers(driver, offers, candidates);

            // Collect dirtied offers
            dirtiedOffers.addAll(usedOffers);
            LOGGER.info("Updated dirtied offers: {}", dirtiedOffers);

            final List<Offer> unacceptedOffers = PlanUtils.filterAcceptedOffers(offers, dirtiedOffers);
            offers.clear();
            offers.addAll(unacceptedOffers);
        }

        LOGGER.info("Total dirtied offers: {}", dirtiedOffers);
        return dirtiedOffers;
    }

    @Override
    public boolean hasOperations() {
        boolean ret = false;
        for (final PlanManager planManager : planManagers) {
            LOGGER.debug("Plan: name={} status={}",
                    planManager.getPlan().getName(),
                    planManager.getPlan().getStatus());
            ret = ret || PlanUtils.hasOperations(planManager.getPlan());
        }
        return ret;
    }

    @Override
    public Collection<PlanManager> getPlanManagers() {
        return planManagers;
    }

    private Collection<PodInstanceRequirement> getRelevantDirtyAssets(
            PlanManager planManager,
            Set<PodInstanceRequirement> dirtyAssets) {
        LOGGER.info("Input dirty assets: {}", dirtyAssets);
        LOGGER.info("Plan's dirty assets: {}", planManager.getDirtyAssets());

        Plan plan = planManager.getPlan();
        return dirtyAssets.stream()
                .filter(podInstanceRequirement -> assetIsRelevant(podInstanceRequirement, plan))
                .collect(Collectors.toList());
    }

    private boolean assetIsRelevant(PodInstanceRequirement podInstanceRequirement, Plan plan) {
        return plan.getChildren().stream()
                .flatMap(phase -> phase.getChildren().stream())
                .filter(step -> step.getPodInstanceRequirement().isPresent())
                .filter(step -> step.isInProgress())
                .map(step -> step.getPodInstanceRequirement().get())
                .filter(podRequirement -> podRequirement.conflictsWith(podInstanceRequirement))
                .count() == 0;
    }
}
