package org.apache.mesos.scheduler.plan.api;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.apache.mesos.scheduler.plan.Phase;
import org.apache.mesos.scheduler.plan.StageManager;
import org.apache.mesos.scheduler.plan.Status;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Immutable JSON serialization object for a Stage which includes child Phases.
 */
class StageInfo {

    private final List<PhaseInfo> phases;
    private final List<String> errors;
    private final Status status;

    @JsonCreator
    public static StageInfo create(
            @JsonProperty("phases") final List<PhaseInfo> phases,
            @JsonProperty("errors") final List<String> errors,
            @JsonProperty("status") final Status status) {
        return new StageInfo(phases, errors, status);
    }

    public static StageInfo forStage(final StageManager manager) {

        List<PhaseInfo> info = new ArrayList<>(
                manager.getStage().getPhases().size());
        for (Phase phase : manager.getStage().getPhases()) {
            info.add(PhaseInfo.forPhase(phase, manager));
        }

        return create(info,
                manager.getStage().getErrors(),
                manager.getStatus());

    }

    public StageInfo(final List<PhaseInfo> phases,
                     final List<String> errors,
                     final Status status) {
        this.phases = phases;
        this.errors = errors;
        this.status = status;
    }

    @JsonProperty("phases")
    public List<PhaseInfo> getPhases() {
        return phases;
    }

    @JsonProperty("errors")
    public List<String> getErrors() {
        return errors;
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
        if (!(o instanceof StageInfo)) {
            return false;
        }
        StageInfo stageInfo = (StageInfo) o;
        return Objects.equals(getPhases(), stageInfo.getPhases()) &&
            Objects.equals(getErrors(), stageInfo.getErrors()) &&
            Objects.equals(getStatus(), stageInfo.getStatus());
    }

    @Override
    public int hashCode() {
        return Objects.hash(getPhases(), getErrors(), getStatus());
    }

    @Override
    public String toString() {
        return "StageInfo{" +
                "phases=" + getPhases() +
                "errors=" + getErrors() +
                "status=" + getStatus() +
                '}';
    }
}
