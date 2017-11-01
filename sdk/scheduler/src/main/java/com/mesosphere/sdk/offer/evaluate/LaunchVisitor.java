package com.mesosphere.sdk.offer.evaluate;

import com.google.common.annotations.VisibleForTesting;
import com.mesosphere.sdk.api.ArtifactResource;
import com.mesosphere.sdk.api.EndpointUtils;
import com.mesosphere.sdk.dcos.DcosConstants;
import com.mesosphere.sdk.offer.CommonIdUtils;
import com.mesosphere.sdk.offer.Constants;
import com.mesosphere.sdk.offer.InvalidRequirementException;
import com.mesosphere.sdk.offer.LegacyLaunchOfferRecommendation;
import com.mesosphere.sdk.offer.taskdata.AuxLabelAccess;
import com.mesosphere.sdk.offer.taskdata.EnvConstants;
import com.mesosphere.sdk.offer.taskdata.EnvUtils;
import com.mesosphere.sdk.offer.taskdata.TaskLabelWriter;
import com.mesosphere.sdk.scheduler.SchedulerConfig;
import com.mesosphere.sdk.scheduler.plan.PodInstanceRequirement;
import com.mesosphere.sdk.specification.CommandSpec;
import com.mesosphere.sdk.specification.ConfigFileSpec;
import com.mesosphere.sdk.specification.DiscoverySpec;
import com.mesosphere.sdk.specification.HealthCheckSpec;
import com.mesosphere.sdk.specification.NamedVIPSpec;
import com.mesosphere.sdk.specification.NetworkSpec;
import com.mesosphere.sdk.specification.PodInstance;
import com.mesosphere.sdk.specification.PodSpec;
import com.mesosphere.sdk.specification.PortSpec;
import com.mesosphere.sdk.specification.ReadinessCheckSpec;
import com.mesosphere.sdk.specification.ResourceSpec;
import com.mesosphere.sdk.specification.SecretSpec;
import com.mesosphere.sdk.specification.TaskSpec;
import com.mesosphere.sdk.specification.VolumeSpec;
import com.mesosphere.sdk.specification.RLimitSpec;
import org.apache.mesos.Protos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.UUID;
import java.util.stream.Collectors;

import static com.mesosphere.sdk.offer.evaluate.EvaluationOutcome.pass;

/**
 * The LaunchVisitor, along with an {@link org.apache.mesos.Protos.Offer}, visits all of the specs contained within
 * in a PodSpec and constructs a set of {@link LegacyLaunchOfferRecommendation}s for a custom-executor-based execution
 * environment.
 */
public class LaunchVisitor implements SpecVisitor<List<EvaluationOutcome>> {
    private static final Logger LOGGER = LoggerFactory.getLogger(LaunchGroupVisitor.class);

    private static final String CONFIG_TEMPLATE_KEY_FORMAT = "CONFIG_TEMPLATE_%s";
    private static final String CONFIG_TEMPLATE_DOWNLOAD_PATH = "config-templates/";

    private final SpecVisitor delegate;
    private final VisitorResultCollector<List<EvaluationOutcome>> collector;
    private final Collection<Protos.TaskInfo> podTasks;
    private final Collection<Protos.TaskInfo> allTasks;
    private final Protos.Offer offer;
    private final String serviceName;
    private final Protos.FrameworkID frameworkID;
    private final UUID targetConfigurationId;
    private final SchedulerConfig schedulerConfig;
    private final boolean isRunning;

    private Protos.ExecutorInfo executorInfo;
    private Protos.Offer.Operation.Launch.Builder launch;
    private PodInstanceRequirement podInstanceRequirement;
    private List<EvaluationOutcome> outcomes;
    private boolean isTaskActive;

    public LaunchVisitor(
            Collection<Protos.TaskInfo> podTasks,
            Collection<Protos.TaskInfo> allTasks,
            Protos.Offer offer,
            String serviceName,
            Protos.FrameworkID frameworkID,
            UUID targetConfigurationId,
            SchedulerConfig schedulerConfig,
            boolean isRunning,
            SpecVisitor delegate) {
        this.podTasks = podTasks;
        this.allTasks = allTasks;
        this.offer = offer;
        this.serviceName = serviceName;
        this.frameworkID = frameworkID;
        this.targetConfigurationId = targetConfigurationId;
        this.schedulerConfig = schedulerConfig;
        this.delegate = delegate;
        this.collector = createVisitorResultCollector();
        this.isRunning = isRunning;

        this.outcomes = new ArrayList<>();
        this.isTaskActive = false;
    }

