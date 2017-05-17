package com.mesosphere.sdk.offer;

import com.mesosphere.sdk.offer.evaluate.OfferEvaluationStage;

import java.util.Optional;

/**
 * Created by gabriel on 5/17/17.
 */
public interface MesosRequirement {
    OfferEvaluationStage getEvaluationStage(String taskName);
    Optional<MesosResource> satisfy(MesosResourcePool pool);
}
