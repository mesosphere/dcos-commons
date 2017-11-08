package com.mesosphere.sdk.api.types;

import com.fasterxml.jackson.annotation.JsonProperty;

import com.mesosphere.sdk.scheduler.plan.Phase;
import com.mesosphere.sdk.scheduler.plan.Plan;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ReflectionToStringBuilder;
import com.mesosphere.sdk.scheduler.plan.Status;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Immutable JSON serialization object for a {@link Plan} which includes child {@link Phase}s.
 */
public class PlanInfo {

    private final List<PhaseInfo> phases;
    private final String strategyName;
    private final List<String> errors;
    private final Status status;

    public static PlanInfo forPlan(final Plan plan) {
        List<PhaseInfo> phaseInfos = plan.getChildren().stream()
                .map(phase -> PhaseInfo.forPhase(phase))
                .collect(Collectors.toList());

        return new PlanInfo(phaseInfos, plan.getStrategy().getName(), plan.getErrors(), plan.getStatus());
    }

    private PlanInfo(
            final List<PhaseInfo> phases, String strategyName, final List<String> errors, final Status status) {
        this.phases = phases;
        this.strategyName = strategyName;
        this.errors = errors;
        this.status = status;
    }

    @JsonProperty("phases")
    public List<PhaseInfo> getPhases() {
        return phases;
    }

    @JsonProperty("strategy")
    public String getStrategyName() {
        return strategyName;
    }

    @JsonProperty("errors")
    public List<String> getErrors() {
        return errors;
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
