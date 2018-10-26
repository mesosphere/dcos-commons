package com.mesosphere.sdk.http.types;

import com.mesosphere.sdk.scheduler.plan.Phase;
import com.mesosphere.sdk.scheduler.plan.Status;
import com.mesosphere.sdk.scheduler.plan.Step;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ReflectionToStringBuilder;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Immutable JSON serialization object for a {@link Phase} which includes child {@link Step}s.
 */
final class PhaseInfo {
  private final String id;

  private final String name;

  private final List<StepInfo> steps;

  private final String strategyName;

  private final Status status;

  private PhaseInfo(
      final String id,
      final String name,
      final List<StepInfo> steps,
      final String strategyName,
      final Status status)
  {
    this.id = id;
    this.name = name;
    this.steps = steps;
    this.strategyName = strategyName;
    this.status = status;
  }

  public static PhaseInfo forPhase(final Phase phase) {
    // Calculate the status of the phase based on the steps, THEN generate the StepInfos for those steps.
    // This ordering is a workaround for potential inconsistency when step statuses change while we're rendering the
    // plan. By fetching step statuses after the phase status, inconsistencies should typically appear as e.g. a
    // phase that's IN_PROGRESS when the steps are COMPLETE. If we did the phase status last, then we'd risk getting
    // the opposite of that, which is less intuitive to an end user.

    // phase status first ...
    Status phaseStatus = phase.getStatus();
    // ... then steps
    List<StepInfo> stepInfos = phase.getChildren().stream()
        .map(StepInfo::forStep)
        .collect(Collectors.toList());
    return new PhaseInfo(
        phase.getId().toString(),
        phase.getName(),
        stepInfos,
        phase.getStrategy().getName(),
        phaseStatus);
  }

  @JsonProperty("steps")
  public List<StepInfo> getSteps() {
    return steps;
  }

  @JsonProperty("id")
  public String getId() {
    return id;
  }

  @JsonProperty("name")
  public String getName() {
    return name;
  }

  @JsonProperty("strategy")
  public String getStrategyName() {
    return strategyName;
  }

  @JsonProperty("status")
  public Status getStatus() {
    return status;
  }

  @Override
  public boolean equals(Object o) {
    return EqualsBuilder.reflectionEquals(this, o);
  }

  @Override
  public int hashCode() {
    return HashCodeBuilder.reflectionHashCode(this);
  }

  @Override
  public String toString() {
    return ReflectionToStringBuilder.toString(this);
  }
}
