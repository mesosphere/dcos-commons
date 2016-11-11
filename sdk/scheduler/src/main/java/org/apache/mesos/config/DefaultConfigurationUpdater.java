package org.apache.mesos.config;

import com.google.protobuf.TextFormat;
import org.apache.mesos.Protos;
import org.apache.mesos.config.validate.ConfigurationValidationError;
import org.apache.mesos.config.validate.ConfigurationValidator;
import org.apache.mesos.offer.TaskException;
import org.apache.mesos.offer.TaskUtils;
import org.apache.mesos.specification.PodSpec;
import org.apache.mesos.specification.ServiceSpec;
import org.apache.mesos.state.StateStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * Handles the validation and update of a new configuration against a prior configuration, if any.
 * In the case of the DefaultConfigurationUpdater, configurations are ServiceSpecifications.
 */
public class DefaultConfigurationUpdater implements ConfigurationUpdater<ServiceSpec> {

    private static final Logger LOGGER = LoggerFactory.getLogger(ConfigurationUpdater.class);

    private final StateStore stateStore;
    private final ConfigStore<ServiceSpec> configStore;
    private final ConfigurationComparator<ServiceSpec> configComparator;
    private final Collection<ConfigurationValidator<ServiceSpec>> validators;

    public DefaultConfigurationUpdater(
            StateStore stateStore,
            ConfigStore<ServiceSpec> configStore,
            ConfigurationComparator<ServiceSpec> configComparator,
            Collection<ConfigurationValidator<ServiceSpec>> validators) {
        this.stateStore = stateStore;
        this.configStore = configStore;
        this.configComparator = configComparator;
        this.validators = validators;
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

        ServiceSpec targetConfig;
        if (targetConfigId != null) {
            targetConfig = configStore.fetch(targetConfigId);
        } else {
            targetConfig = null;
        }

        // Log the config state before proceeding with checks.
        final List<ConfigurationValidationError> errors = new ArrayList<>();
        if (targetConfig != null) {
            try {
                LOGGER.info("Old target config: {}", targetConfig.toJsonString());
            } catch (Exception e) {
                LOGGER.error(String.format(
                        "Unable to get JSON representation of old target config object %s: %s",
                        targetConfigId, targetConfig), e);
                // Don't add a validation error: That'd prevent the new config from replacing this
                // one, and we'd be stuck with this config forever! Hopefully the new config will fix things...
            }
        } else {
            LOGGER.info("Old target config: <null>");
        }

        try {
            LOGGER.info("New prospective config: {}", candidateConfig.toJsonString());
        } catch (Exception e) {
            LOGGER.error(String.format(
                    "Unable to get JSON representation of new prospective config object: %s",
                    candidateConfig), e);
            errors.add(ConfigurationValidationError.valueError("NewConfigAsJson", "jsonString",
                    String.format("Unable to serialize new config to JSON for logging: %s", e.getMessage())));
        }

        // Check for any validation errors (including against the prior config, if one is available)
        // NOTE: We ALWAYS run validation regardless of config equality. This allows the configured
        // validators to always have a say in whether a given configuration is valid, regardless of
        // whether it's considered equal by the ConfigComparator.
        for (ConfigurationValidator<ServiceSpec> validator : validators) {
            errors.addAll(validator.validate(targetConfig, candidateConfig));
        }

        // Select the appropriate configuration ID as the target. If the config hasn't changed or if
        // there are validation errors against the new config, we continue using the prior target.
        if (!errors.isEmpty()) {
            StringJoiner sj = new StringJoiner("\n");
            int i = 1;
            for (ConfigurationValidationError error : errors) {
                sj.add(String.format("%d: %s", i++, error.toString()));
            }
            LOGGER.warn("New configuration failed validation against current target " +
                            "configuration {}, with {} errors across {} validators:\n{}",
                    targetConfigId, errors.size(), validators.size(), sj.toString());
            if (targetConfig == null) {
                throw new ConfigStoreException(String.format(
                        "Configuration failed validation without any prior target configuration" +
                                "available for fallback. Initial launch with invalid configuration? " +
                                "%d Errors: %s", errors.size(), sj.toString()));
            }
        } else if (targetConfig == null || !configComparator.equals(targetConfig, candidateConfig)) {
            LOGGER.info("Changes detected between current target configuration '{}' and new " +
                            "configuration. Setting target to new configuration.",
                    targetConfigId);
            targetConfigId = configStore.store(candidateConfig);
            targetConfig = candidateConfig;
            configStore.setTargetConfig(targetConfigId);
        } else {
            LOGGER.info("No changes detected between current target configuration '{}' and new " +
                            "configuration. Leaving current configuration as the target.",
                    targetConfigId);
        }

        // Update config IDs on tasks whose config contents match the current target, then clean up
        // leftover configs which are not the target and which are not referenced by any tasks.
        cleanupDuplicateAndUnusedConfigs(targetConfig, targetConfigId);

        return new ConfigurationUpdater.UpdateResult(targetConfigId, errors);
    }