    @Override
    public PodInstanceRequirement visitImplementation(PodInstanceRequirement podInstanceRequirement) {
        this.podInstanceRequirement = podInstanceRequirement;

        return podInstanceRequirement;
    }

    @Override
    public PodSpec visitImplementation(PodSpec podSpec) {
        executorInfo = getExecutorInfo(podSpec);

        if (podSpec.getPlacementRule().isPresent()) {
            outcomes.add(
                    podSpec.getPlacementRule().get().filter(offer, podInstanceRequirement.getPodInstance(), allTasks));
        }

        return podSpec;
    }

    @Override
    public TaskSpec visitImplementation(TaskSpec taskSpec) throws InvalidRequirementException {
        if (!podInstanceRequirement.getTasksToLaunch().contains(taskSpec.getName())) {
            return taskSpec;
        }

        launch = Protos.Offer.Operation.Launch.newBuilder();
        isTaskActive = true;
        String taskName = TaskSpec.getInstanceName(podInstanceRequirement.getPodInstance(), taskSpec);
        Protos.TaskInfo.Builder taskBuilder = launch.addTaskInfosBuilder()
                .setName(taskName)
                .setTaskId(CommonIdUtils.toTaskId(taskName))
                .setSlaveId(CommonIdUtils.emptyAgentId());

        // create default labels:
        TaskLabelWriter writer = new TaskLabelWriter(taskBuilder);
        writer.setOfferAttributes(offer)
                .setTargetConfiguration(targetConfigurationId)
                .setGoalState(taskSpec.getGoal())
                .setType(podInstanceRequirement.getPodInstance().getPod().getType())
                .setIndex(podInstanceRequirement.getPodInstance().getIndex())
                .setHostname(offer);

        if (offer.hasDomain() && offer.getDomain().hasFaultDomain()) {
            writer.setRegion(offer.getDomain().getFaultDomain().getRegion());
            writer.setZone(offer.getDomain().getFaultDomain().getZone());
        }

        taskBuilder.setLabels(writer.toProto());

        if (taskSpec.getCommand().isPresent()) {
            taskBuilder.getCommandBuilder()
                    .setValue(taskSpec.getCommand().get().getValue())
                    .setEnvironment(EnvUtils.toProto(getTaskEnvironment(
                            serviceName, podInstanceRequirement.getPodInstance(), taskSpec)));
            setBootstrapConfigFileEnv(taskBuilder.getCommandBuilder(), taskSpec);
            extendEnv(taskBuilder.getCommandBuilder(), podInstanceRequirement.getEnvironment());
        }

        if (taskSpec.getDiscovery().isPresent()) {
            taskBuilder.setDiscovery(getDiscoveryInfo(
                    taskSpec.getDiscovery().get(),
                    podInstanceRequirement.getPodInstance().getIndex()));
        }
        taskBuilder.setContainer(getContainerInfo(podInstanceRequirement.getPodInstance().getPod(), true, true));
        setHealthCheck(taskBuilder, serviceName, podInstanceRequirement.getPodInstance(), taskSpec);
        setReadinessCheck(taskBuilder, serviceName, podInstanceRequirement.getPodInstance(), taskSpec);
        taskBuilder.setExecutor(executorInfo);

        setTaskKillGracePeriod(taskBuilder, taskSpec);

        return taskSpec;
    }

    @Override
    public TaskSpec finalizeImplementation(TaskSpec taskSpec) {
        outcomes.add(
                pass(
                        this,
                        Arrays.asList(new LegacyLaunchOfferRecommendation(
                                offer,
                                launch.getTaskInfos(0),
                                podInstanceRequirement.getTasksToLaunch().contains(taskSpec.getName()))),
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
        return (VolumeSpec) visitImplementation((ResourceSpec) volumeSpec);
    }

    @Override
    public PortSpec visitImplementation(PortSpec portSpec) {
        return (PortSpec) visitImplementation((ResourceSpec) portSpec);
    }

    @Override
    public NamedVIPSpec visitImplementation(NamedVIPSpec namedVIPSpec) throws SpecVisitorException {
        visitImplementation((PortSpec) namedVIPSpec);

        return namedVIPSpec;
    }

    @Override
    public Optional<SpecVisitor> getDelegate() {
        return Optional.ofNullable(delegate);
    }

    @Override
    public void compileResultImplementation() {
        getVisitorResultCollector().setResult(outcomes);
    }

    @Override
    public VisitorResultCollector getVisitorResultCollector() {
        return collector;
    }

    private Protos.ExecutorInfo getExecutorInfo(PodSpec podSpec) {
        if (!isRunning || podTasks.isEmpty()) {
            Protos.ExecutorInfo.Builder executorBuilder = Protos.ExecutorInfo.newBuilder()
                    .setType(Protos.ExecutorInfo.Type.DEFAULT)
                    .setName(podSpec.getType())
                    .setFrameworkId(frameworkID)
                    .setExecutorId(Protos.ExecutorID.newBuilder().setValue(""));
            AuxLabelAccess.setDcosSpace(executorBuilder, schedulerConfig.getDcosSpace());

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
            executorCommandBuilder.addUrisBuilder().setValue(schedulerConfig.getLibmesosURI());
            executorCommandBuilder.addUrisBuilder().setValue(schedulerConfig.getJavaURI());

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
                                    serviceName,
                                    targetConfigurationId,
                                    podSpec.getType(),
                                    taskSpec.getName(),
                                    config.getName()))
                            .setOutputFile(getConfigTemplateDownloadPath(config))
                            .setExtract(false);
                }
            }

