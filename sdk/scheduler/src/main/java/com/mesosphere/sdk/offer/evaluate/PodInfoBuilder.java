package com.mesosphere.sdk.offer.evaluate;

import com.google.common.annotations.VisibleForTesting;
import com.mesosphere.sdk.api.ArtifactResource;
import com.mesosphere.sdk.api.EndpointUtils;
import com.mesosphere.sdk.dcos.DcosConstants;
import com.mesosphere.sdk.offer.CommonIdUtils;
import com.mesosphere.sdk.offer.Constants;
import com.mesosphere.sdk.offer.InvalidRequirementException;
import com.mesosphere.sdk.offer.TaskException;
import com.mesosphere.sdk.offer.taskdata.AuxLabelAccess;
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
    private final Map<String, TaskPortLookup> portsByTask;
    private final boolean useDefaultExecutor;

    public PodInfoBuilder(
            PodInstanceRequirement podInstanceRequirement,
            String serviceName,
            UUID targetConfigId,
            SchedulerFlags schedulerFlags,
            Collection<Protos.TaskInfo> currentPodTasks,
            Protos.FrameworkID frameworkID,
            boolean useDefaultExecutor) throws InvalidRequirementException {
        PodInstance podInstance = podInstanceRequirement.getPodInstance();
        this.useDefaultExecutor = useDefaultExecutor;

        // Generate new TaskInfos based on the task spec. To keep things consistent, we always generate new TaskInfos
        // from scratch, with the only carry-over being the prior task environment.
        for (TaskSpec taskSpec : podInstance.getPod().getTasks()) {
            Protos.TaskInfo.Builder taskInfoBuilder = createTaskInfo(
                    podInstance,
                    taskSpec,
                    podInstanceRequirement.getEnvironment(),
                    serviceName,
                    targetConfigId,
                    schedulerFlags);
            // Store tasks against the task spec name 'node' instead of 'broker-0-node': the pod segment is redundant
            // as we're only looking at tasks within a given pod
            this.taskBuilders.put(taskSpec.getName(), taskInfoBuilder);

            taskSpec.getResourceSet().getResources().stream()
                    .filter(resourceSpec -> resourceSpec.getName().equals(Constants.PORTS_RESOURCE_TYPE))
                    .filter(resourceSpec -> resourceSpec.getValue().getRanges().getRange(0).getBegin() > 0)
                    .forEach(resourceSpec -> assignedOverlayPorts
                            .add(resourceSpec.getValue().getRanges().getRange(0).getBegin()));

        }

        this.executorBuilder = getExecutorInfoBuilder(
                serviceName, podInstance, frameworkID, targetConfigId, schedulerFlags);

        this.podInstance = podInstance;
        this.portsByTask = new HashMap<>();
        for (Protos.TaskInfo currentTask : currentPodTasks) {
            // Just store against the full TaskInfo name ala 'broker-0-node'. The task spec name will be mapped to the
            // TaskInfo name in the getter function below. This is easier than extracting the task spec name from the
            // TaskInfo name.
            portsByTask.put(currentTask.getName(), new TaskPortLookup(currentTask));
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
    public Optional<Long> getPriorPortForTask(String taskSpecName, PortSpec portSpec) {
        TaskPortLookup portFinder = portsByTask.get(TaskSpec.getInstanceName(podInstance, taskSpecName));
        if (portFinder == null) {
            return Optional.empty();
        }
        return portFinder.getPriorPort(portSpec);
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

    public void setExecutorVolume(VolumeSpec volumeSpec) {
        // Volumes on the executor must be declared in each TaskInfo.ContainerInfo to be shared among them.
        if (useDefaultExecutor) {
            Protos.Volume executorVolume = getVolume(volumeSpec);

            for (Protos.TaskInfo.Builder t : getTaskBuilders()) {
                t.getContainerBuilder()
                        .setType(Protos.ContainerInfo.Type.MESOS)
                        .addVolumes(executorVolume);
            }
        }
    }

    public static Protos.Resource getExistingExecutorVolume(
            VolumeSpec volumeSpec, String resourceId, String persistenceId) {
        Protos.Resource.Builder resourceBuilder = Protos.Resource.newBuilder()
                .setName("disk")
                .setType(Protos.Value.Type.SCALAR)
                .setScalar(volumeSpec.getValue().getScalar());

        Protos.Resource.DiskInfo.Builder diskInfoBuilder = resourceBuilder.getDiskBuilder();
        diskInfoBuilder.getPersistenceBuilder()
                .setId(persistenceId)
                .setPrincipal(volumeSpec.getPrincipal());
        diskInfoBuilder.getVolumeBuilder()
                .setContainerPath(volumeSpec.getContainerPath())
                .setMode(Protos.Volume.Mode.RW);

        Protos.Resource.ReservationInfo.Builder reservationBuilder = resourceBuilder.addReservationsBuilder();
        reservationBuilder
                .setPrincipal(volumeSpec.getPrincipal())
                .setRole(volumeSpec.getRole());
        AuxLabelAccess.setResourceId(reservationBuilder, resourceId);

        return resourceBuilder.build();
    }

    private static Protos.Volume getVolume(VolumeSpec volumeSpec) {
        Protos.Volume.Builder builder = Protos.Volume.newBuilder();
        builder.setMode(Protos.Volume.Mode.RW)
                .setContainerPath(volumeSpec.getContainerPath())
                .setSource(Protos.Volume.Source.newBuilder()
                        .setType(Protos.Volume.Source.Type.SANDBOX_PATH)
                        .setSandboxPath(Protos.Volume.Source.SandboxPath.newBuilder()
                                .setType(Protos.Volume.Source.SandboxPath.Type.PARENT)
                                .setPath(volumeSpec.getContainerPath())));

        return builder.build();
    }

    private Protos.TaskInfo.Builder createTaskInfo(
            PodInstance podInstance,
            TaskSpec taskSpec,
            Map<String, String> environment,
            String serviceName,
            UUID targetConfigurationId,
            SchedulerFlags schedulerFlags) throws InvalidRequirementException {
        PodSpec podSpec = podInstance.getPod();
        Protos.TaskInfo.Builder taskInfoBuilder = Protos.TaskInfo.newBuilder()
                .setName(TaskSpec.getInstanceName(podInstance, taskSpec))
                .setTaskId(CommonIdUtils.emptyTaskId())
                .setSlaveId(CommonIdUtils.emptyAgentId());

        // create default labels:
        taskInfoBuilder.setLabels(new TaskLabelWriter(taskInfoBuilder)
                .setTargetConfiguration(targetConfigurationId)
                .setGoalState(taskSpec.getGoal())
                .setType(podInstance.getPod().getType())
                .setIndex(podInstance.getIndex())
                .toProto());

        if (taskSpec.getCommand().isPresent()) {
            CommandSpec commandSpec = taskSpec.getCommand().get();
            Protos.CommandInfo.Builder commandBuilder = taskInfoBuilder.getCommandBuilder();
            commandBuilder.setValue(commandSpec.getValue())
                    .setEnvironment(getTaskEnvironment(serviceName, podInstance, taskSpec, commandSpec));
            setBootstrapConfigFileEnv(taskInfoBuilder.getCommandBuilder(), taskSpec);
            extendEnv(taskInfoBuilder.getCommandBuilder(), environment);

            if (useDefaultExecutor) {
                // Any URIs defined in PodSpec itself.
                for (URI uri : podSpec.getUris()) {
                    commandBuilder.addUrisBuilder().setValue(uri.toString());
                }

                for (ConfigFileSpec config : taskSpec.getConfigFiles()) {
                    commandBuilder.addUrisBuilder()
                            .setValue(ArtifactResource.getTemplateUrl(
                                    serviceName,
                                    targetConfigurationId,
                                    podSpec.getType(),
                                    taskSpec.getName(),
                                    config.getName()))
                            .setOutputFile(getConfigTemplateDownloadPath(config))
                            .setExtract(false);
                }

                // Secrets are constructed differently from other envvars where the proto is concerned:
                for (SecretSpec secretSpec : podInstance.getPod().getSecrets()) {
                    if (secretSpec.getEnvKey().isPresent()) {
                        commandBuilder.getEnvironmentBuilder().addVariablesBuilder()
                                .setName(secretSpec.getEnvKey().get())
                                .setType(Protos.Environment.Variable.Type.SECRET)
                                .setSecret(getReferenceSecret(secretSpec.getSecretPath()));
                    }
                }

                if (podSpec.getUser().isPresent()) {
                    commandBuilder.setUser(podSpec.getUser().get());
                }
            }
        }

        if (taskSpec.getDiscovery().isPresent()) {
            taskInfoBuilder.setDiscovery(getDiscoveryInfo(taskSpec.getDiscovery().get(), podInstance.getIndex()));
        }

        //TODO(nickbp): This ContainerInfo handling has turned a bit spaghetti-like and needs cleaning up.
        //              Currently blindly retaining prior behavior.
        if (useDefaultExecutor) {
            taskInfoBuilder.setContainer(getContainerInfo(podInstance.getPod(), true, true));
        } else if (!podInstance.getPod().getNetworks().isEmpty()) {
            taskInfoBuilder.setContainer(getContainerInfo(podInstance.getPod(), false, true));
        } else {
            taskInfoBuilder.setContainer(Protos.ContainerInfo.newBuilder().setType(Protos.ContainerInfo.Type.MESOS));
        }

        setHealthCheck(taskInfoBuilder, serviceName, podInstance, taskSpec, taskSpec.getCommand().get());
        setReadinessCheck(taskInfoBuilder, serviceName, podInstance, taskSpec, taskSpec.getCommand().get());

        return taskInfoBuilder;
    }

    private Protos.ExecutorInfo.Builder getExecutorInfoBuilder(
            String serviceName,
            PodInstance podInstance,
            Protos.FrameworkID frameworkID,
            UUID targetConfigurationId,
            SchedulerFlags schedulerFlags) throws IllegalStateException {
        PodSpec podSpec = podInstance.getPod();
        Protos.ExecutorInfo.Builder executorInfoBuilder = Protos.ExecutorInfo.newBuilder()
                .setName(podSpec.getType())
                .setExecutorId(Protos.ExecutorID.newBuilder().setValue("").build());
        AuxLabelAccess.setDcosSpace(executorInfoBuilder, schedulerFlags.getDcosSpaceLabelValue());

        if (useDefaultExecutor) {
            executorInfoBuilder.setType(Protos.ExecutorInfo.Type.DEFAULT)
                    .setFrameworkId(frameworkID);
        } else {
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
        }

        // Populate ContainerInfo with the appropriate information from PodSpec
        // This includes networks, rlimits, secret volumes...
        executorInfoBuilder.setContainer(getContainerInfo(podSpec, true, false));

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

    private void setHealthCheck(
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

        if (useDefaultExecutor) {
            healthCheckBuilder.setType(Protos.HealthCheck.Type.COMMAND);
        }

        Protos.CommandInfo.Builder healthCheckCommandBuilder = healthCheckBuilder.getCommandBuilder()
                .setValue(healthCheckSpec.getCommand());
        if (taskSpec.getCommand().isPresent()) {
            healthCheckCommandBuilder.setEnvironment(
                    getTaskEnvironment(serviceName, podInstance, taskSpec, commandSpec));
        }
    }

    private void setReadinessCheck(
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

        Protos.CommandInfo.Builder readinessCheckCommandBuilder;
        if (useDefaultExecutor) {
            Protos.CheckInfo.Builder builder = taskInfoBuilder.getCheckBuilder()
                    .setType(Protos.CheckInfo.Type.COMMAND)
                    .setDelaySeconds(readinessCheckSpec.getDelay())
                    .setIntervalSeconds(readinessCheckSpec.getInterval())
                    .setTimeoutSeconds(readinessCheckSpec.getTimeout());

            readinessCheckCommandBuilder = builder.getCommandBuilder().getCommandBuilder();
            readinessCheckCommandBuilder.setValue(readinessCheckSpec.getCommand());
            if (taskSpec.getCommand().isPresent()) {
                readinessCheckCommandBuilder.setEnvironment(
                        getTaskEnvironment(serviceName, podInstance, taskSpec, commandSpec));
            }
        } else {
            Protos.HealthCheck.Builder builder = Protos.HealthCheck.newBuilder()
                    .setDelaySeconds(readinessCheckSpec.getDelay())
                    .setIntervalSeconds(readinessCheckSpec.getInterval())
                    .setTimeoutSeconds(readinessCheckSpec.getTimeout());

            readinessCheckCommandBuilder = builder.getCommandBuilder();
            readinessCheckCommandBuilder.setValue(readinessCheckSpec.getCommand());
            if (taskSpec.getCommand().isPresent()) {
                readinessCheckCommandBuilder.setEnvironment(
                        getTaskEnvironment(serviceName, podInstance, taskSpec, commandSpec));
            }

            taskInfoBuilder.setLabels(new TaskLabelWriter(taskInfoBuilder)
                    .setReadinessCheck(builder.build())
                    .toProto());
        }
    }

    private static String getConfigTemplateDownloadPath(ConfigFileSpec config) {
        // Name is unique.
        return String.format("%s%s", CONFIG_TEMPLATE_DOWNLOAD_PATH, config.getName());
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

        if (!podSpec.getImage().isPresent()
                && podSpec.getNetworks().isEmpty()
                && podSpec.getRLimits().isEmpty()
                && secretVolumes.isEmpty()) {
            // Nothing left to do.
            return containerInfo.build();
        }

        boolean shouldAddImage =
                podSpec.getImage().isPresent() &&
                addExtraParameters &&
                ((isTaskContainer && useDefaultExecutor) || (!isTaskContainer && !useDefaultExecutor));
        if (shouldAddImage) {
            containerInfo.getMesosBuilder().getImageBuilder()
                    .setType(Protos.Image.Type.DOCKER)
                    .getDockerBuilder().setName(podSpec.getImage().get());
        }

        // With the default executor, all NetworkInfos must be defined on the executor itself rather than individual
        // tasks. This check can be made much less ugly once the custom executor no longer need be supported.
        if (!podSpec.getNetworks().isEmpty() && (!useDefaultExecutor || !isTaskContainer)) {
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

    @Override
    public String toString() {
        return ToStringBuilder.reflectionToString(this);
    }

}
