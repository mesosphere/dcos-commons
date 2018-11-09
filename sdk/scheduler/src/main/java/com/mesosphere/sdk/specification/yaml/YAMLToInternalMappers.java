package com.mesosphere.sdk.specification.yaml;

import com.mesosphere.sdk.config.TaskEnvRouter;
import com.mesosphere.sdk.dcos.DcosConstants;
import com.mesosphere.sdk.framework.FrameworkConfig;
import com.mesosphere.sdk.offer.Constants;
import com.mesosphere.sdk.offer.LoggingUtils;
import com.mesosphere.sdk.offer.evaluate.placement.MarathonConstraintParser;
import com.mesosphere.sdk.offer.evaluate.placement.PassthroughRule;
import com.mesosphere.sdk.offer.evaluate.placement.PlacementRule;
import com.mesosphere.sdk.scheduler.SchedulerConfig;
import com.mesosphere.sdk.specification.ConfigFileSpec;
import com.mesosphere.sdk.specification.DefaultCommandSpec;
import com.mesosphere.sdk.specification.DefaultConfigFileSpec;
import com.mesosphere.sdk.specification.DefaultDiscoverySpec;
import com.mesosphere.sdk.specification.DefaultHealthCheckSpec;
import com.mesosphere.sdk.specification.DefaultHostVolumeSpec;
import com.mesosphere.sdk.specification.DefaultNetworkSpec;
import com.mesosphere.sdk.specification.DefaultPodSpec;
import com.mesosphere.sdk.specification.DefaultReadinessCheckSpec;
import com.mesosphere.sdk.specification.DefaultResourceSet;
import com.mesosphere.sdk.specification.DefaultSecretSpec;
import com.mesosphere.sdk.specification.DefaultServiceSpec;
import com.mesosphere.sdk.specification.DefaultTaskSpec;
import com.mesosphere.sdk.specification.DefaultTransportEncryptionSpec;
import com.mesosphere.sdk.specification.DefaultVolumeSpec;
import com.mesosphere.sdk.specification.DiscoverySpec;
import com.mesosphere.sdk.specification.GoalState;
import com.mesosphere.sdk.specification.HealthCheckSpec;
import com.mesosphere.sdk.specification.HostVolumeSpec;
import com.mesosphere.sdk.specification.NamedVIPSpec;
import com.mesosphere.sdk.specification.NetworkSpec;
import com.mesosphere.sdk.specification.PodSpec;
import com.mesosphere.sdk.specification.PortSpec;
import com.mesosphere.sdk.specification.RLimitSpec;
import com.mesosphere.sdk.specification.ReadinessCheckSpec;
import com.mesosphere.sdk.specification.ResourceSet;
import com.mesosphere.sdk.specification.SecretSpec;
import com.mesosphere.sdk.specification.ServiceSpec;
import com.mesosphere.sdk.specification.TaskSpec;
import com.mesosphere.sdk.specification.TransportEncryptionSpec;
import com.mesosphere.sdk.specification.VolumeSpec;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Strings;
import org.apache.commons.collections.MapUtils;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.mesos.Protos;
import org.slf4j.Logger;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Adapter utilities for mapping Raw YAML objects to internal objects.
 */
@SuppressWarnings({
    "checkstyle:InnerTypeLast",
    "checkstyle:IllegalCatch",
    "checkstyle:DeclarationOrder",
    "checkstyle:VariableDeclarationUsageDistance",
    "checkstyle:EqualsAvoidNull"
})
public final class YAMLToInternalMappers {

  private YAMLToInternalMappers() {}

  private static final Logger LOGGER = LoggingUtils.getLogger(YAMLToInternalMappers.class);

  /**
   * Implementation for reading files from disk. Meant to be overridden by a mock in tests.
   */
  @VisibleForTesting
  public static class ConfigTemplateReader {
    private final File templateDir;

    public ConfigTemplateReader(File templateDir) {
      this.templateDir = templateDir;
    }

    public String read(String templateFileName) throws IOException {
      return FileUtils.readFileToString(
          new File(templateDir, templateFileName),
          StandardCharsets.UTF_8);
    }
  }

