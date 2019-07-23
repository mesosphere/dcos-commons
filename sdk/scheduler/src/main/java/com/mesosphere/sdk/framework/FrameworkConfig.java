package com.mesosphere.sdk.framework;

import com.mesosphere.sdk.dcos.DcosConstants;
import com.mesosphere.sdk.offer.Constants;
import com.mesosphere.sdk.scheduler.SchedulerUtils;
import com.mesosphere.sdk.specification.PodSpec;
import com.mesosphere.sdk.specification.ServiceSpec;
import com.mesosphere.sdk.specification.yaml.RawPod;
import com.mesosphere.sdk.specification.yaml.RawServiceSpec;

import com.google.common.collect.ImmutableList;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.builder.ReflectionToStringBuilder;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * This class encapsulates global Framework settings retrieved from the environment, or from a
 * ServiceSpec in the case of a single-Service Framework. Presented as a non-static object to
 * simplify scheduler tests, and to make it painfully obvious when global settings are being used
 * in awkward places.
 */
public final class FrameworkConfig {

  private static final String DEFAULT_ROLE_SUFFIX = "-role";

  private static final String DEFAULT_PRINCIPAL_SUFFIX = "-principal";

  private final String frameworkName;

  private final String principal;

  private final String user;

  private final String zookeeperHostPort;

  private final ImmutableList<String> preReservedRoles;

  private final String role;

  private final String webUrl;

  /**
   * @param frameworkName     name to use for ZK and for registering the framework, in
   *                          single-service schedulers this is the service name
   * @param principal         the principal to use when registering the framework
   * @param user              the username to use when registering the framework
   * @param zookeeperHostPort ZK connection string of the form "master.mesos:2181"
   * @param preReservedRoles  list of pre-reserved parent roles to register for (in addition to the
   *                          base role)
   * @param role              base mesos role
   * @param webUrl            optional URL to advertise in framework info, or empty string
   */
  private FrameworkConfig(
      String frameworkName,
      String role,
      String principal,
      String user,
      String zookeeperHostPort,
      Collection<String> preReservedRoles,
      String webUrl)
  {
    this.frameworkName = frameworkName;
    this.role = role;
    this.principal = principal;
    this.user = user;
    this.zookeeperHostPort = zookeeperHostPort;
    this.preReservedRoles = ImmutableList.copyOf(preReservedRoles);
    this.webUrl = webUrl;
  }

  /**
   * Creates a {@link FrameworkConfig} instance based off the contents of the provided
   * {@link RawServiceSpec}.
   */
  public static FrameworkConfig fromRawServiceSpec(RawServiceSpec rawServiceSpec, Optional<String> serviceNamespace) {
    String serviceRole = getServiceRole(rawServiceSpec.getName(), serviceNamespace);
    return new FrameworkConfig(
        rawServiceSpec.getName(),
        serviceRole,
        getServicePrincipal(rawServiceSpec, rawServiceSpec.getName()),
        getUser(rawServiceSpec),
        getZkHostPort(rawServiceSpec),
        getFrameworkPreReservedRoles(serviceRole, rawServiceSpec.getPods().values().stream()
            .map(RawPod::getPreReservedRole)
            .distinct()
            .collect(Collectors.toList())),
        rawServiceSpec.getWebUrl());
  }

  /**
   * Creates a {@link FrameworkConfig} instance based off the contents of the provided
   * {@link ServiceSpec}.
   */
  public static FrameworkConfig fromServiceSpec(ServiceSpec serviceSpec, Optional<String> serviceNamespace) {
    String serviceRole = getServiceRole(serviceSpec.getName(), serviceNamespace);
    return new FrameworkConfig(
        serviceSpec.getName(),
        serviceRole,
        serviceSpec.getPrincipal(),
        serviceSpec.getUser(),
        serviceSpec.getZookeeperConnection(),
        getFrameworkPreReservedRoles(serviceSpec.getRole(), serviceSpec.getPods().stream()
            .map(PodSpec::getPreReservedRole)
            .distinct()
            .collect(Collectors.toList())),
        serviceSpec.getWebUrl());
  }

  /**
   * Creates a {@link FrameworkConfig} instance based off the provided process environment. This is
   * used when no single {@link ServiceSpec} is applicable (multi-service mode).
   */
  public static FrameworkConfig fromEnvStore(EnvStore envStore) {
    // The only required value is FRAMEWORK_NAME.
    String frameworkName = envStore.getRequired("FRAMEWORK_NAME");

    // We can only use MESOS_ALLOCATION_ROLE if MARATHON_APP_ENFORCE_GROUP_ROLE is true..
    Optional<String> serviceNamespace;
    boolean enforceRole = envStore.getOptionalBoolean("MARATHON_APP_ENFORCE_GROUP_ROLE", false);
    if (enforceRole) {
      serviceNamespace = Optional.ofNullable(envStore.getOptional("MESOS_ALLOCATION_ROLE", null));
    } else {
      serviceNamespace = Optional.empty();
    }

    return new FrameworkConfig(
        frameworkName,
        getServiceRole(frameworkName, serviceNamespace),
        envStore.getOptionalNonEmpty(
            "FRAMEWORK_PRINCIPAL", frameworkName + DEFAULT_PRINCIPAL_SUFFIX),
        envStore.getOptionalNonEmpty("FRAMEWORK_USER", DcosConstants.DEFAULT_SERVICE_USER),
        envStore.getOptionalNonEmpty(
            "FRAMEWORK_ZOOKEEPER", DcosConstants.MESOS_MASTER_ZK_CONNECTION_STRING),
        envStore.getOptionalStringList("FRAMEWORK_PRERESERVED_ROLES", Collections.emptyList())
                .stream()
                .distinct()
                .collect(Collectors.toList()),
        envStore.getOptionalNonEmpty("FRAMEWORK_WEB_URL", ""));
  }

