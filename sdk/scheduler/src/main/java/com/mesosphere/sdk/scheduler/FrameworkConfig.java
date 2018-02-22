package com.mesosphere.sdk.scheduler;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import com.mesosphere.sdk.config.validate.PodSpecsCannotUseUnsupportedFeatures;
import com.mesosphere.sdk.dcos.DcosConstants;
import com.mesosphere.sdk.offer.Constants;
import com.mesosphere.sdk.specification.ServiceSpec;

/**
 * This class encapsulates global Framework settings retrieved from the environment, or from a ServiceSpec in the case
 * of a single-Service Framework. Presented as a non-static object to simplify scheduler tests, and to make it painfully
 * obvious when global settings are being used in awkward places.
 */
class FrameworkConfig {

    final String frameworkName;
    final String principal;
    final String user;
    final String zookeeperConnection;
    final List<String> preReservedRoles;
    final String role;
    final String webUrl;
    final boolean enableGpuResources;

    /**
     * @param frameworkName name to use for ZK and for registering the framework, in single-service schedulers this
     *                      is the service name
     * @param principal the principal to use when registering the framework
     * @param user the username to use when registering the framework
     * @param zookeeperConnection ZK connection string of the form "master.mesos:2181"
     * @param preReservedRoles list of pre-reserved parent roles to register for (in addition to the base role)
     * @param role base mesos role
     * @param webUrl optional URL to advertise in framework info, or empty string
     * @param enableGpuResources whether this service should receive any GPU resources
     */
    private FrameworkConfig(
            String frameworkName,
            String role,
            String principal,
            String user,
            String zookeeperConnection,
            List<String> preReservedRoles,
            String webUrl,
            boolean enableGpuResources) {
        this.frameworkName = frameworkName;
        this.role = role;
        this.principal = principal;
        this.user = user;
        this.zookeeperConnection = zookeeperConnection;
        this.preReservedRoles = preReservedRoles;
        this.webUrl = webUrl;
        this.enableGpuResources = enableGpuResources;
    }

    /**
     * Creates a {@link FrameworkConfig} instance based off the contents of the provided {@link ServiceSpec}.
     */
    static FrameworkConfig fromServiceSpec(ServiceSpec serviceSpec) {
        List<String> preReservedRoles =
                serviceSpec.getPods().stream()
                .filter(podSpec -> !podSpec.getPreReservedRole().equals(Constants.ANY_ROLE))
                .map(podSpec -> podSpec.getPreReservedRole() + "/" + serviceSpec.getRole())
                .collect(Collectors.toList());
        return new FrameworkConfig(
                serviceSpec.getName(),
                serviceSpec.getRole(),
                serviceSpec.getPrincipal(),
                serviceSpec.getUser(),
                serviceSpec.getZookeeperConnection(),
                preReservedRoles,
                serviceSpec.getWebUrl(),
                PodSpecsCannotUseUnsupportedFeatures.serviceRequestsGpuResources(serviceSpec));
    }

    /**
     * Creates a {@link FrameworkConfig} instance based off the process environment, when no single {@link ServiceSpec}
     * is applicable.
     */
    static FrameworkConfig fromEnv() {
        EnvStore envStore = new EnvStore(System.getenv());
        // The only required value is FRAMEWORK_NAME.
        String frameworkName = envStore.getRequired("FRAMEWORK_NAME");
        return new FrameworkConfig(
                frameworkName,
                SchedulerUtils.getServiceRole(frameworkName),
                envStore.getOptional("FRAMEWORK_PRINCIPAL", frameworkName + DcosConstants.DEFAULT_PRINCIPAL_SUFFIX),
                envStore.getOptional("FRAMEWORK_USER", DcosConstants.DEFAULT_QUEUE_SERVICE_USER),
                envStore.getOptional("FRAMEWORK_ZOOKEEPER", DcosConstants.MESOS_MASTER_ZK_CONNECTION_STRING),
                envStore.getOptionalStringList("FRAMEWORK_PRERESERVED_ROLES", Collections.emptyList()),
                envStore.getOptional("FRAMEWORK_WEB_URL", ""),
                envStore.getOptionalBoolean("FRAMEWORK_GPUS", false));
    }
}
