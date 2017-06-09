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
    /** Provides top-level-domain suffix for DNS with Navstar (uses overlay network and the host network) **/
    public static final String FRAMEWORK_TLD_TASKENV = "FRAMEWORK_TLD";
    /** Provides top-level-domain suffix for Mesos-DNS (only reflects task IP when the task is on the
     *  host network)
     */
    public static final String MESOS_TLD_TASKENV = "MESOS_DNS_TLD";

}