  /**
   * Returns the configured Mesos role to use for running the service.
   * Priority is given to the role specified by MESOS_ALLOCATION_ROLE environment variable set by Mesos.
   * If no such environment-variable is found, or MARATHON_APP_ENFORCE_GROUP_ROLE is false we revert to the legacy
   * version which uses the service name. Leading slashes are removed and intermediate slashes are escaped.
   */
  private static String getServiceRole(String frameworkName, Optional<String> serviceNamespace) {
    if (serviceNamespace.isPresent()) {
      return SchedulerUtils.withEscapedSlashes(serviceNamespace.get());
    } else {
      return getServiceRole(frameworkName);
    }
  }

  /**
   * Returns the configured Mesos role to use for running the service, based on the service name.
   * Unlike the Mesos principal and pre-reserved roles, this value cannot be configured directly
   * via the YAML schema.
   * <p>
   * Use {@code <svcname>-role} (or throw if svcname is missing)
   * <p>
   * If the service name has a leading slash (due to folders), omit that leading slash from the role
   * This is done with the reasoning that "/path/to/service" and "path/to/service" should be
   * equivalent.
   * <p>
   * Slashes are currently banned from roles by as of mesos commit e0d8cc7c. Sounds like they will
   * be allowed again in 1.4 when hierarchical roles are supported.
   * <p>
   * For example: /path/to/service => path__to__service-role
   */
  private static String getServiceRole(String frameworkName) {
    //

    //
    //

    //
    //
    return SchedulerUtils.withEscapedSlashes(frameworkName) + DEFAULT_ROLE_SUFFIX;
  }

  /**
   * Returns the configured Mesos principal to use for running the service.
   */
  private static String getServicePrincipal(RawServiceSpec rawServiceSpec, String frameworkName) {
    // If the svc.yml explicitly provided a principal, use that
    if (rawServiceSpec.getScheduler() != null
        && !StringUtils.isEmpty(rawServiceSpec.getScheduler().getPrincipal()))
    {
      return rawServiceSpec.getScheduler().getPrincipal();
    }
    // Fallback: Use <fwkname>-principal
    return frameworkName + DEFAULT_PRINCIPAL_SUFFIX;
  }

  /**
   * Returns the configured {@code hostname:port} to use for state storage at the scheduler.
   */
  private static String getZkHostPort(RawServiceSpec rawServiceSpec) {
    // If the svc.yml explicitly provided a zk host:port, use that
    if (rawServiceSpec.getScheduler() != null
        && !StringUtils.isEmpty(rawServiceSpec.getScheduler().getZookeeper()))
    {
      return rawServiceSpec.getScheduler().getZookeeper();
    }
    // Fallback: Use the default host:port
    return DcosConstants.MESOS_MASTER_ZK_CONNECTION_STRING;
  }

  /**
   * Maps the provided per-pod pre-reserved roles to a set of namespaced roles which should be
   * registered with Mesos.
   * <p>
   * For example, ["slave_public", "*", "*"] => ["mysvc-role/slave_public"]
   */
  private static Collection<String> getFrameworkPreReservedRoles(
      String frameworkRole, Collection<String> podSpecPreReservedRoles)
  {
    return podSpecPreReservedRoles.stream()
        .filter(r -> !r.equals(Constants.ANY_ROLE))
        .map(r -> r + "/" + frameworkRole)
        .collect(Collectors.toList());
  }

  /**
   * Returns the framework name to use with Mesos. When running a single service, this is equal to
   * the service name.
   */
  public String getFrameworkName() {
    return frameworkName;
  }

  /**
   * Returns the principal to use in resource reservations.
   */
  public String getPrincipal() {
    return principal;
  }

  /**
   * Returns the configured user to use for running the service pods.
   */
  private static String getUser(RawServiceSpec rawServiceSpec) {
    // If the svc.yml explicitly provided a service user, use that
    if (rawServiceSpec.getScheduler() != null &&
        !StringUtils.isEmpty(rawServiceSpec.getScheduler().getUser()))
    {
      return rawServiceSpec.getScheduler().getUser();
    }

    // Fallback: Use the default scheduler user
    return DcosConstants.DEFAULT_SERVICE_USER;
  }

  /**
   * Returns the user to run tasks as.
   */
  public String getUser() {
    return user;
  }

  /**
   * Returns the zookeeper endpoint to use for state storage, of the form "hostname:port".
   */
  public String getZookeeperHostPort() {
    return zookeeperHostPort;
  }

  /**
   * Returns the list of pre-reserved roles as they should be provided to Mesos with the main role
   * included.
   */
  public ImmutableList<String> getPreReservedRoles() {
    return preReservedRoles;
  }

  /**
   * Returns the 'main' role to register as.
   */
  public String getRole() {
    return role;
  }

  /**
   * Returns the union of the pre-reserved roles and the main role.
   */
  public Set<String> getAllResourceRoles() {
    Set<String> roles = new HashSet<>(preReservedRoles);
    roles.add(role);
    roles.add(getNonNamespacedRole());
    return roles;
  }

  /**
   * Returns the web URL to specify when registering with Mesos.
   */
  public String getWebUrl() {
    return webUrl;
  }

  @Override
  public String toString() {
    return ReflectionToStringBuilder.toString(this);
  }

  /**
   * Returns the non-namespaced role used in earlier versions.
   * Can be compared with {@code getRole} to determine which role
   * the current footprint has been deployed with.
   */
  public String getNonNamespacedRole() {
    return SchedulerUtils.withEscapedSlashes(frameworkName) + DEFAULT_ROLE_SUFFIX;
  }
}
