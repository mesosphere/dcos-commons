package com.mesosphere.sdk.offer.taskdata;

/**
 * Environment variables to be set inside launched tasks themselves
 * These are accessible to the tasks themselves via the container environment.
 */
public class EnvConstants {

    private EnvConstants() {
        // do not instantiate
    }

    /** Provides the Task/Pod index of the instance, starting at 0. */
    public static final String POD_INSTANCE_INDEX_TASKENV = "POD_INSTANCE_INDEX";
    /** Prefix used for port environment variables which advertise reserved ports by their name. */
    public static final String PORT_NAME_TASKENV_PREFIX = "PORT_";
    /** Provides the configured name of the framework/service. */
    public static final String FRAMEWORK_NAME_TASKENV = "FRAMEWORK_NAME";
    /** Provides the name of the pod/task within the service. */
    public static final String TASK_NAME_TASKENV = "TASK_NAME";
    /** Provides the host domain of the service. Pods within the service would be accessed as subdomains under this
     *  domain. For example, <TASK_NAME>.<FRAMEWORK_HOST> => pod hostname. **/
    public static final String FRAMEWORK_HOST_TASKENV = "FRAMEWORK_HOST";
}
