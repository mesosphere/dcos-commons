package com.mesosphere.sdk.api.types;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.mesosphere.sdk.scheduler.plan.Plan;
import com.mesosphere.sdk.scheduler.plan.Status;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.ReflectionToStringBuilder;

import java.util.Objects;

/**
 * Immutable JSON serialization object for a summary of a {@link Plan}.
 */
public class PlanSummaryInfo {
    private final String name;
    private final Status status;
    private final String msg;

    public static PlanSummaryInfo forPlan(final Plan plan) {
        return new PlanSummaryInfo(plan.getName(), plan.getStatus(), plan.getMessage());
    }

    @JsonCreator
    private PlanSummaryInfo(
            @JsonProperty("name") final String name,
            @JsonProperty("status") final Status status,
            @JsonProperty("message") final String msg) {
        this.name = name;
        this.status = status;
        this.msg = msg;
    }

    @JsonProperty("name")
    public String getName() {
        return name;
    }

    @JsonProperty("status")
    public Status getStatus() {
        return status;
    }

    @JsonProperty("message")
    public String getMessage() {
        return msg;
    }

    @Override
    public boolean equals(Object o) {
        return EqualsBuilder.reflectionEquals(this, o);
    }

    @Override
    public int hashCode() {
        return Objects.hash(getName(), getStatus(), getMessage());
    }

    @Override
    public String toString() {
        return ReflectionToStringBuilder.toString(this);
    }
}

