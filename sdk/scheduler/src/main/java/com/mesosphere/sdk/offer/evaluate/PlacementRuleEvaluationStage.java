package com.mesosphere.sdk.offer.evaluate;

import com.mesosphere.sdk.offer.MesosResourcePool;
import com.mesosphere.sdk.offer.evaluate.placement.PlacementRule;
import org.apache.mesos.Protos;

import java.util.Collection;

import static com.mesosphere.sdk.offer.evaluate.EvaluationOutcome.pass;

/**
 * This class evaluates an offer against a given {@link com.mesosphere.sdk.scheduler.plan.PodInstanceRequirement},
 * ensuring that its resources meet the constraints imposed by the supplied
 * {@link com.mesosphere.sdk.offer.evaluate.placement.PlacementRule}.
 */
public class PlacementRuleEvaluationStage implements OfferEvaluationStage {
    private final Collection<Protos.TaskInfo> deployedTasks;
    private final PlacementRule placementRule;

    public PlacementRuleEvaluationStage(Collection<Protos.TaskInfo> deployedTasks, PlacementRule placementRule) {
        this.deployedTasks = deployedTasks;
        this.placementRule = placementRule;
    }

    @Override
    public EvaluationOutcome evaluate(MesosResourcePool mesosResourcePool, PodInfoBuilder podInfoBuilder) {
        if (placementRule == null) {
            return pass(this, "No placement rule defined").build();
        }

        return placementRule.filter(
                mesosResourcePool.getOffer(),
                podInfoBuilder.getPodInstance(),
                deployedTasks);
    }
}
