package com.mesosphere.sdk.offer.evaluate;

import com.mesosphere.sdk.api.ArtifactResource;
import com.mesosphere.sdk.offer.CommonIdUtils;
import com.mesosphere.sdk.offer.InvalidRequirementException;
import com.mesosphere.sdk.offer.LaunchGroupOfferRecommendation;
import com.mesosphere.sdk.offer.taskdata.AuxLabelAccess;
import com.mesosphere.sdk.scheduler.SchedulerConfig;
import com.mesosphere.sdk.specification.ConfigFileSpec;
import com.mesosphere.sdk.specification.NamedVIPSpec;
import com.mesosphere.sdk.specification.PodSpec;
import com.mesosphere.sdk.specification.PortSpec;
import com.mesosphere.sdk.specification.ResourceSpec;
import com.mesosphere.sdk.specification.SecretSpec;
import com.mesosphere.sdk.specification.TaskSpec;
import com.mesosphere.sdk.specification.VolumeSpec;
import org.apache.mesos.Protos;

import java.net.URI;
import java.util.Arrays;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import static com.mesosphere.sdk.offer.evaluate.EvaluationOutcome.pass;

/**
 * The LaunchGroupVisitor, along with an {@link org.apache.mesos.Protos.Offer}, visits all of the specs contained within
 * in a PodSpec and constructs a set of {@link LaunchGroupOfferRecommendation}s for a default-executor-based execution
 * environment.
 */
public class LaunchGroupVisitor extends LaunchVisitor {
    private final Collection<Protos.TaskInfo> podTasks;
    private final Collection<Protos.TaskInfo> allTasks;
    private final Protos.FrameworkID frameworkID;
    private final boolean isRunning;

    private Map<String, Protos.TaskInfo.Builder> launchGroupTasks;
    private Protos.ExecutorInfo.Builder executorInfo;
    private String activeTask;

    public LaunchGroupVisitor(
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
    }

    @Override
    public PodSpec visitImplementation(PodSpec podSpec) throws SpecVisitorException {
        launchGroupTasks = podSpec.getTasks().stream().collect(
                Collectors.toMap(
                        t -> t.getName(),
                        t -> Protos.TaskInfo.newBuilder()
                                .setName(TaskSpec.getInstanceName(getPodInstanceRequirement().getPodInstance(), t))));
        executorInfo = getExecutorInfo(podSpec);
        evaluatePlacementRule(podSpec, allTasks);

        return podSpec;
    }

    @Override
    public TaskSpec visitImplementation(TaskSpec taskSpec) throws InvalidRequirementException {
        setActiveTask(taskSpec.getName());
        Protos.TaskInfo.Builder taskBuilder = getActiveTask();
        buildTaskInfo(taskBuilder, taskSpec);

        return taskSpec;
    }

    @Override
    public TaskSpec finalizeImplementation(TaskSpec taskSpec) {
        getOutcomes().add(
                pass(
                        this,
                        Arrays.asList(new LaunchGroupOfferRecommendation(
                                getOffer(),
                                launchGroupTasks.get(taskSpec.getName()).build(),
                                executorInfo.build(),
                                getPodInstanceRequirement().getTasksToLaunch().contains(taskSpec.getName()))),
                        "Added launch information to offer requirement").build());
        clearActiveTask();

        return taskSpec;
    }

    @Override
    void buildCommandInfo(Protos.TaskInfo.Builder taskBuilder, TaskSpec taskSpec) {
        super.buildCommandInfo(taskBuilder, taskSpec);

        if (taskSpec.getCommand().isPresent()) {
            Protos.CommandInfo.Builder commandBuilder = taskBuilder.getCommandBuilder();

            // Any URIs defined in PodSpec itself.
            for (URI uri : getPodInstanceRequirement().getPodInstance().getPod().getUris()) {
                commandBuilder.addUrisBuilder().setValue(uri.toString());
            }

            for (ConfigFileSpec config : taskSpec.getConfigFiles()) {
                commandBuilder.addUrisBuilder()
                        .setValue(ArtifactResource.getTemplateUrl(
                                getServiceName(),
                                getTargetConfigurationId(),
                                getPodInstanceRequirement().getPodInstance().getPod().getType(),
                                taskSpec.getName(),
                                config.getName()))
                        .setOutputFile(getConfigTemplateDownloadPath(config))
                        .setExtract(false);
            }

            // Secrets are constructed differently from other envvars where the proto is concerned:
            for (SecretSpec secretSpec : getPodInstanceRequirement().getPodInstance().getPod().getSecrets()) {
                if (secretSpec.getEnvKey().isPresent()) {
                    commandBuilder.getEnvironmentBuilder().addVariablesBuilder()
                            .setName(secretSpec.getEnvKey().get())
                            .setType(Protos.Environment.Variable.Type.SECRET)
                            .setSecret(getReferenceSecret(secretSpec.getSecretPath()));
                }
            }

            if (getPodInstanceRequirement().getPodInstance().getPod().getUser().isPresent()) {
                commandBuilder.setUser(getPodInstanceRequirement().getPodInstance().getPod().getUser().get());
            }
        }
    }

