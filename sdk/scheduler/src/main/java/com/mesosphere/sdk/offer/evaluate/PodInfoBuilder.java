package com.mesosphere.sdk.offer.evaluate;

import com.mesosphere.sdk.api.ArtifactResource;
import com.mesosphere.sdk.dcos.DcosConstants;
import com.mesosphere.sdk.offer.CommonIdUtils;
import com.mesosphere.sdk.offer.InvalidRequirementException;
import com.mesosphere.sdk.offer.TaskException;
import com.mesosphere.sdk.offer.taskdata.EnvConstants;
import com.mesosphere.sdk.offer.taskdata.EnvUtils;
import com.mesosphere.sdk.offer.taskdata.SchedulerLabelReader;
import com.mesosphere.sdk.offer.taskdata.SchedulerLabelWriter;
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

    private final Map<String, Protos.TaskInfo.Builder> taskBuilders = new HashMap<>();
    private Protos.ExecutorInfo.Builder executorBuilder;
    private final PodInstance podInstance;
    private final Map<String, Map<String, String>> lastTaskEnvs;

    public PodInfoBuilder(
            PodInstanceRequirement podInstanceRequirement,
            String serviceName,
            UUID targetConfigId,
            SchedulerFlags schedulerFlags,
            Collection<Protos.TaskInfo> currentPodTasks,
            Protos.FrameworkID frameworkID) throws InvalidRequirementException {
        PodInstance podInstance = podInstanceRequirement.getPodInstance();
        for (TaskSpec taskSpec : podInstance.getPod().getTasks()) {
            Protos.TaskInfo.Builder taskInfoBuilder =
                    getTaskInfo(
                            podInstance,
                            taskSpec,
                            podInstanceRequirement.getEnvironment(),
                            serviceName,
                            targetConfigId).toBuilder();
            // Store tasks against the task spec name 'node' instead of 'broker-0-node': the pod segment is redundant
            // as we're only looking at tasks within a given pod
            this.taskBuilders.put(taskSpec.getName(), taskInfoBuilder);
        }

        this.executorBuilder = getExecutorInfo(
                podInstance.getPod(), serviceName, frameworkID, targetConfigId, schedulerFlags).toBuilder();

        this.podInstance = podInstance;

        this.lastTaskEnvs = new HashMap<>();
        for (Protos.TaskInfo currentTask : currentPodTasks) {
            // Just store against the full TaskInfo name ala 'broker-0-node'. The task spec name will be mapped to the
            // TaskInfo name in the getter function below. This is easier than extracting the task spec name from the
            // TaskInfo name.
            this.lastTaskEnvs.put(
                    currentTask.getName(), EnvUtils.toMap(currentTask.getCommand().getEnvironment()));
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

    public Optional<String> getLastTaskEnv(String taskSpecName, String envName) {
        Map<String, String> env = lastTaskEnvs.get(TaskSpec.getInstanceName(podInstance, taskSpecName));
        return (env == null)
            ? Optional.empty()
            : Optional.ofNullable(env.get(envName));
    }

    public Collection<Protos.Resource.Builder> getTaskResourceBuilders() {
        return taskBuilders.values().stream()
                .map(t -> t.getResourcesBuilderList())
                .flatMap(xs -> xs.stream())
                .collect(Collectors.toList());
    }

    public Collection<Protos.Resource.Builder> getExecutorResourceBuilders() {
        return executorBuilder.getResourcesBuilderList().stream()
                .collect(Collectors.toList());
    }

    private static Protos.TaskInfo getTaskInfo(
            PodInstance podInstance,
            TaskSpec taskSpec,
            Map<String, String> environment,
            String serviceName,
            UUID targetConfigurationId) throws InvalidRequirementException {
        Protos.TaskInfo.Builder taskInfoBuilder = Protos.TaskInfo.newBuilder()
                .setName(TaskSpec.getInstanceName(podInstance, taskSpec))
                .setTaskId(CommonIdUtils.emptyTaskId())
                .setSlaveId(CommonIdUtils.emptyAgentId());

        // create default labels:
        taskInfoBuilder.setLabels(new SchedulerLabelWriter(taskInfoBuilder)
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

        return taskInfoBuilder.build();
    }

    private static Protos.ExecutorInfo getExecutorInfo(
            PodSpec podSpec,
            String serviceName,
            Protos.FrameworkID frameworkID,
            UUID targetConfigurationId,
            SchedulerFlags schedulerFlags) throws IllegalStateException {
        Protos.ExecutorInfo.Builder executorInfoBuilder = Protos.ExecutorInfo.newBuilder()
                .setType(Protos.ExecutorInfo.Type.DEFAULT)
                .setName(podSpec.getType())
                .setExecutorId(Protos.ExecutorID.newBuilder().setValue("").build())
                .setFrameworkId(frameworkID); // Set later by ExecutorRequirement
        // Populate ContainerInfo with the appropriate information from PodSpec
        // FIX: propagate images downward
        Protos.ContainerInfo containerInfo = getContainerInfo(podSpec);
        if (containerInfo != null) {
            executorInfoBuilder.setContainer(containerInfo);
        }

        // FIX: config templates ughhhhhhhh

        return executorInfoBuilder.build();
    }

    private static Protos.Environment getTaskEnvironment(
            String serviceName, PodInstance podInstance, TaskSpec taskSpec, CommandSpec commandSpec) {
        Map<String, String> environment = new HashMap<>();

        // Task envvars from either of the following sources:
        // - ServiceSpec (provided by developer)
        // - TASKCFG_<podname>_* (provided by user, handled when parsing YAML, potentially overrides ServiceSpec)
        environment.putAll(commandSpec.getEnvironment());

        // Default envvars for use by executors/developers:

        // Inject Pod Instance Index
        environment.put(EnvConstants.POD_INSTANCE_INDEX_TASKENV, String.valueOf(podInstance.getIndex()));
        // Inject Framework Name
        environment.put(EnvConstants.FRAMEWORK_NAME_TASKENV, serviceName);
        // Inject TASK_NAME as KEY:VALUE
        environment.put(EnvConstants.TASK_NAME_TASKENV, TaskSpec.getInstanceName(podInstance, taskSpec));
        // Inject TASK_NAME as KEY for conditional mustache templating
        environment.put(TaskSpec.getInstanceName(podInstance, taskSpec), "true");

        return EnvUtils.toProto(environment);
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
            builder.setVisibility(Protos.DiscoveryInfo.Visibility.CLUSTER);
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
                .setType(Protos.HealthCheck.Type.COMMAND)
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
        Protos.CheckInfo.Builder builder = taskInfoBuilder.getCheckBuilder()
                .setType(Protos.CheckInfo.Type.COMMAND)
                .setDelaySeconds(readinessCheckSpec.getDelay())
                .setIntervalSeconds(readinessCheckSpec.getInterval())
                .setTimeoutSeconds(readinessCheckSpec.getTimeout());

        Protos.CommandInfo.Builder readinessCheckCommandBuilder = builder.getCommandBuilder()
                .getCommandBuilder()
                .setValue(readinessCheckSpec.getCommand());
        if (taskSpec.getCommand().isPresent()) {
            readinessCheckCommandBuilder.setEnvironment(
                    getTaskEnvironment(serviceName, podInstance, taskSpec, commandSpec));
        }
    }

    private static String getConfigTemplateDownloadPath(ConfigFileSpec config) {
        // Name is unique.
        return String.format("%s%s", CONFIG_TEMPLATE_DOWNLOAD_PATH, config.getName());
    }

    private static Protos.ContainerInfo getContainerInfo(PodSpec podSpec) {
        if (!podSpec.getImage().isPresent() && podSpec.getNetworks().isEmpty() && podSpec.getRLimits().isEmpty()) {
            return null;
        }

        Protos.ContainerInfo.Builder containerInfo = Protos.ContainerInfo.newBuilder()
                .setType(Protos.ContainerInfo.Type.MESOS);

        if (podSpec.getImage().isPresent()) {
            containerInfo.getMesosBuilder()
                    .setImage(Protos.Image.newBuilder()
                            .setType(Protos.Image.Type.DOCKER)
                            .setDocker(Protos.Image.Docker.newBuilder()
                                    .setName(podSpec.getImage().get())));
        }

        if (!podSpec.getNetworks().isEmpty()) {
            containerInfo.addAllNetworkInfos(
                    podSpec.getNetworks().stream().map(n -> getNetworkInfo(n)).collect(Collectors.toList()));
        }

        if (!podSpec.getRLimits().isEmpty()) {
            containerInfo.setRlimitInfo(getRLimitInfo(podSpec.getRLimits()));
        }

        return containerInfo.build();
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
            throw new IllegalStateException("Network-labels is not implemented, yet");
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

        SchedulerLabelReader labels = new SchedulerLabelReader(builder);

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

    @Override
    public String toString() {
        return ToStringBuilder.reflectionToString(this);
    }

}
