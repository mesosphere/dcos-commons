package com.mesosphere.sdk.offer.evaluate;

import com.mesosphere.sdk.offer.MesosResourcePool;
import com.mesosphere.sdk.offer.OfferRecommendationSlate;
import com.mesosphere.sdk.offer.OfferRequirement;
import org.apache.mesos.Protos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;

/**
 * This class evaluates an offer against a given {@link OfferRequirement}, ensuring that its resources meet the
 * constraints imposed by the supplied {@link com.mesosphere.sdk.offer.constrain.PlacementRule}.
 */
public class PlacementRuleEvaluationStage implements OfferEvaluationStage {
    private static final Logger logger = LoggerFactory.getLogger(PlacementRuleEvaluationStage.class);

    private final Collection<Protos.TaskInfo> deployedTasks;

    public PlacementRuleEvaluationStage(Collection<Protos.TaskInfo> deployedTasks) {
        this.deployedTasks = deployedTasks;
    }

    @Override
    public void evaluate(
            MesosResourcePool mesosResourcePool,
            OfferRequirement offerRequirement,
            OfferRecommendationSlate offerRecommendationSlate) throws OfferEvaluationException {
        if (!offerRequirement.getPlacementRuleOptional().isPresent()) {
            return;
        }

        Protos.Offer originalOffer = mesosResourcePool.getOffer();
        Protos.Offer filteredOffer = offerRequirement.getPlacementRuleOptional().get().filter(
                mesosResourcePool.getOffer(), offerRequirement, deployedTasks);
        mesosResourcePool.update(filteredOffer);

        int originalCount = originalOffer.getResourcesCount();
        int filteredCount = filteredOffer.getResourcesCount();

        if (filteredCount == originalCount) {
            logger.info(
                    "- Fully passed placement constraint, {} resources remain for evaluation: {}",
                    filteredCount, filteredOffer.getId().getValue());
        } else if (filteredCount > 0) {
            logger.info(
                    "- {}: Partially passed placement constraint, {} of {} resources remain for evaluation: {}",
                    filteredCount, originalCount, filteredOffer.getId().getValue());
        } else {
            throw new OfferEvaluationException(String.format(
                    "Failed placement constraint for all %s resources, removed from resource evaluation",
                    originalCount));
        }
    }
}
