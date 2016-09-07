package org.apache.mesos.scheduler.plan.api;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.ReflectionToStringBuilder;
import org.apache.mesos.scheduler.plan.Block;
import org.apache.mesos.scheduler.plan.Phase;
import org.apache.mesos.scheduler.plan.PlanManager;

import java.util.Objects;
import java.util.Optional;

/**
 * Immutable JSON serialization object for the Active Plan Status.
 */
class CurrentlyActiveInfo {

    private final BlockInfo block;
    private final CurrentlyActivePhaseInfo phaseStatus;
    private final CurrentlyActivePlanInfo planStatus;

    @JsonCreator
    public static CurrentlyActiveInfo create(
            @JsonProperty("block") final Object block,
            @JsonProperty("phase") final Object phase,
            @JsonProperty("plan") final CurrentlyActivePlanInfo plan) {
        BlockInfo blockInfo = block instanceof BlockInfo ? (BlockInfo) block : null;
        CurrentlyActivePhaseInfo phaseInfo =
            phase instanceof CurrentlyActivePhaseInfo ? (CurrentlyActivePhaseInfo) phase : null;
        return new CurrentlyActiveInfo(blockInfo, phaseInfo, plan);
    }

    public static CurrentlyActiveInfo forPlan(final PlanManager manager) {
        Optional<Block> activeBlockOptional = manager.getCurrentBlock();
        Optional<Phase> activePhaseOptional = manager.getCurrentPhase();
        return create(
            activeBlockOptional.isPresent() ? BlockInfo.forBlock(activeBlockOptional.get(), manager) : null,
            activePhaseOptional.isPresent() ?
                    CurrentlyActivePhaseInfo.forPhase(activePhaseOptional.get(), manager) :
                    null,
            CurrentlyActivePlanInfo.forStage(manager));
    }

    private CurrentlyActiveInfo(final BlockInfo block,
                     final CurrentlyActivePhaseInfo phaseStatus,
                     final CurrentlyActivePlanInfo planStatus) {
        this.block = block;
        this.phaseStatus = phaseStatus;
        this.planStatus = planStatus;
    }

    @JsonInclude(JsonInclude.Include.NON_NULL) // omit field when null/COMPLETE
    @JsonProperty("block")
    public BlockInfo getBlock() {
      return block;
    }

    @JsonInclude(JsonInclude.Include.NON_NULL) // omit field when null/COMPLETE
    @JsonProperty("phase")
    public CurrentlyActivePhaseInfo getPhaseStatus() {
      return phaseStatus;
    }

    @JsonProperty("plan")
    public CurrentlyActivePlanInfo getPlanStatus() {
      return planStatus;
    }

    @Override
    public boolean equals(Object o) {
        return EqualsBuilder.reflectionEquals(this, o);
    }

    @Override
    public int hashCode() {
        return Objects.hash(getBlock(), getPhaseStatus(), getPlanStatus());
    }

    @Override
    public String toString() {
        return ReflectionToStringBuilder.toString(this);
    }
}
