package org.apache.mesos.scheduler.plan;

import org.apache.commons.collections.CollectionUtils;
import org.apache.mesos.Protos.Offer;
import org.apache.mesos.Protos.OfferID;
import org.apache.mesos.SchedulerDriver;
import org.apache.mesos.scheduler.ChainedObserver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Default implementation of PlanCoordinator.
 *
 * A {@link DefaultPlanCoordinator} is an {@link Observable} and will forward updates from its plans.
 */
public class DefaultPlanCoordinator extends ChainedObserver implements PlanCoordinator {
    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultPlanCoordinator.class);

    private List<PlanManager> planManagers = new LinkedList<>();
    private PlanScheduler planScheduler;

    public DefaultPlanCoordinator(
            List<PlanManager> planManagers,
            PlanScheduler planScheduler) {
        if (CollectionUtils.isEmpty(planManagers)) {
            throw new IllegalArgumentException("At least one plan manager is required");
        }
        this.planManagers.addAll(planManagers);
        this.planManagers.stream().forEach(manager -> manager.subscribe(this));
        this.planScheduler = planScheduler;
    }

    @Override
    public Collection<OfferID> processOffers(
            final SchedulerDriver driver,
            final List<Offer> offersToProcess) {
        // Offers that have already been used
        final Set<OfferID> dirtiedOffers = new HashSet<>();

        // Assets that are being actively worked on
        final Set<String> dirtiedAssets = new HashSet<>();

        // Offers that are available for scheduling
        final List<Offer> offers = new ArrayList<>(offersToProcess);

        // Pro-actively determine all known dirty assets. This is used to ensure that PlanManagers that are presented
        // with offers first, does not accidentally schedule an asset that's actively being worked upon by another
        // PlanManager that is presented offers later.
        dirtiedAssets.addAll(planManagers.stream()
                .flatMap(planManager -> planManager.getDirtyAssets().stream())
                .collect(Collectors.toList()));

        LOGGER.info("Initial dirtied assets: {}", dirtiedAssets);

        for (final PlanManager planManager : planManagers) {
            try {
                LOGGER.info("Processing offers for plan: {}", planManager.getPlan().getName());

                Set<String> relevantDirtyAssets = getRelevantDirtyAssets(planManager, dirtiedAssets);
                LOGGER.info("Relevant dirtied assets: {}, for: {}",
                        relevantDirtyAssets, planManager.getPlan().getName());

                // Get candidate steps to be scheduled
                Collection<? extends Step> candidateSteps = planManager.getCandidates(relevantDirtyAssets);
                LOGGER.info("Attempting to process candidates: {}",
                        candidateSteps.stream().map(step -> step.getName()).collect(Collectors.toList()));

                // Try scheduling candidate steps using the available offers
                Collection<OfferID> usedOffers = planScheduler.resourceOffers(driver, offers, candidateSteps);

                // Collect dirtied offers
                dirtiedOffers.addAll(usedOffers);
                LOGGER.info("Updated dirtied offers: {}", dirtiedOffers);

                // Collect known dirtied assets
                dirtiedAssets.addAll(planManager.getDirtyAssets());
                LOGGER.info("Updated dirtied assets: {}", dirtiedAssets);
            } catch (Throwable t) {
                LOGGER.error(String.format("Error with plan manager: %s.", planManager), t);
            }

            // Filter out dirtied offers, and only present unused offers to the next PlanManager.
            final List<Offer> unacceptedOffers = PlanUtils.filterAcceptedOffers(offers, dirtiedOffers);
            offers.clear();
            offers.addAll(unacceptedOffers);
        }

        LOGGER.info("Total dirtied offers: {}", dirtiedOffers);
        return dirtiedOffers;
    }

    @Override
    public boolean hasOperations() {
        return planManagers.stream().anyMatch(manager -> !manager.getPlan().isComplete());
    }

    private Set<String> getRelevantDirtyAssets(PlanManager planManager, Set<String> dirtiedAssets) {
        Set<String> relevantDirtyAssets = new HashSet<>(dirtiedAssets);
        relevantDirtyAssets.removeAll(planManager.getDirtyAssets());
        return relevantDirtyAssets;
    }
}
