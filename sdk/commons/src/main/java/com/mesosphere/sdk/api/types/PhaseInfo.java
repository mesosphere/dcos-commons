package com.mesosphere.sdk.api.types;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.ReflectionToStringBuilder;
import com.mesosphere.sdk.scheduler.plan.Step;
import com.mesosphere.sdk.scheduler.plan.Phase;
import com.mesosphere.sdk.scheduler.plan.Status;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Immutable JSON serialization object for a {@link Phase} which includes child {@link Step}s.
 */
class PhaseInfo {

    private final String id;
    private final String name;
    private final List<StepInfo> steps;
    private final Status status;

    @JsonCreator
    public static PhaseInfo create(
            @JsonProperty("id") final String id,
            @JsonProperty("name") final String name,
            @JsonProperty("steps") final List<StepInfo> steps,
            @JsonProperty("status") final Status status) {
        return new PhaseInfo(id, name, steps, status);
    }

    public static PhaseInfo forPhase(final Phase phase) {
        List<StepInfo> info = new ArrayList<>();
        List<Step> steps = phase.getChildren();
        steps.forEach(step -> info.add(StepInfo.forStep(step)));

        return create(phase.getId().toString(),
                phase.getName(),
                info,
                phase.getStatus());
    }

    private PhaseInfo(
            final String id, final String name, final List<StepInfo> steps, final Status status) {
        this.id = id;
        this.name = name;
        this.steps = steps;
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
        return Objects.hash(getId(), getName(), getSteps(), getStatus());
    }

    @Override
    public String toString() {
        return ReflectionToStringBuilder.toString(this);
    }
}
