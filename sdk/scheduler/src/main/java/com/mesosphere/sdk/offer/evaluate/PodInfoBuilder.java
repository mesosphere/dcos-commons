package com.mesosphere.sdk.offer.evaluate;

import com.google.common.annotations.VisibleForTesting;
import com.mesosphere.sdk.api.ArtifactResource;
import com.mesosphere.sdk.api.EndpointUtils;
import com.mesosphere.sdk.dcos.DcosConstants;
import com.mesosphere.sdk.offer.CommonIdUtils;
import com.mesosphere.sdk.offer.Constants;
import com.mesosphere.sdk.offer.InvalidRequirementException;
import com.mesosphere.sdk.offer.TaskException;
import com.mesosphere.sdk.offer.taskdata.EnvConstants;
import com.mesosphere.sdk.offer.taskdata.EnvUtils;
import com.mesosphere.sdk.offer.taskdata.TaskLabelReader;
import com.mesosphere.sdk.offer.taskdata.TaskLabelWriter;
import com.mesosphere.sdk.scheduler.SchedulerFlags;
import com.mesosphere.sdk.scheduler.plan.PodInstanceRequirement;
import com.mesosphere.sdk.specification.*;
import com.mesosphere.sdk.specification.util.RLimit;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.logging.log4j.util.Strings;
import org.apache.mesos.Protos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.util.*;
import java.util.stream.Collectors;


/**
 * A {@link PodInfoBuilder} encompasses a mutable group of {@link org.apache.mesos.Protos.TaskInfo.Builder}s and,
 * optionally, a {@link org.apache.mesos.Protos.ExecutorInfo.Builder}. This supports the modification of task infos
 * during the evaluation process, allowing e.g. dynamic ports to be represented as environment variables in the task
 * to which they are attached.
 */
public class PodInfoBuilder {
    private static final Logger LOGGER = LoggerFactory.getLogger(PodInfoBuilder.class);
    private static final String CONFIG_TEMPLATE_KEY_FORMAT = "CONFIG_TEMPLATE_%s";
    private static final String CONFIG_TEMPLATE_DOWNLOAD_PATH = "config-templates/";
    private Set<Long> assignedOverlayPorts = new HashSet<>();
    private final Map<String, Protos.TaskInfo.Builder> taskBuilders = new HashMap<>();
    private Protos.ExecutorInfo.Builder executorBuilder;
    private final PodInstance podInstance;
    // TODO(nickbp): Remove this env storage after October 2017 when it's no longer used as a fallback for dynamic ports
    private final Map<String, Map<String, String>> lastTaskEnvs;
    private final Map<String, Map<String, Long>> lastTaskPorts;

    public PodInfoBuilder(
            PodInstanceRequirement podInstanceRequirement,
            String serviceName,
            UUID targetConfigId,
            SchedulerFlags schedulerFlags,
            Collection<Protos.TaskInfo> currentPodTasks)
                    throws InvalidRequirementException {
        PodInstance podInstance = podInstanceRequirement.getPodInstance();

        // Generate new TaskInfos based on the task spec. To keep things consistent, we always generate new TaskInfos
        // from scratch, with the only carry-over being the prior task environment.
        for (TaskSpec taskSpec : podInstance.getPod().getTasks()) {
            Protos.TaskInfo.Builder taskInfoBuilder = createTaskInfo(
                    podInstance,
                    taskSpec,
                    podInstanceRequirement.getEnvironment(),
                    serviceName,
                    targetConfigId);
            // Store tasks against the task spec name 'node' instead of 'broker-0-node': the pod segment is redundant
            // as we're only looking at tasks within a given pod
            this.taskBuilders.put(taskSpec.getName(), taskInfoBuilder);

            taskSpec.getResourceSet().getResources().stream()
                    .filter(resourceSpec -> resourceSpec.getName().equals(Constants.PORTS_RESOURCE_TYPE))
                    .filter(resourceSpec -> resourceSpec.getValue().getRanges().getRange(0).getBegin() > 0)
                    .forEach(resourceSpec -> assignedOverlayPorts
                            .add(resourceSpec.getValue().getRanges().getRange(0).getBegin()));

        }

        this.executorBuilder = getExecutorInfoBuilder(serviceName, podInstance, targetConfigId, schedulerFlags);

        this.podInstance = podInstance;

        this.lastTaskPorts = new HashMap<>();
        this.lastTaskEnvs = new HashMap<>();
        for (Protos.TaskInfo currentTask : currentPodTasks) {
            // Just store against the full TaskInfo name ala 'broker-0-node'. The task spec name will be mapped to the
            // TaskInfo name in the getter function below. This is easier than extracting the task spec name from the
            // TaskInfo name.
            Map<String, Long> taskPorts = new HashMap<>();
            for (Protos.Port port : currentTask.getDiscovery().getPorts().getPortsList()) {
                if (!Strings.isEmpty(port.getName())) {
                    taskPorts.put(port.getName(), (long) port.getNumber());
                }
            }
            this.lastTaskPorts.put(currentTask.getName(), taskPorts);
            this.lastTaskEnvs.put(currentTask.getName(), EnvUtils.toMap(currentTask.getCommand().getEnvironment()));
        }

        for (Protos.TaskInfo.Builder taskBuilder : taskBuilders.values()) {
            validateTaskInfo(taskBuilder);
        }
    }

