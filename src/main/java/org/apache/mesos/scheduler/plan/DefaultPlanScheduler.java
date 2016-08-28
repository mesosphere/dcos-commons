package org.apache.mesos.scheduler.plan;

import com.google.inject.Inject;
import org.apache.mesos.Protos;
import org.apache.mesos.SchedulerDriver;
import org.apache.mesos.offer.OfferAccepter;
import org.apache.mesos.offer.OfferEvaluator;
import org.apache.mesos.offer.OfferRecommendation;
import org.apache.mesos.offer.OfferRequirement;
import org.apache.mesos.scheduler.TaskKiller;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Default scheduler. See docs in {@link StageScheduler} interface.
 */
public class DefaultPlanScheduler implements StageScheduler {

    private static final Logger logger = LoggerFactory.getLogger(DefaultPlanScheduler.class);

    private final OfferAccepter offerAccepter;
    private final OfferEvaluator offerEvaluator;
    private final TaskKiller taskKiller;

    @Inject
    public DefaultPlanScheduler(OfferAccepter offerAccepter, TaskKiller taskKiller) {
        this(offerAccepter, new OfferEvaluator(), taskKiller);
    }

    public DefaultPlanScheduler(OfferAccepter offerAccepter, OfferEvaluator offerEvaluator, TaskKiller taskKiller) {
        this.offerAccepter = offerAccepter;
        this.offerEvaluator = offerEvaluator;
        this.taskKiller = taskKiller;
    }

    @Override
    public List<Protos.OfferID> resourceOffers(
            SchedulerDriver driver, List<Protos.Offer> offers, Block block) {
        List<Protos.OfferID> acceptedOffers = new ArrayList<>();

        if (driver == null || offers == null) {
            logger.error("Unexpected null argument encountered: driver='{}' offers='{}'",
                    driver, offers);
            return acceptedOffers;
        }

        if (block == null) {
            logger.info("Ignoring resource offers for null block.");
            return acceptedOffers;
        }

        if (!block.isPending()) {
            logger.info("Ignoring resource offers for block: {} status: {}",
                    block.getName(), Block.getStatus(block));
            return acceptedOffers;
        }

        logger.info("Processing resource offers for block: {}", block.getName());
        Optional<OfferRequirement> offerRequirementOptional = block.start();
        if (!offerRequirementOptional.isPresent()) {
            logger.info("No OfferRequirement for block: {}", block.getName());
            block.updateOfferStatus(false);
            return acceptedOffers;
        }

        // Block has returned an OfferRequirement to process. Find offers which match the
        // requirement and accept them, if any are found:
        List<OfferRecommendation> recommendations = offerEvaluator.evaluate(offerRequirementOptional.get(), offers);
        if (recommendations.isEmpty()) {
            // complain that we're not finding suitable offers. out of space on the cluster?:
            logger.warn(
                    "Unable to find any offers which fulfill requirement provided by block {}: {}",
                    block.getName(), offerRequirementOptional.get());
            block.updateOfferStatus(false);
            return acceptedOffers;
        }

        acceptedOffers = offerAccepter.accept(driver, recommendations);
        // notify block of offer outcome:
        block.updateOfferStatus(!acceptedOffers.isEmpty());
        return acceptedOffers;
    }
}
