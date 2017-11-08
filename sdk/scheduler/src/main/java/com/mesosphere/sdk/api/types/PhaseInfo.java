package com.mesosphere.sdk.api.types;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ReflectionToStringBuilder;
import com.mesosphere.sdk.scheduler.plan.Step;
import com.mesosphere.sdk.scheduler.plan.Phase;
import com.mesosphere.sdk.scheduler.plan.Status;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Immutable JSON serialization object for a {@link Phase} which includes child {@link Step}s.
 */
class PhaseInfo {
    private final String id;
    private final String name;
    private final List<StepInfo> steps;
    private final String strategyName;
    private final Status status;

    public static PhaseInfo forPhase(final Phase phase) {
        List<StepInfo> stepInfos = phase.getChildren().stream()
                .map(step -> StepInfo.forStep(step))
                .collect(Collectors.toList());

        return new PhaseInfo(
                phase.getId().toString(),
                phase.getName(),
                stepInfos,
                phase.getStrategy().getName(),
                phase.getStatus());
    }

    private PhaseInfo(
            final String id,
            final String name,
            final List<StepInfo> steps,
            final String strategyName,
            final Status status) {
        this.id = id;
        this.name = name;
        this.steps = steps;
        this.strategyName = strategyName;
        this.status = status;
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
