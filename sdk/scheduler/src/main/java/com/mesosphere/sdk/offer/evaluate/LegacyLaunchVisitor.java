package com.mesosphere.sdk.offer.evaluate;

import com.mesosphere.sdk.api.ArtifactResource;
import com.mesosphere.sdk.offer.InvalidRequirementException;
import com.mesosphere.sdk.offer.LegacyLaunchOfferRecommendation;
import com.mesosphere.sdk.offer.taskdata.AuxLabelAccess;
import com.mesosphere.sdk.offer.taskdata.EnvUtils;
import com.mesosphere.sdk.offer.taskdata.TaskLabelWriter;
import com.mesosphere.sdk.scheduler.SchedulerConfig;
import com.mesosphere.sdk.specification.ConfigFileSpec;
import com.mesosphere.sdk.specification.NamedVIPSpec;
import com.mesosphere.sdk.specification.PodSpec;
import com.mesosphere.sdk.specification.PortSpec;
import com.mesosphere.sdk.specification.ReadinessCheckSpec;
import com.mesosphere.sdk.specification.ResourceSpec;
import com.mesosphere.sdk.specification.SecretSpec;
import com.mesosphere.sdk.specification.TaskSpec;
import com.mesosphere.sdk.specification.VolumeSpec;
import org.apache.mesos.Protos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.util.Arrays;
import java.util.Collection;
import java.util.UUID;
import java.util.stream.Collectors;

import static com.mesosphere.sdk.offer.evaluate.EvaluationOutcome.pass;

/**
 * The LegacyLaunchVisitor, along with an {@link org.apache.mesos.Protos.Offer}, visits all of the specs contained
 * within a PodSpec and constructs a set of {@link LegacyLaunchOfferRecommendation}s for a custom-executor-based
 * execution environment.
 */
public class LegacyLaunchVisitor extends LaunchVisitor {
    private static final Logger LOGGER = LoggerFactory.getLogger(LaunchGroupVisitor.class);

    private final Collection<Protos.TaskInfo> podTasks;
    private final Collection<Protos.TaskInfo> allTasks;
    private final Protos.FrameworkID frameworkID;
    private final boolean isRunning;

    private Protos.ExecutorInfo.Builder executorInfo;
    private Protos.Offer.Operation.Launch.Builder launch;
    private boolean isTaskActive;

    public LegacyLaunchVisitor(
            Collection<Protos.TaskInfo> podTasks,
            Collection<Protos.TaskInfo> allTasks,
            Protos.Offer offer,
            String serviceName,
            Protos.FrameworkID frameworkID,
            UUID targetConfigurationId,
            SchedulerConfig schedulerConfig,
            boolean isRunning,
            SpecVisitor delegate) {
        super(offer, serviceName, targetConfigurationId, schedulerConfig, delegate);

        this.podTasks = podTasks;
        this.allTasks = allTasks;
        this.frameworkID = frameworkID;
        this.isRunning = isRunning;
        this.isTaskActive = false;
    }

    @Override
    public PodSpec visitImplementation(PodSpec podSpec) {
        executorInfo = getExecutorInfo(podSpec);
        evaluatePlacementRule(podSpec, allTasks);

        return podSpec;
    }

    @Override
    public TaskSpec visitImplementation(TaskSpec taskSpec) throws InvalidRequirementException {
        launch = Protos.Offer.Operation.Launch.newBuilder();
        Protos.TaskInfo.Builder taskBuilder = launch.addTaskInfosBuilder();

        isTaskActive = true;
        buildTaskInfo(taskBuilder, taskSpec);

        return taskSpec;
    }

    @Override
    public TaskSpec finalizeImplementation(TaskSpec taskSpec) {
        launch.getTaskInfosBuilder(0).setExecutor(executorInfo);
        getOutcomes().add(
                pass(
                        this,
                        Arrays.asList(new LegacyLaunchOfferRecommendation(
                                getOffer(),
                                launch.getTaskInfos(0),
                                getPodInstanceRequirement().getTasksToLaunch().contains(taskSpec.getName()))),
                        "Added launch information to offer requirement").build());
        isTaskActive = false;

        return taskSpec;
    }

    @Override
    public ResourceSpec visitImplementation(ResourceSpec resourceSpec) {
        Protos.Resource.Builder resource = resourceSpec.getResource();
        addResource(resource);

        return resourceSpec;
    }

    @Override
    public VolumeSpec visitImplementation(VolumeSpec volumeSpec) {
        addResource(volumeSpec.getResource());

        return volumeSpec;
    }

    @Override
    public PortSpec visitImplementation(PortSpec portSpec) {
        buildPortResource(launch.getTaskInfosBuilder(0), portSpec);

        return portSpec;
    }

    @Override
    public NamedVIPSpec visitImplementation(NamedVIPSpec namedVIPSpec) throws SpecVisitorException {
        buildNamedVIPResource(launch.getTaskInfosBuilder(0), namedVIPSpec);

        return namedVIPSpec;
    }

