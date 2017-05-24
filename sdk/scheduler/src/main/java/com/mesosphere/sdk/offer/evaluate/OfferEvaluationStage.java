package com.mesosphere.sdk.offer.evaluate;

import com.mesosphere.sdk.offer.MesosResourcePool;

/**
 * Classes implementing this interface evaluate a pod against an offer and its resources for suitability to run.
 */
public interface OfferEvaluationStage {

    /**
     * Evaluate the given offer (in the form of a {@link MesosResourcePool}, modifying the
     * deducting used resources from the pool, and creating the appropriate {@link EvaluationOutcome}.
     *
     * @param mesosResourcePool the available resources left available for this stage to consume
     * @param podInfoBuilder the grouping of all task and executor builders for the pod
     * @return an {@link EvaluationOutcome} describing whether this evaluation stage succeeded or failed, and why
     */
    EvaluationOutcome evaluate(MesosResourcePool mesosResourcePool, PodInfoBuilder podInfoBuilder);
}
