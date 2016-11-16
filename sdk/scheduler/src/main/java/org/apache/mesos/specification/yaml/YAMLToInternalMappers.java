package org.apache.mesos.specification.yaml;

import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.mesos.Protos;
import org.apache.mesos.config.ConfigTargetStore;
import org.apache.mesos.offer.InvalidRequirementException;
import org.apache.mesos.offer.OfferRequirementProvider;
import org.apache.mesos.scheduler.SchedulerUtils;
import org.apache.mesos.scheduler.plan.*;
import org.apache.mesos.scheduler.plan.strategy.Strategy;
import org.apache.mesos.scheduler.plan.strategy.StrategyFactory;
import org.apache.mesos.specification.*;
import org.apache.mesos.state.StateStore;

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

    public static Phase from(RawPhase rawPhase,
                             PodSpec podSpec,
                             ConfigTargetStore configTargetStore,
                             StateStore stateStore,
                             OfferRequirementProvider offerRequirementProvider) {
        String name = rawPhase.getName();
        String pod = rawPhase.getPod();
        if (!Objects.equals(podSpec.getType(), pod)) {
            throw new IllegalArgumentException("Phase refers to illegal pod: " + pod + " instead of: "
                    + podSpec.getType());
        }
        Integer count = podSpec.getCount();

        /**
         * Case 1: Steps is empty. Run all.
         */
        final List<Step> steps = new LinkedList<>();
        for (int i = 0; i < count; i++) {
            final DefaultPodInstance podInstance = new DefaultPodInstance(podSpec, i);

            List<String> tasksToLaunch = podInstance.getPod().getTasks().stream()
                    .filter(taskSpec -> taskSpec.getGoal().equals(TaskSpec.GoalState.RUNNING))
                    .map(taskSpec -> TaskSpec.getInstanceName(podInstance, taskSpec))
                    .collect(Collectors.toList());

            try {
                steps.add(new DefaultStepFactory(configTargetStore, stateStore, offerRequirementProvider)
                        .getStep(podInstance, tasksToLaunch));
            } catch (Step.InvalidStepException | InvalidRequirementException e) {
                // TODO(mohit): Re-Throw and capture as plan error.
                throw new RuntimeException(e);
            }
        }

        String strategy = rawPhase.getStrategy();
        Strategy<Step> stepStrategy = StrategyFactory.generateForSteps(strategy);
        return DefaultPhaseFactory.getPhase(name, steps, stepStrategy);
    }

    public static Plan from(RawPlan rawPlan,
                            PodSpec podSpec,
                            ConfigTargetStore configTargetStore,
                            StateStore stateStore,
                            OfferRequirementProvider offerRequirementProvider) {
        String name = rawPlan.getName();
        final List<Phase> phases = rawPlan.getPhases().stream()
                .map(rawPhase -> from(rawPhase, podSpec, configTargetStore, stateStore, offerRequirementProvider))
                .collect(Collectors.toList());
        String strategy = rawPlan.getStrategy();
        return DefaultPlanFactory.getPlan(name,
                phases,
                StrategyFactory.generateForPhase(strategy));
    }

    public static PodSpec from(RawPod rawPod, String role, String principal) throws Exception {
        List<TaskSpec> taskSpecs = new ArrayList<>();
        String podName = rawPod.getName();
        Integer podInstanceCount = rawPod.getCount();
        Collection<RawResourceSet> rawResourceSets = rawPod.getResourceSets();
        String user = rawPod.getUser();
        LinkedHashMap<String, RawTask> tasks = rawPod.getTasks();

//        if (CollectionUtils.isEmpty(rawResourceSets)) {
//
//        }

        Collection<ResourceSet> resourceSets =
                rawResourceSets.stream()
                        .map(rawResourceSet -> from(rawResourceSet, role, principal))
                        .collect(Collectors.toList());

        final LinkedHashMap<String, RawTask> rawTasks = tasks;
        for (Map.Entry<String, RawTask> entry : rawTasks.entrySet()) {
            entry.getValue().setName(entry.getKey());
            taskSpecs.add(from(entry.getValue(), Optional.ofNullable(user), podName, resourceSets));
        }

        final DefaultPodSpec podSpec = DefaultPodSpec.newBuilder()
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
//        } else if ("disk".equalsIgnoreCase(name)) {
//            // TODO(mohit): Throw error
        }

        return new DefaultResourceSpecification(name, resourceValue, role, principal, envKey);
    }

    public static ResourceSet from(RawResourceSet rawResourceSet, String role, String principal) {
        final Collection<RawVolume> rawVolumes = rawResourceSet.getVolumes();
        final Collection<RawResource> rawResources = rawResourceSet.getResources();

        final Collection<ResourceSpecification> resources = new LinkedList<>();
        final Collection<VolumeSpecification> volumes = new LinkedList<>();

        for (RawResource rawResource : rawResources) {
            resources.add(from(rawResource, role, principal));
        }

        for (RawVolume rawVolume : rawVolumes) {
            volumes.add(from(rawVolume, role, principal));
        }

        return DefaultResourceSet.newBuilder()
                .id(rawResourceSet.getId())
                .resources(resources)
                .volumes(volumes)
                .build();
    }

    public static TaskSpec from(RawTask rawTask,
                                Optional<String> user,
                                String podType,
                                Collection<ResourceSet> resourceSets) throws Exception {
        Collection<URI> uris = new ArrayList<>();

        for (String uriStr : rawTask.getUris()) {
            uris.add(new URI(uriStr));
        }

        DefaultCommandSpec.Builder commandSpecBuilder = DefaultCommandSpec.newBuilder();
        if (user.isPresent()) {
            commandSpecBuilder.user(user.get());
        }
        final DefaultCommandSpec commandSpec = commandSpecBuilder
                .environment(rawTask.getEnv())
                .uris(uris)
                .value(rawTask.getCmd())
                .build();

        List<ConfigFileSpecification> configFiles = new LinkedList<>();
        Collection<RawConfiguration> rawConfigurations =
                rawTask.getConfigurations() == null ? Collections.emptyList() : rawTask.getConfigurations();
        for (RawConfiguration rawConfig : rawConfigurations) {
            configFiles.add(from(rawConfig));
        }

        HealthCheckSpec healthCheckSpec = null;
        final LinkedHashMap<String, RawHealthCheck> healthChecks = rawTask.getHealthChecks();

        if (CollectionUtils.isNotEmpty(healthChecks.entrySet())) {
            Map.Entry<String, RawHealthCheck> entry = healthChecks.entrySet().iterator().next();
            healthCheckSpec = from(entry.getValue(), entry.getKey());
        }

        return DefaultTaskSpec.newBuilder()
                .commandSpec(commandSpec)
                .configFiles(configFiles)
                .containerSpec(null /* TODO (mohit) */)
                .goalState(TaskSpec.GoalState.valueOf(StringUtils.upperCase(rawTask.getGoal())))
                .healthCheckSpec(healthCheckSpec)
                .name(rawTask.getName())
                .type(podType)
                .resourceSet(
                        resourceSets.stream()
                                .filter(resourceSet -> resourceSet.getId().equals(rawTask.getResourceSet()))
                                .findFirst().get())
                .uris(uris)
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
