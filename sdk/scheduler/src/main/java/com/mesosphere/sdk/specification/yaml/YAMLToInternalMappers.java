package com.mesosphere.sdk.specification.yaml;

import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.collections.MapUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.mesos.Protos;
import com.mesosphere.sdk.scheduler.SchedulerUtils;
import com.mesosphere.sdk.specification.*;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Adapter utilities for mapping Raw YAML objects to internal objects.
 */
public class YAMLToInternalMappers {
    private static final Collection<String> SCALARS = Arrays.asList("cpus", "mem");

    public static DefaultServiceSpec from(RawServiceSpecification rawSvcSpec) throws Exception {
        final String role = SchedulerUtils.nameToRole(rawSvcSpec.getName());
        final String principal = rawSvcSpec.getPrincipal();

        List<PodSpec> pods = new ArrayList<>();
        final LinkedHashMap<String, RawPod> rawPods = rawSvcSpec.getPods();
        for (Map.Entry<String, RawPod> entry : rawPods.entrySet()) {
            final RawPod rawPod = entry.getValue();
            rawPod.setName(entry.getKey());
            pods.add(from(rawPod, role, principal));
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

    public static ConfigFileSpecification from(RawConfiguration rawConfiguration) throws IOException {
        return new DefaultConfigFileSpecification(
                rawConfiguration.getDest(),
                new File(rawConfiguration.getTemplate()));
    }

    public static HealthCheckSpec from(RawHealthCheck rawHealthCheck, String name) {
        return DefaultHealthCheckSpec.newBuilder()
                .name(name)
                .command(rawHealthCheck.getCmd())
                .delay(rawHealthCheck.getDelay())
                .gracePeriod(rawHealthCheck.getGracePeriod())
                .interval(rawHealthCheck.getInterval())
                .maxConsecutiveFailures(rawHealthCheck.getMaxConsecutiveFailures())
                .timeout(rawHealthCheck.getTimeout())
                .build();
    }

    public static PodSpec from(RawPod rawPod, String role, String principal) throws Exception {
        List<TaskSpec> taskSpecs = new ArrayList<>();
        String podName = rawPod.getName();
        Integer podInstanceCount = rawPod.getCount();
        // String placement = rawPod.getPlacement();
        RawContainer container = rawPod.getContainer();
        WriteOnceLinkedHashMap<String, RawResourceSet> rawResourceSets = rawPod.getResourceSets();
        String user = rawPod.getUser();
        LinkedHashMap<String, RawTask> tasks = rawPod.getTasks();

        Collection<String> rawTaskUris = rawPod.getUris();
        Collection<URI> uris = new ArrayList<>();
        for (String uriStr : rawTaskUris) {
            uris.add(new URI(uriStr));
        }

        final Collection<ResourceSet> resourceSets = new ArrayList<>();
        if (MapUtils.isNotEmpty(rawResourceSets)) {
            resourceSets.addAll(rawResourceSets.entrySet().stream()
                    .map(rawResourceSetEntry -> {
                        String rawResourceSetName = rawResourceSetEntry.getKey();
                        RawResourceSet rawResourceSet = rawResourceSets.get(rawResourceSetName);
                        rawResourceSet.setId(rawResourceSetName);
                        return from(rawResourceSet, role, principal);
                    })
                    .collect(Collectors.toList()));
        }


        final LinkedHashMap<String, RawTask> rawTasks = tasks;
        for (Map.Entry<String, RawTask> entry : rawTasks.entrySet()) {
            entry.getValue().setName(entry.getKey());
            taskSpecs.add(from(
                    entry.getValue(), uris,
                    Optional.ofNullable(user),
                    podName,
                    resourceSets,
                    role,
                    principal));
        }

        DefaultPodSpec.Builder builder = DefaultPodSpec.newBuilder();

        if (container != null) {
            builder.container(new DefaultContainerSpec(container.getImageName()));
        }

        final DefaultPodSpec podSpec = builder
                .count(podInstanceCount)
                .placementRule(null /** TODO(mohit) */)
                .tasks(taskSpecs)
                .type(podName)
                .user(user)
                .resources(resourceSets)
                .build();

        return podSpec;
    }

    public static ResourceSpecification from(RawResource rawResource, String role, String principal) {
        final String name = rawResource.getName();
        final String value = rawResource.getValue();
        final String envKey = rawResource.getEnvKey();

        Protos.Value resourceValue = null;
        if (SCALARS.contains(name)) {
            resourceValue = Protos.Value.newBuilder()
                    .setType(Protos.Value.Type.SCALAR)
                    .setScalar(Protos.Value.Scalar.newBuilder().setValue(Double.parseDouble(value)))
                    .build();
        }

        return new DefaultResourceSpecification(name, resourceValue, role, principal, envKey);
    }

    public static ResourceSet from(RawResourceSet rawResourceSet, String role, String principal) {
        Double cpus = rawResourceSet.getCpus();
        Integer memory = rawResourceSet.getMemory();
        Collection<RawPort> ports = rawResourceSet.getPorts();
        String id = rawResourceSet.getId();
        final Collection<RawVolume> rawVolumes = rawResourceSet.getVolumes();

        return from(id, cpus, memory, ports, rawVolumes, role, principal);
    }

    public static TaskSpec from(RawTask rawTask, Collection<URI> podUris,
                                Optional<String> user,
                                String podType,
                                Collection<ResourceSet> resourceSets,
                                String role,
                                String principal) throws Exception {
        String cmd = rawTask.getCmd();
        Collection<RawConfiguration> configurations = rawTask.getConfigurations();
        Map<String, String> env = rawTask.getEnv();
        String goal = rawTask.getGoal();
        LinkedHashMap<String, RawHealthCheck> rawTaskHealthChecks = rawTask.getHealthChecks();
        String taskName = rawTask.getName();
        String resourceSetName = rawTask.getResourceSet();
        Collection<String> rawTaskUris = rawTask.getUris();

        Double cpus = rawTask.getCpus();
        Integer memory = rawTask.getMemory();
        Collection<RawPort> ports = rawTask.getPorts();
        Collection<RawVolume> rawVolumes = rawTask.getVolumes();

        Collection<URI> uris = new ArrayList<>();
        for (String uriStr : rawTaskUris) {
            uris.add(new URI(uriStr));
        }
        uris.addAll(podUris);

        DefaultCommandSpec.Builder commandSpecBuilder = DefaultCommandSpec.newBuilder();
        if (user.isPresent()) {
            commandSpecBuilder.user(user.get());
        }
        final DefaultCommandSpec commandSpec = commandSpecBuilder
                .environment(env)
                .uris(uris)
                .value(cmd)
                .build();

        List<ConfigFileSpecification> configFiles = new LinkedList<>();
        Collection<RawConfiguration> rawConfigurations =
                configurations == null ? Collections.emptyList() : configurations;
        for (RawConfiguration rawConfig : rawConfigurations) {
            configFiles.add(from(rawConfig));
        }

        HealthCheckSpec healthCheckSpec = null;
        final LinkedHashMap<String, RawHealthCheck> healthChecks = rawTaskHealthChecks;

        if (MapUtils.isNotEmpty(healthChecks)) {
            Map.Entry<String, RawHealthCheck> entry = healthChecks.entrySet().iterator().next();
            healthCheckSpec = from(entry.getValue(), entry.getKey());
        }

        DefaultTaskSpec.Builder builder = DefaultTaskSpec.newBuilder();

        if (StringUtils.isNotBlank(resourceSetName)) {
            builder.resourceSet(
                    resourceSets.stream()
                            .filter(resourceSet -> resourceSet.getId().equals(resourceSetName))
                            .findFirst().get());
        } else {
            builder.resourceSet(from(taskName + "-resource-set", cpus, memory, ports, rawVolumes, role, principal));
        }

        return builder
                .commandSpec(commandSpec)
                .configFiles(configFiles)
                .goalState(GoalState.valueOf(StringUtils.upperCase(goal)))
                .healthCheckSpec(healthCheckSpec)
                .name(taskName)
                .type(podType)
                .uris(uris)
                .build();
    }

    public static DefaultResourceSet from(
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

    public static VolumeSpecification from(RawVolume rawVolume, String role, String principal) {
        return new DefaultVolumeSpecification(
                rawVolume.getSize(),
                VolumeSpecification.Type.valueOf(rawVolume.getType()),
                rawVolume.getPath(),
                role,
                principal,
                "DISK_SIZE");
    }

    public static VipSpec from(RawVip rawVip, int applicationPort) {
        return DefaultVipSpec.newBuilder()
                .vipPort(rawVip.getPort())
                .vipName(rawVip.getPrefix())
                .applicationPort(applicationPort)
                .build();
    }
}