    /**
     * Searches for any task configurations which are already identical to the target configuration
     * and updates the embedded config version label in those tasks to point to the current target
     * configuration.
     */
    private void cleanupDuplicateAndUnusedConfigs(ServiceSpec targetConfig, UUID targetConfigId)
            throws ConfigStoreException {
        List<Protos.TaskInfo> taskInfosToUpdate = new ArrayList<>();
        Set<UUID> neededConfigs = new HashSet<>();
        neededConfigs.add(targetConfigId);
        // Search task labels for configs which need to be cleaned up.
        for (Protos.TaskInfo taskInfo : stateStore.fetchTasks()) {
            final UUID taskConfigId;
            try {
                taskConfigId = TaskUtils.getTargetConfiguration(taskInfo);
            } catch (TaskException e) {
                LOGGER.warn(String.format("Unable to extract configuration ID from task %s: %s",
                        taskInfo.getName(), TextFormat.shortDebugString(taskInfo)), e);
                continue;
            }

            if (taskConfigId.equals(targetConfigId)) {
                LOGGER.info("Task {} configuration ID matches target: {}",
                        taskInfo.getName(), taskConfigId);
            } else {
                final ServiceSpec taskConfig = configStore.fetch(taskConfigId);
                if (!needsConfigUpdate(taskInfo, targetConfig, taskConfig)) {
                    // Task is effectively already on the target config. Update task's config ID to match target,
                    // and allow the duplicate config to be dropped from configStore.
                    LOGGER.info("Task {} config {} is identical to target {}. Updating task configuration to {}.",
                            taskInfo.getName(), taskConfigId, targetConfigId, targetConfigId);
                    taskInfosToUpdate.add(
                            TaskUtils.setTargetConfiguration(taskInfo.toBuilder(), targetConfigId).build());
                } else {
                    // Config isn't the same as the target. Refrain from updating task, mark config as 'needed'.
                    LOGGER.info("Task {} config {} differs from target {}. Leaving task as-is.",
                            taskInfo.getName(), taskConfigId, targetConfigId);
                    neededConfigs.add(taskConfigId);
                }
            }
        }

        if (!taskInfosToUpdate.isEmpty()) {
            LOGGER.info("Updating {} tasks in StateStore with target configuration ID {}",
                    taskInfosToUpdate.size(), targetConfigId);
            stateStore.storeTasks(taskInfosToUpdate);
        }

        clearConfigsNotListed(neededConfigs);
    }

    private boolean needsConfigUpdate(
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
            PodSpec targetSpec = targetSpecOptional.get();
            PodSpec taskSpec = taskSpecOptional.get();
            boolean updateNeeded = !targetSpec.equals(taskSpec);
            LOGGER.info("Compared target: {} to current: {}, update needed: {}", targetSpec, taskSpec, updateNeeded);
            return updateNeeded;
        } else {
            LOGGER.info("Configuration update is needed for: {}", taskInfo.getName());
            return true;
        }
    }

    private Optional<PodSpec> getPodSpec(
            Protos.TaskInfo taskInfo,
            ServiceSpec serviceSpecification) {

        try {
            final String taskType = TaskUtils.getType(taskInfo);

            return serviceSpecification.getPods().stream()
                    .filter(pod -> pod.getType().equals(taskType))
                    .findFirst();
        } catch (TaskException e) {
            LOGGER.error("Failed to find existing TaskSpecification.", e);
            return Optional.empty();
        }
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
