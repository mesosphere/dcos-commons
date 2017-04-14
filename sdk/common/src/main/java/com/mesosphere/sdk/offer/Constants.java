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

    // Environment variables read by the scheduler itself

    /** Specifies the URI of the executor artifact to be used when launching tasks. */
    public static final String EXECUTOR_URI_SCHEDENV = "EXECUTOR_URI";
    /** Specifies the URI of the libmesos package used by the scheduler itself. */
    public static final String LIBMESOS_URI_SCHEDENV = "LIBMESOS_URI";
    /** Specifies the Java URI to be used when launching tasks. */
    public static final String JAVA_URI_SCHEDENV = "JAVA_URI";
    /**
     * Controls whether the {@link StateStoreCache} is disabled (enabled by default).
     * If this envvar is set (to anything at all), the cache is disabled.
     */
    public static final String DISABLE_STATE_CACHE_SCHEDENV = "DISABLE_STATE_CACHE";

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
