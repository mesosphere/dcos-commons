package org.apache.mesos.scheduler.plan.api;


import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.apache.mesos.scheduler.plan.Block;
import org.apache.mesos.scheduler.plan.Phase;
import org.apache.mesos.scheduler.plan.StageManager;
import org.apache.mesos.scheduler.plan.Status;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Immutable JSON serialization object for a Phase which includes child Blocks.
 */
class PhaseInfo {

    private final String id;
    private final String name;
    private final List<BlockInfo> blocks;
    private final Status status;

    @JsonCreator
    public static PhaseInfo create(
            @JsonProperty("id") final String id,
            @JsonProperty("name") final String name,
            @JsonProperty("blocks") final List<BlockInfo> blocks,
            @JsonProperty("status") final Status status) {
        return new PhaseInfo(id, name, blocks, status);
    }

    public static PhaseInfo forPhase(final Phase phase,
                                     final StageManager stageManager) {

        List<BlockInfo> info = new ArrayList<>(phase.getBlocks().size());
        for (Block block : phase.getBlocks()) {
            info.add(BlockInfo.forBlock(block, stageManager));
        }
        return create(phase.getId().toString(),
                phase.getName(),
                info,
                stageManager.getPhaseStatus(phase.getId()));
    }

    public PhaseInfo(final String id,
                     final String name,
                     final List<BlockInfo> blocks,
                     final Status status) {
        this.id = id;
        this.name = name;
        this.blocks = blocks;
        this.status = status;
    }

    @JsonProperty("blocks")
    public List<BlockInfo> getBlocks() {
        return blocks;
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
        if (this == o) {
            return true;
        }
        if (!(o instanceof PhaseInfo)) {
            return false;
        }
        PhaseInfo phaseInfo = (PhaseInfo) o;
        return getId() == phaseInfo.getId() &&
                Objects.equals(getName(), phaseInfo.getName()) &&
                Objects.equals(getBlocks(), phaseInfo.getBlocks()) &&
                Objects.equals(getStatus(), phaseInfo.getStatus());
    }

    @Override
    public int hashCode() {
        return Objects.hash(getId(), getName(), getBlocks(), getStatus());
    }

    @Override
    public String toString() {
        return "PhaseInfo{" +
                "blocks=" + getBlocks() +
                ", id='" + getId() + "'" +
                ", name='" + getName() + "'" +
                ", status='" + getStatus() + "'" +
                '}';
    }
}