  /**
   * Converts the provided YAML {@link RawServiceSpec} into a new {@link ServiceSpec}.
   *
   * @param rawServiceSpec       the raw service specification representing a YAML file
   * @param configTemplateReader the file reader to be used for reading template files, allowing overrides for testing
   * @throws Exception if the conversion fails
   */
  public static DefaultServiceSpec convertServiceSpec(
      RawServiceSpec rawServiceSpec,
      FrameworkConfig frameworkConfig,
      SchedulerConfig schedulerConfig,
      TaskEnvRouter taskEnvRouter,
      ConfigTemplateReader configTemplateReader) throws Exception
  {
    if (StringUtils.isEmpty(rawServiceSpec.getName())) {
      throw new IllegalStateException("Missing required 'name' in Service Spec");
    }
    return convertServiceSpec(
        rawServiceSpec.getName(),
        rawServiceSpec.getPods(),
        frameworkConfig,
        schedulerConfig,
        taskEnvRouter,
        configTemplateReader);
  }

  /**
   * Note: We make it very explicit here that many fields from the {@link RawServiceSpec} should not be read directly.
   * Instead we should go to the {@link FrameworkConfig} for those values. This is because they may be different in
   * the case of a multi-service scheduler.
   */
  private static DefaultServiceSpec convertServiceSpec(
      String serviceName,
      LinkedHashMap<String, RawPod> rawPods,
      FrameworkConfig frameworkConfig,
      SchedulerConfig schedulerConfig,
      TaskEnvRouter taskEnvRouter,
      ConfigTemplateReader configTemplateReader) throws Exception
  {
    LOGGER.info("Using framework config : {}", frameworkConfig.toString());
    verifyDistinctDiscoveryPrefixes(rawPods.values());
    verifyDistinctEndpointNames(rawPods.values());

    DefaultServiceSpec.Builder builder = DefaultServiceSpec.newBuilder()
        .name(serviceName)
        .role(frameworkConfig.getRole())
        .principal(frameworkConfig.getPrincipal())
        .zookeeperConnection(frameworkConfig.getZookeeperHostPort())
        .webUrl(frameworkConfig.getWebUrl())
        .user(frameworkConfig.getUser());

    // Add all pods
    List<PodSpec> pods = new ArrayList<>();
    for (Map.Entry<String, RawPod> entry : rawPods.entrySet()) {
      String podName = entry.getKey();
      RawPod rawPod = entry.getValue();
      pods.add(convertPod(
          rawPod,
          configTemplateReader,
          podName,
          taskEnvRouter.getConfig(podName),
          getPodRole(rawPod.getPreReservedRole(), frameworkConfig.getRole()),
          frameworkConfig.getPrincipal(),
          frameworkConfig.getUser()));

    }
    builder.pods(pods);

    return builder.build();
  }

