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

    /** Provides the configured name of the framework/service. */
    public static final String FRAMEWORK_NAME_TASKENV = "FRAMEWORK_NAME";
    /** Provides the name of the pod/task within the service. */
    public static final String TASK_NAME_TASKENV = "TASK_NAME";

    /** Provides the host domain for pods within the service. Pods within the service would be accessed as subdomains
     *  under this domain. For example, <TASK_NAME>.<FRAMEWORK_HOST> => pod hostname. */
    public static final String FRAMEWORK_HOST_TASKENV = "FRAMEWORK_HOST";
    /** Provides the host domain for VIPs within the service. VIPs within the service would be accessed as subdomains
     *  under this domain. For example, <VIP_NAME>.<FRAMEWORK_VIP_HOST> => vip hostname. */
    public static final String FRAMEWORK_VIP_HOST_TASKENV = "FRAMEWORK_VIP_HOST";

    /** Provides the hostname of the Scheduler's own API. Pods within the service may access scheduler-hosted APIs under
     *  this hostname. For example, <SCHEDULER_API_HOSTNAME>/v1/state/files/notavirus.exe.txt */
    public static final String SCHEDULER_API_HOSTNAME_TASKENV = "SCHEDULER_API_HOSTNAME";

    /** Provides the zone in which the task has been launched. */
    public static final String ZONE_TASKENV = "ZONE";

    /** Provides the region in which the task has been launched. */
    public static final String REGION_TASKENV = "REGION";

    /** An environment variable indicating whether detection of zones should be required by base technology. */
    public static final String PLACEMENT_REFERENCED_ZONE_ENV = "PLACEMENT_REFERENCED_ZONE";

    /** An environment variable indicating whether detection of regions should be required by base technology. */
    public static final String PLACEMENT_REFERENCED_REGION_ENV = "PLACEMENT_REFERENCED_REGION";
}
