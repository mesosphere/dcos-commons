package com.mesosphere.sdk.specification.yaml;

import com.google.common.base.Strings;
import com.mesosphere.sdk.dcos.DcosConstants;
import org.apache.commons.collections.MapUtils;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;

import com.google.common.annotations.VisibleForTesting;
import com.mesosphere.sdk.config.TaskEnvRouter;
import com.mesosphere.sdk.offer.Constants;
import com.mesosphere.sdk.offer.evaluate.placement.MarathonConstraintParser;
import com.mesosphere.sdk.offer.evaluate.placement.PassthroughRule;
import com.mesosphere.sdk.offer.evaluate.placement.PlacementRule;
import com.mesosphere.sdk.scheduler.SchedulerFlags;
import com.mesosphere.sdk.scheduler.SchedulerUtils;
import com.mesosphere.sdk.specification.*;
import com.mesosphere.sdk.specification.util.RLimit;
import org.apache.mesos.Protos;
import org.apache.mesos.Protos.DiscoveryInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Adapter utilities for mapping Raw YAML objects to internal objects.
 */
public class YAMLToInternalMappers {
    private static final String DEFAULT_VIP_PROTOCOL = "tcp";

    public static final DiscoveryInfo.Visibility PUBLIC_VIP_VISIBILITY = DiscoveryInfo.Visibility.EXTERNAL;

    private static final Logger LOGGER = LoggerFactory.getLogger(YAMLToInternalMappers.class);

    /**
     * Implementation for reading files from disk. Meant to be overridden by a mock in tests.
     */
    @VisibleForTesting
    public static class FileReader {
        public String read(String path) throws IOException {
            return FileUtils.readFileToString(new File(path), StandardCharsets.UTF_8);
        }
    }

    /**
     * Converts the provided YAML {@link RawServiceSpec} into a new {@link ServiceSpec}.
     *
     * @param rawServiceSpec the raw service specification representing a YAML file
     * @param fileReader the file reader to be used for reading template files, allowing overrides for testing
     * @throws Exception if the conversion fails
     */
    public static DefaultServiceSpec from(
            RawServiceSpec rawServiceSpec,
            SchedulerFlags schedulerFlags,
            TaskEnvRouter taskEnvRouter,
            FileReader fileReader) throws Exception {
        verifyDistinctDiscoveryPrefixes(rawServiceSpec.getPods().values());

        String role = SchedulerUtils.getServiceRole(rawServiceSpec);
        String principal = SchedulerUtils.getServicePrincipal(rawServiceSpec);

        DefaultServiceSpec.Builder builder = DefaultServiceSpec.newBuilder()
                .name(SchedulerUtils.getServiceName(rawServiceSpec))
                .role(role)
                .principal(principal)
                .apiPort(SchedulerUtils.getApiPort(rawServiceSpec, schedulerFlags))
                .zookeeperConnection(SchedulerUtils.getZkHost(rawServiceSpec, schedulerFlags))
                .webUrl(rawServiceSpec.getWebUrl());

        // Add all pods
        List<PodSpec> pods = new ArrayList<>();
        final LinkedHashMap<String, RawPod> rawPods = rawServiceSpec.getPods();
        for (Map.Entry<String, RawPod> entry : rawPods.entrySet()) {
            pods.add(from(
                    entry.getValue(),
                    fileReader,
                    entry.getKey(),
                    taskEnvRouter.getConfig(entry.getKey()),
                    role,
                    principal,
                    schedulerFlags.getExecutorURI()));

        }
        builder.pods(pods);

        return builder.build();
    }

