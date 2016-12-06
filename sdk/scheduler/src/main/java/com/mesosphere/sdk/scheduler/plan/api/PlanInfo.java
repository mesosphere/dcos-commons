package com.mesosphere.sdk.scheduler.plan.api;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.ReflectionToStringBuilder;
import com.mesosphere.sdk.scheduler.plan.Phase;
import com.mesosphere.sdk.scheduler.plan.PlanManager;
import com.mesosphere.sdk.scheduler.plan.Status;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Immutable JSON serialization object for a Plan which includes child Phases.
 */
class PlanInfo {

    private final List<PhaseInfo> phases;
    private final List<String> errors;
    private final Status status;

    @JsonCreator
    public static PlanInfo create(
            @JsonProperty("phases") final List<PhaseInfo> phases,
            @JsonProperty("errors") final List<String> errors,
            @JsonProperty("status") final Status status) {
        return new PlanInfo(phases, errors, status);
    }

    public static PlanInfo forPlan(final PlanManager manager) {
        List<PhaseInfo> info = new ArrayList<>(manager.getPlan().getChildren().size());
        manager.getPlan().getChildren().forEach(phase -> info.add(PhaseInfo.forPhase((Phase) phase)));

        return create(info, manager.getPlan().getErrors(), manager.getPlan().getStatus());
    }

    private PlanInfo(final List<PhaseInfo> phases, final List<String> errors, final Status status) {
        this.phases = phases;
        this.errors = errors;
        this.status = status;
    }

    @JsonProperty("phases")
    public List<PhaseInfo> getPhases() {
        return phases;
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
        return Objects.hash(getPhases(), getErrors(), getStatus());
    }

    @Override
    public String toString() {
        return ReflectionToStringBuilder.toString(this);
    }
}
