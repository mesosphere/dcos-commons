package com.mesosphere.sdk.offer.evaluate;

import com.mesosphere.sdk.offer.MesosResourcePool;
import com.mesosphere.sdk.offer.OfferRecommendationSlate;
import com.mesosphere.sdk.offer.OfferRequirement;

/**
 * Classes implementing this interface evaluate a pod against an offer and its resources for suitability to run.
 */
public interface OfferEvaluationStage {
    /**
     * Evaluate the given offer (in the form of a {@link MesosResourcePool}, modifying the
     * {@link org.apache.mesos.Protos.TaskInfo}s and {@link org.apache.mesos.Protos.ExecutorInfo} in the given
     * {@link OfferRequirement} where appropriate to ensure they have necessary metadata (e.g. a populated environment
     * variable), deducting used resources from the pool, and creating the appropriate
     * {@link com.mesosphere.sdk.offer.OfferRecommendation} in the given {@link OfferRecommendationSlate} if relevant.
     * @param mesosResourcePool the available resources left available for this stage to consume
     * @param offerRequirement the tasks and executor to launch, with their associated resource requirements
     * @param offerRecommendationSlate the current set of all {@link com.mesosphere.sdk.offer.OfferRecommendation}s
     *                                 for this offer
     * @throws OfferEvaluationException
     */
    void evaluate(
            MesosResourcePool mesosResourcePool,
            OfferRequirement offerRequirement,
            OfferRecommendationSlate offerRecommendationSlate) throws OfferEvaluationException;
}
