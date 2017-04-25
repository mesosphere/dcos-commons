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
    private final Status status;
    private final String msg;

    public static PlanSummaryInfo forPlan(final Plan plan) {
        return new PlanSummaryInfo(plan.getStatus(), plan.getMessage());
    }

    @JsonCreator
    private PlanSummaryInfo(
            @JsonProperty("status") final Status status,
            @JsonProperty("message") final String msg) {
        this.status = status;
        this.msg = msg;
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
        return Objects.hash(getStatus(), getMessage());
    }

    @Override
    public String toString() {
        return ReflectionToStringBuilder.toString(this);
    }
}

