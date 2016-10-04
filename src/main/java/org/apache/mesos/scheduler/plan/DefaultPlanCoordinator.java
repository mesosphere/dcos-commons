package org.apache.mesos.scheduler.plan;

import com.google.common.annotations.VisibleForTesting;
import org.apache.commons.collections.CollectionUtils;
import org.apache.mesos.Protos;
import org.apache.mesos.SchedulerDriver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Default implementation of PlanCoordinator.
 */
public class DefaultPlanCoordinator implements PlanCoordinator {
    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultPlanCoordinator.class);

    private List<PlanManager> planManagers = new LinkedList<>();
    private PlanScheduler planScheduler;

    public DefaultPlanCoordinator(List<PlanManager> planManagers, PlanScheduler planScheduler) {
        if (CollectionUtils.isEmpty(planManagers)) {
            throw new IllegalArgumentException("Atleast one plan manager is required");
        }
        this.planManagers.addAll(planManagers);
        this.planScheduler = planScheduler;
    }

    /**
     * @return List of dirtied offers.
     */
    @Override
    public Collection<Protos.OfferID> processOffers(final SchedulerDriver driver, final List<Protos.Offer> offers) {
        final Set<Protos.OfferID> dirtiedOffers = new HashSet<>();
        for (final PlanManager planManager : planManagers) {
            // TODO(mohit): Fix dirtied assets
            final Optional<Block> currentBlock = planManager.getCurrentBlock(Arrays.asList());
            if (currentBlock.isPresent()) {
                final Block blockToSchedule = currentBlock.get();
                LOGGER.info("Current block to schedule: {}", blockToSchedule.getName());
                try {
                    dirtiedOffers.addAll(planScheduler.resourceOffers(driver, offers, blockToSchedule));
                } catch (Throwable t) {
                    LOGGER.error("Error scheduling block: {}. Reason: {}", blockToSchedule.getName(), t);
                    // Continue processing offers for other plan managers.
                }
            } else {
                LOGGER.info("Current block to schedule: No block");
                LOGGER.info("Current plan {} interrupted.", planManager.isInterrupted() ? "is" : "is not");
            }
            final List<Protos.Offer> unacceptedOffers = filterAcceptedOffers(
                    offers,
                    dirtiedOffers);
            offers.clear();
            offers.addAll(unacceptedOffers);
        }

        return dirtiedOffers;
    }

    @VisibleForTesting
    protected List<Protos.Offer> filterAcceptedOffers(List<Protos.Offer> offers,
                                                      Collection<Protos.OfferID> acceptedOfferIds) {
        return offers.stream()
                .filter(offer -> !offerAccepted(offer, acceptedOfferIds))
                .collect(Collectors.toList());
    }

    @VisibleForTesting
    protected boolean offerAccepted(Protos.Offer offer,
                                    Collection<Protos.OfferID> acceptedOfferIds) {
        return acceptedOfferIds.stream()
                .anyMatch(acceptedOfferId -> acceptedOfferId.equals(offer.getId()));
    }
}
