package org.apache.mesos.specification.yaml;

import org.apache.commons.lang3.StringUtils;
import org.apache.mesos.Protos;
import org.apache.mesos.scheduler.SchedulerUtils;
import org.apache.mesos.scheduler.plan.Phase;
import org.apache.mesos.scheduler.plan.Plan;
import org.apache.mesos.specification.*;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.util.*;

public class YAMLToInternalMappers {
    public static ServiceSpec from(RawServiceSpecification rawSvcSpec) throws Exception {
        final String role = SchedulerUtils.nameToRole(rawSvcSpec.getName());

        List<PodSpec> pods = new ArrayList<>();
        final LinkedHashMap<String, RawPod> rawPods = rawSvcSpec.getPods();
        for (Map.Entry<String, RawPod> entry : rawPods.entrySet()) {
            final RawPod rawPod = entry.getValue();
            rawPod.setName(entry.getKey());
            pods.add(from(rawPod));
        }

        return DefaultServiceSpec.Builder.newBuilder()
                .name(rawSvcSpec.getName())
                .apiPort(rawSvcSpec.getApiPort())
                .principal(rawSvcSpec.getPrincipal())
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
        return null;
    }

    public static Plan from(RawPlan rawPlan) {
        return null;
    }

    public static PodSpec from(RawPod rawPod) throws Exception {
        final Collection<ResourceSet> resourceSets = new LinkedList<>();
        final Collection<RawResourceSet> rawResourceSets = rawPod.getResourceSets();

        for (RawResourceSet rawResourceSet : rawResourceSets) {
            resourceSets.add(from(rawResourceSet));
        }

        List<TaskSpec> taskSpecs = new ArrayList<>();

        final DefaultPodSpec podSpec = DefaultPodSpec.newBuilder()
                .count(rawPod.getCount())
                .placementRule(null /** TODO */)
                .resources(resourceSets)
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

    public static Protos.Resource from(RawResource rawResource) {
        return null;
    }

    public static ResourceSet from(RawResourceSet rawResourceSet) {
        return null;
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

        List<HealthCheckSpec> healthCheckSpecs = new ArrayList<>();
        final LinkedHashMap<String, RawHealthCheck> healthChecks = rawTask.getHealthChecks();
        for (Map.Entry<String, RawHealthCheck> entry : healthChecks.entrySet()) {
            healthCheckSpecs.add(from(entry.getValue(), entry.getKey()));
        }

        return DefaultTaskSpec.newBuilder()
                .commandSpec(commandSpec)
                .configFiles(configFiles)
                .containerSpec(null /* TODO */)
                .goalState(TaskSpec.GoalState.valueOf(StringUtils.upperCase(rawTask.getGoal())))
                .healthCheckSpec(healthCheckSpecs)
                .name(rawTask.getName())
                .pod(podSpec)
                .resourceSetId(rawTask.getResourceSet())
                .uris(uris)
                .build();
    }

    public static VolumeSpecification from(RawVolume rawVolume, String role, String principal) {
        return new DefaultVolumeSpecification(
                rawVolume.getSize(),
                VolumeSpecification.Type.valueOf(rawVolume.getType()),
                rawVolume.getPath(),
                role,
                principal);
    }

    public static VipSpec from(RawVip rawVip, int applicationPort) {
        return DefaultVipSpec.newBuilder()
                .vipPort(rawVip.getPort())
                .vipName(rawVip.getPrefix())
                .applicationPort(applicationPort)
                .build();
    }
}
