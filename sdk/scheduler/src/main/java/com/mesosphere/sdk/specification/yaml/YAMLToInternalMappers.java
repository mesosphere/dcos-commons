package com.mesosphere.sdk.specification.yaml;

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

import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Adapter utilities for mapping Raw YAML objects to internal objects.
 */
public class YAMLToInternalMappers {

    /**
     * Converts the provided YAML {@link RawServiceSpecification} into a new {@link ServiceSpec}.
     *
     * @param rawSvcSpec the raw service specification representing a YAML file
     * @param fileReader the file reader to be used for reading template files, allowing overrides for testing
     * @throws Exception if the conversion fails
     */
    static DefaultServiceSpec from(
            RawServiceSpecification rawSvcSpec,
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
        // Fall back to defaults as needed:
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

        DefaultServiceSpec.Builder builder = DefaultServiceSpec.newBuilder()
                .name(rawSvcSpec.getName())
                .role(role)
                .principal(principal)
                .apiPort(apiPort)
                .zookeeperConnection(zookeeper);

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

        RawReplacementFailurePolicy replacementFailurePolicy = rawSvcSpec.getReplacementFailurePolicy();
        if (replacementFailurePolicy != null) {
            Integer minReplaceDelayMs = replacementFailurePolicy.getMinReplaceDelayMs();
            Integer permanentFailureTimoutMs = replacementFailurePolicy.getPermanentFailureTimoutMs();

            builder.replacementFailurePolicy(ReplacementFailurePolicy.newBuilder()
                    .minReplaceDelayMs(minReplaceDelayMs)
                    .permanentFailureTimoutMs(permanentFailureTimoutMs)
                    .build());
        }

        return builder.build();
    }

    private static PodSpec from(
            RawPod rawPod,
            YAMLServiceSpecFactory.FileReader fileReader,
            String podName,
            ConfigNamespace configNamespace,
            String role,
            String principal) throws Exception {
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
                        RawResourceSet rawResourceSet = rawResourceSets.get(rawResourceSetName);
                        return from(
                                rawResourceSetName,
                                rawResourceSet.getCpus(),
                                rawResourceSet.getMemory(),
                                rawResourceSet.getEndpoints(),
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
            List<RLimit> rlimits = new ArrayList<>();
            for (Map.Entry<String, RawRLimit> entry : rawPod.getContainer().getRLimits().entrySet()) {
                RawRLimit rawRLimit = entry.getValue();
                rlimits.add(new RLimit(entry.getKey(), rawRLimit.getSoft(), rawRLimit.getHard()));
            }
            builder.container(new DefaultContainerSpec(rawPod.getContainer().getImageName(), rlimits));
        }

        return builder.build();
    }

    private static TaskSpec from(
            RawTask rawTask,
            YAMLServiceSpecFactory.FileReader fileReader,
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

        List<ConfigFileSpecification> configFiles = new ArrayList<>();
        if (MapUtils.isNotEmpty(rawTask.getConfigurations())) {
            for (RawConfiguration rawConfig : rawTask.getConfigurations().values()) {
                configFiles.add(new DefaultConfigFileSpecification(
                        rawConfig.getDest(), fileReader.read(rawConfig.getTemplate())));
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
                    rawTask.getEndpoints(),
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
            WriteOnceLinkedHashMap<String, RawEndpoint> rawEndpoints,
            RawVolume rawSingleVolume,
            WriteOnceLinkedHashMap<String, RawVolume> rawVolumes,
            String role,
            String principal) {

        DefaultResourceSet.Builder resourceSetBuilder = DefaultResourceSet.newBuilder(role, principal);

        if (MapUtils.isNotEmpty(rawVolumes)) {
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

        if (MapUtils.isNotEmpty(rawEndpoints)) {
            for (Map.Entry<String, RawEndpoint> rawEndpoint : rawEndpoints.entrySet()) {
                resourceSetBuilder.addEndpoint(rawEndpoint.getKey(), rawEndpoint.getValue());
            }
        }

        return resourceSetBuilder
                .id(id)
                .build();
    }
}
