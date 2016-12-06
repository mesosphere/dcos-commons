package com.mesosphere.sdk.scheduler.plan.api;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ReflectionToStringBuilder;
import com.mesosphere.sdk.scheduler.plan.Step;
import com.mesosphere.sdk.scheduler.plan.Status;

/**
 * Immutable JSON serialization object for a Step.
 */
class StepInfo {

    private final String id;
    private final Status status;
    private final String name;
    private final String message;

    @JsonCreator
    public static StepInfo create(
            @JsonProperty("id") final String id,
            @JsonProperty("status") final Status status,
            @JsonProperty("name") final String name,
            @JsonProperty("message") final String message) {
        return new StepInfo(id, status, name, message);
    }

    public static StepInfo forStep(final Step step) {
        return create(
                step.getId().toString(),
                step.getStatus(),
                step.getName(),
                step.getMessage());
    }

    private StepInfo(
            final String id,
            final Status status,
            final String name,
            final String message) {
        this.id = id;
        this.status = status;
        this.name = name;
        this.message = message;
    }

    @JsonProperty("id")
    public String getId() {
        return id;
    }

    @JsonProperty("message")
    public String getMessage() {
        return message;
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
        return HashCodeBuilder.reflectionHashCode(this);
    }

    @Override
    public String toString() {
        return ReflectionToStringBuilder.toString(this);
    }
}