    public Collection<Protos.TaskInfo.Builder> getTaskBuilders() {
        return taskBuilders.values();
    }

    public Protos.TaskInfo.Builder getTaskBuilder(String taskSpecName) {
        return taskBuilders.get(taskSpecName);
    }

    public Optional<Protos.ExecutorInfo.Builder> getExecutorBuilder() {
        return Optional.ofNullable(executorBuilder);
    }

    public void setExecutorBuilder(Protos.ExecutorInfo.Builder executorBuilder) {
        this.executorBuilder = executorBuilder;
    }

    /**
     * This is the only carry-over from old tasks: If a port was dynamically allocated, we want to avoid reallocating
     * it on task relaunch.
     */
    public Optional<Long> lookupPriorTaskPortValue(String taskSpecName, String portName, String portEnvName) {
        Map<String, Long> taskPorts = lastTaskPorts.get(TaskSpec.getInstanceName(podInstance, taskSpecName));
        if (taskPorts != null) {
            Long lastPort = taskPorts.get(portName);
            if (lastPort != null) {
                return Optional.of(lastPort);
            }
        }

        // Fall back to searching the task environment.
        // Tasks launched in older SDK releases may omit the port names in the DiscoveryInfo.
        // TODO(nickbp): Remove this fallback after October 2017
        Map<String, String> env = lastTaskEnvs.get(TaskSpec.getInstanceName(podInstance, taskSpecName));
        if (env != null) {
            try {
                return Optional.ofNullable(Long.parseLong(env.get(portEnvName)));
            } catch (NumberFormatException e) {
                // We're just making a best-effort attempt to recover the port value, so give up if this happens.
                LOGGER.warn(String.format("Failed to recover port %s from task %s environment variable %s",
                        portName, portEnvName, taskSpecName), e);
            }
        }

        // Port not found.
        return Optional.empty();
    }

    public Collection<Protos.Resource.Builder> getTaskResourceBuilders() {
        return taskBuilders.values().stream()
                .map(t -> t.getResourcesBuilderList())
                .flatMap(xs -> xs.stream())
                .collect(Collectors.toList());
    }

    public Collection<Protos.Resource.Builder> getExecutorResourceBuilders() {
        return new ArrayList<>(executorBuilder.getResourcesBuilderList());
    }

    public boolean isAssignedOverlayPort(long candidatePort) {
        return assignedOverlayPorts.contains(candidatePort);
    }

    public void addAssignedOverlayPort(long port) {
        assignedOverlayPorts.add(port);
    }

    @VisibleForTesting
    public Set<Long> getAssignedOverlayPorts() {
        return assignedOverlayPorts;
    }

