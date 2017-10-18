package com.mesosphere.sdk.scheduler;

import com.mesosphere.sdk.dcos.DcosConstants;
import com.mesosphere.sdk.scheduler.plan.Plan;
import com.mesosphere.sdk.specification.yaml.RawServiceSpec;
import com.mesosphere.sdk.storage.PersisterUtils;
import org.apache.commons.lang3.StringUtils;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * This class provides utilities common to the construction and operation of Mesos Schedulers.
 */
public class SchedulerUtils {
    private static final String DEFAULT_ROLE_SUFFIX = "-role";
    private static final String DEFAULT_PRINCIPAL_SUFFIX = "-principal";

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
     * Returns the configured {@code hostname:port} to use for state storage at the scheduler.
     */
    public static String getZkHost(RawServiceSpec rawServiceSpec, SchedulerConfig schedulerConfig) {
        // If the svc.yml explicitly provided a zk host:port, use that
        if (rawServiceSpec.getScheduler() != null
                && !StringUtils.isEmpty(rawServiceSpec.getScheduler().getZookeeper())) {
            return rawServiceSpec.getScheduler().getZookeeper();
        }
        // Fallback: Use the default host:port
        return DEFAULT_ZK_HOST_PORT;
    }

    /**
     * Returns the configured user to use for running the scheduler.
     */
    public static String getUser(RawServiceSpec rawServiceSpec) {
        // If the svc.yml explicitly provided a service user, use that
        if (rawServiceSpec.getScheduler() != null && !StringUtils.isEmpty(rawServiceSpec.getScheduler().getUser())) {
            return rawServiceSpec.getScheduler().getUser();
        }

        // Fallback: Use the default scheduler user
        return DcosConstants.DEFAULT_SERVICE_USER;
    }

    /**
     * Immediately exits the process with the ordinal value of the provided {@code errorCode}.
     */
    @SuppressWarnings("DM_EXIT")
    public static void hardExit(SchedulerErrorCode errorCode) {
        String message = String.format("Scheduler exiting immediately with code: %d", errorCode.getValue());
        System.err.println(message);
        System.out.println(message);
        System.exit(errorCode.getValue());
    }

    static Optional<Plan> getDeployPlan(Collection<Plan> plans) {
        List<Plan> deployPlans = plans.stream().filter(Plan::isDeployPlan).collect(Collectors.toList());

        if (deployPlans.size() == 1) {
            return Optional.of(deployPlans.get(0));
        } else if (deployPlans.size() == 0) {
            return Optional.empty();
        } else {
            throw new IllegalStateException(String.format("Found multiple deploy plans: %s", deployPlans));
        }
    }
}
