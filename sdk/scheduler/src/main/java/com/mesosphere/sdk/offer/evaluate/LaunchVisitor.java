package com.mesosphere.sdk.offer.evaluate;

import com.google.common.annotations.VisibleForTesting;
import com.mesosphere.sdk.api.EndpointUtils;
import com.mesosphere.sdk.dcos.DcosHttpClientBuilder;
import com.mesosphere.sdk.dcos.DcosHttpExecutor;
import com.mesosphere.sdk.dcos.clients.CertificateAuthorityClient;
import com.mesosphere.sdk.dcos.DcosConstants;
import com.mesosphere.sdk.dcos.clients.SecretsClient;
import com.mesosphere.sdk.offer.CommonIdUtils;
import com.mesosphere.sdk.offer.Constants;
import com.mesosphere.sdk.offer.InvalidRequirementException;
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
import com.mesosphere.sdk.specification.RLimitSpec;
import com.mesosphere.sdk.specification.VolumeSpec;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.impl.client.LaxRedirectStrategy;
import org.apache.mesos.Protos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidKeySpecException;
import java.util.ArrayList;
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
 * The LaunchVisitor is responsible for traversing a {@link com.mesosphere.sdk.specification.PodSpec} and creating a
 * collection of {@link org.apache.mesos.Protos.Offer.Operation}s for task launch. Concrete implementations build
 * protos according to the requirements of the execution environment.
 */
public abstract class LaunchVisitor extends SpecVisitor<List<EvaluationOutcome>> {
    private static final Logger LOGGER = LoggerFactory.getLogger(LaunchGroupVisitor.class);

    static final String CONFIG_TEMPLATE_KEY_FORMAT = "CONFIG_TEMPLATE_%s";
    static final String CONFIG_TEMPLATE_DOWNLOAD_PATH = "config-templates/";

    private final Protos.Offer offer;
    private final String serviceName;
    private final UUID targetConfigurationId;
    private final SchedulerConfig schedulerConfig;

    private PodInstanceRequirement podInstanceRequirement;
    private List<EvaluationOutcome> outcomes;

    public LaunchVisitor(
            Protos.Offer offer,
            String serviceName,
            UUID targetConfigurationId,
            SchedulerConfig schedulerConfig,
            SpecVisitor delegate) {
        super(delegate);

        this.offer = offer;
        this.serviceName = serviceName;
        this.targetConfigurationId = targetConfigurationId;
        this.schedulerConfig = schedulerConfig;

        this.outcomes = new ArrayList<>();
    }

    @Override
    public abstract PodSpec visitImplementation(PodSpec podSpec) throws SpecVisitorException;

    @Override
    public abstract TaskSpec visitImplementation(TaskSpec taskSpec) throws SpecVisitorException;

    @Override
    public abstract TaskSpec finalizeImplementation(TaskSpec taskSpec) throws SpecVisitorException;

    @Override
    public abstract ResourceSpec visitImplementation(ResourceSpec resourceSpec) throws SpecVisitorException;

    @Override
    public abstract VolumeSpec visitImplementation(VolumeSpec volumeSpec) throws SpecVisitorException;

    @Override
    public abstract PortSpec visitImplementation(PortSpec portSpec) throws SpecVisitorException;

    @Override
    public abstract NamedVIPSpec visitImplementation(NamedVIPSpec namedVIPSpec) throws SpecVisitorException;

    abstract void buildContainerInfo(Protos.TaskInfo.Builder taskBuilder, TaskSpec taskSpec);

    abstract void buildReadinessCheckInfo(Protos.TaskInfo.Builder taskBuilder, TaskSpec taskSpec);

    @Override
    public PodInstanceRequirement visitImplementation(PodInstanceRequirement podInstanceRequirement) {
        this.podInstanceRequirement = podInstanceRequirement;

        return podInstanceRequirement;
    }

    @Override
    public void compileResultImplementation() {
        getVisitorResultCollector().setResult(outcomes);
    }

    Protos.Offer getOffer() {
        return offer;
    }

    String getServiceName() {
        return serviceName;
    }

    UUID getTargetConfigurationId() {
        return targetConfigurationId;
    }

    SchedulerConfig getSchedulerConfig() {
        return schedulerConfig;
    }

    PodInstanceRequirement getPodInstanceRequirement() {
        return podInstanceRequirement;
    }

    List<EvaluationOutcome> getOutcomes() {
        return outcomes;
    }

    void evaluatePlacementRule(PodSpec podSpec, Collection<Protos.TaskInfo> allTasks) {
        if (podSpec.getPlacementRule().isPresent()) {
            outcomes.add(
                    podSpec.getPlacementRule().get().filter(offer, podInstanceRequirement.getPodInstance(), allTasks));
        }
    }

    void buildTaskInfo(Protos.TaskInfo.Builder taskBuilder, TaskSpec taskSpec) throws InvalidRequirementException {
        taskBuilder
                .setName(TaskSpec.getInstanceName(getPodInstanceRequirement().getPodInstance(), taskSpec))
                .setTaskId(CommonIdUtils.toTaskId(taskBuilder.getName()))
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

        buildCommandInfo(taskBuilder, taskSpec);
        buildDiscoveryInfo(taskBuilder, taskSpec);
        buildContainerInfo(taskBuilder, taskSpec);
        buildHealthCheckInfo(taskBuilder, taskSpec);
        buildReadinessCheckInfo(taskBuilder, taskSpec);
        setTaskKillGracePeriod(taskBuilder, taskSpec);

        if (!taskSpec.getTransportEncryption().isEmpty()) {
            outcomes.add(new TLSEnabledTaskFactory(schedulerConfig).addTransportEncryption(taskBuilder, taskSpec));
        }
    }

