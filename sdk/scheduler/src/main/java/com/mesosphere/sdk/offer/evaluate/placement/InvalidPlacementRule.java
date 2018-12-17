package com.mesosphere.sdk.offer.evaluate.placement;

import com.mesosphere.sdk.offer.evaluate.EvaluationOutcome;
import com.mesosphere.sdk.specification.PodInstance;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.mesos.Protos.Offer;
import org.apache.mesos.Protos.TaskInfo;

import java.util.Collection;
import java.util.Collections;

/**
 * An implementation of a {@PlacementRule} that is ALWAYS invalid.
 */
public class InvalidPlacementRule implements PlacementRule {
  private final String constraints;

  private final String exception;

  @JsonCreator
  public InvalidPlacementRule(
      @JsonProperty("constraints ") String constraints,
      @JsonProperty("exception") String exception)
  {
    this.constraints = constraints;
    this.exception = exception;
  }

  @Override
  public EvaluationOutcome filter(
      Offer offer,
      PodInstance podInstance,
      Collection<TaskInfo> tasks)
  {
    return EvaluationOutcome
        .fail(
            this,
            String.format(
                "Invalid placement constraints for %s: %s",
                podInstance.getName(),
                constraints
            )
        ).build();
  }

  @Override
  public String toString() {
    return String.format(
        "InvalidPlacementRule{constraints=%s, exception=%s}",
        constraints,
        exception
    );
  }

  @Override
  public Collection<PlacementField> getPlacementFields() {
    return Collections.emptyList();
  }

  @Override
  public boolean equals(Object o) {
    return EqualsBuilder.reflectionEquals(this, o);
  }

  @JsonProperty("constraints")
  public String getConstraints() {
    return constraints;
  }

  @JsonProperty("exception")
  public String getException() {
    return exception;
  }

  @Override
  public int hashCode() {
    return HashCodeBuilder.reflectionHashCode(this);
  }

}