    private static void verifyDistinctDiscoveryPrefixes(Collection<RawPod> rawPods) {
        // Verify that tasks in separate pods don't share a discovery prefix.
        Map<String, Long> dnsPrefixCounts = rawPods.stream()
                .flatMap(p -> p.getTasks().values().stream()
                        .map(t -> t.getDiscovery())
                        .filter(d -> d != null)
                        .map(d -> d.getPrefix())
                        .filter(prefix -> prefix != null)
                        .distinct())
                .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()));
        List<String> dnsNameDuplicates = dnsPrefixCounts.entrySet().stream()
                .filter(e -> e.getValue() > 1)
                .map(e -> e.getKey())
                .collect(Collectors.toList());
        if (!dnsNameDuplicates.isEmpty()) {
            throw new IllegalArgumentException(String.format(
                    "Tasks in different pods cannot share DNS names: %s", dnsNameDuplicates));
        }
    }

    private static ReadinessCheckSpec from(RawReadinessCheck rawReadinessCheck) {
        return DefaultReadinessCheckSpec.newBuilder()
                .command(rawReadinessCheck.getCmd())
                .delay(rawReadinessCheck.getDelay() == null ? Integer.valueOf(0) : rawReadinessCheck.getDelay())
                .interval(rawReadinessCheck.getInterval())
                .timeout(rawReadinessCheck.getTimeout())
                .build();
    }

    private static DiscoverySpec from(RawDiscovery rawDiscovery) {
        Protos.DiscoveryInfo.Visibility visibility = Protos.DiscoveryInfo.Visibility.CLUSTER;
        if (rawDiscovery.getVisibility() != null) {
            switch (rawDiscovery.getVisibility()) {
                case "FRAMEWORK":
                    visibility = Protos.DiscoveryInfo.Visibility.FRAMEWORK;
                    break;
                case "CLUSTER":
                    visibility = Protos.DiscoveryInfo.Visibility.CLUSTER;
                    break;
                case "EXTERNAL":
                    visibility = Protos.DiscoveryInfo.Visibility.EXTERNAL;
                    break;
                default:
                    throw new IllegalArgumentException("Visibility must be one of: {FRAMEWORK, CLUSTER, EXTERNAL}");
            }
        }

        return new DefaultDiscoverySpec(rawDiscovery.getPrefix(), visibility);
    }

    private static PodSpec from(
            RawPod rawPod,
            FileReader fileReader,
            String podName,
            Map<String, String> additionalEnv,
            String role,
            String principal,
            String executorUri) throws Exception {
        DefaultPodSpec.Builder builder = DefaultPodSpec.newBuilder(executorUri)
                .count(rawPod.getCount())
                .type(podName)
                .user(rawPod.getUser())
                .preReservedRole(rawPod.getPreReservedRole());

        // ContainerInfo parsing section: we allow Networks and RLimits to be within RawContainer, but new
        // functionality (CNI or otherwise) will land in the pod-level only.
        RawContainerInfoProvider containerInfoProvider = null;
        List<String> networkNames = new ArrayList<>();
        if (rawPod.getImage() != null || !rawPod.getNetworks().isEmpty() || !rawPod.getRLimits().isEmpty()) {
            if (rawPod.getContainer() != null) {
                throw new IllegalArgumentException(String.format("You may define container settings directly under the "
                        + "pod %s or under %s:container, but not both.", podName, podName));
            }
            containerInfoProvider = rawPod;
        } else if (rawPod.getContainer() != null) {
            containerInfoProvider = rawPod.getContainer();
        }

        if (containerInfoProvider != null) {
            List<RLimit> rlimits = new ArrayList<>();
            for (Map.Entry<String, RawRLimit> entry : containerInfoProvider.getRLimits().entrySet()) {
                RawRLimit rawRLimit = entry.getValue();
                rlimits.add(new RLimit(entry.getKey(), rawRLimit.getSoft(), rawRLimit.getHard()));
            }

            WriteOnceLinkedHashMap<String, RawNetwork> rawNetworks = containerInfoProvider.getNetworks();
            final Collection<NetworkSpec> networks = new ArrayList<>();
            if (MapUtils.isNotEmpty(rawNetworks)) {
                networks.addAll(rawNetworks.entrySet().stream()
                        .map(rawNetworkEntry -> {
                            String networkName = rawNetworkEntry.getKey();
                            if (!DcosConstants.isSupportedNetwork(networkName)) {
                                LOGGER.warn(String.format("Virtual network %s is not currently supported, you " +
                                        "may experience unexpected behavior", networkName));
                            }
                            networkNames.add(networkName);
                            RawNetwork rawNetwork = rawNetworks.get(networkName);
                            return from(networkName, rawNetwork, collatePorts(rawPod));
                        })
                        .collect(Collectors.toList()));
            }

            builder.image(containerInfoProvider.getImage())
                    .networks(networks)
                    .rlimits(rlimits);

        }

        // Collect the resourceSets (if given)
        final Collection<ResourceSet> resourceSets = new ArrayList<>();
        WriteOnceLinkedHashMap<String, RawResourceSet> rawResourceSets = rawPod.getResourceSets();
        if (MapUtils.isNotEmpty(rawResourceSets)) {
            resourceSets.addAll(rawResourceSets.entrySet().stream()
                    .map(rawResourceSetEntry -> {
                        String rawResourceSetName = rawResourceSetEntry.getKey();
                        RawResourceSet rawResourceSet = rawResourceSets.get(rawResourceSetName);
                        return from(
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
                    .map(v -> from(v))
                    .collect(Collectors.toList()));

            builder.secrets(secretSpecs);
        }

        if (rawPod.getVolume() != null || !rawPod.getVolumes().isEmpty()) {
            Collection<VolumeSpec> volumeSpecs = new ArrayList<>(rawPod.getVolume() == null ?
                    Collections.emptyList() :
                    Arrays.asList(from(rawPod.getVolume(), role, rawPod.getPreReservedRole(), principal)));

            volumeSpecs.addAll(rawPod.getVolumes().values().stream()
                    .map(v -> from(v, role, rawPod.getPreReservedRole(), principal))
                    .collect(Collectors.toList()));

            builder.volumes(volumeSpecs);
        }

        // Parse the TaskSpecs
        List<TaskSpec> taskSpecs = new ArrayList<>();
        for (Map.Entry<String, RawTask> entry : rawPod.getTasks().entrySet()) {
            taskSpecs.add(from(
                    entry.getValue(),
                    fileReader,
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

    private static TaskSpec from(
            RawTask rawTask,
            FileReader fileReader,
            String taskName,
            Map<String, String> additionalEnv,
            Collection<ResourceSet> resourceSets,
            String role,
            String preReservedRole,
            String principal,
            Collection<String> networkNames) throws Exception {

        DefaultCommandSpec.Builder commandSpecBuilder = DefaultCommandSpec.newBuilder(additionalEnv)
                .environment(rawTask.getEnv())
                .value(rawTask.getCmd());

        List<ConfigFileSpec> configFiles = new ArrayList<>();
        if (rawTask.getConfigs() != null) {
            for (Map.Entry<String, RawConfig> configEntry : rawTask.getConfigs().entrySet()) {
                configFiles.add(new DefaultConfigFileSpec(
                        configEntry.getKey(),
                        configEntry.getValue().getDest(),
                        fileReader.read(configEntry.getValue().getTemplate())));
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
           readinessCheckSpec = from(rawTask.getReadinessCheck());
        }

        DiscoverySpec discoverySpec = null;
        if (rawTask.getDiscovery() != null) {
            discoverySpec = from(rawTask.getDiscovery());
        }

        DefaultTaskSpec.Builder builder = DefaultTaskSpec.newBuilder()
                .commandSpec(commandSpecBuilder.build())
                .configFiles(configFiles)
                .discoverySpec(discoverySpec)
                .goalState(GoalState.valueOf(StringUtils.upperCase(rawTask.getGoal())))
                .healthCheckSpec(healthCheckSpec)
                .readinessCheckSpec(readinessCheckSpec)
                .name(taskName);

        if (StringUtils.isNotBlank(rawTask.getResourceSet())) {
            // Use resource set content:
            builder.resourceSet(
                    resourceSets.stream()
                            .filter(resourceSet -> resourceSet.getId().equals(rawTask.getResourceSet()))
                            .findFirst().get());
        } else {
            // Use task content:
            builder.resourceSet(from(
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

    private static DefaultResourceSet from(
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
            Collection<String> networkNames) {

        DefaultResourceSet.Builder resourceSetBuilder = DefaultResourceSet.newBuilder(role, preReservedRole, principal);

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
                        rawVolume.getPath());
            }
        }
        if (rawSingleVolume != null) {
            resourceSetBuilder.addVolume(
                    rawSingleVolume.getType(),
                    Double.valueOf(rawSingleVolume.getSize()),
                    rawSingleVolume.getPath());
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
            from(role, preReservedRole, principal, rawPorts, networkNames).getPortSpecs()
                    .forEach(resourceSetBuilder::addResource);
        }

        return resourceSetBuilder
                .id(id)
                .build();
    }

    private static DefaultSecretSpec from(RawSecret rawSecret) {
        String filePath =  (rawSecret.getFilePath() == null && rawSecret.getEnvKey() == null) ?
                rawSecret.getSecretPath() : rawSecret.getFilePath();

        return new DefaultSecretSpec(
                rawSecret.getSecretPath(),
                rawSecret.getEnvKey(),
                filePath);
    }

    private static DefaultVolumeSpec from(RawVolume rawVolume, String role, String preReservedRole, String principal) {
        VolumeSpec.Type volumeTypeEnum;
        try {
            volumeTypeEnum = VolumeSpec.Type.valueOf(rawVolume.getType());
        } catch (Exception e) {
            throw new IllegalArgumentException(String.format(
                    "Provided volume type '%s' for path '%s' is invalid. Expected type to be one of: %s",
                    rawVolume.getType(), rawVolume.getPath(), Arrays.asList(VolumeSpec.Type.values())));
        }

        return new DefaultVolumeSpec(
                rawVolume.getSize(),
                volumeTypeEnum,
                rawVolume.getPath(),
                role,
                preReservedRole,
                principal,
                "DISK_SIZE");
    }

    private static DefaultNetworkSpec from(
            String networkName,
            RawNetwork rawNetwork,
            Collection<Integer> ports) throws IllegalArgumentException {
        DefaultNetworkSpec.Builder builder = DefaultNetworkSpec.newBuilder().networkName(networkName);
        boolean supportsPortMapping = DcosConstants.networkSupportsPortMapping(networkName);
        if (!supportsPortMapping && rawNetwork.numberOfPortMappings() > 0) {
            throw new IllegalArgumentException(String.format(
                    "Virtual Network %s doesn't support container->host port mapping", networkName));
        }
        if (supportsPortMapping) {
            Map<Integer, Integer> portMap = new HashMap<>();  // hostPort:containerPort
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
        } else {
            builder.portMappings(Collections.emptyMap());
        }

        if (!Strings.isNullOrEmpty(rawNetwork.getLabelsCsv())) {
            builder.networkLabels(rawNetwork.getValidadedLabels()
                    .stream().collect(Collectors.toMap(s -> s[0], s -> s[1])));
        } else {
            builder.networkLabels(Collections.emptyMap());
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

    private static boolean maybeUsePortResources(Collection<String> networkNames) {
        for (String networkName : networkNames) {
            if (DcosConstants.networkSupportsPortMapping(networkName)) {
                return true;
            }
        }
        return networkNames.size() == 0;  // if we have no networks, we want to use port resources
    }

    private static PortsSpec from(
            String role,
            String preReservedRole,
            String principal,
            WriteOnceLinkedHashMap<String, RawPort> rawPorts,
                                     Collection<String> networkNames) {
        Collection<PortSpec> portSpecs = new ArrayList<>();
        Protos.Value.Builder portsValueBuilder = Protos.Value.newBuilder().setType(Protos.Value.Type.RANGES);
        String envKey = null;
        for (Map.Entry<String, RawPort> portEntry : rawPorts.entrySet()) {
            String name = portEntry.getKey();
            RawPort rawPort = portEntry.getValue();
            Protos.Value.Builder portValueBuilder = Protos.Value.newBuilder()
                    .setType(Protos.Value.Type.RANGES);
            portValueBuilder.getRangesBuilder().addRangeBuilder()
                    .setBegin(rawPort.getPort())
                    .setEnd(rawPort.getPort());
            portsValueBuilder.mergeRanges(portValueBuilder.getRanges());
            if (envKey == null) {
                envKey = rawPort.getEnvKey();
            }

            if (rawPort.getVip() != null) {
                final RawVip rawVip = rawPort.getVip();
                final String protocol =
                        StringUtils.isEmpty(rawVip.getProtocol()) ? DEFAULT_VIP_PROTOCOL : rawVip.getProtocol();
                final String vipName = StringUtils.isEmpty(rawVip.getPrefix()) ? name : rawVip.getPrefix();
                NamedVIPSpec namedVIPSpec = new NamedVIPSpec(
                        portValueBuilder.build(),
                        role,
                        preReservedRole,
                        principal,
                        rawPort.getEnvKey(),
                        name,
                        protocol,
                        toVisibility(rawVip.isAdvertised()),
                        vipName,
                        rawVip.getPort(),
                        networkNames);
                portSpecs.add(namedVIPSpec);
            } else {
                portSpecs.add(new PortSpec(
                        portValueBuilder.build(),
                        role,
                        preReservedRole,
                        principal,
                        rawPort.getEnvKey(),
                        name,
                        networkNames));
            }
        }
        return new PortsSpec(
                Constants.PORTS_RESOURCE_TYPE, portsValueBuilder.build(), role, principal, envKey, portSpecs);
    }

    /**
     * This visibility information isn't currently used by DC/OS Service Discovery. At the moment it's only enforced in
     * our own {@link com.mesosphere.sdk.api.EndpointsResource}.
     */
    private static DiscoveryInfo.Visibility toVisibility(Boolean rawIsVisible) {
        if (rawIsVisible == null) {
            return PUBLIC_VIP_VISIBILITY;
        }
        return rawIsVisible ? DiscoveryInfo.Visibility.EXTERNAL : DiscoveryInfo.Visibility.CLUSTER;
    }
}
