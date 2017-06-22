package com.mesosphere.sdk.scheduler;

import org.apache.commons.lang3.StringUtils;

import com.mesosphere.sdk.specification.yaml.RawServiceSpec;
import com.mesosphere.sdk.storage.PersisterUtils;

/**
 * This class provides utilities common to the construction and operation of Mesos Schedulers.
 */
public class SchedulerUtils {
    private static final String DEFAULT_ROLE_SUFFIX = "-role";
    private static final String DEFAULT_PRINCIPAL_SUFFIX = "-principal";
    private static final String DEFAULT_SERVICE_USER = "nobody";

    /** Reasonable zk host on DC/OS systems. */
    private static final String DEFAULT_ZK_HOST_PORT = "master.mesos:2181";

    /**
     * Escape sequence to use for slashes in service names. Slashes are used in DC/OS for folders, and we don't want to
     * confuse ZK with those.
     */
    private static final String FRAMEWORK_NAME_SLASH_ESCAPE = "__";

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
     * Removes any slashes from the provided framework name and replaces them with double underscores.
     *
     * For example:
     * <ul>
     * <li>/path/to/kafka => path__to__kafka</li>
     * <li>path/to/some-kafka => path__to__some-kafka</li>
     * <li>path__to__kafka => EXCEPTION</li>
     * </ul>
     *
     * @throws IllegalArgumentException if the provided framework name already contains double underscores
     */
    public static String withEscapedSlashes(String frameworkName) {
        if (frameworkName.contains(FRAMEWORK_NAME_SLASH_ESCAPE)) {
            throw new IllegalArgumentException("Service names may not contain double underscores: " + frameworkName);
        }

        if (frameworkName.startsWith(PersisterUtils.PATH_DELIM_STR)) {
            // Trim any leading slash
            frameworkName = frameworkName.substring(PersisterUtils.PATH_DELIM_STR.length());
        }
        // Replace any other slashes (e.g. from folder support) with double underscores:
        frameworkName = frameworkName.replace(PersisterUtils.PATH_DELIM_STR, FRAMEWORK_NAME_SLASH_ESCAPE);
        return frameworkName;
    }

    /**
     * Returns the configured Mesos role to use for running the service.
     *
     * For example: /path/to/kafka => path__to__kafka-role
     */
    public static String getServiceRole(RawServiceSpec rawServiceSpec) {
        // Use <svcname>-role (or throw if svcname is missing)

        // If the service name has a leading slash (due to folders), omit that leading slash from the role.
        // This is done with the reasoning that "/path/to/kafka" and "path/to/kafka" should be equivalent.

        // Slashes are currently banned from roles by as of mesos commit e0d8cc7c. Sounds like they will be allowed
        // again in 1.4 when hierarchical roles are supported.
        //TODO(nickbp): Revisit use of slashes here, as they're needed for hierarchical roles.
        return withEscapedSlashes(getServiceName(rawServiceSpec)) + DEFAULT_ROLE_SUFFIX;
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
     * Returns the configured Mesos user to use for running the service.
     */
    public static String getServiceUser(RawServiceSpec rawServiceSpec) {
        // If the svc.yml explicitly provided a user, use that
        if (rawServiceSpec.getScheduler() != null
                && !StringUtils.isEmpty(rawServiceSpec.getScheduler().getUser())) {
            return rawServiceSpec.getScheduler().getUser();
        }
        // Fallback: "nobody"
        return DEFAULT_SERVICE_USER;
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
