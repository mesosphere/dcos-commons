package org.apache.mesos.scheduler.recovery;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.base.Objects;
import org.apache.mesos.Protos.TaskInfo;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Represents the collection of nodes in the stopped and failed pools.
 * <p>
 * Failed nodes are ones whose task is no longer running, but we believe may be restarted. This allows them to recover
 * data persisted to disk.
 * <p>
 * Stopped nodes are ones we believe are completely gone, and must be restarted elsewhere.
 */
public class RecoveryStatus {
    private final List<TaskInfo> stopped;
    private final List<TaskInfo> failed;

    public RecoveryStatus(
            List<TaskInfo> stopped,
            List<TaskInfo> failed) {
        this.stopped = stopped;
        this.failed = failed;
    }

    public List<TaskInfo> getStopped() {
        return stopped;
    }

    public List<TaskInfo> getFailed() {
        return failed;
    }

    @JsonProperty("stopped")
    public List<String> getStoppedNames() {
        return getStopped().stream()
                .map(TaskInfo::getName)
                .collect(Collectors.toList());
    }

    @JsonProperty("failed")
    public List<String> getFailedNames() {
        return getFailed().stream()
                .map(TaskInfo::getName)
                .collect(Collectors.toList());
    }

    @Override
    public String toString() {
        return "RecoveryStatus{" +
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
        RecoveryStatus that = (RecoveryStatus) o;
        return Objects.equal(stopped, that.stopped) &&
                Objects.equal(failed, that.failed);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(stopped, failed);
    }
}
