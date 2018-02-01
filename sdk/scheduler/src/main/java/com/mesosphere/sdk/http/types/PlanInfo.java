package com.mesosphere.sdk.http.types;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.mesosphere.sdk.scheduler.plan.Phase;
import com.mesosphere.sdk.scheduler.plan.Plan;
import com.mesosphere.sdk.scheduler.plan.Status;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ReflectionToStringBuilder;

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
        // Calculate the status of the plan based on the phases, THEN generate the PhaseInfos for those phases.
        // This ordering is a workaround for potential inconsistency when step statuses change while we're rendering the
        // plan. By fetching phase statuses after the plan status, inconsistencies should typically appear as e.g. a
        // plan that's IN_PROGRESS when the steps or phases are COMPLETE. If we did the plan status last, then we'd risk
        // getting the opposite of that, which is less intuitive to an end user.
        Status planStatus = plan.getStatus(); // plan status first ...
        List<PhaseInfo> phaseInfos = plan.getChildren().stream() // ... then phases
                .map(phase -> PhaseInfo.forPhase(phase))
                .collect(Collectors.toList());
        return new PlanInfo(phaseInfos, plan.getStrategy().getName(), plan.getErrors(), planStatus);
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
