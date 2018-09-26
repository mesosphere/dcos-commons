package com.mesosphere.sdk.offer.evaluate;

import com.google.common.annotations.VisibleForTesting;
import com.mesosphere.sdk.dcos.DcosConstants;
import com.mesosphere.sdk.http.EndpointUtils;
import com.mesosphere.sdk.http.queries.ArtifactQueries;
import com.mesosphere.sdk.offer.*;
import com.mesosphere.sdk.offer.evaluate.placement.PlacementUtils;
import com.mesosphere.sdk.offer.taskdata.AuxLabelAccess;
import com.mesosphere.sdk.offer.taskdata.EnvConstants;
import com.mesosphere.sdk.offer.taskdata.EnvUtils;
import com.mesosphere.sdk.offer.taskdata.TaskLabelReader;
import com.mesosphere.sdk.offer.taskdata.TaskLabelWriter;
import com.mesosphere.sdk.scheduler.SchedulerConfig;
import com.mesosphere.sdk.scheduler.plan.PodInstanceRequirement;
import com.mesosphere.sdk.scheduler.recovery.FailureUtils;
import com.mesosphere.sdk.specification.*;
import com.mesosphere.sdk.state.GoalStateOverride;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.mesos.Protos;
import org.slf4j.Logger;

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
    private static final Logger LOGGER = LoggingUtils.getLogger(PodInfoBuilder.class);

    private static final String CONFIG_TEMPLATE_KEY_FORMAT = "CONFIG_TEMPLATE_%s";
    private static final String CONFIG_TEMPLATE_DOWNLOAD_PATH = "config-templates/";

    private final Set<Long> assignedOverlayPorts = new HashSet<>();
    private final Map<String, Protos.TaskInfo.Builder> taskBuilders = new HashMap<>();
    private final Protos.ExecutorInfo.Builder executorBuilder;
    private final PodInstance podInstance;
    private final Map<String, TaskPortLookup> portsByTask;

    public PodInfoBuilder(
            PodInstanceRequirement podInstanceRequirement,
            String serviceName,
            UUID targetConfigId,
            ArtifactQueries.TemplateUrlFactory templateUrlFactory,
            SchedulerConfig schedulerConfig,
            Collection<Protos.TaskInfo> currentPodTasks,
            Protos.FrameworkID frameworkID,
            Map<TaskSpec, GoalStateOverride> overrideMap) throws InvalidRequirementException {
        PodInstance podInstance = podInstanceRequirement.getPodInstance();

        // Generate new TaskInfos based on the task spec. To keep things consistent, we always generate new TaskInfos
        // from scratch, with the only carry-over being the prior task environment.
        for (TaskSpec taskSpec : podInstance.getPod().getTasks()) {
            Protos.TaskInfo.Builder taskInfoBuilder = createTaskInfo(
                    podInstance,
                    taskSpec,
                    podInstanceRequirement.getEnvironment(),
                    serviceName,
                    targetConfigId,
                    templateUrlFactory,
                    schedulerConfig,
                    overrideMap.get(taskSpec));
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
                podInstance, frameworkID, targetConfigId, templateUrlFactory, schedulerConfig);

        this.podInstance = podInstance;
        this.portsByTask = new HashMap<>();
        for (Protos.TaskInfo currentTask : currentPodTasks) {
            // Just store against the full TaskInfo name ala 'broker-0-node'. The task spec name will be mapped to the
            // TaskInfo name in the getter function below. This is easier than extracting the task spec name from the
            // TaskInfo name.

            // If the pod was replaced, discard any previously used ports. We want dynamic ports
            // to re-roll.
            if (!FailureUtils.isPermanentlyFailed(currentTask)) {
                portsByTask.put(currentTask.getName(), new TaskPortLookup(currentTask));
            }
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

        Protos.Volume executorVolume = getVolume(volumeSpec);

        for (Protos.TaskInfo.Builder t : getTaskBuilders()) {
            t.getContainerBuilder()
                    .setType(Protos.ContainerInfo.Type.MESOS)
                    .addVolumes(executorVolume);
        }

    }

    public static Protos.Resource getExistingExecutorVolume(
            VolumeSpec volumeSpec,
            Optional<String> resourceId,
            Optional<String> resourceNamespace,
            Optional<String> persistenceId,
            Optional<Protos.ResourceProviderID> providerId,
            Optional<Protos.Resource.DiskInfo.Source> diskSource) {

        Protos.Resource.Builder builder = ResourceBuilder
                .fromSpec(volumeSpec, resourceId, resourceNamespace, persistenceId, providerId, diskSource)
                .build()
                .toBuilder();

        return builder.build();
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
            ArtifactQueries.TemplateUrlFactory templateUrlFactory,
            SchedulerConfig schedulerConfig,
            GoalStateOverride override) throws InvalidRequirementException {
        if (override == null) {
            override = GoalStateOverride.NONE;
        }

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
            Protos.CommandInfo.Builder commandBuilder = taskInfoBuilder.getCommandBuilder();
            commandBuilder.setEnvironment(EnvUtils.toProto(getTaskEnvironment(serviceName,
                    podInstance,
                    taskSpec,
                    schedulerConfig)));

            if (override.equals(GoalStateOverride.PAUSED)) {
                LOGGER.info("Overriding task command: {}", override);
                commandBuilder.setValue(schedulerConfig.getPauseOverrideCmd());
            } else {
                commandBuilder.setValue(taskSpec.getCommand().get().getValue());
            }

            setBootstrapConfigFileEnv(taskInfoBuilder.getCommandBuilder(), taskSpec);
            extendEnv(taskInfoBuilder.getCommandBuilder(), environment);

            // Always add the bootstrap URI as the paused command depends on it
            if (override.equals(GoalStateOverride.PAUSED)) {
                commandBuilder.addUrisBuilder().setValue(schedulerConfig.getBootstrapURI());
            }


            // Any URIs defined in PodSpec itself.
            for (URI uri : podSpec.getUris()) {
                commandBuilder.addUrisBuilder().setValue(uri.toString());
            }

            for (ConfigFileSpec config : taskSpec.getConfigFiles()) {
                commandBuilder.addUrisBuilder()
                        .setValue(templateUrlFactory.get(
                                targetConfigurationId, podSpec.getType(), taskSpec.getName(), config.getName()))
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

        if (taskSpec.getDiscovery().isPresent()) {
            taskInfoBuilder.setDiscovery(getDiscoveryInfo(taskSpec.getDiscovery().get(), podInstance.getIndex()));
        }


        taskInfoBuilder.setContainer(getContainerInfo(podInstance.getPod(), true, true));


        setHealthCheck(taskInfoBuilder, serviceName, podInstance, taskSpec, override, schedulerConfig);
        setReadinessCheck(taskInfoBuilder, serviceName, podInstance, taskSpec, override, schedulerConfig);
        setTaskKillGracePeriod(taskInfoBuilder, taskSpec);

        return taskInfoBuilder;
    }

    private Protos.ExecutorInfo.Builder getExecutorInfoBuilder(
            PodInstance podInstance,
            Protos.FrameworkID frameworkID,
            UUID targetConfigurationId,
            ArtifactQueries.TemplateUrlFactory templateUrlFactory,
            SchedulerConfig schedulerConfig) throws IllegalStateException {
        PodSpec podSpec = podInstance.getPod();
        Protos.ExecutorInfo.Builder executorInfoBuilder = Protos.ExecutorInfo.newBuilder()
                .setName(podSpec.getType())
                .setExecutorId(Protos.ExecutorID.newBuilder().setValue("").build());
        AuxLabelAccess.setDcosSpace(executorInfoBuilder, schedulerConfig.getDcosSpace());


        executorInfoBuilder.setType(Protos.ExecutorInfo.Type.DEFAULT)
                .setFrameworkId(frameworkID);

        // Populate ContainerInfo with the appropriate information from PodSpec
        // This includes networks, rlimits, secret volumes...
        executorInfoBuilder.setContainer(getContainerInfo(podSpec, true, false));

        return executorInfoBuilder;
    }

    /**
     * Generates a Task environment containing the configured environment values from the {@link CommandSpec}, along
     * with a set of default environment variables that all SDK tasks get for free.
     */
    @VisibleForTesting
    public static Map<String, String> getTaskEnvironment(
            String serviceName, PodInstance podInstance, TaskSpec taskSpec, SchedulerConfig schedulerConfig) {
        Map<String, String> environmentMap = new TreeMap<>();

        // Task envvars from either of the following sources:
        // - ServiceSpec (provided by developer)
        // - TASKCFG_<podname>_* (provided by user, handled when parsing YAML, potentially overrides ServiceSpec)
        if (taskSpec.getCommand().isPresent()) {
            environmentMap.putAll(taskSpec.getCommand().get().getEnvironment());
        }

        // Default envvars for use by executors/developers:
        // Unline the envvars added in getExecutorEnvironment(), these are specific to individual tasks and currently
        // aren't visible to sidecar tasks (as they would need to be added at the executor...):

        // Inject Pod Instance Index
        environmentMap.put(EnvConstants.POD_INSTANCE_INDEX_TASKENV, String.valueOf(podInstance.getIndex()));
        // Inject Framework Name (raw, not safe for use in hostnames)
        environmentMap.put(EnvConstants.FRAMEWORK_NAME_TASKENV, serviceName);
        // Inject Framework pod host domain (with hostname-safe framework name)
        environmentMap.put(EnvConstants.FRAMEWORK_HOST_TASKENV,
                EndpointUtils.toAutoIpDomain(serviceName, schedulerConfig));
        // Inject Framework VIP domain (with hostname-safe framework name)
        environmentMap.put(EnvConstants.FRAMEWORK_VIP_HOST_TASKENV,
                EndpointUtils.toVipDomain(serviceName, schedulerConfig));
        // Inject Scheduler API hostname (with hostname-safe scheduler name)
        environmentMap.put(EnvConstants.SCHEDULER_API_HOSTNAME_TASKENV,
                EndpointUtils.toSchedulerAutoIpHostname(serviceName, schedulerConfig));
        environmentMap.put(EnvConstants.SCHEDULER_API_PORT_TASKENV, String.valueOf(schedulerConfig.getApiServerPort()));

        // Inject TASK_NAME as KEY:VALUE
        environmentMap.put(EnvConstants.TASK_NAME_TASKENV, TaskSpec.getInstanceName(podInstance, taskSpec));
        // Inject TASK_NAME as KEY for conditional mustache templating
        environmentMap.put(TaskSpec.getInstanceName(podInstance, taskSpec), "true");

        // Inject PLACEMENT_REFERENCED_REGION
        environmentMap.put(
                EnvConstants.PLACEMENT_REFERENCED_REGION_ENV,
                String.valueOf(PlacementUtils.placementRuleReferencesRegion(podInstance.getPod())));
        // Inject PLACEMENT_REFERENCED_ZONE
        environmentMap.put(
                EnvConstants.PLACEMENT_REFERENCED_ZONE_ENV,
                String.valueOf(PlacementUtils.placementRuleReferencesZone(podInstance.getPod())));

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
            GoalStateOverride override,
            SchedulerConfig schedulerConfig) {
        if (!taskSpec.getHealthCheck().isPresent()) {
            LOGGER.debug("No health check defined for taskSpec: {}", taskSpec.getName());
            return;
        }

        if (override.equals(GoalStateOverride.PAUSED)) {
            LOGGER.info("Removing health check for PAUSED task: {}", taskSpec.getName());
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
                .setEnvironment(EnvUtils.toProto(getTaskEnvironment(serviceName,
                        podInstance,
                        taskSpec,
                        schedulerConfig)));
    }

    private Optional<ReadinessCheckSpec> getReadinessCheck(TaskSpec taskSpec, GoalStateOverride override) {
        if (override.equals(GoalStateOverride.PAUSED)) {
            // Go with an arbitrary interval/timeout of 5s. Leave delay at the default 0s:
            return Optional.of(DefaultReadinessCheckSpec.newBuilder(
                    GoalStateOverride.PAUSE_READINESS_COMMAND,
                    Constants.SHORT_DECLINE_SECONDS,
                    Constants.SHORT_DECLINE_SECONDS)
                    .build());
        }

        return taskSpec.getReadinessCheck();
    }

    private void setReadinessCheck(
            Protos.TaskInfo.Builder taskInfoBuilder,
            String serviceName,
            PodInstance podInstance,
            TaskSpec taskSpec,
            GoalStateOverride override,
            SchedulerConfig schedulerConfig) {

        Optional<ReadinessCheckSpec> readinessCheckSpecOptional = getReadinessCheck(taskSpec, override);
        if (!readinessCheckSpecOptional.isPresent()) {
            LOGGER.debug("No readiness check defined for taskSpec: {}", taskSpec.getName());
            return;
        }

        ReadinessCheckSpec readinessCheckSpec = readinessCheckSpecOptional.get();

        // Default executor supports the newer TaskInfo.check field:
        Protos.CheckInfo.Builder builder = taskInfoBuilder.getCheckBuilder()
                .setType(Protos.CheckInfo.Type.COMMAND)
                .setDelaySeconds(readinessCheckSpec.getDelay())
                .setIntervalSeconds(readinessCheckSpec.getInterval())
                .setTimeoutSeconds(readinessCheckSpec.getTimeout());
        builder.getCommandBuilder().getCommandBuilder()
                .setValue(readinessCheckSpec.getCommand())
                .setEnvironment(EnvUtils.toProto(getTaskEnvironment(serviceName,
                        podInstance,
                        taskSpec,
                        schedulerConfig)));

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
        Collection<Protos.Volume> hostVolumes = getExecutorInfoHostVolumes(podSpec.getHostVolumes());
        Protos.ContainerInfo.Builder containerInfo = Protos.ContainerInfo.newBuilder()
                .setType(Protos.ContainerInfo.Type.MESOS);

        if (isTaskContainer) {
            containerInfo.getLinuxInfoBuilder().setSharePidNamespace(podSpec.getSharePidNamespace());
        }

        // Isolate the tmp directory of tasks
        //switch to SANDBOX SELF after dc/os 1.13
        containerInfo.addVolumes(Protos.Volume.newBuilder()
                .setContainerPath("/tmp")
                .setHostPath("tmp")
                .setMode(Protos.Volume.Mode.RW));

        for (Protos.Volume hostVolume: hostVolumes) {
            containerInfo.addVolumes(hostVolume);
        }

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
                isTaskContainer;

        if (shouldAddImage) {
            containerInfo.getMesosBuilder().getImageBuilder()
                    .setType(Protos.Image.Type.DOCKER)
                    .getDockerBuilder().setName(podSpec.getImage().get());
        }

        // With the default executor, all NetworkInfos must be defined on the executor itself rather than individual
        // tasks. This check can be made much less ugly once the custom executor no longer need be supported.
        if (!podSpec.getNetworks().isEmpty() && !isTaskContainer) {
            LOGGER.info("Adding NetworkInfos for networks: {}",
                    podSpec.getNetworks().stream().map(n -> n.getName()).collect(Collectors.toList()));
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

    private static Collection<Protos.Volume> getExecutorInfoHostVolumes(Collection<HostVolumeSpec> hostVolumeSpecs) {
        Collection<Protos.Volume> volumes = new ArrayList<>();

        for (HostVolumeSpec hostVolumeSpec: hostVolumeSpecs) {
            volumes.add(Protos.Volume.newBuilder()
                    .setHostPath(hostVolumeSpec.getHostPath())
                    .setContainerPath(hostVolumeSpec.getContainerPath())
                    .setMode(Protos.Volume.Mode.RW)
                    .build());
        }

        return volumes;
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
