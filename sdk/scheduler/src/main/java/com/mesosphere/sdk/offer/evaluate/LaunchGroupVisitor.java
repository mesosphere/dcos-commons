package com.mesosphere.sdk.offer.evaluate;

import com.google.common.annotations.VisibleForTesting;
import com.mesosphere.sdk.api.ArtifactResource;
import com.mesosphere.sdk.api.EndpointUtils;
import com.mesosphere.sdk.dcos.DcosHttpClientBuilder;
import com.mesosphere.sdk.dcos.DcosHttpExecutor;
import com.mesosphere.sdk.dcos.clients.CertificateAuthorityClient;
import com.mesosphere.sdk.dcos.DcosConstants;
import com.mesosphere.sdk.dcos.clients.SecretsClient;
import com.mesosphere.sdk.offer.CommonIdUtils;
import com.mesosphere.sdk.offer.Constants;
import com.mesosphere.sdk.offer.InvalidRequirementException;
import com.mesosphere.sdk.offer.LaunchGroupOfferRecommendation;
import com.mesosphere.sdk.offer.TaskException;
import com.mesosphere.sdk.offer.evaluate.security.*;
import com.mesosphere.sdk.offer.taskdata.AuxLabelAccess;
import com.mesosphere.sdk.offer.taskdata.EnvConstants;
import com.mesosphere.sdk.offer.taskdata.EnvUtils;
import com.mesosphere.sdk.offer.taskdata.TaskLabelReader;
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
import com.mesosphere.sdk.specification.TransportEncryptionSpec;
import com.mesosphere.sdk.specification.VolumeSpec;
import com.mesosphere.sdk.specification.RLimitSpec;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.impl.client.LaxRedirectStrategy;
import org.apache.mesos.Protos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidKeySpecException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.UUID;
import java.util.stream.Collectors;

import static com.mesosphere.sdk.offer.evaluate.EvaluationOutcome.fail;
import static com.mesosphere.sdk.offer.evaluate.EvaluationOutcome.pass;

/**
 * The LaunchGroupVisitor, along with an {@link org.apache.mesos.Protos.Offer}, visits all of the specs contained within
 * in a PodSpec and constructs a set of {@link LaunchGroupOfferRecommendation}s for a default-executor-based execution
 * environment.
 */
public class LaunchGroupVisitor implements SpecVisitor<List<EvaluationOutcome>> {
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

