package org.apache.mesos.scheduler.plan;

import org.apache.commons.collections.CollectionUtils;
import org.apache.mesos.Protos;
import org.apache.mesos.SchedulerDriver;
import org.apache.mesos.offer.OfferUtils;
import org.apache.mesos.scheduler.ChainedObserver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Default implementation of PlanCoordinator.
 * <p>
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
    public Collection<Protos.OfferID> processOffers(
            final SchedulerDriver driver,
            final List<Protos.Offer> offersToProcess) {
        final Set<Protos.OfferID> dirtiedOffers = new HashSet<>();
        final Set<String> dirtiedAssets = new HashSet<>();
        final List<Protos.Offer> offers = new ArrayList<>(offersToProcess);

        // Pro-actively determine all known dirty assets.
        dirtiedAssets.addAll(planManagers.stream()
                .flatMap(planManager -> planManager.getDirtyAssets().stream())
                .collect(Collectors.toList()));

        for (final PlanManager planManager : planManagers) {
            try {
                LOGGER.info("Current PlanManager: {}. Current plan {} interrupted.",
                        planManager.getClass().getSimpleName(),
                        planManager.isInterrupted() ? "is" : "is not");
                final Optional<Block> currentBlock = planManager.getCurrentBlock(dirtiedAssets);
                if (currentBlock.isPresent()) {
                    final Block blockToSchedule = currentBlock.get();
                    LOGGER.info("Current block to schedule: {}", blockToSchedule.getName());
                    List<Protos.OfferID> usedOffers = planScheduler.resourceOffers(driver, offers, blockToSchedule);
                    dirtiedOffers.addAll(usedOffers);

                    // If an offer was used, the block was also scheduled, so let's mark it dirty.
                    if (CollectionUtils.isNotEmpty(usedOffers)) {
                        dirtiedAssets.add(blockToSchedule.getName());
                    }
                } else {
                    LOGGER.info("Current block to schedule: No block");
                }
            } catch (Throwable t) {
                LOGGER.error("Error with plan manager: {}. Reason: {}", planManager, t);
            }

            // Filter dirtied offers.
            final List<Protos.Offer> unacceptedOffers = OfferUtils.filterOutAcceptedOffers(
                    offers,
                    dirtiedOffers);
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
