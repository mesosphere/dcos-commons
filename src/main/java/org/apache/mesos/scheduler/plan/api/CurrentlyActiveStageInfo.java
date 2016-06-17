package org.apache.mesos.scheduler.plan.api;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.ReflectionToStringBuilder;
import org.apache.mesos.scheduler.plan.StageManager;
import org.apache.mesos.scheduler.plan.Status;

import java.util.List;
import java.util.Objects;

/**
 * Immutable JSON serialization object for a Stage which omits child Phases.
 */
class CurrentlyActiveStageInfo {

    private final Integer phaseCount;
    private final List<String> errors;
    private final Status status;

    @JsonCreator
    public static CurrentlyActiveStageInfo create(
            @JsonProperty("phase_count") final Integer phaseCount,
            @JsonProperty("errors") final List<String> errors,
            @JsonProperty("status") final Status status) {
        return new CurrentlyActiveStageInfo(phaseCount, errors, status);
    }

    public static CurrentlyActiveStageInfo forStage(final StageManager manager) {
        return create(manager.getStage().getPhases().size(),
                manager.getStage().getErrors(),
                manager.getStatus());
    }

    private CurrentlyActiveStageInfo(final Integer phaseCount,
                     final List<String> errors,
                     final Status status) {
        this.phaseCount = phaseCount;
        this.errors = errors;
        this.status = status;
    }

    @JsonProperty("phase_count")
    public Integer getPhaseCount() {
        return phaseCount;
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
        return Objects.hash(getPhaseCount(), getErrors(), getStatus());
    }

    @Override
    public String toString() {
        return ReflectionToStringBuilder.toString(this);
    }
}
