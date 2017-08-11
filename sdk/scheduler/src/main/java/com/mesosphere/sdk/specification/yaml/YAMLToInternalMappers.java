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
    public static DefaultServiceSpec convertServiceSpec(
            RawServiceSpec rawServiceSpec,
            SchedulerFlags schedulerFlags,
            TaskEnvRouter taskEnvRouter,
            FileReader fileReader) throws Exception {
        verifyDistinctDiscoveryPrefixes(rawServiceSpec.getPods().values());
        verifyDistinctEndpointNames(rawServiceSpec.getPods().values());

        String role = SchedulerUtils.getServiceRole(rawServiceSpec);
        String principal = SchedulerUtils.getServicePrincipal(rawServiceSpec);
        String user = SchedulerUtils.getUser(rawServiceSpec);

        DefaultServiceSpec.Builder builder = DefaultServiceSpec.newBuilder()
                .name(SchedulerUtils.getServiceName(rawServiceSpec))
                .role(role)
                .principal(principal)
                .zookeeperConnection(SchedulerUtils.getZkHost(rawServiceSpec, schedulerFlags))
                .webUrl(rawServiceSpec.getWebUrl())
                .user(user);

        // Add all pods
        List<PodSpec> pods = new ArrayList<>();
        final LinkedHashMap<String, RawPod> rawPods = rawServiceSpec.getPods();
        for (Map.Entry<String, RawPod> entry : rawPods.entrySet()) {
            String podName = entry.getKey();
            RawPod rawPod = entry.getValue();
            pods.add(convertPod(
                    rawPod,
                    fileReader,
                    podName,
                    taskEnvRouter.getConfig(podName),
                    getRole(rawPod.getPreReservedRole(), role),
                    principal,
                    schedulerFlags.getExecutorURI(),
                    user));

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
                    collectDuplicateEndpoints(resourceSet.getPorts(), allEndpointNames, duplicateEndpointNames);
                }
            }
        }
        if (!duplicateEndpointNames.isEmpty()) {
            throw new IllegalArgumentException(String.format(
                    "Service has duplicate advertised ports across tasks: %s", duplicateEndpointNames));
        }
    }

    private static void collectDuplicateEndpoints(
            Map<String, RawPort> map, Set<String> seenEndpoints, Set<String> duplicateEndpoints) {
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
        return DefaultReadinessCheckSpec.newBuilder()
                .command(rawReadinessCheck.getCmd())
                .delay(rawReadinessCheck.getDelay() == null ? Integer.valueOf(0) : rawReadinessCheck.getDelay())
                .interval(rawReadinessCheck.getInterval())
                .timeout(rawReadinessCheck.getTimeout())
                .build();
    }

    private static DiscoverySpec convertDiscovery(RawDiscovery rawDiscovery) {
        Protos.DiscoveryInfo.Visibility visibility = Protos.DiscoveryInfo.Visibility.CLUSTER;
        if (rawDiscovery.getVisibility() != null) {
            visibility = Protos.DiscoveryInfo.Visibility.valueOf(rawDiscovery.getVisibility());
            if (visibility == null) {
                throw new IllegalArgumentException(String.format(
                        "Visibility must be one of: %s", Arrays.asList(Protos.DiscoveryInfo.Visibility.values())));
            }
        }

        return new DefaultDiscoverySpec(rawDiscovery.getPrefix(), visibility);
    }

    private static PodSpec convertPod(
            RawPod rawPod,
            FileReader fileReader,
            String podName,
            Map<String, String> additionalEnv,
            String role,
            String principal,
            String executorUri,
            String user) throws Exception {
        DefaultPodSpec.Builder builder = DefaultPodSpec.newBuilder(executorUri)
                .count(rawPod.getCount())
                .type(podName)
                .user(user)
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
                            DcosConstants.warnIfUnsupportedNetwork(networkName);
                            networkNames.add(networkName);
                            RawNetwork rawNetwork = rawNetworks.get(networkName);
                            return convertNetwork(networkName, rawNetwork, collatePorts(rawPod));
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

        if (rawPod.getVolume() != null || !rawPod.getVolumes().isEmpty()) {
            Collection<VolumeSpec> volumeSpecs = new ArrayList<>(rawPod.getVolume() == null ?
                    Collections.emptyList() :
                    Arrays.asList(convertVolume(rawPod.getVolume(), role, rawPod.getPreReservedRole(), principal)));

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

    private static TaskSpec convertTask(
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
           readinessCheckSpec = convertReadinessCheck(rawTask.getReadinessCheck());
        }

        DiscoverySpec discoverySpec = null;
        if (rawTask.getDiscovery() != null) {
            discoverySpec = convertDiscovery(rawTask.getDiscovery());
        }

        Collection<TransportEncryptionSpec> transportEncryption = rawTask
                .getTransportEncryption()
                .stream()
                .map(task -> new DefaultTransportEncryptionSpec.Builder()
                        .name(task.getName())
                        .type(TransportEncryptionSpec.Type.valueOf(task.getType()))
                        .build())
                .collect(Collectors.toCollection(ArrayList::new));

        DefaultTaskSpec.Builder builder = DefaultTaskSpec.newBuilder()
                .commandSpec(commandSpecBuilder.build())
                .configFiles(configFiles)
                .discoverySpec(discoverySpec)
                .goalState(GoalState.valueOf(StringUtils.upperCase(rawTask.getGoal())))
                .healthCheckSpec(healthCheckSpec)
                .readinessCheckSpec(readinessCheckSpec)
                .setTransportEncryption(transportEncryption)
                .name(taskName);

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
            convertPorts(role, preReservedRole, principal, rawPorts, networkNames)
                    .forEach(resourceSetBuilder::addResource);
        }

        return resourceSetBuilder
                .id(id)
                .build();
    }

    private static DefaultSecretSpec convertSecret(
            RawSecret rawSecret) {
        String filePath =  (rawSecret.getFilePath() == null && rawSecret.getEnvKey() == null) ?
                rawSecret.getSecretPath() : rawSecret.getFilePath();

        return new DefaultSecretSpec(
                rawSecret.getSecretPath(),
                rawSecret.getEnvKey(),
                filePath);
    }

    private static DefaultVolumeSpec convertVolume(
            RawVolume rawVolume, String role, String preReservedRole, String principal) {
        VolumeSpec.Type volumeTypeEnum;
        try {
            volumeTypeEnum = VolumeSpec.Type.valueOf(rawVolume.getType());
        } catch (Exception e) {
            throw new IllegalArgumentException(String.format(
                    "Provided volume type '%s' for path '%s' is invalid. Expected type to be one of: %s",
                    rawVolume.getType(), rawVolume.getPath(), Arrays.asList(VolumeSpec.Type.values())));
        }

        return new DefaultVolumeSpec(
                rawVolume.getSize(), volumeTypeEnum, rawVolume.getPath(), role, preReservedRole, principal);
    }

    private static DefaultNetworkSpec convertNetwork(
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
            builder.networkLabels(rawNetwork.getValidatedLabels()
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

    private static Collection<PortSpec> convertPorts(
            String role,
            String preReservedRole,
            String principal,
            WriteOnceLinkedHashMap<String, RawPort> rawPorts,
            Collection<String> networkNames) {
        Collection<PortSpec> portSpecs = new ArrayList<>();
        Set<Integer> ports = new HashSet<>();
        Protos.Value.Builder portsValueBuilder = Protos.Value.newBuilder().setType(Protos.Value.Type.RANGES);

        for (Map.Entry<String, RawPort> portEntry : rawPorts.entrySet()) {
            String name = portEntry.getKey();
            RawPort rawPort = portEntry.getValue();
            boolean ok = ports.add(rawPort.getPort());
            if (!ok && rawPort.getPort() > 0) {
                throw new IllegalArgumentException(String.format("Cannot have duplicate port values: Task has " +
                        "multiple ports with value %d", rawPort.getPort()));
            }
            Protos.Value.Builder portValueBuilder = Protos.Value.newBuilder()
                    .setType(Protos.Value.Type.RANGES);
            portValueBuilder.getRangesBuilder().addRangeBuilder()
                    .setBegin(rawPort.getPort())
                    .setEnd(rawPort.getPort());
            portsValueBuilder.mergeRanges(portValueBuilder.getRanges());

            final Protos.DiscoveryInfo.Visibility visibility =
                    rawPort.isAdvertised() ? Constants.DISPLAYED_PORT_VISIBILITY : Constants.OMITTED_PORT_VISIBILITY;

            if (rawPort.getVip() != null) {
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

                NamedVIPSpec namedVIPSpec = new NamedVIPSpec(
                        portValueBuilder.build(),
                        role,
                        preReservedRole,
                        principal,
                        rawPort.getEnvKey(),
                        name,
                        DcosConstants.DEFAULT_IP_PROTOCOL,
                        visibility,
                        StringUtils.isEmpty(rawVip.getPrefix()) ? name : rawVip.getPrefix(),
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
                        visibility,
                        networkNames));
            }
        }
        return portSpecs;
    }

    /**
     * This method provides the correct role for resources to use when reserving refined resources.
     * @param preReservedRole The role of pre-reserved resources
     * @param role The role of the service
     * @return The final role which refined resources should use
     */
    private static String getRole(String preReservedRole, String role) {
        if (preReservedRole == null || preReservedRole.equals(Constants.ANY_ROLE)) {
            return role;
        } else {
            return preReservedRole + "/" + role;
        }
    }
}
