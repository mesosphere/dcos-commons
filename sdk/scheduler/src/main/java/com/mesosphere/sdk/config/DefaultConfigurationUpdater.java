package com.mesosphere.sdk.config;

import com.google.common.base.Joiner;
import com.google.common.base.Splitter;
import com.google.common.collect.Lists;
import com.google.protobuf.TextFormat;
import com.mesosphere.sdk.config.validate.ConfigValidationError;
import com.mesosphere.sdk.config.validate.ConfigValidator;
import com.mesosphere.sdk.offer.TaskException;
import com.mesosphere.sdk.offer.taskdata.SchedulerLabelReader;
import com.mesosphere.sdk.offer.taskdata.SchedulerLabelWriter;
import com.mesosphere.sdk.specification.DefaultPodSpec;
import com.mesosphere.sdk.specification.PodSpec;
import com.mesosphere.sdk.specification.ServiceSpec;
import com.mesosphere.sdk.state.StateStore;
import com.mesosphere.sdk.state.StateStoreUtils;
import com.mesosphere.sdk.storage.StorageError.Reason;
import difflib.DiffUtils;
import org.apache.mesos.Protos;
import org.apache.mesos.Protos.TaskInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * Handles the validation and update of a new configuration against a prior configuration, if any.
 * In the case of the DefaultConfigurationUpdater, configurations are ServiceSpecifications.
 */
