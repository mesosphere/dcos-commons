package com.mesosphere.sdk.specification.yaml;

import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.collections.MapUtils;
import org.apache.commons.lang3.StringUtils;

import com.mesosphere.sdk.config.ConfigNamespace;
import com.mesosphere.sdk.config.DefaultTaskConfigRouter;
import com.mesosphere.sdk.config.TaskConfigRouter;
import com.mesosphere.sdk.offer.constrain.MarathonConstraintParser;
import com.mesosphere.sdk.offer.constrain.PassthroughRule;
import com.mesosphere.sdk.offer.constrain.PlacementRule;
import com.mesosphere.sdk.scheduler.SchedulerUtils;
import com.mesosphere.sdk.specification.*;
import com.mesosphere.sdk.specification.util.RLimit;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Adapter utilities for mapping Raw YAML objects to internal objects.
 */
public class YAMLToInternalMappers {

    static DefaultServiceSpec from(RawServiceSpec rawSvcSpec) throws Exception {
        final String role = SchedulerUtils.nameToRole(rawSvcSpec.getName());
        final String principal = rawSvcSpec.getPrincipal();

        List<PodSpec> pods = new ArrayList<>();
        final LinkedHashMap<String, RawPod> rawPods = rawSvcSpec.getPods();
        TaskConfigRouter taskConfigRouter = new DefaultTaskConfigRouter();
        for (Map.Entry<String, RawPod> entry : rawPods.entrySet()) {
            pods.add(from(
                    entry.getValue(),
                    entry.getKey(),
                    taskConfigRouter.getConfig(entry.getKey()),
                    role,
                    principal));
        }

        RawReplacementFailurePolicy replacementFailurePolicy = rawSvcSpec.getReplacementFailurePolicy();
        DefaultServiceSpec.Builder builder = DefaultServiceSpec.newBuilder();

        if (replacementFailurePolicy != null) {
            Integer minReplaceDelayMs = replacementFailurePolicy.getMinReplaceDelayMs();
            Integer permanentFailureTimoutMs = replacementFailurePolicy.getPermanentFailureTimoutMs();

            builder.replacementFailurePolicy(ReplacementFailurePolicy.newBuilder()
                    .minReplaceDelayMs(minReplaceDelayMs)
                    .permanentFailureTimoutMs(permanentFailureTimoutMs)
                    .build());
        }

        return builder
                .name(rawSvcSpec.getName())
                .apiPort(rawSvcSpec.getApiPort())
                .principal(principal)
                .zookeeperConnection(rawSvcSpec.getZookeeper())
                .pods(pods)
                .role(role)
                .build();
    }

    private static ConfigFileSpec from(RawConfiguration rawConfiguration) throws IOException {
        return new DefaultConfigFileSpec(
                rawConfiguration.getDest(),
                new File(rawConfiguration.getTemplate()));
    }

    private static HealthCheckSpec from(RawHealthCheck rawHealthCheck) {
        return DefaultHealthCheckSpec.newBuilder()
                .command(rawHealthCheck.getCmd())
                .delay(rawHealthCheck.getDelay())
                .gracePeriod(rawHealthCheck.getGracePeriod())
                .interval(rawHealthCheck.getInterval())
                .maxConsecutiveFailures(rawHealthCheck.getMaxConsecutiveFailures())
                .timeout(rawHealthCheck.getTimeout())
                .build();
    }

    private static PodSpec from(
            RawPod rawPod, String podName, ConfigNamespace configNamespace, String role, String principal)
                    throws Exception {
        Collection<URI> uris = new ArrayList<>();
        for (String uriStr : rawPod.getUris()) {
            uris.add(new URI(uriStr));
        }

        WriteOnceLinkedHashMap<String, RawResourceSet> rawResourceSets = rawPod.getResourceSets();
        final Collection<ResourceSet> resourceSets = new ArrayList<>();
        if (MapUtils.isNotEmpty(rawResourceSets)) {
            resourceSets.addAll(rawResourceSets.entrySet().stream()
                    .map(rawResourceSetEntry -> {
                        String rawResourceSetName = rawResourceSetEntry.getKey();
                        return from(rawResourceSets.get(rawResourceSetName), rawResourceSetName, role, principal);
                    })
                    .collect(Collectors.toList()));
        }

        List<TaskSpec> taskSpecs = new ArrayList<>();
        for (Map.Entry<String, RawTask> entry : rawPod.getTasks().entrySet()) {
            taskSpecs.add(from(
                    entry.getValue(),
                    entry.getKey(),
                    uris,
                    Optional.ofNullable(rawPod.getUser()),
                    configNamespace,
                    resourceSets,
                    role,
                    principal));
        }

        DefaultPodSpec.Builder builder = DefaultPodSpec.newBuilder()
                .count(rawPod.getCount())
                .tasks(taskSpecs)
                .type(podName)
                .user(rawPod.getUser())
                .resources(resourceSets);

        PlacementRule placementRule = MarathonConstraintParser.parse(rawPod.getPlacement());
        if (!(placementRule instanceof PassthroughRule)) {
            builder.placementRule(placementRule);
        }
        if (rawPod.getContainer() != null) {
            builder.container(
                    new DefaultContainerSpec(
                            rawPod.getContainer().getImageName(), from(rawPod.getContainer().getRLimits())));
        }

        return builder.build();
    }

