package com.mesosphere.sdk.specification.yaml;

import org.apache.commons.collections.MapUtils;
import org.apache.commons.lang3.StringUtils;

import com.mesosphere.sdk.config.ConfigNamespace;
import com.mesosphere.sdk.config.DefaultTaskConfigRouter;
import com.mesosphere.sdk.config.TaskConfigRouter;
import com.mesosphere.sdk.offer.evaluate.placement.MarathonConstraintParser;
import com.mesosphere.sdk.offer.evaluate.placement.PassthroughRule;
import com.mesosphere.sdk.offer.evaluate.placement.PlacementRule;
import com.mesosphere.sdk.scheduler.SchedulerUtils;
import com.mesosphere.sdk.specification.*;
import com.mesosphere.sdk.specification.util.RLimit;
import org.apache.mesos.Protos;

import java.net.URI;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collector;
import java.util.stream.Collectors;

/**
 * Adapter utilities for mapping Raw YAML objects to internal objects.
 */
public class YAMLToInternalMappers {

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

        if (rawPod.getContainer() != null) {
            List<RLimit> rlimits = new ArrayList<>();
            for (Map.Entry<String, RawRLimit> entry : rawPod.getContainer().getRLimits().entrySet()) {
                RawRLimit rawRLimit = entry.getValue();
                rlimits.add(new RLimit(entry.getKey(), rawRLimit.getSoft(), rawRLimit.getHard()));
            }
        }

        WriteOnceLinkedHashMap<String, RawNetwork> rawNetworks = rawPod.getNetwork();
        final Collection<NetworkSpec> network = new ArrayList<>();
        if (MapUtils.isNotEmpty(rawNetworks)) {
            network.addAll(rawNetworks.entrySet().stream()
            .map(rawNetworkEntry -> {
                String networkName = rawNetworkEntry.getKey();
                RawNetwork rawNetwork = rawNetworks.get(networkName);
                return from(
                        networkName,
                        rawNetwork.getHostPorts(),
                        rawNetwork.getContainerPorts());
            })
            .collect(Collectors.toList()));
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
            Integer memory,
            WriteOnceLinkedHashMap<String, RawPort> rawEndpoints,
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

        if (memory != null) {
            resourceSetBuilder.memory(Double.valueOf(memory));
        }

        if (rawEndpoints != null) {
            resourceSetBuilder.addPorts(rawEndpoints);
        }

        return resourceSetBuilder
                .id(id)
                .build();
    }

    private static DefaultNetworkSpec from(
            String name,
            List<Integer> hostPorts,
            List<Integer> contaierPorts) {

    }
}
