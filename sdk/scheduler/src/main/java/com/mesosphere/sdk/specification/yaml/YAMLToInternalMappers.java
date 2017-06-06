package com.mesosphere.sdk.specification.yaml;

import com.mesosphere.sdk.dcos.DcosConstants;
import org.apache.commons.collections.MapUtils;
import org.apache.commons.lang3.StringUtils;

import com.mesosphere.sdk.config.ConfigNamespace;
import com.mesosphere.sdk.config.DefaultTaskConfigRouter;
import com.mesosphere.sdk.config.TaskConfigRouter;
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

import java.net.URI;
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
     * Converts the provided YAML {@link RawServiceSpec} into a new {@link ServiceSpec}.
     *
     * @param rawSvcSpec the raw service specification representing a YAML file
     * @param fileReader the file reader to be used for reading template files, allowing overrides for testing
     * @throws Exception if the conversion fails
     */
    static DefaultServiceSpec from(
            RawServiceSpec rawSvcSpec,
            SchedulerFlags schedulerFlags,
            YAMLServiceSpecFactory.FileReader fileReader) throws Exception {
        RawScheduler rawScheduler = rawSvcSpec.getScheduler();
        String role = null;
        String principal = null;
        Integer apiPort = null;
        String zookeeper = null;

        if (rawScheduler != null) {
            principal = rawScheduler.getPrincipal();
            role = rawScheduler.getRole();
            apiPort = rawScheduler.getApiPort();
            zookeeper = rawScheduler.getZookeeper();
        }
        // Fall back to defaults as needed, if either RawScheduler or a given RawScheduler field is missing:
        if (StringUtils.isEmpty(role)) {
            role = SchedulerUtils.nameToRole(rawSvcSpec.getName());
        }
        if (StringUtils.isEmpty(principal)) {
            principal = SchedulerUtils.nameToPrincipal(rawSvcSpec.getName());
        }
        if (apiPort == null) {
            apiPort = schedulerFlags.getApiServerPort();
        }
        if (StringUtils.isEmpty(zookeeper)) {
            zookeeper = SchedulerUtils.defaultZkHost();
        }

        verifyRawSpec(rawSvcSpec);

        DefaultServiceSpec.Builder builder = DefaultServiceSpec.newBuilder()
                .name(rawSvcSpec.getName())
                .role(role)
                .principal(principal)
                .apiPort(apiPort)
                .zookeeperConnection(zookeeper)
                .webUrl(rawSvcSpec.getWebUrl());

        // Add all pods
        List<PodSpec> pods = new ArrayList<>();
        final LinkedHashMap<String, RawPod> rawPods = rawSvcSpec.getPods();
        TaskConfigRouter taskConfigRouter = new DefaultTaskConfigRouter();
        for (Map.Entry<String, RawPod> entry : rawPods.entrySet()) {
            pods.add(from(
                    entry.getValue(),
                    fileReader,
                    entry.getKey(),
                    taskConfigRouter.getConfig(entry.getKey()),
                    role,
                    principal,
                    schedulerFlags.getExecutorURI()));

        }
        builder.pods(pods);

        return builder.build();
    }

    private static void verifyRawSpec(RawServiceSpec rawServiceSpec) {
        // Verify that tasks in separate pods don't share a discovery prefix.
        Map<String, Long> dnsPrefixCounts = rawServiceSpec.getPods().values().stream()
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
                .delay(rawReadinessCheck.getDelay())
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
            YAMLServiceSpecFactory.FileReader fileReader,
            String podName,
            ConfigNamespace configNamespace,
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

        boolean usePortResources = maybeUsePortResources(networkNames);

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
                                usePortResources);
                    })
                    .collect(Collectors.toList()));
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
                    configNamespace,
                    resourceSets,
                    role,
                    rawPod.getPreReservedRole(),
                    principal,
                    usePortResources));
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
            YAMLServiceSpecFactory.FileReader fileReader,
            String taskName,
            ConfigNamespace configNamespace,
            Collection<ResourceSet> resourceSets,
            String role,
            String preReservedRole,
            String principal,
            boolean usePortResources) throws Exception {

        DefaultCommandSpec.Builder commandSpecBuilder = DefaultCommandSpec.newBuilder(configNamespace)
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
                    usePortResources));
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
            boolean usePortResources) {

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

        if (rawPorts != null && usePortResources) {
            from(role, preReservedRole, principal, rawPorts).getPortSpecs()
                    .forEach(portSpec -> resourceSetBuilder.addResource(portSpec));
        }

        return resourceSetBuilder
                .id(id)
                .build();
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

        //TODO(arand) implement network labels

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
            WriteOnceLinkedHashMap<String, RawPort> rawPorts) {
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
                portSpecs.add(new NamedVIPSpec(
                        portValueBuilder.build(),
                        role,
                        preReservedRole,
                        principal,
                        rawPort.getEnvKey(),
                        name,
                        protocol,
                        toVisibility(rawVip.isAdvertised()),
                        vipName,
                        rawVip.getPort()));
            } else {
                portSpecs.add(new PortSpec(
                        portValueBuilder.build(),
                        role,
                        preReservedRole,
                        principal,
                        rawPort.getEnvKey(),
                        name));
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
