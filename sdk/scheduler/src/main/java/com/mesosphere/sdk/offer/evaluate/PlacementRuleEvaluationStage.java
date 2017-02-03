package com.mesosphere.sdk.offer.evaluate;

import com.mesosphere.sdk.offer.MesosResourcePool;
import com.mesosphere.sdk.offer.OfferRequirement;
import org.apache.mesos.Protos;

import java.util.Collection;

import static com.mesosphere.sdk.offer.evaluate.EvaluationOutcome.*;

/**
 * This class evaluates an offer against a given {@link OfferRequirement}, ensuring that its resources meet the
 * constraints imposed by the supplied {@link com.mesosphere.sdk.offer.evaluate.placement.PlacementRule}.
 */
public class PlacementRuleEvaluationStage implements OfferEvaluationStage {
    private final Collection<Protos.TaskInfo> deployedTasks;

    public PlacementRuleEvaluationStage(Collection<Protos.TaskInfo> deployedTasks) {
        this.deployedTasks = deployedTasks;
    }

    @Override
    public EvaluationOutcome evaluate(MesosResourcePool mesosResourcePool, PodInfoBuilder podInfoBuilder) {
        OfferRequirement offerRequirement = podInfoBuilder.getOfferRequirement();
        if (!offerRequirement.getPlacementRuleOptional().isPresent()) {
            return pass(this, "No placement rule defined.");
        }
        return offerRequirement.getPlacementRuleOptional().get().filter(
                mesosResourcePool.getOffer(), offerRequirement, deployedTasks);
    }
}
