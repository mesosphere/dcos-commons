package org.apache.mesos.specification.yaml;

import org.apache.commons.lang3.StringUtils;
import org.apache.mesos.Protos;
import org.apache.mesos.scheduler.SchedulerUtils;
import org.apache.mesos.scheduler.plan.*;
import org.apache.mesos.specification.*;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.util.*;

public class YAMLToInternalMappers {
    private static final Collection<String> SCALARS = Arrays.asList("cpus", "mem");

    public static ServiceSpec from(RawServiceSpecification rawSvcSpec) throws Exception {
        final String role = SchedulerUtils.nameToRole(rawSvcSpec.getName());
        final String principal = rawSvcSpec.getPrincipal();

        List<PodSpec> pods = new ArrayList<>();
        final LinkedHashMap<String, RawPod> rawPods = rawSvcSpec.getPods();
        for (Map.Entry<String, RawPod> entry : rawPods.entrySet()) {
            final RawPod rawPod = entry.getValue();
            rawPod.setName(entry.getKey());
            pods.add(from(rawPod, role, principal));
        }

        return DefaultServiceSpec.Builder.newBuilder()
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

    public static Phase from(RawPhase rawPhase) {
//        final Collection<String> steps = rawPhase.getSteps();
//        for (String step : steps) {
//            new DefaultStep(step)
//        }
//        final String pod = rawPhase.getPod();
//        final String strategy = rawPhase.getStrategy();
//        final String name = rawPhase.getName();
//        return new DefaultPhase(name)
        return null;
    }

    public static Plan from(RawPlan rawPlan) {
        return null;
    }

    public static PodSpec from(RawPod rawPod, String role, String principal) throws Exception {
        List<TaskSpec> taskSpecs = new ArrayList<>();

        final DefaultPodSpec podSpec = DefaultPodSpec.newBuilder()
                .count(rawPod.getCount())
                .placementRule(null /** TODO */)
                .tasks(taskSpecs)
                .type(rawPod.getName())
                .user(rawPod.getUser())
                .build();

        final LinkedHashMap<String, RawTask> rawTasks = rawPod.getTasks();
        for (Map.Entry<String, RawTask> entry : rawTasks.entrySet()) {
            taskSpecs.add(from(entry.getValue(), podSpec));
        }

        return podSpec;
    }

    public static ResourceSpecification from(RawResource rawResource, String role, String principal) {
        final String name = rawResource.getName();
        final String value = rawResource.getValue();
        final String envKey = rawResource.getEnvKey();

        Protos.Value resourceValue = null;
        if (SCALARS.contains(name)) {
            resourceValue = Protos.Value.newBuilder()
                    .setScalar(Protos.Value.Scalar.newBuilder().setValue(Double.parseDouble(value)))
                    .build();
        } else if ("disk".equalsIgnoreCase(name)) {
            // TODO: Throw error
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

    public static TaskSpec from(RawTask rawTask, PodSpec podSpec) throws Exception {
        Collection<URI> uris = new ArrayList<>();

        for (String uriStr : rawTask.getUris()) {
            uris.add(new URI(uriStr));
        }

        final DefaultCommandSpec commandSpec = DefaultCommandSpec.newBuilder()
                .environment(rawTask.getEnv())
                .uris(uris)
                .user(podSpec.getUser().get())
                .value(rawTask.getCmd())
                .build();

        List<ConfigFileSpecification> configFiles = new LinkedList<>();
        for (RawConfiguration rawConfig : rawTask.getConfigurations()) {
            configFiles.add(from(rawConfig));
        }

        HealthCheckSpec healthCheckSpec = null;
        final LinkedHashMap<String, RawHealthCheck> healthChecks = rawTask.getHealthChecks();
        for (Map.Entry<String, RawHealthCheck> entry : healthChecks.entrySet()) {
            healthCheckSpec = from(entry.getValue(), entry.getKey());
            // Only one entry. Sugar.
            break;
        }

        return DefaultTaskSpec.newBuilder()
                .commandSpec(commandSpec)
                .configFiles(configFiles)
                .containerSpec(null /* TODO */)
                .goalState(TaskSpec.GoalState.valueOf(StringUtils.upperCase(rawTask.getGoal())))
                .healthCheckSpec(healthCheckSpec)
                .name(rawTask.getName())
                .type(podSpec.getType())
                .resourceSet(
                        podSpec.getResources().stream()
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