    @Override
    void buildContainerInfo(Protos.TaskInfo.Builder taskBuilder, TaskSpec taskSpec) {
        Protos.ContainerInfo.Builder containerInfo = taskBuilder.getContainerBuilder();
        PodSpec podSpec = getPodInstanceRequirement().getPodInstance().getPod();

        containerInfo
                .setType(Protos.ContainerInfo.Type.MESOS);

        if (isTaskActive()) {
            containerInfo.getLinuxInfoBuilder().setSharePidNamespace(podSpec.getSharePidNamespace());

            if (podSpec.getImage().isPresent()) {
                containerInfo.getMesosBuilder().getImageBuilder()
                        .setType(Protos.Image.Type.DOCKER)
                        .getDockerBuilder().setName(podSpec.getImage().get());
            }

            if (!podSpec.getRLimits().isEmpty()) {
                containerInfo.setRlimitInfo(getRLimitInfo(podSpec.getRLimits()));
            }
        } else {
            if (!podSpec.getNetworks().isEmpty()) {
                containerInfo.addAllNetworkInfos(
                        podSpec.getNetworks().stream()
                                .map(LaunchGroupVisitor::getNetworkInfo)
                                .collect(Collectors.toList()));
            }

            for (Protos.Volume secretVolume : getExecutorInfoSecretVolumes(podSpec.getSecrets())) {
                containerInfo.addVolumes(secretVolume);
            }
        }
    }

    @Override
    public ResourceSpec visitImplementation(ResourceSpec resourceSpec) {
        Protos.Resource.Builder resource = resourceSpec.getResource();
        addResource(resource);

        return resourceSpec;
    }

    @Override
    public VolumeSpec visitImplementation(VolumeSpec volumeSpec) {
        if (!isTaskActive()) {
            Protos.Volume.Builder volumeBuilder = Protos.Volume.newBuilder();
            Protos.Volume.Source.SandboxPath.Builder sandboxPathBuilder = Protos.Volume.Source.SandboxPath.newBuilder();

            sandboxPathBuilder.setType(Protos.Volume.Source.SandboxPath.Type.PARENT)
                    .setPath(volumeSpec.getContainerPath());
            volumeBuilder.setMode(Protos.Volume.Mode.RW)
                    .setContainerPath(volumeSpec.getContainerPath())
                    .setSource(Protos.Volume.Source.newBuilder()
                            .setType(Protos.Volume.Source.Type.SANDBOX_PATH)
                            .setSandboxPath(sandboxPathBuilder));

            for (Map.Entry<String, Protos.TaskInfo.Builder> e : launchGroupTasks.entrySet()) {
                e.getValue().getContainerBuilder().addVolumes(volumeBuilder);
            }
        }
        addResource(volumeSpec.getResource());

        return volumeSpec;
    }

    @Override
    public PortSpec visitImplementation(PortSpec portSpec) {
        Protos.TaskInfo.Builder taskBuilder = getActiveTask();
        buildPortResource(taskBuilder, portSpec);

        return portSpec;
    }

    @Override
    public NamedVIPSpec visitImplementation(NamedVIPSpec namedVIPSpec) throws SpecVisitorException {
        Protos.TaskInfo.Builder taskBuilder = getActiveTask();
        buildNamedVIPResource(taskBuilder, namedVIPSpec);

        return namedVIPSpec;
    }

    @Override
    void buildReadinessCheckInfo(Protos.TaskInfo.Builder taskBuilder, TaskSpec taskSpec) {
        Optional<Protos.CheckInfo.Builder> readinessCheckBuilder = getReadinessCheckBuilder(
                taskSpec, getPodInstanceRequirement().getPodInstance());

        if (readinessCheckBuilder.isPresent()) {
            taskBuilder.setCheck(readinessCheckBuilder.get());
        }
    }

    private Protos.ExecutorInfo.Builder getExecutorInfo(PodSpec podSpec) {
        if (!isRunning || podTasks.isEmpty()) {
            String name = podSpec.getType();
            Protos.ExecutorInfo.Builder executorBuilder = Protos.ExecutorInfo.newBuilder()
                    .setType(Protos.ExecutorInfo.Type.DEFAULT)
                    .setName(name)
                    .setFrameworkId(frameworkID)
                    .setExecutorId(CommonIdUtils.toExecutorId(name));
            AuxLabelAccess.setDcosSpace(executorBuilder, getSchedulerConfig().getDcosSpace());

            return executorBuilder;
        } else {
            return podTasks.stream().findFirst().get().getExecutor().toBuilder().clearResources();
        }
    }

    private void setActiveTask(String taskName) {
        activeTask = taskName;
    }

    private Protos.TaskInfo.Builder getActiveTask() {
        return launchGroupTasks.get(activeTask);
    }

    private void clearActiveTask() {
        activeTask = null;
    }

    private boolean isTaskActive() {
        return activeTask != null;
    }

    private void addResource(Protos.Resource.Builder resource) {
        if (isTaskActive()) {
            getActiveTask().addResources(resource);
        } else {
            executorInfo.addResources(resource);
        }
    }
}