  /**
   * Verifies that tasks in separate pods don't share a discovery prefix.
   */
  private static void verifyDistinctDiscoveryPrefixes(Collection<RawPod> rawPods) {
    Map<String, Long> dnsPrefixCounts = rawPods.stream()
        .flatMap(p -> p.getTasks().values().stream()
            .map(RawTask::getDiscovery)
            .filter(Objects::nonNull)
            .map(RawDiscovery::getPrefix)
            .filter(Objects::nonNull)
            .distinct())
        .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()));
    List<String> dnsNameDuplicates = dnsPrefixCounts.entrySet().stream()
        .filter(e -> e.getValue() > 1)
        .map(Map.Entry::getKey)
        .collect(Collectors.toList());
    if (!dnsNameDuplicates.isEmpty()) {
      throw new IllegalArgumentException(String.format(
          "Tasks in different pods cannot share DNS names: %s", dnsNameDuplicates));
    }
  }

  /**
   * Verifies that different tasks don't share advertised endpoint names.
   * Otherwise the 'endpoints' command will have them jumbled together.
   */
  private static void verifyDistinctEndpointNames(Collection<RawPod> rawPods) {
    Set<String> allEndpointNames = new HashSet<>();
    Set<String> duplicateEndpointNames = new TreeSet<>();
    for (RawPod pod : rawPods) {
      // Check across both task ports, and resource set ports:
      for (RawTask task : pod.getTasks().values()) {
        collectDuplicateEndpoints(task.getPorts(), allEndpointNames, duplicateEndpointNames);
      }
      if (pod.getResourceSets() != null) {
        for (RawResourceSet resourceSet : pod.getResourceSets().values()) {
          collectDuplicateEndpoints(
              resourceSet.getPorts(),
              allEndpointNames,
              duplicateEndpointNames);
        }
      }
    }
    if (!duplicateEndpointNames.isEmpty()) {
      throw new IllegalArgumentException(String.format(
          "Service has duplicate advertised ports across tasks: %s", duplicateEndpointNames));
    }
  }

  private static void collectDuplicateEndpoints(
      Map<String, RawPort> map, Set<String> seenEndpoints, Set<String> duplicateEndpoints)
  {
    if (map == null) {
      return;
    }
    for (Map.Entry<String, RawPort> entry : map.entrySet()) {
      if (!entry.getValue().isAdvertised()) {
        // Only check ports that are flagged as endpoints
        continue;
      }
      if (!seenEndpoints.add(entry.getKey())) {
        duplicateEndpoints.add(entry.getKey());
      }
    }
  }

  private static ReadinessCheckSpec convertReadinessCheck(RawReadinessCheck rawReadinessCheck) {
    DefaultReadinessCheckSpec.Builder checkBuilder = DefaultReadinessCheckSpec.newBuilder(
        rawReadinessCheck.getCmd(),
        rawReadinessCheck.getInterval(),
        rawReadinessCheck.getTimeout());
    if (rawReadinessCheck.getDelay() != null) {
      checkBuilder.delay(rawReadinessCheck.getDelay());
    }
    return checkBuilder.build();
  }

  private static DiscoverySpec convertDiscovery(RawDiscovery rawDiscovery) {
    Protos.DiscoveryInfo.Visibility visibility = Protos.DiscoveryInfo.Visibility.CLUSTER;
    if (rawDiscovery.getVisibility() != null) {
      visibility = Protos.DiscoveryInfo.Visibility.valueOf(rawDiscovery.getVisibility());
      if (visibility == null) {
        throw new IllegalArgumentException(String.format(
            "Visibility must be one of: %s",
            Arrays.asList(Protos.DiscoveryInfo.Visibility.values())));
      }
    }

    return DefaultDiscoverySpec.newBuilder()
        .prefix(rawDiscovery.getPrefix())
        .visibility(visibility)
        .build();
  }

  private static PodSpec convertPod(
      RawPod rawPod,
      ConfigTemplateReader configTemplateReader,
      String podName,
      Map<String, String> additionalEnv,
      String role,
      String principal,
      String user) throws Exception
  {

    DefaultPodSpec.Builder builder = DefaultPodSpec.newBuilder(
        podName,
        rawPod.getCount(),
        Collections.emptyList())
        .user(user)
        .preReservedRole(rawPod.getPreReservedRole())
        .sharePidNamespace(rawPod.getSharePidNamespace())
        .allowDecommission(rawPod.getAllowDecommission());

    List<String> networkNames = new ArrayList<>();
    List<RLimitSpec> rlimits = new ArrayList<>();
    for (Map.Entry<String, RawRLimit> entry : rawPod.getRLimits().entrySet()) {
      RawRLimit rawRLimit = entry.getValue();
      rlimits.add(new RLimitSpec(entry.getKey(), rawRLimit.getSoft(), rawRLimit.getHard()));
    }

    WriteOnceLinkedHashMap<String, RawNetwork> rawNetworks = rawPod.getNetworks();
    final Collection<NetworkSpec> networks = new ArrayList<>();
    if (MapUtils.isNotEmpty(rawNetworks)) {
      networks.addAll(rawNetworks.entrySet().stream()
          .map(rawNetworkEntry -> {
            String networkName = rawNetworkEntry.getKey();
            DcosConstants.warnIfUnsupportedNetwork(networkName);
            networkNames.add(networkName);
            RawNetwork rawNetwork = rawNetworks.get(networkName);
            return convertNetwork(networkName, rawNetwork, collatePorts(rawPod));
          })
          .collect(Collectors.toList()));
    }

    builder.image(rawPod.getImage())
        .networks(networks)
        .rlimits(rlimits);

    // Collect the resourceSets (if given)
    final Collection<ResourceSet> resourceSets = new ArrayList<>();
    WriteOnceLinkedHashMap<String, RawResourceSet> rawResourceSets = rawPod.getResourceSets();
    if (MapUtils.isNotEmpty(rawResourceSets)) {
      resourceSets.addAll(rawResourceSets.entrySet().stream()
          .map(rawResourceSetEntry -> {
            String rawResourceSetName = rawResourceSetEntry.getKey();
            RawResourceSet rawResourceSet = rawResourceSets.get(rawResourceSetName);
            return convertResourceSet(
                rawResourceSetName,
                rawResourceSet.getCpus(),
                rawResourceSet.getGpus(),
                rawResourceSet.getMemory(),
                rawResourceSet.getPorts(),
                rawResourceSet.getVolume(),
                rawResourceSet.getVolumes(),
                role,
                rawPod.getPreReservedRole(),
                principal,
                networkNames);
          })
          .collect(Collectors.toList()));
    }

    if (!rawPod.getSecrets().isEmpty()) {
      Collection<SecretSpec> secretSpecs = new ArrayList<>();
      secretSpecs.addAll(rawPod.getSecrets().values().stream()
          .map(v -> convertSecret(v))
          .collect(Collectors.toList()));

      builder.secrets(secretSpecs);
    }

    if (!rawPod.getHostVolumes().isEmpty()) {
      Collection<HostVolumeSpec> hostVolumeSpecs = new ArrayList<>();
      hostVolumeSpecs.addAll(rawPod.getHostVolumes().values().stream()
          .map(v -> convertHostVolume(v))
          .collect(Collectors.toList()));

      builder.hostVolumes(hostVolumeSpecs);
    }

    if (rawPod.getVolume() != null || !rawPod.getVolumes().isEmpty()) {
      Collection<VolumeSpec> volumeSpecs = new ArrayList<>(rawPod.getVolume() == null ?
          Collections.emptyList() :
          Arrays.asList(convertVolume(
              rawPod.getVolume(),
              role,
              rawPod.getPreReservedRole(),
              principal)));

      volumeSpecs.addAll(rawPod.getVolumes().values().stream()
          .map(v -> convertVolume(v, role, rawPod.getPreReservedRole(), principal))
          .collect(Collectors.toList()));

      builder.volumes(volumeSpecs);
    }

    // Parse the TaskSpecs
    List<TaskSpec> taskSpecs = new ArrayList<>();
    for (Map.Entry<String, RawTask> entry : rawPod.getTasks().entrySet()) {
      taskSpecs.add(convertTask(
          entry.getValue(),
          configTemplateReader,
          entry.getKey(),
          additionalEnv,
          resourceSets,
          role,
          rawPod.getPreReservedRole(),
          principal,
          networkNames));
    }
    builder.tasks(taskSpecs);

    Collection<URI> podUris = new ArrayList<>();
    for (String uriStr : rawPod.getUris()) {
      podUris.add(new URI(uriStr));
    }
    builder.uris(podUris);

    PlacementRule placementRule = MarathonConstraintParser.parse(podName, rawPod.getPlacement());
    if (!(placementRule instanceof PassthroughRule)) {
      builder.placementRule(placementRule);
    }

    return builder.build();
  }

  private static TaskSpec convertTask(
      RawTask rawTask,
      ConfigTemplateReader configTemplateReader,
      String taskName,
      Map<String, String> additionalEnv,
      Collection<ResourceSet> resourceSets,
      String role,
      String preReservedRole,
      String principal,
      Collection<String> networkNames) throws IOException
  {

    DefaultCommandSpec.Builder commandSpecBuilder = DefaultCommandSpec.newBuilder(additionalEnv)
        .environment(rawTask.getEnv())
        .value(rawTask.getCmd());

    List<ConfigFileSpec> configFiles = new ArrayList<>();
    if (rawTask.getConfigs() != null) {
      for (Map.Entry<String, RawConfig> configEntry : rawTask.getConfigs().entrySet()) {
        configFiles.add(DefaultConfigFileSpec.newBuilder()
            .name(configEntry.getKey())
            .relativePath(configEntry.getValue().getDest())
            .templateContent(configTemplateReader.read(configEntry.getValue().getTemplate()))
            .build());
      }
    }

    HealthCheckSpec healthCheckSpec = null;
    if (rawTask.getHealthCheck() != null) {
      RawHealthCheck rawHealthCheck = rawTask.getHealthCheck();
      healthCheckSpec = DefaultHealthCheckSpec.newBuilder()
          .command(rawHealthCheck.getCmd())
          .delay(rawHealthCheck.getDelay())
          .gracePeriod(rawHealthCheck.getGracePeriod())
          .interval(rawHealthCheck.getInterval())
          .maxConsecutiveFailures(rawHealthCheck.getMaxConsecutiveFailures())
          .timeout(rawHealthCheck.getTimeout())
          .build();
    }

    ReadinessCheckSpec readinessCheckSpec = null;
    if (rawTask.getReadinessCheck() != null) {
      readinessCheckSpec = convertReadinessCheck(rawTask.getReadinessCheck());
    }

    DiscoverySpec discoverySpec = null;
    if (rawTask.getDiscovery() != null) {
      discoverySpec = convertDiscovery(rawTask.getDiscovery());
    }

    Collection<TransportEncryptionSpec> transportEncryption = rawTask
        .getTransportEncryption()
        .stream()
        .map(task -> DefaultTransportEncryptionSpec.newBuilder()
            .name(task.getName())
            .type(TransportEncryptionSpec.Type.valueOf(task.getType()))
            .build())
        .collect(Collectors.toCollection(ArrayList::new));

    String goalString = StringUtils.upperCase(rawTask.getGoal());
    if (goalString.equals("FINISHED")) {
      throw new IllegalArgumentException(String.format(
          "Unsupported GoalState %s in task %s, expected one of: %s",
          goalString, taskName, Arrays.asList(GoalState.values())));
    }
    DefaultTaskSpec.Builder builder = DefaultTaskSpec.newBuilder()
        .commandSpec(commandSpecBuilder.build())
        .configFiles(configFiles)
        .discoverySpec(discoverySpec)
        .goalState(GoalState.valueOf(goalString))
        .essential(rawTask.isEssential())
        .healthCheckSpec(healthCheckSpec)
        .readinessCheckSpec(readinessCheckSpec)
        .name(taskName)
        .taskKillGracePeriodSeconds(rawTask.getTaskKillGracePeriodSeconds())
        .setTransportEncryption(transportEncryption)
        .name(taskName);

    if (!Strings.isNullOrEmpty(rawTask.getLabelsCsv())) {
      builder.taskLabels(convertLabels(rawTask.getLabelsCsv()));
    }

    if (StringUtils.isNotBlank(rawTask.getResourceSet())) {
      // Use resource set content:
      builder.resourceSet(
          resourceSets.stream()
              .filter(resourceSet -> resourceSet.getId().equals(rawTask.getResourceSet()))
              .findFirst().get());
    } else {
      // Use task content:
      builder.resourceSet(convertResourceSet(
          taskName + "-resource-set",
          rawTask.getCpus(),
          rawTask.getGpus(),
          rawTask.getMemory(),
          rawTask.getPorts(),
          rawTask.getVolume(),
          rawTask.getVolumes(),
          role,
          preReservedRole,
          principal,
          networkNames));
    }

    return builder.build();
  }

  private static DefaultResourceSet convertResourceSet(
      String id,
      Double cpus,
      Double gpus,
      Integer memory,
      WriteOnceLinkedHashMap<String, RawPort> rawPorts,
      RawVolume rawSingleVolume,
      WriteOnceLinkedHashMap<String, RawVolume> rawVolumes,
      String role,
      String preReservedRole,
      String principal,
      Collection<String> networkNames)
  {

    DefaultResourceSet.Builder resourceSetBuilder =
        DefaultResourceSet.newBuilder(role, preReservedRole, principal);

    if (rawVolumes != null) {
      if (rawSingleVolume != null) {
        throw new IllegalArgumentException(String.format(
            "Both 'volume' and 'volumes' may not be specified at the same time: %s", id));
      }
      // Note: volume names for multiple volumes are currently ignored
      for (RawVolume rawVolume : rawVolumes.values()) {
        resourceSetBuilder.addVolume(
            rawVolume.getType(),
            Double.valueOf(rawVolume.getSize()),
            rawVolume.getPath(),
            rawVolume.getProfiles());
      }
    }
    if (rawSingleVolume != null) {
      resourceSetBuilder.addVolume(
          rawSingleVolume.getType(),
          Double.valueOf(rawSingleVolume.getSize()),
          rawSingleVolume.getPath(),
          rawSingleVolume.getProfiles());
    }

    if (cpus != null) {
      resourceSetBuilder.cpus(cpus);
    }

    if (gpus != null) {
      resourceSetBuilder.gpus(gpus);
    }

    if (memory != null) {
      resourceSetBuilder.memory(Double.valueOf(memory));
    }

    if (rawPorts != null) {
      convertPorts(role, preReservedRole, principal, rawPorts, networkNames)
          .forEach(resourceSetBuilder::addResource);
    }

    return resourceSetBuilder
        .id(id)
        .build();
  }

  private static DefaultSecretSpec convertSecret(
      RawSecret rawSecret)
  {
    String filePath = rawSecret.getFilePath() == null && rawSecret.getEnvKey() == null
        ? rawSecret.getSecretPath()
        : rawSecret.getFilePath();

    return DefaultSecretSpec.newBuilder()
        .secretPath(rawSecret.getSecretPath())
        .envKey(rawSecret.getEnvKey())
        .filePath(filePath)
        .build();
  }

  private static DefaultHostVolumeSpec convertHostVolume(
      RawHostVolume rawHostVolume)
  {

    return DefaultHostVolumeSpec.newBuilder()
        .hostPath(rawHostVolume.getHostPath())
        .containerPath(rawHostVolume.getContainerPath())
        .build();
  }

  private static DefaultVolumeSpec convertVolume(
      RawVolume rawVolume, String role, String preReservedRole, String principal)
  {
    VolumeSpec.Type volumeTypeEnum;
    try {
      volumeTypeEnum = VolumeSpec.Type.valueOf(rawVolume.getType());
    } catch (Exception e) {
      throw new IllegalArgumentException(String.format(
          "Provided volume type '%s' for path '%s' is invalid. Expected type to be one of: %s",
          rawVolume.getType(), rawVolume.getPath(), Arrays.asList(VolumeSpec.Type.values())));
    }

    return volumeTypeEnum == VolumeSpec.Type.ROOT
        ? DefaultVolumeSpec.createRootVolume(
        rawVolume.getSize(),
        rawVolume.getPath(),
        role,
        preReservedRole,
        principal)
        : DefaultVolumeSpec.createMountVolume(
        rawVolume.getSize(),
        rawVolume.getPath(),
        rawVolume.getProfiles(),
        role,
        preReservedRole,
        principal);
  }

  private static Map<String, String> convertLabels(
      String rawLabelsCsv) throws IllegalArgumentException
  {
    List<String[]> kvs = Arrays.stream(rawLabelsCsv.split(","))
        .map(s -> s.split(":", 2))
        .collect(Collectors.toList());
    kvs.forEach(kv -> {
      if (kv.length != 2) {
        throw new IllegalArgumentException(String.format(
            "Illegal label string, got %s, should be " +
                "comma-seperated key value pairs (seperated by colons)." +
                " For example: k_0:v_0,k_1:v_1,...,k_n:v_n",
            rawLabelsCsv
        ));
      }
    });

    return kvs.stream().collect(Collectors.toMap(s -> s[0], s -> s[1]));
  }

  private static DefaultNetworkSpec convertNetwork(
      String networkName,
      RawNetwork rawNetwork,
      Collection<Integer> ports) throws IllegalArgumentException
  {
    DefaultNetworkSpec.Builder builder = DefaultNetworkSpec.newBuilder().networkName(networkName);
    boolean supportsPortMapping = DcosConstants.networkSupportsPortMapping(networkName);
    if (!supportsPortMapping && rawNetwork.numberOfPortMappings() > 0) {
      throw new IllegalArgumentException(String.format(
          "Virtual Network %s doesn't support container->host port mapping", networkName));
    }
    if (supportsPortMapping) {
      Map<Integer, Integer> portMap = new HashMap<>();
      if (rawNetwork.numberOfPortMappings() > 0) {
        // zip the host and container ports together
        portMap = IntStream.range(0, rawNetwork.numberOfPortMappings())
            .boxed().collect(Collectors
                .toMap(rawNetwork.getHostPorts()::get, rawNetwork.getContainerPorts()::get));
      }
      if (ports.size() > 0) {
        for (Integer port : ports) {
          // iterate over the task ports and if they aren't being remapped do a 1:1 (host:container) mapping
          if (!portMap.keySet().contains(port)) {
            portMap.put(port, port);
          }
        }
      }
      builder.portMappings(portMap);
    }

    if (!Strings.isNullOrEmpty(rawNetwork.getLabelsCsv())) {
      builder.networkLabels(convertLabels(rawNetwork.getLabelsCsv()));
    }

    return builder.build();
  }

  private static Collection<Integer> collatePorts(RawPod rawPod) {
    List<Integer> ports = new ArrayList<>();
    WriteOnceLinkedHashMap<String, RawResourceSet> rawResourceSets = rawPod.getResourceSets();
    if (MapUtils.isNotEmpty(rawResourceSets)) {
      // iterate over the resource sets and add the ports to our collection, if there are any
      for (RawResourceSet rawResourceSet : rawResourceSets.values()) {
        if (rawResourceSet.getPorts() != null) {
          rawResourceSet.getPorts().values().forEach(rawPort -> ports.add(rawPort.getPort()));
        }
      }
    }

    for (RawTask rawTask : rawPod.getTasks().values()) {
      WriteOnceLinkedHashMap<String, RawPort> rawPorts = rawTask.getPorts();
      if (rawPorts != null) {
        rawPorts.values().forEach(rawPort -> ports.add(rawPort.getPort()));
      }
    }
    return ports;
  }

  private static Collection<PortSpec> convertPorts(
      String role,
      String preReservedRole,
      String principal,
      WriteOnceLinkedHashMap<String, RawPort> rawPorts,
      Collection<String> networkNames)
  {
    Collection<PortSpec> portSpecs = new ArrayList<>();
    Set<Integer> ports = new HashSet<>();
    Protos.Value.Builder portsValueBuilder =
        Protos.Value.newBuilder().setType(Protos.Value.Type.RANGES);

    for (Map.Entry<String, RawPort> portEntry : rawPorts.entrySet()) {
      String name = portEntry.getKey();
      RawPort rawPort = portEntry.getValue();
      boolean ok = ports.add(rawPort.getPort());
      if (!ok && rawPort.getPort() > 0) {
        throw new IllegalArgumentException(String.format(
            "Cannot have duplicate port values: Task has " +
            "multiple ports with value %d", rawPort.getPort()));
      }
      Protos.Value.Builder portValueBuilder = Protos.Value.newBuilder()
          .setType(Protos.Value.Type.RANGES);
      portValueBuilder.getRangesBuilder().addRangeBuilder()
          .setBegin(rawPort.getPort())
          .setEnd(rawPort.getPort());
      portsValueBuilder.mergeRanges(portValueBuilder.getRanges());

      final Protos.DiscoveryInfo.Visibility visibility =
          rawPort.isAdvertised() ?
              Constants.DISPLAYED_PORT_VISIBILITY : Constants.OMITTED_PORT_VISIBILITY;

      PortSpec.Builder portSpecBuilder;
      if (rawPort.getVip() == null) {
        portSpecBuilder = PortSpec.newBuilder();
      } else {
        final RawVip rawVip = rawPort.getVip();
        // Check that VIP names dont conflict with other port names. In practice this is only an issue when a
        // custom prefix/name is defined for the VIP as uniqueness is already enforced for port names.
        final String vipName = StringUtils.isEmpty(rawVip.getPrefix()) ? name : rawVip.getPrefix();
        RawPort matchingRawPort = rawPorts.get(vipName);
        if (matchingRawPort != null && matchingRawPort != rawPort) {
          throw new IllegalArgumentException(String.format(
              "Provided VIP prefix '%s' in port '%s' conflicts with other port also named '%s'. " +
                  "Expected VIP prefix to not collide with other ports' names.",
              vipName, name, vipName));
        }
        // Note: Multiple VIPs may share prefixes with each other. For example if one wants the VIP hostnames,
        // across multiple ports, to reflect the host that's serving the port.

        portSpecBuilder = NamedVIPSpec.newBuilder()
            // NamedVIPSpec settings:
            .protocol(DcosConstants.DEFAULT_IP_PROTOCOL)
            .vipName(StringUtils.isEmpty(rawVip.getPrefix()) ? name : rawVip.getPrefix())
            .vipPort(rawVip.getPort());
      }
      portSpecBuilder
          // PortSpec settings:
          .envKey(rawPort.getEnvKey())
          .portName(name)
          .visibility(visibility)
          .networkNames(networkNames)
          // ResourceSpec settings:
          .value(portValueBuilder.build())
          .role(role)
          .preReservedRole(preReservedRole)
          .principal(principal);
      portSpecs.add(portSpecBuilder.build());
    }
    return portSpecs;
  }

  /**
   * This method provides the correct role for resources to use when reserving refined resources.
   *
   * @param preReservedRole The role of pre-reserved resources
   * @param role            The role of the service
   * @return The final role which refined resources should use
   */
  private static String getPodRole(String preReservedRole, String role) {
    if (preReservedRole == null || preReservedRole.equals(Constants.ANY_ROLE)) {
      return role;
    } else {
      return preReservedRole + "/" + role;
    }
  }
}