    private Protos.ExecutorInfo.Builder getExecutorInfo(PodSpec podSpec) {
        if (isRunning) {
            return podTasks.stream().findFirst().get().getExecutor().toBuilder().clearResources();
        }

        Protos.ExecutorInfo.Builder executorBuilder = Protos.ExecutorInfo.newBuilder()
                .setType(Protos.ExecutorInfo.Type.DEFAULT)
                .setName(podSpec.getType())
                .setFrameworkId(frameworkID)
                .setExecutorId(Protos.ExecutorID.newBuilder().setValue(""));
        AuxLabelAccess.setDcosSpace(executorBuilder, getSchedulerConfig().getDcosSpace());

        // command and user:
        Protos.CommandInfo.Builder executorCommandBuilder = executorBuilder.getCommandBuilder().setValue(
                "export LD_LIBRARY_PATH=$MESOS_SANDBOX/libmesos-bundle/lib:$LD_LIBRARY_PATH && " +
                "export MESOS_NATIVE_JAVA_LIBRARY=$(ls $MESOS_SANDBOX/libmesos-bundle/lib/libmesos-*.so) && " +
                "export JAVA_HOME=$(ls -d $MESOS_SANDBOX/jre*/) && " +
                // Remove Xms/Xmx if +UseCGroupMemoryLimitForHeap or equivalent detects cgroups memory limit
                "export JAVA_OPTS=\"-Xms128M -Xmx128M\" && " +
                "$MESOS_SANDBOX/executor/bin/executor");

        if (podSpec.getUser().isPresent()) {
            executorCommandBuilder.setUser(podSpec.getUser().get());
        }

        // Required URIs from the scheduler environment:
        executorCommandBuilder.addUrisBuilder().setValue(getSchedulerConfig().getLibmesosURI());
        executorCommandBuilder.addUrisBuilder().setValue(getSchedulerConfig().getJavaURI());

        // Any URIs defined in PodSpec itself.
        for (URI uri : podSpec.getUris()) {
            executorCommandBuilder.addUrisBuilder().setValue(uri.toString());
        }

        // Secrets are constructed differently from other envvars where the proto is concerned:
        for (SecretSpec secretSpec : podSpec.getSecrets()) {
            if (secretSpec.getEnvKey().isPresent()) {
                executorCommandBuilder.getEnvironmentBuilder().addVariablesBuilder()
                        .setName(secretSpec.getEnvKey().get())
                        .setType(Protos.Environment.Variable.Type.SECRET)
                        .setSecret(getReferenceSecret(secretSpec.getSecretPath()));
            }
        }

        // Finally any URIs for config templates defined in TaskSpecs.
        for (TaskSpec taskSpec : podSpec.getTasks()) {
            for (ConfigFileSpec config : taskSpec.getConfigFiles()) {
                executorCommandBuilder.addUrisBuilder()
                        .setValue(ArtifactResource.getTemplateUrl(
                                getServiceName(),
                                getTargetConfigurationId(),
                                podSpec.getType(),
                                taskSpec.getName(),
                                config.getName()))
                        .setOutputFile(getConfigTemplateDownloadPath(config))
                        .setExtract(false);
            }
        }
        buildContainerInfo(executorBuilder, podSpec);

        return executorBuilder;
    }

    @Override
    void buildReadinessCheckInfo(Protos.TaskInfo.Builder taskBuilder, TaskSpec taskSpec) {
        if (!taskSpec.getReadinessCheck().isPresent()) {
            LOGGER.debug("No readiness check defined for taskSpec: {}", taskSpec.getName());
            return;
        }

        ReadinessCheckSpec readinessCheckSpec = taskSpec.getReadinessCheck().get();

        Protos.HealthCheck.Builder builder = Protos.HealthCheck.newBuilder()
                .setDelaySeconds(readinessCheckSpec.getDelay())
                .setIntervalSeconds(readinessCheckSpec.getInterval())
                .setTimeoutSeconds(readinessCheckSpec.getTimeout());
        builder.getCommandBuilder()
                .setValue(readinessCheckSpec.getCommand())
                .setEnvironment(EnvUtils.toProto(getTaskEnvironment(
                        getServiceName(), getPodInstanceRequirement().getPodInstance(), taskSpec)));
        taskBuilder.setLabels(new TaskLabelWriter(taskBuilder)
                .setReadinessCheck(builder.build())
                .toProto());
    }

    void buildContainerInfo(Protos.ExecutorInfo.Builder executorBuilder, PodSpec podSpec) {
        Protos.ContainerInfo.Builder containerBuilder = executorBuilder.getContainerBuilder();
        containerBuilder.setType(Protos.ContainerInfo.Type.MESOS);
        Collection<Protos.Volume> secretVolumes = getExecutorInfoSecretVolumes(podSpec.getSecrets());

        if (podSpec.getImage().isPresent()) {
            containerBuilder.getMesosBuilder().getImageBuilder()
                    .setType(Protos.Image.Type.DOCKER)
                    .getDockerBuilder().setName(podSpec.getImage().get());
        }

        if (!podSpec.getNetworks().isEmpty()) {
            containerBuilder.addAllNetworkInfos(
                    podSpec.getNetworks().stream()
                            .map(LegacyLaunchVisitor::getNetworkInfo)
                            .collect(Collectors.toList()));
        }

        if (!podSpec.getRLimits().isEmpty()) {
            containerBuilder.setRlimitInfo(getRLimitInfo(podSpec.getRLimits()));
        }

        for (Protos.Volume secretVolume : secretVolumes) {
            containerBuilder.addVolumes(secretVolume);
        }
    }

     void buildContainerInfo(Protos.TaskInfo.Builder taskBuilder, TaskSpec taskSpec) {
         PodSpec podSpec = getPodInstanceRequirement().getPodInstance().getPod();
        Protos.ContainerInfo.Builder containerBuilder = taskBuilder.getContainerBuilder();

        containerBuilder.setType(Protos.ContainerInfo.Type.MESOS);
        containerBuilder.getLinuxInfoBuilder().setSharePidNamespace(podSpec.getSharePidNamespace());
    }

    private void addResource(Protos.Resource.Builder resource) {
        if (isTaskActive) {
            launch.getTaskInfosBuilder(0).addResources(resource);
        } else {
            executorInfo.addResources(resource);
        }
    }
}
