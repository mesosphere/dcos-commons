package com.mesosphere.sdk.offer.evaluate.placement;

import com.mesosphere.sdk.offer.evaluate.EvaluationOutcome;
import com.mesosphere.sdk.specification.PodInstance;

import com.fasterxml.jackson.annotation.JsonCreator;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.mesos.Protos.Offer;
import org.apache.mesos.Protos.TaskInfo;

import java.util.Collection;
import java.util.Collections;

/**
 * A no-op rule which allows all offers.
 */
public class PassthroughRule implements PlacementRule {

  @JsonCreator
  public PassthroughRule() {
  }

  @Override
  public EvaluationOutcome filter(
      Offer offer,
      PodInstance podInstance,
      Collection<TaskInfo> tasks)
  {
    return EvaluationOutcome.pass(this, "Passthrough rule always passes.").build();
  }

  @Override
  public Collection<PlacementField> getPlacementFields() {
    return Collections.emptyList();
  }

  @Override
  public String toString() {
    return "PassthroughRule{}";
  }

  @Override
  public boolean equals(Object o) {
    return EqualsBuilder.reflectionEquals(this, o);
  }

  @Override
  public int hashCode() {
    return HashCodeBuilder.reflectionHashCode(this);
  }
}
