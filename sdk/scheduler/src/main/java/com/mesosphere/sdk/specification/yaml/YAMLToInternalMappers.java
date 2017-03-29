package com.mesosphere.sdk.specification.yaml;

import org.apache.commons.collections.MapUtils;
import org.apache.commons.lang3.StringUtils;

import com.mesosphere.sdk.config.ConfigNamespace;
import com.mesosphere.sdk.config.DefaultTaskConfigRouter;
import com.mesosphere.sdk.config.TaskConfigRouter;
import com.mesosphere.sdk.offer.Constants;
import com.mesosphere.sdk.offer.evaluate.placement.MarathonConstraintParser;
import com.mesosphere.sdk.offer.evaluate.placement.PassthroughRule;
import com.mesosphere.sdk.offer.evaluate.placement.PlacementRule;
import com.mesosphere.sdk.scheduler.SchedulerUtils;
import com.mesosphere.sdk.specification.*;
import com.mesosphere.sdk.specification.util.RLimit;
import org.apache.mesos.Protos;
import org.apache.mesos.Protos.DiscoveryInfo;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;


/**
 * Adapter utilities for mapping Raw YAML objects to internal objects.
 */
public class YAMLToInternalMappers {
    private static final String DEFAULT_VIP_PROTOCOL = "tcp";

    public static final DiscoveryInfo.Visibility PUBLIC_VIP_VISIBILITY = DiscoveryInfo.Visibility.EXTERNAL;

    /**
     * Converts the provided YAML {@link RawServiceSpec} into a new {@link ServiceSpec}.
     *
     * @param rawSvcSpec the raw service specification representing a YAML file
     * @param fileReader the file reader to be used for reading template files, allowing overrides for testing
     * @throws Exception if the conversion fails
     */
    static DefaultServiceSpec from(
            RawServiceSpec rawSvcSpec, YAMLServiceSpecFactory.FileReader fileReader) throws Exception {
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
            apiPort = SchedulerUtils.apiPort();
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
                    principal));

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
            String principal) throws Exception {
        WriteOnceLinkedHashMap<String, RawResourceSet> rawResourceSets = rawPod.getResourceSets();
        final Collection<ResourceSet> resourceSets = new ArrayList<>();
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
                                principal);
                    })
                    .collect(Collectors.toList()));
        }

        List<TaskSpec> taskSpecs = new ArrayList<>();
        for (Map.Entry<String, RawTask> entry : rawPod.getTasks().entrySet()) {
            taskSpecs.add(from(
                    entry.getValue(),
                    fileReader,
                    entry.getKey(),
                    configNamespace,
                    resourceSets,
                    role,
                    principal));
        }
        Collection<URI> podUris = new ArrayList<>();
        for (String uriStr : rawPod.getUris()) {
            podUris.add(new URI(uriStr));
        }

        DefaultPodSpec.Builder builder = DefaultPodSpec.newBuilder()
                .count(rawPod.getCount())
                .tasks(taskSpecs)
                .type(podName)
                .uris(podUris)
                .user(rawPod.getUser());

        PlacementRule placementRule = MarathonConstraintParser.parse(rawPod.getPlacement());
        if (!(placementRule instanceof PassthroughRule)) {
            builder.placementRule(placementRule);
        }

        RawContainerInfoProvider containerInfoProvider = null;
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

            List<NetworkSpec> networks = new ArrayList<>();
            for (Map.Entry<String, RawNetwork> entry : containerInfoProvider.getNetworks().entrySet()) {
                // When features other than network name are added, we'll want to use the RawNetwork entry value here.
                networks.add(new DefaultNetworkSpec(entry.getKey()));
            }

            builder.image(containerInfoProvider.getImage())
                .networks(networks)
                .rlimits(rlimits);

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
            String principal) throws Exception {

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
                    principal));
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
            String principal) {

        DefaultResourceSet.Builder resourceSetBuilder = DefaultResourceSet.newBuilder(role, principal);

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
            resourceSetBuilder.addResource(from(role, principal, rawPorts));
        }

        return resourceSetBuilder
                .id(id)
                .build();
    }

    private static ResourceSpec from(String role, String principal, WriteOnceLinkedHashMap<String, RawPort> rawPorts) {
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
                        Constants.PORTS_RESOURCE_TYPE,
                        portValueBuilder.build(),
                        role,
                        principal,
                        rawPort.getEnvKey(),
                        name,
                        protocol,
                        toVisibility(rawVip.isAdvertised()),
                        vipName,
                        rawVip.getPort()));
            } else {
                portSpecs.add(new PortSpec(
                        Constants.PORTS_RESOURCE_TYPE,
                        portValueBuilder.build(),
                        role,
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
