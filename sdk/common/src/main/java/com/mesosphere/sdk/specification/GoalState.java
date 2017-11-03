package com.mesosphere.sdk.specification;

/**
 * The allowed goal states for a Task. These are treated as the desired outcome for executing a given task.
 */
public enum GoalState {
    /**
     * ERROR.
     */
    UNKNOWN,

    /**
     * Running continuously. Should be restarted if it exits.
     */
    RUNNING,

    /**
     * Running and then exiting successfully. Should not be restarted upon a successful exit.
     */
    FINISHED
}
