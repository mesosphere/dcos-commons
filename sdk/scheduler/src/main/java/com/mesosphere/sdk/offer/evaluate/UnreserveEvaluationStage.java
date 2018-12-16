package com.mesosphere.sdk.offer.evaluate;

import com.mesosphere.sdk.offer.MesosResource;
import com.mesosphere.sdk.offer.MesosResourcePool;
import com.mesosphere.sdk.offer.UnreserveOfferRecommendation;

import com.google.protobuf.TextFormat;
import org.apache.mesos.Protos;

import java.util.Collections;

/**
 * This class unreserves resources no longer needed by a Pod and makes them available for consumption again in the
 * {@link MesosResourcePool}.
 */
public class UnreserveEvaluationStage implements OfferEvaluationStage {
  private final Protos.Resource resource;

  public UnreserveEvaluationStage(Protos.Resource resource) {
    this.resource = resource;
  }

  @Override
  public EvaluationOutcome evaluate(
      MesosResourcePool mesosResourcePool,
      PodInfoBuilder podInfoBuilder)
  {
    mesosResourcePool.free(new MesosResource(resource));
    return EvaluationOutcome.pass(
        this,
        Collections.singletonList(
            new UnreserveOfferRecommendation(mesosResourcePool.getOffer(), resource)
        ),
        "Unreserving orphaned resource: %s",
        TextFormat.shortDebugString(resource))
        .build();
  }
}
