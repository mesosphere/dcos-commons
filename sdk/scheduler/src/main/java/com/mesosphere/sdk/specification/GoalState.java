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
     * Running only once over the lifetime of a service. Should not be run again for the duration of this install. (Will
     * be deprecated in favor of ONCE in future releases, which shares the same semantics.
     */
    FINISHED,

    /**
     * Running and then exiting successfully. Should not be restarted upon a successful exit.
     */
    FINISH,

    /**
     * Running only once over the lifetime of a service. Should not be run again for the duration of this install.
     */
    ONCE
}