            return executorBuilder.build();
        } else {
            return podTasks.stream().findFirst().get().getExecutor();
        }
    }

    /**
     * Generates a Task environment containing the configured environment values from the {@link CommandSpec}, along
     * with a set of default environment variables that all SDK tasks get for free.
     */
    @VisibleForTesting
    public static Map<String, String> getTaskEnvironment(
            String serviceName, PodInstance podInstance, TaskSpec taskSpec) {
        Map<String, String> environmentMap = new TreeMap<>();

        // Task envvars from either of the following sources:
        // - ServiceSpec (provided by developer)
        // - TASKCFG_<podname>_* (provided by user, handled when parsing YAML, potentially overrides ServiceSpec)
        if (taskSpec.getCommand().isPresent()) {
            environmentMap.putAll(taskSpec.getCommand().get().getEnvironment());
        }

        // Default envvars for use by executors/developers
        // Unline the envvars added in getExecutorEnvironment(), these are specific to individual tasks and currently
        // aren't visible to sidecar tasks (as they would need to be added at the executor...):

        // Inject Pod Instance Index
        environmentMap.put(EnvConstants.POD_INSTANCE_INDEX_TASKENV, String.valueOf(podInstance.getIndex()));
        // Inject Framework Name (raw, not safe for use in hostnames)
        environmentMap.put(EnvConstants.FRAMEWORK_NAME_TASKENV, serviceName);
        // Inject Framework host domain (with hostname-safe framework name)
        environmentMap.put(EnvConstants.FRAMEWORK_HOST_TASKENV, EndpointUtils.toAutoIpDomain(serviceName));

        // Inject TASK_NAME as KEY:VALUE
        environmentMap.put(EnvConstants.TASK_NAME_TASKENV, TaskSpec.getInstanceName(podInstance, taskSpec));
        // Inject TASK_NAME as KEY for conditional mustache templating
        environmentMap.put(TaskSpec.getInstanceName(podInstance, taskSpec), "true");

        return environmentMap;
    }

    private static void setBootstrapConfigFileEnv(Protos.CommandInfo.Builder commandInfoBuilder, TaskSpec taskSpec) {
        if (taskSpec.getConfigFiles() == null) {
            return;
        }
        for (ConfigFileSpec config : taskSpec.getConfigFiles()) {
            // For use by bootstrap process: an environment variable pointing to (comma-separated):
            // a. where the template file was downloaded (by the mesos fetcher)
            // b. where the rendered result should go
            commandInfoBuilder.setEnvironment(EnvUtils.withEnvVar(
                    commandInfoBuilder.getEnvironment(),
                    String.format(CONFIG_TEMPLATE_KEY_FORMAT, EnvUtils.toEnvName(config.getName())),
                    String.format("%s,%s", getConfigTemplateDownloadPath(config), config.getRelativePath())));
        }
    }

    private static void extendEnv(Protos.CommandInfo.Builder builder, Map<String, String> environment) {
        for (Map.Entry<String, String> entry : environment.entrySet()) {
            builder.getEnvironmentBuilder().addVariablesBuilder().setName(entry.getKey()).setValue(entry.getValue());
        }
    }

    private static Protos.Secret getReferenceSecret(String secretPath) {
        return Protos.Secret.newBuilder()
                .setType(Protos.Secret.Type.REFERENCE)
                .setReference(Protos.Secret.Reference.newBuilder().setName(secretPath))
                .build();
    }

    private static String getConfigTemplateDownloadPath(ConfigFileSpec config) {
        // Name is unique.
        return String.format("%s%s", CONFIG_TEMPLATE_DOWNLOAD_PATH, config.getName());
    }

    private void setHealthCheck(
            Protos.TaskInfo.Builder taskInfo,
            String serviceName,
            PodInstance podInstance,
            TaskSpec taskSpec) {
        if (!taskSpec.getHealthCheck().isPresent()) {
            LOGGER.debug("No health check defined for taskSpec: {}", taskSpec.getName());
            return;
        }

        HealthCheckSpec healthCheckSpec = taskSpec.getHealthCheck().get();
        Protos.HealthCheck.Builder healthCheckBuilder = taskInfo.getHealthCheckBuilder();
        healthCheckBuilder
                .setDelaySeconds(healthCheckSpec.getDelay())
                .setIntervalSeconds(healthCheckSpec.getInterval())
                .setTimeoutSeconds(healthCheckSpec.getTimeout())
                .setConsecutiveFailures(healthCheckSpec.getMaxConsecutiveFailures())
                .setGracePeriodSeconds(healthCheckSpec.getGracePeriod());

        healthCheckBuilder.getCommandBuilder()
                .setValue(healthCheckSpec.getCommand())
                .setEnvironment(EnvUtils.toProto(getTaskEnvironment(serviceName, podInstance, taskSpec)));
    }

    private void setReadinessCheck(
            Protos.TaskInfo.Builder taskInfoBuilder,
            String serviceName,
            PodInstance podInstance,
            TaskSpec taskSpec) {
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
                .setEnvironment(EnvUtils.toProto(getTaskEnvironment(serviceName, podInstance, taskSpec)));
        taskInfoBuilder.setLabels(new TaskLabelWriter(taskInfoBuilder)
                .setReadinessCheck(builder.build())
                .toProto());
    }

    private static void setTaskKillGracePeriod(
            Protos.TaskInfo.Builder taskInfoBuilder,
            TaskSpec taskSpec) throws InvalidRequirementException {
        Integer taskKillGracePeriodSeconds = taskSpec.getTaskKillGracePeriodSeconds();
        if (taskKillGracePeriodSeconds == null) {
            taskKillGracePeriodSeconds = 0;
        } else if (taskKillGracePeriodSeconds < 0) {
            throw new InvalidRequirementException(String.format(
                    "kill-grace-period must be zero or a positive integer, received: %d",
                    taskKillGracePeriodSeconds));
        }
        long taskKillGracePeriodNanoseconds = 1000000000L * taskKillGracePeriodSeconds;
        Protos.DurationInfo taskKillGracePeriodDuration = Protos.DurationInfo.newBuilder()
                .setNanoseconds(taskKillGracePeriodNanoseconds)
                .build();

        Protos.KillPolicy.Builder killPolicyBuilder = Protos.KillPolicy.newBuilder()
                .setGracePeriod(taskKillGracePeriodDuration);

        taskInfoBuilder.setKillPolicy(killPolicyBuilder.build());
    }

    private static Protos.DiscoveryInfo getDiscoveryInfo(DiscoverySpec discoverySpec, int index) {
        Protos.DiscoveryInfo.Builder builder = Protos.DiscoveryInfo.newBuilder();
        if (discoverySpec.getPrefix().isPresent()) {
            builder.setName(String.format("%s-%d", discoverySpec.getPrefix().get(), index));
        }
        if (discoverySpec.getVisibility().isPresent()) {
            builder.setVisibility(discoverySpec.getVisibility().get());
        } else {
            builder.setVisibility(Constants.DEFAULT_TASK_DISCOVERY_VISIBILITY);
        }

        return builder.build();
    }

    /**
     * Get the ContainerInfo for either an Executor or a Task. Since we support both default and custom executors at
     * the moment, there is some conditional logic in here -- with the default executor, things like rlimits and images
     * must be specified at the task level only, while secrets volumes must be specified at the executor level.
     *
     * @param podSpec The Spec for the task or executor that this container is being attached to
     * @param addExtraParameters Add rlimits and docker image (if task), or secrets volumes if executor
     * @param isTaskContainer Whether this container is being attached to a TaskInfo rather than ExecutorInfo
     * @return the ContainerInfo to be attached
     */
    private Protos.ContainerInfo getContainerInfo(
            PodSpec podSpec, boolean addExtraParameters, boolean isTaskContainer) {
        Collection<Protos.Volume> secretVolumes = getExecutorInfoSecretVolumes(podSpec.getSecrets());
        Protos.ContainerInfo.Builder containerInfo = Protos.ContainerInfo.newBuilder()
                .setType(Protos.ContainerInfo.Type.MESOS);

        if (isTaskContainer) {
            containerInfo.getLinuxInfoBuilder().setSharePidNamespace(podSpec.getSharePidNamespace());
        }

        if (!podSpec.getImage().isPresent()
                && podSpec.getNetworks().isEmpty()
                && podSpec.getRLimits().isEmpty()
                && secretVolumes.isEmpty()) {
            // Nothing left to do.
            return containerInfo.build();
        }

        boolean shouldAddImage = podSpec.getImage().isPresent() && addExtraParameters && isTaskContainer;
        if (shouldAddImage) {
            containerInfo.getMesosBuilder().getImageBuilder()
                    .setType(Protos.Image.Type.DOCKER)
                    .getDockerBuilder().setName(podSpec.getImage().get());
        }

        if (!podSpec.getNetworks().isEmpty() && !isTaskContainer) {
            containerInfo.addAllNetworkInfos(
                    podSpec.getNetworks().stream()
                            .map(LaunchVisitor::getNetworkInfo)
                            .collect(Collectors.toList()));
        }

        if (!podSpec.getRLimits().isEmpty() && addExtraParameters) {
            containerInfo.setRlimitInfo(getRLimitInfo(podSpec.getRLimits()));
        }

        if (addExtraParameters) {
            for (Protos.Volume secretVolume : secretVolumes) {
                containerInfo.addVolumes(secretVolume);
            }
        }

        return containerInfo.build();
    }

    private static Protos.RLimitInfo getRLimitInfo(Collection<RLimitSpec> rlimits) {
        Protos.RLimitInfo.Builder rLimitInfoBuilder = Protos.RLimitInfo.newBuilder();

        for (RLimitSpec rLimit : rlimits) {
            Optional<Long> soft = rLimit.getSoft();
            Optional<Long> hard = rLimit.getHard();
            Protos.RLimitInfo.RLimit.Builder rLimitsBuilder = Protos.RLimitInfo.RLimit.newBuilder()
                    .setType(rLimit.getEnum());

            // RLimit itself validates that both or neither of these are present.
            if (soft.isPresent() && hard.isPresent()) {
                rLimitsBuilder.setSoft(soft.get()).setHard(hard.get());
            }
            rLimitInfoBuilder.addRlimits(rLimitsBuilder);
        }

        return rLimitInfoBuilder.build();
    }

    private static Collection<Protos.Volume> getExecutorInfoSecretVolumes(Collection<SecretSpec> secretSpecs) {
        Collection<Protos.Volume> volumes = new ArrayList<>();

        for (SecretSpec secretSpec: secretSpecs) {
            if (secretSpec.getFilePath().isPresent()) {
                volumes.add(Protos.Volume.newBuilder()
                        .setSource(Protos.Volume.Source.newBuilder()
                                .setType(Protos.Volume.Source.Type.SECRET)
                                .setSecret(getReferenceSecret(secretSpec.getSecretPath()))
                                .build())
                        .setContainerPath(secretSpec.getFilePath().get())
                        .setMode(Protos.Volume.Mode.RO)
                        .build());
            }
        }
        return volumes;
    }

    private static Protos.NetworkInfo getNetworkInfo(NetworkSpec networkSpec) {
        LOGGER.info("Loading NetworkInfo for network named \"{}\"", networkSpec.getName());
        Protos.NetworkInfo.Builder netInfoBuilder = Protos.NetworkInfo.newBuilder();
        netInfoBuilder.setName(networkSpec.getName());
        DcosConstants.warnIfUnsupportedNetwork(networkSpec.getName());

        if (!networkSpec.getPortMappings().isEmpty()) {
            for (Map.Entry<Integer, Integer> e : networkSpec.getPortMappings().entrySet()) {
                netInfoBuilder.addPortMappingsBuilder()
                        .setHostPort(e.getKey())
                        .setContainerPort(e.getValue());
            }
        }

        if (!networkSpec.getLabels().isEmpty()) {
            AuxLabelAccess.setNetworkLabels(netInfoBuilder, networkSpec.getLabels());
        }

        return netInfoBuilder.build();
    }
    private void addResource(Protos.Resource.Builder resource) {
        if (isTaskActive) {
            launch.getTaskInfosBuilder(0).addResources(resource);
        } else {
            launch.getTaskInfosBuilder(0).getExecutorBuilder().addResources(resource);
        }
    }
}
