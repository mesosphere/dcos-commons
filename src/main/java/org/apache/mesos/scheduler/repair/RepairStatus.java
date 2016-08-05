package org.apache.mesos.scheduler.repair;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.base.Objects;

import java.util.List;

/**
 * Represents the collection of nodes in the stopped and failed pools.
 * <p>
 * Failed nodes are ones whose task is no longer running, but we believe may be restarted. This allows them to recover
 * data persisted to disk.
 * <p>
 * Stopped nodes are ones we believe are completely gone, and must be restarted elsewhere.
 */
public class RepairStatus {
    private final List<String> stopped;
    private final List<String> failed;

    @JsonCreator
    public RepairStatus(
            @JsonProperty("stopped") List<String> stopped,
            @JsonProperty("failed") List<String> failed) {
        this.stopped = stopped;
        this.failed = failed;
    }

    @JsonProperty
    public List<String> getStopped() {
        return stopped;
    }

    @JsonProperty
    public List<String> getFailed() {
        return failed;
    }

    @Override
    public String toString() {
        return "RepairStatus{" +
                "stopped=" + stopped +
                ", failed=" + failed +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        RepairStatus that = (RepairStatus) o;
        return Objects.equal(stopped, that.stopped) &&
                Objects.equal(failed, that.failed);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(stopped, failed);
    }
}