public class DefaultConfigurationUpdater implements ConfigurationUpdater<ServiceSpec> {

    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultConfigurationUpdater.class);

    private final StateStore stateStore;
    private final ConfigStore<ServiceSpec> configStore;
    private final ConfigurationComparator<ServiceSpec> configComparator;
    private final Collection<ConfigValidator<ServiceSpec>> validators;
    private final UpdateResult.DeploymentType lastUpdateType;

    public DefaultConfigurationUpdater(
            StateStore stateStore,
            ConfigStore<ServiceSpec> configStore,
            ConfigurationComparator<ServiceSpec> configComparator,
            Collection<ConfigValidator<ServiceSpec>> validators) {
        this.stateStore = stateStore;
        this.configStore = configStore;
        this.configComparator = configComparator;
        this.validators = validators;
        this.lastUpdateType = StateStoreUtils.getLastCompletedUpdateType(stateStore);
    }

    @Override
    public UpdateResult updateConfiguration(ServiceSpec candidateConfig) throws ConfigStoreException {
        // Get the currently stored target configuration
        UUID targetConfigId;
        try {
            targetConfigId = configStore.getTargetConfig();
        } catch (ConfigStoreException e) {
            LOGGER.debug("No target configuration ID was set. First launch?");
            targetConfigId = null;
        }

        Optional<ServiceSpec> targetConfig;
        if (targetConfigId != null) {
            targetConfig = Optional.of(configStore.fetch(targetConfigId));
        } else {
            targetConfig = Optional.empty();
        }

        // Log the config state (with diff of changes vs prior state) before proceeding with checks.

        final List<ConfigValidationError> errors = new ArrayList<>();
        String candidateConfigJson = null;
        try {
            candidateConfigJson = candidateConfig.toJsonString();
            LOGGER.info("New prospective config:\n{}", candidateConfigJson);
        } catch (Exception e) {
            LOGGER.error(String.format(
                    "Unable to get JSON representation of new prospective config object: %s",
                    candidateConfig), e);
            errors.add(ConfigValidationError.valueError("NewConfigAsJson", "jsonString",
                    String.format("Unable to serialize new config to JSON for logging: %s", e.getMessage())));
        }

        if (!targetConfig.isPresent()) {
            LOGGER.info("Skipping config diff: There is no old config target to diff against");
        } else if (candidateConfigJson == null) {
            LOGGER.info("Skipping config diff: New target couldn't be represented as JSON");
        } else {
            LOGGER.info("Prior target config:\n{}", targetConfig.get().toJsonString());
            printConfigDiff(targetConfig.get(), targetConfigId, candidateConfigJson);
        }

        // Check for any validation errors (including against the prior config, if one is available)
        // NOTE: We ALWAYS run validation regardless of config equality. This allows the configured
        // validators to always have a say in whether a given configuration is valid, regardless of
        // whether it's considered equal by the ConfigComparator.
        for (ConfigValidator<ServiceSpec> validator : validators) {
            errors.addAll(validator.validate(targetConfig, candidateConfig));
        }

        // Select the appropriate configuration ID as the target. If the config hasn't changed or if
        // there are validation errors against the new config, we continue using the prior target.
        if (!errors.isEmpty()) {
            StringJoiner sj = new StringJoiner("\n");
            int i = 1;
            for (ConfigValidationError error : errors) {
                sj.add(String.format("%d: %s", i++, error.toString()));
            }
            LOGGER.warn("New configuration failed validation against current target " +
                            "configuration {}, with {} errors across {} validators:\n{}",
                    targetConfigId, errors.size(), validators.size(), sj.toString());
            if (!targetConfig.isPresent()) {
                throw new ConfigStoreException(Reason.LOGIC_ERROR, String.format(
                        "Configuration failed validation without any prior target configuration" +
                                "available for fallback. Initial launch with invalid configuration? " +
                                "%d Errors: %s", errors.size(), sj.toString()));
            }
        } else if (!targetConfig.isPresent() || !configComparator.equals(targetConfig.get(), candidateConfig)) {
            LOGGER.info("Changes detected between current target configuration '{}' and new " +
                            "configuration. Setting target to new configuration.",
                    targetConfigId);

            targetConfigId = configStore.store(candidateConfig);
            targetConfig = Optional.of(candidateConfig);
            configStore.setTargetConfig(targetConfigId);
        } else {
            LOGGER.info("No changes detected between current target configuration '{}' and new " +
                            "configuration. Leaving current configuration as the target.",
                    targetConfigId);
        }

        // Update config IDs on tasks whose config contents match the current target, then clean up
        // leftover configs which are not the target and which are not referenced by any tasks.
        cleanupDuplicateAndUnusedConfigs(targetConfig.get(), targetConfigId);

        UpdateResult.DeploymentType updateType =
                lastUpdateType.equals(UpdateResult.DeploymentType.NONE) ?
                        UpdateResult.DeploymentType.DEPLOY :
                        UpdateResult.DeploymentType.UPDATE;

        return new ConfigurationUpdater.UpdateResult(targetConfigId, updateType, errors);
    }

    /**
     * Searches for any task configurations which are already identical to the target configuration
     * and updates the embedded config version label in those tasks to point to the current target
     * configuration.
     */
    private void cleanupDuplicateAndUnusedConfigs(
            ServiceSpec targetConfig,
            UUID targetConfigId)
            throws ConfigStoreException {
        List<Protos.TaskInfo> taskInfosToUpdate = new ArrayList<>();
        Set<UUID> neededConfigs = new HashSet<>();
        neededConfigs.add(targetConfigId);
        // Search task labels for configs which need to be cleaned up.
        for (Protos.TaskInfo taskInfo : stateStore.fetchTasks()) {
            final UUID taskConfigId;
            try {
                taskConfigId = new SchedulerLabelReader(taskInfo).getTargetConfiguration();
            } catch (TaskException e) {
                LOGGER.warn(String.format("Unable to extract configuration ID from task %s: %s",
                        taskInfo.getName(), TextFormat.shortDebugString(taskInfo)), e);
                continue;
            }

            if (taskConfigId.equals(targetConfigId)) {
                LOGGER.info("Task {} configuration ID matches target: {}",
                        taskInfo.getName(), taskConfigId);
            } else {
                try {
                    final ServiceSpec taskConfig = configStore.fetch(taskConfigId);
                    if (!needsConfigUpdate(taskInfo, targetConfig, taskConfig)) {
                        // Task is effectively already on the target config. Update task's config ID to match target,
                        // and allow the duplicate config to be dropped from configStore.
                        LOGGER.info("Task {} config {} is identical to target {}. Updating task configuration to {}.",
                                taskInfo.getName(), taskConfigId, targetConfigId, targetConfigId);
                        TaskInfo.Builder taskBuilder = taskInfo.toBuilder();
                        taskBuilder.setLabels(new SchedulerLabelWriter(taskInfo)
                                .setTargetConfiguration(targetConfigId)
                                .toProto());
                        taskInfosToUpdate.add(taskBuilder.build());
                    } else {
                        // Config isn't the same as the target. Refrain from updating task, mark config as 'needed'.
                        LOGGER.info("Task {} config {} differs from target {}. Leaving task as-is.",
                                taskInfo.getName(), taskConfigId, targetConfigId);
                        neededConfigs.add(taskConfigId);
                    }
                } catch (Exception e) {
                    LOGGER.error("Can not fetch configuration taskConfigId {} for task {}",
                            taskConfigId, taskInfo.getName());
                    LOGGER.info("TaskInfo has incompatible configuration, skipping task {} : {}",
                            taskInfo.getName(), e.getMessage());
                    //Can not read this task's config. Do not delete it.
                    neededConfigs.add(taskConfigId);
                }
            }
        }

        if (!taskInfosToUpdate.isEmpty()) {
            LOGGER.info("Updating {} tasks in StateStore with target configuration ID {}",
                    taskInfosToUpdate.size(), targetConfigId);
            stateStore.storeTasks(taskInfosToUpdate);
        }

        for (UUID configId : configStore.list()) {
            try {
                ServiceSpec serviceSpec = configStore.fetch(configId);
                LOGGER.info("Config {} : {} ", configId, serviceSpec.getName());
            } catch (Exception e) {
                LOGGER.info("Config {} has incompatible format, leaving it as-is : {}", configId, e.getMessage());
                neededConfigs.add(configId);
            }
        }

        clearConfigsNotListed(neededConfigs);
    }

    private static void printConfigDiff(ServiceSpec oldConfig, UUID oldConfigId, String newConfigJson) {
        // Print a diff of this new config vs the prior config:
        try {
            final List<String> oldLines =
                    Lists.newArrayList(Splitter.on('\n').split(oldConfig.toJsonString()));
            final List<String> newLines = Lists.newArrayList(Splitter.on('\n').split(newConfigJson));
            List<String> diffResult = DiffUtils.generateUnifiedDiff(
                    "ServiceSpec.old", "ServiceSpec.new", oldLines, DiffUtils.diff(oldLines, newLines), 2);
            LOGGER.info("Difference between configs:\n{}", Joiner.on('\n').join(diffResult));
            // Don't log the old target, as that would be redundant.
            // Instead just log the new target then the diff vs the old target (below)
        } catch (Exception e) {
            LOGGER.error(String.format(
                    "Unable to get JSON representation of old target config object %s, " +
                    "skipping diff vs new target: %s",
                    oldConfigId, oldConfig), e);
            // Don't add a validation error: That'd prevent the new config from replacing this one,
            // and we'd be stuck with this config forever! Hopefully the new config will fix things...
        }
    }

    private static boolean needsConfigUpdate(
            Protos.TaskInfo taskInfo,
            ServiceSpec targetConfig,
            ServiceSpec taskConfig) {
        LOGGER.info("Checking whether config update is needed for task: {}", taskInfo.getName());

        if (targetConfig.equals(taskConfig)) {
            LOGGER.info("Configurations are equal, no update needed for task: {}", taskInfo.getName());
            return false;
        }

        Optional<PodSpec> targetSpecOptional = getPodSpec(taskInfo, targetConfig);
        Optional<PodSpec> taskSpecOptional = getPodSpec(taskInfo, taskConfig);

        if (targetSpecOptional.isPresent() && taskSpecOptional.isPresent()) {
            boolean updateNeeded = !areMatching(targetSpecOptional.get(), taskSpecOptional.get());
            LOGGER.info("Update needed: {} for: {}", updateNeeded, taskInfo.getName());
            return updateNeeded;
        } else {
            LOGGER.info("Configuration update is needed for: {}", taskInfo.getName());
            return true;
        }
    }

    private static Optional<PodSpec> getPodSpec(Protos.TaskInfo taskInfo, ServiceSpec serviceSpecification) {

        try {
            final String taskType = new SchedulerLabelReader(taskInfo).getType();

            return serviceSpecification.getPods().stream()
                    .filter(pod -> pod.getType().equals(taskType))
                    .findFirst();
        } catch (TaskException e) {
            LOGGER.error("Failed to find existing TaskSpecification.", e);
            return Optional.empty();
        }
    }

    private static boolean areMatching(PodSpec podSpec1, PodSpec podSpec2) {
        if (podSpec1.equals(podSpec2)) {
            // Shortcut: Below modification was not needed to check for equality
            return true;
        }

        // When evaluating whether a pod should be updated, some PodSpec changes are immaterial:
        //   1. Count: Extant pods do not care if they will have more fellows
        //   2. Placement Rules: Extant pods should not (immediately) move around due to placement changes
        // As such, ignore these values when checking for changes:
        podSpec1 = DefaultPodSpec.newBuilder(podSpec1).count(0).placementRule(null).build();
        podSpec2 = DefaultPodSpec.newBuilder(podSpec2).count(0).placementRule(null).build();
        return podSpec1.equals(podSpec2);
    }

    /**
     * Searches for any config IDs which are no longer active and removes them from the config
     * store.
     *
     * @throws ConfigStoreException if config access fails
     */
    private void clearConfigsNotListed(Set<UUID> neededConfigs) throws ConfigStoreException {
        final Set<UUID> configsToClear = new HashSet<>();
        for (UUID configId : configStore.list()) {
            if (!neededConfigs.contains(configId)) {
                configsToClear.add(configId);
            }
        }
        LOGGER.info("Cleaning up {} unused configs: {}", configsToClear.size(), configsToClear);
        for (UUID configToClear : configsToClear) {
            configStore.clear(configToClear);
        }
    }
}
