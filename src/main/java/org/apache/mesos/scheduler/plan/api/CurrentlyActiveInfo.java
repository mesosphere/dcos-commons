package org.apache.mesos.scheduler.plan.api;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import org.apache.mesos.scheduler.plan.Block;
import org.apache.mesos.scheduler.plan.Phase;
import org.apache.mesos.scheduler.plan.StageManager;
import java.util.Objects;

/**
 * Immutable JSON serialization object for the Active Stage Status.
 */
class CurrentlyActiveInfo {

    private final BlockInfo block;
    private final CurrentlyActivePhaseInfo phaseStatus;
    private final CurrentlyActiveStageInfo stageStatus;

    @JsonCreator
    public static CurrentlyActiveInfo create(
            @JsonProperty("block") final Object block,
            @JsonProperty("phase") final Object phase,
            @JsonProperty("stage") final CurrentlyActiveStageInfo stage) {
        BlockInfo blockInfo = block instanceof BlockInfo ? (BlockInfo) block : null;
        CurrentlyActivePhaseInfo phaseInfo =
            phase instanceof CurrentlyActivePhaseInfo ? (CurrentlyActivePhaseInfo) phase : null;
        return new CurrentlyActiveInfo(blockInfo, phaseInfo, stage);
    }

    public static CurrentlyActiveInfo forStage(final StageManager manager) {
        Block activeBlock = manager.getCurrentBlock();
        Phase activePhase = manager.getCurrentPhase();
        return create(
            (activeBlock != null) ? BlockInfo.forBlock(activeBlock, manager) : null,
            (activePhase != null) ? CurrentlyActivePhaseInfo.forPhase(activePhase, manager) : null,
            CurrentlyActiveStageInfo.forStage(manager));
    }

    public CurrentlyActiveInfo(final BlockInfo block,
                     final CurrentlyActivePhaseInfo phaseStatus,
                     final CurrentlyActiveStageInfo stageStatus) {
        this.block = block;
        this.phaseStatus = phaseStatus;
        this.stageStatus = stageStatus;
    }

    @JsonInclude(JsonInclude.Include.NON_NULL) // omit field when null/Complete
    @JsonProperty("block")
    public BlockInfo getBlock() {
      return block;
    }

    @JsonInclude(JsonInclude.Include.NON_NULL) // omit field when null/Complete
    @JsonProperty("phase")
    public CurrentlyActivePhaseInfo getPhaseStatus() {
      return phaseStatus;
    }

    @JsonProperty("stage")
    public CurrentlyActiveStageInfo getStageStatus() {
      return stageStatus;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof CurrentlyActiveInfo)) {
            return false;
        }
        CurrentlyActiveInfo statusInfo = (CurrentlyActiveInfo) o;
        return Objects.equals(getBlock(), statusInfo.getBlock()) &&
            Objects.equals(getPhaseStatus(), statusInfo.getPhaseStatus()) &&
            Objects.equals(getStageStatus(), statusInfo.getStageStatus());
    }

    @Override
    public int hashCode() {
        return Objects.hash(getBlock(), getPhaseStatus(), getStageStatus());
    }

    @Override
    public String toString() {
        return "CurrentlyActiveInfo" +
                "block=" + block +
                ", phaseStatus=" + phaseStatus +
                ", stageStatus=" + stageStatus +
                '}';
    }
}
