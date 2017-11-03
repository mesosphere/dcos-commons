package com.mesosphere.sdk.api.types;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ReflectionToStringBuilder;
import com.mesosphere.sdk.scheduler.plan.Step;

/**
 * Immutable JSON serialization object for a {@link Step}.
 */
class StepInfo {
    private final String id;
    private final String status;
    private final String name;
    private final String message;

    public static StepInfo forStep(final Step step) {
        return new StepInfo(
                step.getId().toString(),
                step.getDisplayStatus(),
                step.getName(),
                step.getMessage());
    }

    private StepInfo(final String id, final String status, final String name, final String message) {
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
    public String getStatus() {
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
