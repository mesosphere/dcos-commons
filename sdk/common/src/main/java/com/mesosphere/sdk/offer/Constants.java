package com.mesosphere.sdk.offer;

/**
 * This class encapsulates constants of relevance to the SDK.
 */
public class Constants {

    // Labels to be included in the TaskInfos of launched tasks.
    // These are accessible to the Scheduler and to custom Executors (which we are moving away from ASAP)

    /** Identifies the goal state of this task, e.g. TASK_RUNNING or TASK_FINISHED */
    public static final String GOAL_STATE_LABEL = "goal_state";
    /** Identifies the target configuration UUID used by a given task. */
    public static final String TARGET_CONFIGURATION_LABEL = "target_configuration";
    /** Identifies a 'task' which isn't actually launched, but is instead created to fill reserved resources. */
    public static final String TRANSIENT_FLAG_LABEL = "transient";

    /** Storage of the original Offer's attributes (in a string representation), for use in placement constraints. */
    public static final String OFFER_ATTRIBUTES_LABEL = "offer_attributes";
    /** Storage of the original Offer's hostname, for use in placement constraints. */
    public static final String OFFER_HOSTNAME_LABEL = "offer_hostname";
    /** Label against which the readiness check (if present) is stored. */
    public static final String READINESS_CHECK_LABEL = "readiness_check";
    /** Label used to find the result of a readiness check in a TaskStatus label. */
    public static final String READINESS_CHECK_PASSED_LABEL = "readiness_check_passed";
    /** Label against which the Task/Pod Type is stored. */
    public static final String TASK_TYPE_LABEL = "task_type";
    /** Label against which the Task/Pod index (starting at 0) is stored. */
    public static final String TASK_INDEX_LABEL = "index";

    // Environment variables to be set inside launched tasks themselves
    // These are accessible to the tasks themselves via the container environment.

    /** Provides the Task/Pod index of the instance, starting at 0. */
    public static final String POD_INSTANCE_INDEX_TASKENV = "POD_INSTANCE_INDEX";
    /** Prefix used for port environment variables which advertise reserved ports by their name. */
    public static final String PORT_NAME_TASKENV_PREFIX = "PORT_";
    /** Provides the configured name of the framework/service. */
    public static final String FRAMEWORK_NAME_TASKENV = "FRAMEWORK_NAME";
    /** Provides the name of the pod/task within the service. */
    public static final String TASK_NAME_TASKENV = "TASK_NAME";

    // Environment variables read by the scheduler itself

    /** Specifies the URI of the executor artifact to be used when launching tasks. */
    public static final String EXECUTOR_URI_SCHEDENV = "EXECUTOR_URI";
    /** Specifies the URI of the libmesos package used by the scheduler itself. */
    public static final String LIBMESOS_URI_SCHEDENV = "LIBMESOS_URI";
    /** Specifies the Java URI to be used when launching tasks. */
    public static final String JAVA_URI_SCHEDENV = "JAVA_URI";
    /** A reasonable default value for {@code JAVA_URI}. */
    public static final String JAVA_URI_DEFAULT =
            "https://downloads.mesosphere.com/java/jre-8u112-linux-x64-jce-unlimited.tar.gz";

    // Other names/constants

    /** Used to mark packed task data within an {@link ExecutorInfo}. */
    public static final String COMMAND_DATA_PACKAGE_EXECUTORID = "command_data_package_executor";
    /** Used in task IDs to separate the task name from a UUID. */
    public static final String TASK_NAME_DELIM = "__";

    /** The name used for the task deployment plan. */
    public static final String DEPLOY_PLAN_NAME = "deploy";

    /** The name used for reserved network port resources. */
    public static final String PORTS_RESOURCE_TYPE = "ports";
    /** The name used for reserved storage/disk resources. */
    public static final String DISK_RESOURCE_TYPE = "disk";
}
