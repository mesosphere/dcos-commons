package com.mesosphere.sdk.state;

import java.util.Arrays;
import java.util.regex.Pattern;

import com.mesosphere.sdk.offer.Constants;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;

import com.mesosphere.sdk.specification.GoalState;

/**
 * The definition of a goal state override for a task. These are custom states, other than {@link GoalState}s, which a
 * task may enter. The main difference between this and {@link GoalState} is that {@link GoalState}s are configured by
 * the service developer, whereas {@link GoalStateOverride}s are applied by the operator.
 */
public enum GoalStateOverride {

    /** The definition of the default no-override state. Refer to the task's {@link GoalState} setting. */
    NONE("NONE", "STARTING"),
    /** The definition of the "PAUSED" override state, where commands are replaced with sleep()s. */
    PAUSED("PAUSED", "PAUSING"),
    /** The definition of the "DECOMMISSIONED" override state, where tasks are removed from the service. */
    DECOMMISSIONED("DECOMMISSIONED", "DECOMMISSIONING");

    /** Sleep forever when pausing. */
    public static final String PAUSE_COMMAND =
            String.format(
                    "echo This task is PAUSED, sleeping ... && " +
                            "./bootstrap --resolve=false && while true; do sleep %d; done",
                    Constants.LONG_DECLINE_SECONDS);

    /**
     * Plans should not assume that a paused task has fulfilled the requirement of its dependencies.  Returnging an
     * always failing readinness check guarantees that plan execution does not interpret a paused task as being ready.
     */
    public static final String PAUSE_READINESS_COMMAND = "exit 1";

    /**
     * The state of the override itself.
     */
    public enum Progress {
        /**
         * The desired override (or lack of override) has ben set, but relaunching the task hasn't occurred yet.
         * In practice this state should only appear very briefly.
         */
        PENDING("PENDING"),

        /**
         * The override (or lack of override) has started processing but hasn't finished taking effect.
         */
        IN_PROGRESS("IN_PROGRESS"),

        /**
         * The override (or lack of override) has been committed.
         */
        COMPLETE("COMPLETE");

        private final String serializedName;

        private Progress(String serializedName) {
            this.serializedName = serializedName;
        }

        /**
         * The label which overrides in this state are given. For example "RUNNING" or "PAUSED". This is stored in task
         * state storage.
         *
         * <p>WARNING: THIS IS STORED IN ZOOKEEPER TASK METADATA AND THEREFORE CANNOT EASILY BE CHANGED
         */
        public String getSerializedName() {
            return serializedName;
        }
    }

    /**
     * Describes the current state of an override.
     *
     * The state of the override itself. Sample flow for enabling and disabling an override:
     *
     * <table><tr><td>Operation</td><td>Resulting status (override + progress)</td></tr>
     * <tr><td>(Initial state)</td><td>NONE + COMPLETE (==INACTIVE)</td></tr>
     * <tr><td>"Pause" triggered, kill command issued</td><td>PAUSED + PENDING</td></tr>
     * <tr><td>TASK_KILLED status received</td><td>PAUSED + IN_PROGRESS</td></tr>
     * <tr><td>Task relaunch in paused state has been triggered</td><td>PAUSED + COMPLETE</td></tr>
     * <tr><td>"Start" triggered, kill command issued</td><td>NONE + PENDING</td></tr>
     * <tr><td>TASK_KILLED status received</td><td>NONE + IN_PROGRESS</td></tr>
     * <tr><td>Task relaunch in normal state has been triggered</td><td>NONE + COMPLETE (==INACTIVE)</td></tr></table>
     */
    public static class Status {

        /**
         * The override status of a task for which no overrides are applicable, and which has reached its goal state.
         * The task is not entering, exiting, or currently in an override state.
         */
        public static final Status INACTIVE = new Status(GoalStateOverride.NONE, Progress.COMPLETE);

        /**
         * The target override state for this task. May be {@link GoalStateOverride#NONE} in the case of no override.
         */
        public final GoalStateOverride target;

        /**
         * The current state for transitioning to the {@link #target} in question. May be {@link Progress#NONE} in the
         * case of no override being applicable.
         */
        public final Progress progress;

        private Status(GoalStateOverride target, Progress progress) {
            this.target = target;
            this.progress = progress;
        }

        public static Progress translateStatus(com.mesosphere.sdk.scheduler.plan.Status planStatus) {
            switch (planStatus) {
                case PENDING:
                    return Progress.PENDING;
                case STARTED:
                case COMPLETE:
                    return Progress.COMPLETE;
                default:
                    return Progress.IN_PROGRESS;
            }
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
            return String.format("%s/%s", target, progress);
        }
    }

    public Status newStatus(Progress progress) {
        return new Status(this, progress);
    }

    private final String serializedName;
    private final String pendingName;

    private GoalStateOverride(String serializedName, String pendingName) {
        // Validation: By convention all state labels must be all caps/no spaces
        Pattern validLabelPattern = Pattern.compile("^[A-Z_]+$"); // cannot use a static final member here
        if (!validLabelPattern.matcher(this.name()).matches()
                || !validLabelPattern.matcher(serializedName).matches()
                || !validLabelPattern.matcher(pendingName).matches()) {
            throw new IllegalArgumentException(String.format(
                    "Provided GoalStateOverride names '%s'/'%s'/'%s' do not validate against pattern: %s",
                    this.name(), serializedName, pendingName, validLabelPattern.pattern()));
        }

        // Validation: Overrides may not overlap with existing GoalStates (RUNNING, ONCE)
        for (GoalState goalState : GoalState.values()) {
            if (goalState.toString().equals(serializedName)) {
                throw new IllegalArgumentException(String.format(
                        "Provided GoalStateOverride serialized name '%s' collides with an existing GoalState=%s",
                        serializedName, Arrays.asList(GoalState.values())));
            }

        }
        this.serializedName = serializedName;
        this.pendingName = pendingName;
    }

    /**
     * The label which tasks in this state are given. For example "RUNNING" or "PAUSED". This is shown to users and
     * stored in task state storage.
     *
     * <p>WARNING: THIS IS STORED IN ZOOKEEPER TASK METADATA AND THEREFORE CANNOT EASILY BE CHANGED
     */
    public String getSerializedName() {
        return serializedName;
    }

    /**
     * The state which tasks which are in the process of entering this state are given. For example "STARTING" or
     * "PAUSING". This is shown to users but not stored anywhere.
     */
    public String getTransitioningName() {
        return pendingName;
    }
}