    private static Collection<RLimit> from(LinkedHashMap<String, RawRLimit> rawRLimits) throws Exception {
        List<RLimit> rlimits = new ArrayList<>();
        for (Map.Entry<String, RawRLimit> entry : rawRLimits.entrySet()) {
            RawRLimit rawRLimit = entry.getValue();
            rlimits.add(new RLimit(entry.getKey(), rawRLimit.getSoft(), rawRLimit.getHard()));
        }

        return rlimits;
    }

    private static ResourceSet from(
            RawResourceSet rawResourceSet, String resourceSetId, String role, String principal) {
        Double cpus = rawResourceSet.getCpus();
        Integer memory = rawResourceSet.getMemory();
        Collection<RawPort> ports = rawResourceSet.getPorts();
        final Collection<RawVolume> rawVolumes = rawResourceSet.getVolumes();

        return from(resourceSetId, cpus, memory, ports, rawVolumes, role, principal);
    }

    private static TaskSpec from(
            RawTask rawTask,
            String taskName,
            Collection<URI> podUris,
            Optional<String> user,
            ConfigNamespace configNamespace,
            Collection<ResourceSet> resourceSets,
            String role,
            String principal) throws Exception {
        Collection<URI> uris = new ArrayList<>();
        for (String uriStr : rawTask.getUris()) {
            uris.add(new URI(uriStr));
        }
        uris.addAll(podUris);

        DefaultCommandSpec.Builder commandSpecBuilder = DefaultCommandSpec.newBuilder(configNamespace)
                .environment(rawTask.getEnv())
                .uris(uris)
                .value(rawTask.getCmd());
        if (user.isPresent()) {
            commandSpecBuilder.user(user.get());
        }

        List<ConfigFileSpec> configFiles = new LinkedList<>();
        if (rawTask.getConfigurations() != null) {
            for (RawConfiguration rawConfig : rawTask.getConfigurations()) {
                configFiles.add(from(rawConfig));
            }
        }

        HealthCheckSpec healthCheckSpec = null;
        if (rawTask.getHealthCheck() != null) {
            healthCheckSpec = from(rawTask.getHealthCheck());
        }

        DefaultTaskSpec.Builder builder = DefaultTaskSpec.newBuilder()
                .commandSpec(commandSpecBuilder.build())
                .configFiles(configFiles)
                .goalState(GoalState.valueOf(StringUtils.upperCase(rawTask.getGoal())))
                .healthCheckSpec(healthCheckSpec)
                .name(taskName)
                .uris(uris);

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
            Collection<RawPort> ports,
            Collection<RawVolume> rawVolumes,
            String role,
            String principal) {

        DefaultResourceSet.Builder resourceSetBuilder = DefaultResourceSet.newBuilder(role, principal);

        if (CollectionUtils.isNotEmpty(rawVolumes)) {
            for (RawVolume rawVolume : rawVolumes) {
                resourceSetBuilder
                        .addVolume(rawVolume.getType(), Double.valueOf(rawVolume.getSize()), rawVolume.getPath());
            }
        } else {
            resourceSetBuilder.volumes(Collections.emptyList());
        }

        if (cpus != null) {
            resourceSetBuilder.cpus(cpus);
        }

        if (memory != null) {
            resourceSetBuilder.memory(Double.valueOf(memory));
        }

        if (CollectionUtils.isNotEmpty(ports)) {
            resourceSetBuilder.addPorts(ports);
        }

        return resourceSetBuilder
                .id(id)
                .build();
    }
}