    private static Protos.TaskInfo.Builder createTaskInfo(
            PodInstance podInstance,
            TaskSpec taskSpec,
            Map<String, String> environment,
            String serviceName,
            UUID targetConfigurationId) throws InvalidRequirementException {
        Protos.TaskInfo.Builder taskInfoBuilder = Protos.TaskInfo.newBuilder()
                .setName(TaskSpec.getInstanceName(podInstance, taskSpec))
                .setTaskId(CommonIdUtils.emptyTaskId())
                .setSlaveId(CommonIdUtils.emptyAgentId());

        if (!podInstance.getPod().getNetworks().isEmpty()) {
            taskInfoBuilder.setContainer(getContainerInfo(podInstance.getPod(), false));
        } else {
            taskInfoBuilder.setContainer(Protos.ContainerInfo.newBuilder().setType(Protos.ContainerInfo.Type.MESOS));
        }

        // create default labels:
        taskInfoBuilder.setLabels(new TaskLabelWriter(taskInfoBuilder)
                .setTargetConfiguration(targetConfigurationId)
                .setGoalState(taskSpec.getGoal())
                .setType(podInstance.getPod().getType())
                .setIndex(podInstance.getIndex())
                .toProto());

        if (taskSpec.getCommand().isPresent()) {
            CommandSpec commandSpec = taskSpec.getCommand().get();
            taskInfoBuilder.getCommandBuilder()
                    .setValue(commandSpec.getValue())
                    .setEnvironment(getTaskEnvironment(serviceName, podInstance, taskSpec, commandSpec));
            setBootstrapConfigFileEnv(taskInfoBuilder.getCommandBuilder(), taskSpec);
            extendEnv(taskInfoBuilder.getCommandBuilder(), environment);
        }

        if (taskSpec.getDiscovery().isPresent()) {
            taskInfoBuilder.setDiscovery(getDiscoveryInfo(taskSpec.getDiscovery().get(), podInstance.getIndex()));
        }

        setHealthCheck(taskInfoBuilder, serviceName, podInstance, taskSpec, taskSpec.getCommand().get());
        setReadinessCheck(taskInfoBuilder, serviceName, podInstance, taskSpec, taskSpec.getCommand().get());

        return taskInfoBuilder;
    }