    void buildCommandInfo(Protos.TaskInfo.Builder taskBuilder, TaskSpec taskSpec) {
        if (!taskSpec.getCommand().isPresent()) {
            return;
        }

        Protos.CommandInfo.Builder commandBuilder = taskBuilder.getCommandBuilder();

        commandBuilder.setValue(taskSpec.getCommand().get().getValue())
                .setEnvironment(EnvUtils.toProto(getTaskEnvironment(
                        serviceName, podInstanceRequirement.getPodInstance(), taskSpec)));
        setBootstrapConfigFileEnv(commandBuilder, taskSpec);
        extendEnv(commandBuilder, podInstanceRequirement.getEnvironment());
    }

    void buildHealthCheckInfo(Protos.TaskInfo.Builder taskBuilder, TaskSpec taskSpec) {
        if (!taskSpec.getHealthCheck().isPresent()) {
            LOGGER.debug("No health check defined for taskSpec: {}", taskSpec.getName());
            return;
        }

        HealthCheckSpec healthCheckSpec = taskSpec.getHealthCheck().get();
        Protos.HealthCheck.Builder healthCheckBuilder = taskBuilder.getHealthCheckBuilder();

        healthCheckBuilder
                .setDelaySeconds(healthCheckSpec.getDelay())
                .setIntervalSeconds(healthCheckSpec.getInterval())
                .setTimeoutSeconds(healthCheckSpec.getTimeout())
                .setConsecutiveFailures(healthCheckSpec.getMaxConsecutiveFailures())
                .setGracePeriodSeconds(healthCheckSpec.getGracePeriod());

        healthCheckBuilder.setType(Protos.HealthCheck.Type.COMMAND);

        healthCheckBuilder.getCommandBuilder()
                .setValue(healthCheckSpec.getCommand())
                .setEnvironment(EnvUtils.toProto(getTaskEnvironment(
                        serviceName, podInstanceRequirement.getPodInstance(), taskSpec)));
    }

    void buildDiscoveryInfo(Protos.TaskInfo.Builder taskBuilder, TaskSpec taskSpec) {
        if (!taskSpec.getDiscovery().isPresent()) {
            return;
        }

        Protos.DiscoveryInfo.Builder builder = taskBuilder.getDiscoveryBuilder();
        DiscoverySpec discoverySpec = taskSpec.getDiscovery().get();
        int index = podInstanceRequirement.getPodInstance().getIndex();

        if (discoverySpec.getPrefix().isPresent()) {
            builder.setName(String.format("%s-%d", discoverySpec.getPrefix().get(), index));
        }
        if (discoverySpec.getVisibility().isPresent()) {
            builder.setVisibility(discoverySpec.getVisibility().get());
        } else {
            builder.setVisibility(Constants.DEFAULT_TASK_DISCOVERY_VISIBILITY);
        }
    }

    static void setTaskKillGracePeriod(
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

    static void setBootstrapConfigFileEnv(Protos.CommandInfo.Builder commandInfoBuilder, TaskSpec taskSpec) {
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

    static void extendEnv(Protos.CommandInfo.Builder builder, Map<String, String> environment) {
        for (Map.Entry<String, String> entry : environment.entrySet()) {
            builder.getEnvironmentBuilder().addVariablesBuilder().setName(entry.getKey()).setValue(entry.getValue());
        }
    }

    static Protos.Secret getReferenceSecret(String secretPath) {
        return Protos.Secret.newBuilder()
                .setType(Protos.Secret.Type.REFERENCE)
                .setReference(Protos.Secret.Reference.newBuilder().setName(secretPath))
                .build();
    }

    static String getConfigTemplateDownloadPath(ConfigFileSpec config) {
        // Name is unique.
        return String.format("%s%s", CONFIG_TEMPLATE_DOWNLOAD_PATH, config.getName());
    }

    Optional<Protos.CheckInfo.Builder> getReadinessCheckBuilder(TaskSpec taskSpec, PodInstance podInstance) {
        if (!taskSpec.getReadinessCheck().isPresent()) {
            LOGGER.debug("No readiness check defined for taskSpec: {}", taskSpec.getName());
            return Optional.empty();
        }

        Protos.CheckInfo.Builder readinessCheckBuilder = Protos.CheckInfo.newBuilder();
        ReadinessCheckSpec readinessCheckSpec = taskSpec.getReadinessCheck().get();

        readinessCheckBuilder.setType(Protos.CheckInfo.Type.COMMAND)
                .setDelaySeconds(readinessCheckSpec.getDelay())
                .setIntervalSeconds(readinessCheckSpec.getInterval())
                .setTimeoutSeconds(readinessCheckSpec.getTimeout());
        readinessCheckBuilder.getCommandBuilder().getCommandBuilder()
                .setValue(readinessCheckSpec.getCommand())
                .setEnvironment(EnvUtils.toProto(getTaskEnvironment(serviceName, podInstance, taskSpec)));

        return Optional.of(readinessCheckBuilder);
    }

    static Protos.RLimitInfo getRLimitInfo(Collection<RLimitSpec> rlimits) {
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

    static Collection<Protos.Volume> getExecutorInfoSecretVolumes(Collection<SecretSpec> secretSpecs) {
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

    static Protos.NetworkInfo getNetworkInfo(NetworkSpec networkSpec) {
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

    void buildPortResource(Protos.TaskInfo.Builder taskBuilder, PortSpec portSpec) {
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
            taskBuilder.addResources(portSpec.getResource());
        }
    }

    void buildNamedVIPResource(
            Protos.TaskInfo.Builder taskBuilder, NamedVIPSpec namedVIPSpec) throws SpecVisitorException {
        buildPortResource(taskBuilder, namedVIPSpec);

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
    }

    class TLSEnabledTaskFactory {
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
