package org.apache.mesos.scheduler.plan.api;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.apache.mesos.scheduler.plan.Block;
import org.apache.mesos.scheduler.plan.StageManager;
import org.apache.mesos.scheduler.plan.Status;

import java.util.Objects;

/**
 * Immutable JSON serialization object for a Block.
 */
class BlockInfo {

    private final String id;
    private final Status status;
    private final String name;
    private final String message;
    private final Boolean hasDecisionPoint;

    @JsonCreator
    public static BlockInfo create(
            @JsonProperty("id") final String id,
            @JsonProperty("status") final Status status,
            @JsonProperty("name") final String name,
            @JsonProperty("message") final String message,
            @JsonProperty("has_decision_point") final boolean hasDecisionPoint) {
        return new BlockInfo(id, status, name, message, hasDecisionPoint);
    }

    public static BlockInfo forBlock(final Block block,
                                     final StageManager stageManager) {
        return create(block.getId().toString(),
                block.getStatus(), block
                .getName(),
                block.getMessage(),
                stageManager.hasDecisionPoint(block));

    }

    public BlockInfo(final String id,
                     final Status status,
                     final String name,
                     final String message,
                     final boolean hasDecisionPoint) {
        this.id = id;
        this.status = status;
        this.name = name;
        this.message = message;
        this.hasDecisionPoint = hasDecisionPoint;

    }

    @JsonProperty("has_decision_point")
    public Boolean getHasDecisionPoint() {
        return hasDecisionPoint;
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
        if (this == o) {
            return true;
        }
        if (!(o instanceof BlockInfo)) {
            return false;
        }
        BlockInfo blockInfo = (BlockInfo) o;
        return Objects.equals(getId(), blockInfo.getId()) &&
                getStatus() == blockInfo.getStatus() &&
                Objects.equals(getName(), blockInfo.getName()) &&
                Objects.equals(getMessage(), blockInfo.getMessage()) &&
                Objects.equals(getHasDecisionPoint(),
                        blockInfo.getHasDecisionPoint());
    }

    @Override
    public int hashCode() {
        return Objects.hash(getId(), getStatus(), getName(), getMessage(),
                getHasDecisionPoint());
    }

    @Override
    public String toString() {
        return "BlockInfo{" +
                "has_decision_point=" + hasDecisionPoint +
                ", id='" + id + '\'' +
                ", status=" + status +
                ", name='" + name + '\'' +
                ", message='" + message + '\'' +
                '}';
    }
}