    private Map<String, Protos.TaskInfo.Builder> launchGroupTasks;
    private Protos.ExecutorInfo.Builder executorInfo;
    private PodInstanceRequirement podInstanceRequirement;
    private List<EvaluationOutcome> outcomes;
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
    }

    @Override
    public PodInstanceRequirement visitImplementation(PodInstanceRequirement podInstanceRequirement) {
        LOGGER.info("Visiting PodInstanceRequirement {}", podInstanceRequirement.getName());
        this.podInstanceRequirement = podInstanceRequirement;

        return podInstanceRequirement;
    }

    @Override
    public PodSpec visitImplementation(PodSpec podSpec) throws SpecVisitorException {
        LOGGER.info("Visiting PodSpec {}", podSpec.getType());
        launchGroupTasks = podSpec.getTasks().stream().collect(
                Collectors.toMap(
                        t -> t.getName(),
                        t -> Protos.TaskInfo.newBuilder()
                                .setName(TaskSpec.getInstanceName(podInstanceRequirement.getPodInstance(), t))));
        executorInfo = getExecutorInfo(podSpec);

        if (podSpec.getPlacementRule().isPresent()) {
            outcomes.add(
                    podSpec.getPlacementRule().get().filter(offer, podInstanceRequirement.getPodInstance(), allTasks));
        }

        return podSpec;
    }

    @Override
    public TaskSpec visitImplementation(TaskSpec taskSpec) throws InvalidRequirementException {
        LOGGER.info("Visiting TaskSpec {}", taskSpec.getName());

        setActiveTask(taskSpec.getName());
        Protos.TaskInfo.Builder taskBuilder = getActiveTask();
        taskBuilder
                .setTaskId(CommonIdUtils.toTaskId(taskBuilder.getName()))
                .setSlaveId(CommonIdUtils.emptyAgentId());

        // create default labels:
        taskBuilder.setLabels(new TaskLabelWriter(taskBuilder)
                .setTargetConfiguration(targetConfigurationId)
                .setGoalState(taskSpec.getGoal())
                .setType(podInstanceRequirement.getPodInstance().getPod().getType())
                .setIndex(podInstanceRequirement.getPodInstance().getIndex())
                .setHostname(offer)
                .toProto());

        if (taskSpec.getCommand().isPresent()) {
            Protos.CommandInfo.Builder commandBuilder = taskBuilder.getCommandBuilder()
                    .setValue(taskSpec.getCommand().get().getValue())
                    .setEnvironment(EnvUtils.toProto(getTaskEnvironment(
                            serviceName, podInstanceRequirement.getPodInstance(), taskSpec)));
            setBootstrapConfigFileEnv(taskBuilder.getCommandBuilder(), taskSpec);
            extendEnv(taskBuilder.getCommandBuilder(), podInstanceRequirement.getEnvironment());

            // Any URIs defined in PodSpec itself.
            for (URI uri : podInstanceRequirement.getPodInstance().getPod().getUris()) {
                commandBuilder.addUrisBuilder().setValue(uri.toString());
            }

            for (ConfigFileSpec config : taskSpec.getConfigFiles()) {
                commandBuilder.addUrisBuilder()
                        .setValue(ArtifactResource.getTemplateUrl(
                                serviceName,
                                targetConfigurationId,
                                podInstanceRequirement.getPodInstance().getPod().getType(),
                                taskSpec.getName(),
                                config.getName()))
                        .setOutputFile(getConfigTemplateDownloadPath(config))
                        .setExtract(false);
            }

            // Secrets are constructed differently from other envvars where the proto is concerned:
            for (SecretSpec secretSpec : podInstanceRequirement.getPodInstance().getPod().getSecrets()) {
                if (secretSpec.getEnvKey().isPresent()) {
                    commandBuilder.getEnvironmentBuilder().addVariablesBuilder()
                            .setName(secretSpec.getEnvKey().get())
                            .setType(Protos.Environment.Variable.Type.SECRET)
                            .setSecret(getReferenceSecret(secretSpec.getSecretPath()));
                }
            }

            if (podInstanceRequirement.getPodInstance().getPod().getUser().isPresent()) {
                commandBuilder.setUser(podInstanceRequirement.getPodInstance().getPod().getUser().get());
            }
        }

        if (taskSpec.getDiscovery().isPresent()) {
            taskBuilder.setDiscovery(getDiscoveryInfo(
                    taskSpec.getDiscovery().get(),
                    podInstanceRequirement.getPodInstance().getIndex()));
        }
        addContainerInfo(taskBuilder, podInstanceRequirement.getPodInstance().getPod());
        setHealthCheck(taskBuilder, serviceName, podInstanceRequirement.getPodInstance(), taskSpec);
        setReadinessCheck(taskBuilder, serviceName, podInstanceRequirement.getPodInstance(), taskSpec);

        setTaskKillGracePeriod(taskBuilder, taskSpec);

        if (!taskSpec.getTransportEncryption().isEmpty()) {
            outcomes.add(new TLSEnabledTaskFactory(schedulerConfig).addTransportEncryption(taskBuilder, taskSpec));
        }

        return taskSpec;
    }


    @Override
    public TaskSpec finalizeImplementation(TaskSpec taskSpec) {
        outcomes.add(
                pass(
                        this,
                        Arrays.asList(new LaunchGroupOfferRecommendation(
                                offer,
                                launchGroupTasks.get(taskSpec.getName()).build(),
                                executorInfo.build(),
                                podInstanceRequirement.getTasksToLaunch().contains(taskSpec.getName()))),
                                "Added launch information to offer requirement").build());
        clearActiveTask();

        return taskSpec;
    }

    @Override
    public ResourceSpec visitImplementation(ResourceSpec resourceSpec) {
        LOGGER.info("Visiting ResourceSpec {}", resourceSpec.getName());
        Protos.Resource.Builder resource = resourceSpec.getResource();
        addResource(resource);

        return resourceSpec;
    }

    @Override
    public VolumeSpec visitImplementation(VolumeSpec volumeSpec) {
        LOGGER.info("Visiting VolumeSpec {}", volumeSpec.getContainerPath());
        if (!isTaskActive()) {
            LOGGER.info("Adding {} to tasks...", volumeSpec.getContainerPath());
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
        LOGGER.info("Visiting PortSpec {}", portSpec.getResource());

        Protos.TaskInfo.Builder taskBuilder = getActiveTask();
        if (!taskBuilder.hasDiscovery()) {
            // Initialize with defaults:
            taskBuilder.getDiscoveryBuilder()
                    .setVisibility(Constants.DEFAULT_TASK_DISCOVERY_VISIBILITY)
                    .setName(taskBuilder.getName());
        }

        taskBuilder.getDiscoveryBuilder().getPortsBuilder().addPortsBuilder()
                .setNumber((int) portSpec.getPort())
                .setVisibility(portSpec.getVisibility())
                .setProtocol(DcosConstants.DEFAULT_IP_PROTOCOL)
                .setName(portSpec.getPortName());

        if (portSpec.getEnvKey().isPresent()) {
            String portEnvKey = portSpec.getEnvKey().get();
            String portEnvVal = Long.toString(portSpec.getPort());
            // Add port to the main task environment:
            taskBuilder.getCommandBuilder().setEnvironment(
                    EnvUtils.withEnvVar(taskBuilder.getCommandBuilder().getEnvironment(), portEnvKey, portEnvVal));

            // Add port to the health check environment (if defined):
            if (taskBuilder.hasHealthCheck()) {
                Protos.CommandInfo.Builder healthCheckCmdBuilder =
                        taskBuilder.getHealthCheckBuilder().getCommandBuilder();
                healthCheckCmdBuilder.setEnvironment(
                        EnvUtils.withEnvVar(healthCheckCmdBuilder.getEnvironment(), portEnvKey, portEnvVal));
            } else {
                LOGGER.info("Health check is not defined for task: {}", taskBuilder.getName());
            }

            // Add port to the readiness check environment (if a readiness check is defined):
            if (taskBuilder.hasCheck()) {
                // Readiness check version used with default executor
                Protos.CommandInfo.Builder checkCmdBuilder =
                        taskBuilder.getCheckBuilder().getCommandBuilder().getCommandBuilder();
                checkCmdBuilder.setEnvironment(
                        EnvUtils.withEnvVar(checkCmdBuilder.getEnvironment(), portEnvKey, portEnvVal));
            }
            if (new TaskLabelReader(taskBuilder).hasReadinessCheckLabel()) {
                // Readiness check version used with custom executor
                try {
                    taskBuilder.setLabels(new TaskLabelWriter(taskBuilder)
                            .setReadinessCheckEnvvar(portEnvKey, portEnvVal)
                            .toProto());
                } catch (TaskException e) {
                    LOGGER.error("Got exception while adding PORT env var to ReadinessCheck", e);
                }
            }
        }

        if (portSpec.requiresHostPorts()) { // we only use the resource if we're using the host ports
            addResource(portSpec.getResource());
        }

        return portSpec;
    }

    @Override
    public NamedVIPSpec visitImplementation(NamedVIPSpec namedVIPSpec) throws SpecVisitorException {
        LOGGER.info("Visiting NamedVIPSpec {}", namedVIPSpec.getPortName());
        visitImplementation((PortSpec) namedVIPSpec);

        Protos.TaskInfo.Builder taskBuilder = getActiveTask();
        List<Protos.Port.Builder> portBuilders =
                taskBuilder.getDiscoveryBuilder().getPortsBuilder().getPortsBuilderList().stream()
                        .filter(port -> port.getName().equals(namedVIPSpec.getPortName()))
                        .collect(Collectors.toList());
        if (portBuilders.size() != 1) {
            throw new IllegalStateException(String.format(
                    "Expected one port entry with name %s: %s", namedVIPSpec.getPortName(), portBuilders.toString()));
        }

        // Update port entry with VIP metadata.
        Protos.Port.Builder portBuilder = portBuilders.get(0);
        portBuilder.setProtocol(namedVIPSpec.getProtocol());
        AuxLabelAccess.setVIPLabels(portBuilder, namedVIPSpec);

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

    private Protos.ExecutorInfo.Builder getExecutorInfo(PodSpec podSpec) {
        if (!isRunning || podTasks.isEmpty()) {
            String name = podSpec.getType();
            Protos.ExecutorInfo.Builder executorBuilder = Protos.ExecutorInfo.newBuilder()
                    .setType(Protos.ExecutorInfo.Type.DEFAULT)
                    .setName(name)
                    .setFrameworkId(frameworkID)
                    .setExecutorId(CommonIdUtils.toExecutorId(name));
            AuxLabelAccess.setDcosSpace(executorBuilder, schedulerConfig.getDcosSpace());

            return executorBuilder;
        } else {
            return podTasks.stream().findFirst().get().getExecutor().toBuilder().clearResources();
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

        healthCheckBuilder.setType(Protos.HealthCheck.Type.COMMAND);

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

        Protos.CheckInfo.Builder builder = taskInfoBuilder.getCheckBuilder()
                .setType(Protos.CheckInfo.Type.COMMAND)
                .setDelaySeconds(readinessCheckSpec.getDelay())
                .setIntervalSeconds(readinessCheckSpec.getInterval())
                .setTimeoutSeconds(readinessCheckSpec.getTimeout());
        builder.getCommandBuilder().getCommandBuilder()
                .setValue(readinessCheckSpec.getCommand())
                .setEnvironment(EnvUtils.toProto(getTaskEnvironment(serviceName, podInstance, taskSpec)));
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
     * @return the ContainerInfo to be attached
     */
    private void addContainerInfo(Protos.TaskInfo.Builder taskBuilder, PodSpec podSpec) {
        Protos.ContainerInfo.Builder containerInfo = taskBuilder.getContainerBuilder();

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
        LOGGER.info("MRB: adding resource {}", resource);
        if (isTaskActive()) {
            getActiveTask().addResources(resource);
        } else {
            executorInfo.addResources(resource);
        }
    }

    private class TLSEnabledTaskFactory {
        private final SchedulerConfig schedulerConfig;

        private TLSArtifactsUpdater tlsArtifactsUpdater;
        private boolean initialized;

        public TLSEnabledTaskFactory(SchedulerConfig schedulerConfig) {
            this.schedulerConfig = schedulerConfig;
            initialized = false;
        }

        public EvaluationOutcome addTransportEncryption(Protos.TaskInfo.Builder taskBuilder, TaskSpec taskSpec) {
            String taskName = taskBuilder.getName();
            CertificateNamesGenerator certificateNamesGenerator = new CertificateNamesGenerator(
                    serviceName,
                    taskSpec,
                    podInstanceRequirement.getPodInstance());
            TLSArtifactPaths tlsArtifactPaths = new TLSArtifactPaths(
                    schedulerConfig.getSecretsNamespace(serviceName),
                    TaskSpec.getInstanceName(podInstanceRequirement.getPodInstance(), taskName),
                    certificateNamesGenerator.getSANsHash());

            for (TransportEncryptionSpec transportEncryptionSpec : taskSpec.getTransportEncryption()) {
                try {
                    init();
                    tlsArtifactsUpdater.update(
                            tlsArtifactPaths, certificateNamesGenerator, transportEncryptionSpec.getName());
                } catch (Exception e) {
                    LOGGER.error("Failed to get certificate ", taskName, e);
                    return fail(
                            this,
                            "Failed to store TLS artifacts for task %s because of exception: %s", taskName, e)
                            .build();
                }

                Collection<Protos.Volume> volumes = getExecutorInfoSecretVolumes(
                        transportEncryptionSpec, tlsArtifactPaths);

                // Share keys to the task container
                taskBuilder.getContainerBuilder().addAllVolumes(volumes);
            }

            return pass(this, "TLS certificate created and added to the task").build();
        }

        private Collection<Protos.Volume> getExecutorInfoSecretVolumes(
                TransportEncryptionSpec spec, TLSArtifactPaths tlsArtifactPaths) {
            Collection<Protos.Volume> volumes = new ArrayList<>();
            for (TLSArtifactPaths.Entry entry : tlsArtifactPaths.getPathsForType(spec.getType(), spec.getName())) {
                volumes.add(getSecretVolume(entry));
            }
            return volumes;
        }

        private Protos.Volume getSecretVolume(TLSArtifactPaths.Entry entry) {
            Protos.Volume.Builder volumeBuilder = Protos.Volume.newBuilder()
                    .setContainerPath(entry.mountPath)
                    .setMode(Protos.Volume.Mode.RO);
            Protos.Volume.Source.Builder sourceBuilder = volumeBuilder.getSourceBuilder()
                    .setType(Protos.Volume.Source.Type.SECRET);
            sourceBuilder.getSecretBuilder()
                    .setType(Protos.Secret.Type.REFERENCE)
                    .getReferenceBuilder().setName(entry.secretStorePath);
            return volumeBuilder.build();
        }

        private void init() throws NoSuchAlgorithmException, IOException, InvalidKeySpecException {
            if (initialized) {
                return;
            }

            DcosHttpExecutor executor = new DcosHttpExecutor(new DcosHttpClientBuilder()
                    .setTokenProvider(schedulerConfig.getDcosAuthTokenProvider())
                    .setRedirectStrategy(new LaxRedirectStrategy() {
                        protected boolean isRedirectable(String method) {
                            // Also treat PUT calls as redirectable
                            return method.equalsIgnoreCase(HttpPut.METHOD_NAME) || super.isRedirectable(method);
                        }
                    }));
            this.tlsArtifactsUpdater = new TLSArtifactsUpdater(
                    serviceName, new SecretsClient(executor), new CertificateAuthorityClient(executor));
        }
    }
}
