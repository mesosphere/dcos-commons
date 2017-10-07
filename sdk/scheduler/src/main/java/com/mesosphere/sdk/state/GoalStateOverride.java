package com.mesosphere.sdk.state;

import java.util.Arrays;

import com.mesosphere.sdk.specification.GoalState;

/**
 * The definition of a goal state override for a task. These are custom states, other than {@link GoalState}s, which a
 * task may enter. The main difference between this and {@link GoalState} is that {@link GoalState}s are configured by
 * the service developer, whereas {@link GoalStateOverride}s are applied by the operator.
 */
public enum GoalStateOverride {

    /** The definition of the default no-override state. Refer to the task's {@link GoalState} setting. */
    NONE(null, null, null),
    /** The definition of the "STOPPED" override state, where commands are replaced with sleep()s.*/
    STOPPED("stop", "STOPPED", "STOPPING");

    private final String command;
    private final String serializedName;
    private final String pendingName;

    private GoalStateOverride(String command, String serializedName, String pendingName) {
        // Validation: Overrides may not overlap with existing GoalStates
        for (GoalState goalState : GoalState.values()) {
            if (goalState.toString().equals(serializedName)) {
                throw new IllegalArgumentException(String.format(
                        "Provided GoalStateOverride serialized name '%s' collides with an existing GoalState=%s",
                        serializedName, Arrays.asList(GoalState.values())));
            }
        }
        this.command = command;
        this.serializedName = serializedName;
        this.pendingName = pendingName;
    }

    /**
     * The name of the command to assign this override to a task. For example "go" or "stop".
     */
    public String getCommand() {
        return command;
    }

    /**
     * The state which tasks in this state are given. For example "RUNNING" or "STOPPED". This is shown to users and
     * stored in task state storage.
     *
     * <p>WARNING: THIS IS STORED IN ZOOKEEPER TASK METADATA AND THEREFORE CANNOT EASILY BE CHANGED
     */
    public String getSerializedName() {
        return serializedName;
    }

    /**
     * The state which tasks which are in the process of entering this state are given. For example "STARTING" or
     * "STOPPING". This is shown to users but not stored anywhere.
     */
    public String getPendingName() {
        return pendingName;
    }
}
