package com.mesosphere.sdk.scheduler;

/**
 * This class provides utilities common to the construction and operation of Mesos Schedulers.
 */
public class SchedulerUtils {
    private static final String ROLE_SUFFIX = "-role";
    private static final String PRINCIPAL_SUFFIX = "-principal";

    /**
     * Reasonable zk host on DC/OS systems.
     */
    private static final String ZK_HOST = "master.mesos:2181";

    /**
     * Returns the default role to use for a service named {@code frameworkName}.
     */
    public static String nameToRole(String frameworkName) {
        return frameworkName + ROLE_SUFFIX;
    }

    /**
     * Returns the default principal to use for a service named {@code frameworkName}.
     */
    public static String nameToPrincipal(String frameworkName) {
        return frameworkName + PRINCIPAL_SUFFIX;
    }

    /**
     * Returns the default hostname to use for state storage in zookeeper.
     */
    public static String defaultZkHost() {
        return ZK_HOST;
    }

    /**
     * Immediately exits the process with the ordinal value of the provided {@code errorCode}.
     */
    @SuppressWarnings("DM_EXIT")
    public static void hardExit(SchedulerErrorCode errorCode) {
        System.exit(errorCode.getValue());
    }
}
