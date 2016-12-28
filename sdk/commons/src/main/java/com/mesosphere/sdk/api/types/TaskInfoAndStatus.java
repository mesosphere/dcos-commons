package com.mesosphere.sdk.api.types;

import java.util.Optional;

import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.mesos.Protos.TaskInfo;
import org.apache.mesos.Protos.TaskStatus;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.protobuf.TextFormat;

/**
 * Basic container which holds a {@link TaskInfo} and optionally a corresponding {@link TaskStatus} if any is
 * available.
 */
public class TaskInfoAndStatus {
    private final TaskInfo info;
    private final Optional<TaskStatus> status;

    public static TaskInfoAndStatus create(TaskInfo info, Optional<TaskStatus> status) {
        return new TaskInfoAndStatus(info, status);
    }

    @JsonProperty("info")
    public TaskInfo getInfo() {
        return info;
    }

    @JsonProperty("status")
    public Optional<TaskStatus> getStatus() {
        return status;
    }

    public boolean hasStatus() {
        return status.isPresent();
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
        // Manual string: ensure we use shortDebugString() throughout
        if (status.isPresent()) {
            return String.format("TaskInfoAndStatus{info=%s, status=%s}",
                    TextFormat.shortDebugString(info), TextFormat.shortDebugString(status.get()));
        } else {
            return String.format("TaskInfoAndStatus{info=%s, status=<unset>}",
                    TextFormat.shortDebugString(info));
        }
    }

    private TaskInfoAndStatus(
            @JsonProperty("info") TaskInfo info,
            @JsonProperty("status") Optional<TaskStatus> status) {
        this.info = info;
        this.status = status;
    }
}