    private static Protos.ExecutorInfo.Builder getExecutorInfoBuilder(
            String serviceName,
            PodInstance podInstance,
            UUID targetConfigurationId,
            SchedulerFlags schedulerFlags) throws IllegalStateException {
        PodSpec podSpec = podInstance.getPod();
        Protos.ExecutorInfo.Builder executorInfoBuilder = Protos.ExecutorInfo.newBuilder()
                .setName(podSpec.getType())
                .setExecutorId(Protos.ExecutorID.newBuilder().setValue("").build()); // Set later by ExecutorRequirement
        // Populate ContainerInfo with the appropriate information from PodSpec.
        // This includes networks, rlimits, secret volumes...
        Protos.ContainerInfo containerInfo = getContainerInfo(podSpec);
        if (containerInfo != null) {
            executorInfoBuilder.setContainer(containerInfo);
        }

        // command and user:
        Protos.CommandInfo.Builder executorCommandBuilder = executorInfoBuilder.getCommandBuilder().setValue(
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
        executorCommandBuilder.addUrisBuilder().setValue(schedulerFlags.getLibmesosURI());
        executorCommandBuilder.addUrisBuilder().setValue(schedulerFlags.getJavaURI());

        // Any URIs defined in PodSpec itself.
        for (URI uri : podSpec.getUris()) {
            executorCommandBuilder.addUrisBuilder().setValue(uri.toString());
        }

        // Pod-wide default envvars and secret envvars

        // Secrets are constructed differently from other envvars where the proto is concerned:
        for (SecretSpec secretSpec : podInstance.getPod().getSecrets()) {
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

        executorInfoBuilder.getLabelsBuilder().addLabelsBuilder()
                .setKey("DCOS_SPACE")
                .setValue(getDcosSpaceLabel());

        return executorInfoBuilder;
    }

    private static Protos.Environment getTaskEnvironment(
            String serviceName, PodInstance podInstance, TaskSpec taskSpec, CommandSpec commandSpec) {
        Map<String, String> environmentMap = new TreeMap<>();

        // Task envvars from either of the following sources:
        // - ServiceSpec (provided by developer)
        // - TASKCFG_<podname>_* (provided by user, handled when parsing YAML, potentially overrides ServiceSpec)
        environmentMap.putAll(commandSpec.getEnvironment());

        // Default envvars for use by executors/developers:
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

        return EnvUtils.toProto(environmentMap);
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

    private static void setHealthCheck(
            Protos.TaskInfo.Builder taskInfo,
            String serviceName,
            PodInstance podInstance,
            TaskSpec taskSpec,
            CommandSpec commandSpec) {
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

        Protos.CommandInfo.Builder healthCheckCommandBuilder = healthCheckBuilder.getCommandBuilder()
                .setValue(healthCheckSpec.getCommand());
        if (taskSpec.getCommand().isPresent()) {
            healthCheckCommandBuilder.setEnvironment(
                    getTaskEnvironment(serviceName, podInstance, taskSpec, commandSpec));
        }
    }

    private static void setReadinessCheck(
            Protos.TaskInfo.Builder taskInfoBuilder,
            String serviceName,
            PodInstance podInstance,
            TaskSpec taskSpec,
            CommandSpec commandSpec) {
        if (!taskSpec.getReadinessCheck().isPresent()) {
            LOGGER.debug("No readiness check defined for taskSpec: {}", taskSpec.getName());
            return;
        }

        ReadinessCheckSpec readinessCheckSpec = taskSpec.getReadinessCheck().get();
        Protos.HealthCheck.Builder builder = Protos.HealthCheck.newBuilder()
                .setDelaySeconds(readinessCheckSpec.getDelay())
                .setIntervalSeconds(readinessCheckSpec.getInterval())
                .setTimeoutSeconds(readinessCheckSpec.getTimeout())
                .setConsecutiveFailures(0)
                .setGracePeriodSeconds(0);

        Protos.CommandInfo.Builder readinessCheckCommandBuilder = builder.getCommandBuilder()
                .setValue(readinessCheckSpec.getCommand());
        if (taskSpec.getCommand().isPresent()) {
            readinessCheckCommandBuilder.setEnvironment(
                    getTaskEnvironment(serviceName, podInstance, taskSpec, commandSpec));
        }

        taskInfoBuilder.setLabels(new TaskLabelWriter(taskInfoBuilder)
                .setReadinessCheck(builder.build())
                .toProto());
    }

    private static String getConfigTemplateDownloadPath(ConfigFileSpec config) {
        // Name is unique.
        return String.format("%s%s", CONFIG_TEMPLATE_DOWNLOAD_PATH, config.getName());
    }

    private static Protos.ContainerInfo getContainerInfo(PodSpec podSpec, boolean addExtraParameters) {
        Collection<Protos.Volume> secretVolumes = getExecutorInfoSecretVolumes(podSpec.getSecrets());

        if (!podSpec.getImage().isPresent()
                && podSpec.getNetworks().isEmpty()
                && podSpec.getRLimits().isEmpty()
                && secretVolumes.isEmpty()) {
            return null;
        }

        Protos.ContainerInfo.Builder containerInfo = Protos.ContainerInfo.newBuilder()
                .setType(Protos.ContainerInfo.Type.MESOS);

        if (podSpec.getImage().isPresent() && addExtraParameters) {
            containerInfo.getMesosBuilder()
                    .setImage(Protos.Image.newBuilder()
                            .setType(Protos.Image.Type.DOCKER)
                            .setDocker(Protos.Image.Docker.newBuilder()
                                    .setName(podSpec.getImage().get())));
        }

        if (!podSpec.getNetworks().isEmpty()) {
            containerInfo.addAllNetworkInfos(
                    podSpec.getNetworks().stream().map(PodInfoBuilder::getNetworkInfo).collect(Collectors.toList()));
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

    private static Protos.ContainerInfo getContainerInfo(PodSpec podSpec) {
        return getContainerInfo(podSpec, true);
    }

    private static Protos.NetworkInfo getNetworkInfo(NetworkSpec networkSpec) {
        LOGGER.info("Loading NetworkInfo for network named \"{}\"", networkSpec.getName());
        Protos.NetworkInfo.Builder netInfoBuilder = Protos.NetworkInfo.newBuilder();
        netInfoBuilder.setName(networkSpec.getName());

        if (!DcosConstants.isSupportedNetwork(networkSpec.getName())) {
            LOGGER.warn(String.format("Virtual network %s is not currently supported, you " +
                    "may experience unexpected behavior", networkSpec.getName()));
        }

        if (!networkSpec.getPortMappings().isEmpty()) {
            for (Map.Entry<Integer, Integer> e : networkSpec.getPortMappings().entrySet()) {
                Integer hostPort = e.getKey();
                Integer containerPort = e.getValue();
                netInfoBuilder.addPortMappings(Protos.NetworkInfo.PortMapping.newBuilder()
                        .setHostPort(hostPort)
                        .setContainerPort(containerPort)
                        .build());
            }
        }

        if (!networkSpec.getLabels().isEmpty()) {
            List<Protos.Label> labelList = networkSpec.getLabels().entrySet().stream()
                    .map(e -> Protos.Label.newBuilder().setKey(e.getKey()).setValue(e.getValue()).build())
                    .collect(Collectors.toList());
            netInfoBuilder.setLabels(Protos.Labels.newBuilder().addAllLabels(labelList).build());
        }

        return netInfoBuilder.build();
    }

    private static Protos.RLimitInfo getRLimitInfo(Collection<RLimit> rlimits) {
        Protos.RLimitInfo.Builder rLimitInfoBuilder = Protos.RLimitInfo.newBuilder();

        for (RLimit rLimit : rlimits) {
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

    public String getType() {
        return podInstance.getPod().getType();
    }

    public int getIndex() {
        return podInstance.getIndex();
    }

    public PodInstance getPodInstance() {
        return podInstance;
    }

    /**
     * Checks that the TaskInfo is valid at the point of requirement construction, making it
     * easier for the framework developer to trace problems in their implementation. These checks
     * reflect requirements enforced elsewhere, eg in {@link com.mesosphere.sdk.state.StateStore}.
     */
    private static void validateTaskInfo(Protos.TaskInfo.Builder builder)
            throws InvalidRequirementException {
        if (!builder.hasName() || StringUtils.isEmpty(builder.getName())) {
            throw new InvalidRequirementException(String.format(
                    "TaskInfo must have a name: %s", builder));
        }

        if (builder.hasTaskId()
                && !StringUtils.isEmpty(builder.getTaskId().getValue())) {
            // Task ID may be included if this is replacing an existing task. In that case, we still
            // perform a sanity check to ensure that the original Task ID was formatted correctly.
            // We must allow Task ID to be present but empty because it is a required proto field.
            String taskName;
            try {
                taskName = CommonIdUtils.toTaskName(builder.getTaskId());
            } catch (TaskException e) {
                throw new InvalidRequirementException(String.format(
                        "When non-empty, TaskInfo.id must be a valid ID. "
                                + "Set to an empty string or leave existing valid value. %s %s",
                        builder, e));
            }
            if (!taskName.equals(builder.getName())) {
                throw new InvalidRequirementException(String.format(
                        "When non-empty, TaskInfo.id must align with TaskInfo.name. Use "
                                + "TaskUtils.toTaskId(): %s", builder));
            }
        }

        if (builder.hasExecutor()) {
            throw new InvalidRequirementException(String.format(
                    "TaskInfo must not contain ExecutorInfo. "
                            + "Use ExecutorRequirement for any Executor requirements: %s", builder));
        }

        TaskLabelReader labels = new TaskLabelReader(builder);

        try {
            labels.getType();
        } catch (TaskException e) {
            throw new InvalidRequirementException(e);
        }

        try {
            labels.getIndex();
        } catch (TaskException e) {
            throw new InvalidRequirementException(e);
        }
    }

    private static Protos.Secret getReferenceSecret(String secretPath) {
        return Protos.Secret.newBuilder()
                .setType(Protos.Secret.Type.REFERENCE)
                .setReference(Protos.Secret.Reference.newBuilder().setName(secretPath))
                .build();
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

    private static String getDcosSpaceLabel() {
        String labelString = System.getenv("DCOS_SPACE");
        if (labelString == null) {
            labelString = System.getenv("MARATHON_APP_ID");
        }
        if (labelString == null) {
            return "/"; // No Authorization for this framework
        }
        return labelString;
    }

    @Override
    public String toString() {
        return ToStringBuilder.reflectionToString(this);
    }

}
