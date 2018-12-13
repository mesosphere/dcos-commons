package com.mesosphere.sdk.offer.evaluate.placement;

import com.mesosphere.sdk.offer.evaluate.EvaluationOutcome;
import com.mesosphere.sdk.specification.PodInstance;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.apache.mesos.Protos;

import java.util.Collection;
import java.util.Collections;

/**
 * This rule enforces that a task be placed on the specified region, or enforces that the task
 * avoid that region.
 */
public class RegionRule extends StringMatcherRule {

  @JsonCreator
  protected RegionRule(@JsonProperty("matcher") StringMatcher matcher) {
    super("RegionRule", matcher);
  }

  @Override
  public Collection<String> getKeys(Protos.Offer offer) {
    if (offer.hasDomain() && offer.getDomain().hasFaultDomain()) {
      return Collections.singletonList(offer.getDomain().getFaultDomain().getRegion().getName());
    }

    return Collections.emptyList();
  }

  @Override
  public EvaluationOutcome filter(
      Protos.Offer offer,
      PodInstance podInstance,
      Collection<Protos.TaskInfo> tasks)
  {
    if (isAcceptable(offer, podInstance, tasks)) {
      return EvaluationOutcome.pass(
          this,
          "Offer region matches pattern: '%s'",
          getMatcher().toString())
          .build();
    } else {
      return EvaluationOutcome
          .fail(
              this,
              "Offer region didn't match pattern: '%s'", getMatcher().toString()
          )
          .build();
    }
  }

  @Override
  public Collection<PlacementField> getPlacementFields() {
    return Collections.singletonList(PlacementField.REGION);
  }
}
