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
     * When a port named {@code api} is added to the Marathon app definition for the scheduler, marathon should create
     * an envvar with this name in the scheduler env. This is preferred over using e.g. the {@code PORT0} envvar which
     * is against the index of the port in the list.
     */
    private static final String MARATHON_API_PORT_ENV = "PORT_API";

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
     * Returns the env-configured API port, or throws {@link IllegalStateException} if the environment lacked the
     * required information.
     */
    public static int apiPort() {
        String envPort = System.getenv(MARATHON_API_PORT_ENV);
        if (envPort == null) {
            throw new IllegalStateException(String.format(
                    "Missing environment variable %s. Either this or an explicit apiPort must be provided.",
                    MARATHON_API_PORT_ENV));
        }
        return Integer.parseInt(envPort);
    }

    /**
     * Immediately exits the process with the ordinal value of the provided {@code errorCode}.
     */
    @SuppressWarnings("DM_EXIT")
    public static void hardExit(SchedulerErrorCode errorCode) {
        System.exit(errorCode.getValue());
    }
}
