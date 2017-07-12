package com.mesosphere.sdk.offer.evaluate;

import com.google.protobuf.TextFormat;
import com.mesosphere.sdk.offer.DestroyOfferRecommendation;
import com.mesosphere.sdk.offer.MesosResource;
import com.mesosphere.sdk.offer.MesosResourcePool;
import org.apache.mesos.Protos;

import java.util.Arrays;

import static com.mesosphere.sdk.offer.evaluate.EvaluationOutcome.pass;

/**
 * This class destroys volumes no longer needed by a Pod.
 */
public class DestroyEvaluationStage implements OfferEvaluationStage {
    private final Protos.Resource resource;

    public DestroyEvaluationStage (Protos.Resource resource) {
        this.resource = resource;
    }

    @Override
    public EvaluationOutcome evaluate(MesosResourcePool mesosResourcePool, PodInfoBuilder podInfoBuilder) {
        return pass(
                this,
                Arrays.asList(new DestroyOfferRecommendation(mesosResourcePool.getOffer(), resource)),
                "Unreserving orphaned resource: %s",
                TextFormat.shortDebugString(resource))
                .mesosResource(new MesosResource(resource))
                .build();
    }
}
