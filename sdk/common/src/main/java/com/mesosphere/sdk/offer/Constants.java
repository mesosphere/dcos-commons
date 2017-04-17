package com.mesosphere.sdk.offer;

/**
 * This class encapsulates constants of relevance to the SDK.
 */
public class Constants {

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
