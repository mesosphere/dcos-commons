package org.apache.mesos.scheduler.plan.api;


import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.ReflectionToStringBuilder;
import org.apache.mesos.scheduler.plan.Phase;
import org.apache.mesos.scheduler.plan.PlanManager;
import org.apache.mesos.scheduler.plan.Status;

import java.util.Objects;

/**
 * Immutable JSON serialization object for a Phase which omits child Blocks.
 */
class CurrentlyActivePhaseInfo {

    private final String id;
    private final String name;
    private final Integer blockCount;
    private final Status status;

    @JsonCreator
    public static CurrentlyActivePhaseInfo create(
            @JsonProperty("id") final String id,
            @JsonProperty("name") final String name,
            @JsonProperty("block_count") final Integer blockCount,
            @JsonProperty("status") final Status status) {
        return new CurrentlyActivePhaseInfo(id, name, blockCount, status);
    }

    public static CurrentlyActivePhaseInfo forPhase(
            final Phase phase, final PlanManager planManager) {
        return create(phase.getId().toString(),
                phase.getName(),
                phase.getBlocks().size(),
                planManager.getPhaseStatus(phase.getId()));
    }

    private CurrentlyActivePhaseInfo(final String id,
                     final String name,
                     final Integer blockCount,
                     final Status status) {
        this.id = id;
        this.name = name;
        this.blockCount = blockCount;
        this.status = status;
    }

    @JsonProperty("block_count")
    public Integer getBlockCount() {
        return blockCount;
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
        return Objects.hash(getId(), getName(), getBlockCount(), getStatus());
    }

    @Override
    public String toString() {
        return ReflectionToStringBuilder.toString(this);
    }
}
