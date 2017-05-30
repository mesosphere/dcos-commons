package com.mesosphere.sdk.scheduler;

import org.apache.commons.lang3.StringUtils;

import com.mesosphere.sdk.specification.yaml.RawServiceSpec;

/**
 * This class provides utilities common to the construction and operation of Mesos Schedulers.
 */
public class SchedulerUtils {
    private static final String DEFAULT_ROLE_SUFFIX = "-role";
    private static final String DEFAULT_PRINCIPAL_SUFFIX = "-principal";

    /** Reasonable zk host on DC/OS systems. */
    private static final String DEFAULT_ZK_HOST_PORT = "master.mesos:2181";

    /**
     * Returns the configured service name (aka framework name) to use for running the service.
     *
     * @throws IllegalArgumentException if no service name could be found
     */
    public static String getServiceName(RawServiceSpec rawServiceSpec) {
        if (!StringUtils.isEmpty(rawServiceSpec.getName())) {
            return rawServiceSpec.getName();
        }
        throw new IllegalStateException("Missing required 'name' in Service Spec");
    }

    /**
     * Returns the configured Mesos role to use for running the service.
     */
    public static String getServiceRole(RawServiceSpec rawServiceSpec) {
        // Use <svcname>-role (or throw if svcname is missing)
        String serviceName = getServiceName(rawServiceSpec);
        if (serviceName.startsWith("/")) {
            // If the service name has a leading slash (due to folders), omit that leading slash from the role.
            // Mesos disallows roles which start with a slash.
            serviceName = serviceName.substring(1);
        }
        return serviceName + DEFAULT_ROLE_SUFFIX;
    }

    /**
     * Returns the configured Mesos principal to use for running the service.
     */
    public static String getServicePrincipal(RawServiceSpec rawServiceSpec) {
        // If the svc.yml explicitly provided a principal, use that
        if (rawServiceSpec.getScheduler() != null
                && !StringUtils.isEmpty(rawServiceSpec.getScheduler().getPrincipal())) {
            return rawServiceSpec.getScheduler().getPrincipal();
        }
        // Fallback: Use <svcname>-principal (or throw if svcname is missing)
        return getServiceName(rawServiceSpec) + DEFAULT_PRINCIPAL_SUFFIX;
    }

    /**
     * Returns the configured API port to use for serving requests at the scheduler.
     */
    public static Integer getApiPort(RawServiceSpec rawServiceSpec, SchedulerFlags schedulerFlags) {
        // If the svc.yml explicitly provided an api port, use that
        if (rawServiceSpec.getScheduler() != null
                && rawServiceSpec.getScheduler().getApiPort() != null) {
            return rawServiceSpec.getScheduler().getApiPort();
        }
        // Fallback: Use api port provided (by Marathon) in scheduler env, or throw if it's missing
        return schedulerFlags.getApiServerPort();
    }

    /**
     * Returns the configured {@code hostname:port} to use for state storage at the scheduler.
     */
    public static String getZkHost(RawServiceSpec rawServiceSpec, SchedulerFlags schedulerFlags) {
        // If the svc.yml explicitly provided a zk host:port, use that
        if (rawServiceSpec.getScheduler() != null
                && !StringUtils.isEmpty(rawServiceSpec.getScheduler().getZookeeper())) {
            return rawServiceSpec.getScheduler().getZookeeper();
        }
        // Fallback: Use the default host:port
        return DEFAULT_ZK_HOST_PORT;
    }

    /**
     * Immediately exits the process with the ordinal value of the provided {@code errorCode}.
     */
    @SuppressWarnings("DM_EXIT")
    public static void hardExit(SchedulerErrorCode errorCode) {
        System.exit(errorCode.getValue());
    }
}
