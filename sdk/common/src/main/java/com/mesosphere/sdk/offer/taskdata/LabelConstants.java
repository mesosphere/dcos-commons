package com.mesosphere.sdk.offer.taskdata;

/**
 * Names of {@link org.apache.mesos.Protos.Label}s to be included in the {@link org.apache.mesos.Protos.TaskInfo}s of
 * launched tasks. These are accessible to the Scheduler, and/or to custom Executors which we are moving away from ASAP.
 * These are different from environment variables as they are not directly accessible to the running task itself.
 */
class LabelConstants {

    private LabelConstants() {
        // do not instantiate
    }

    /** Identifies the goal state of this task, e.g. TASK_RUNNING or TASK_FINISHED */
    static final String GOAL_STATE_LABEL = "goal_state";
    /** Identifies the target configuration UUID used by a given task. */
    static final String TARGET_CONFIGURATION_LABEL = "target_configuration";
    /** Identifies a 'task' which isn't actually launched, but is instead created to fill reserved resources. */
    static final String TRANSIENT_FLAG_LABEL = "transient";

    /** Storage of the original Offer's attributes (in a string representation), for use in placement constraints. */
    static final String OFFER_ATTRIBUTES_LABEL = "offer_attributes";
    /** Storage of the original Offer's hostname, for use in placement constraints. */
    static final String OFFER_HOSTNAME_LABEL = "offer_hostname";
    /** Label against which the readiness check (if present) is stored. */
    static final String READINESS_CHECK_LABEL = "readiness_check";
    /** Label used to find the result of a readiness check in a TaskStatus label. */
    static final String READINESS_CHECK_PASSED_LABEL = "readiness_check_passed";
    /** Label against which the Task/Pod Type is stored. */
    static final String TASK_TYPE_LABEL = "task_type";
    /** Label against which the Task/Pod index (starting at 0) is stored. */
    static final String TASK_INDEX_LABEL = "index";
    /** Label for tracking in the state store whether a task failed. Not passed to Mesos itself. */
    static final String PERMANENTLY_FAILED_LABEL = "permanently-failed";

    /** Value for boolean labels which indicates that the label applies.
     * In practice this value doesn't need to be compared, users can instead just check for label presence. */
    static final String BOOLEAN_LABEL_TRUE_VALUE = "true";
}
