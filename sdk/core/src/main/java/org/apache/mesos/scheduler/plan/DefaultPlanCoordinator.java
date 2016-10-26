package org.apache.mesos.scheduler.plan;

import org.apache.commons.collections.CollectionUtils;
import org.apache.mesos.Protos.Offer;
import org.apache.mesos.Protos.OfferID;
import org.apache.mesos.SchedulerDriver;
import org.apache.mesos.scheduler.ChainedObserver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * Default implementation of PlanCoordinator.
 *
 * A {@DefaultPlanCoordinator} is an {@Observable} and will forward updates from its plans.
 */
public class DefaultPlanCoordinator extends ChainedObserver implements PlanCoordinator {
    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultPlanCoordinator.class);

    private List<PlanManager> planManagers = new LinkedList<>();
    private PlanScheduler planScheduler;

    public DefaultPlanCoordinator(
            List<PlanManager> planManagers,
            PlanScheduler planScheduler) {
        if (CollectionUtils.isEmpty(planManagers)) {
            throw new IllegalArgumentException("Atleast one plan manager is required");
        }
        this.planManagers.addAll(planManagers);
        this.planManagers.stream().forEach(manager -> manager.subscribe(this));
        this.planScheduler = planScheduler;
    }

    @Override
    public Collection<OfferID> processOffers(
            final SchedulerDriver driver,
            final List<Offer> offersToProcess) {

        final Set<OfferID> dirtiedOffers = new HashSet<>();
        final Set<String> dirtiedAssets = new HashSet<>();
        final List<Offer> offers = new ArrayList<>(offersToProcess);

        for (final PlanManager planManager : planManagers) {
            try {
                Collection<? extends Block> candidateBlocks = planManager.getCandidates(dirtiedAssets);
                Collection<OfferID> usedOffers = planScheduler.resourceOffers(driver, offers, candidateBlocks);
                dirtiedOffers.addAll(usedOffers);

                // If a Block is InProgress let's mark it dirty.
                for (Block block : candidateBlocks) {
                    if (block.isInProgress()) {
                        dirtiedAssets.add(block.getName());
                    }
                }
            } catch (Throwable t) {
                LOGGER.error("Error with plan manager: {}. Reason: {}", planManager, t);
            }

            // Filter dirtied offers.
            final List<Offer> unacceptedOffers = PlanUtils.filterAcceptedOffers(offers, dirtiedOffers);
            offers.clear();
            offers.addAll(unacceptedOffers);
        }

        return dirtiedOffers;
    }

    @Override
    public boolean hasOperations() {
        return planManagers.stream().anyMatch(manager -> !manager.getPlan().isComplete());
    }
}
