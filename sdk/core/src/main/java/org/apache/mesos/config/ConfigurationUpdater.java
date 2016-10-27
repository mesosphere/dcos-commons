package org.apache.mesos.config;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.StringJoiner;
import java.util.UUID;

import org.apache.mesos.Protos.TaskInfo;
import org.apache.mesos.config.validate.ConfigurationValidationError;
import org.apache.mesos.config.validate.ConfigurationValidator;
import org.apache.mesos.offer.TaskException;
import org.apache.mesos.offer.TaskUtils;
import org.apache.mesos.state.StateStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.protobuf.TextFormat;

/**
 * Handles the validation and update of a new configuration against a prior configuration, if any.
 *
 * @param <C> the type of configuration being used by the service
 */
public class ConfigurationUpdater<C extends Configuration> {

    /**
     * The result of an {@link ConfigurationUpdater#updateConfiguration(Configuration)} call.
     */
    public static class UpdateResult {
        /**
         * The resulting configuration ID which should be used by service tasks.
         */
        public final UUID targetId;

        /**
         * A list of zero or more validation errors with the current configuration. If there were
         * errors, the {@link #targetId} will point to a previous valid configuration.
         */
        public final Collection<ConfigurationValidationError> errors;

        private UpdateResult(UUID targetId, Collection<ConfigurationValidationError> errors) {
            this.targetId = targetId;
            this.errors = errors;
        }
    }

    private static final Logger LOGGER = LoggerFactory.getLogger(ConfigurationUpdater.class);

    private final StateStore stateStore;
    private final ConfigStore<C> configStore;
    private final ConfigurationComparator<C> configComparator;
    private final Collection<ConfigurationValidator<C>> validators;

    public ConfigurationUpdater(
            StateStore stateStore,
            ConfigStore<C> configStore,
            ConfigurationComparator<C> configComparator,
            Collection<ConfigurationValidator<C>> validators) {
        this.stateStore = stateStore;
        this.configStore = configStore;
        this.configComparator = configComparator;
        this.validators = validators;
    }

    /**
     * Validates the provided {@code candidateConfig}, and updates the target configuration if the
     * validation passes.
     *
     * @param candidateConfig the new candidate configuration to be validated and potentially marked
     *                        as the target configuration
     * @return a list of errors if the new candidate configuration failed validation, or an empty
     *         list if the configuration passed validation and was updated to the config store
     * @throws ConfigStoreException if there's an error when reading or writing to the config store,
     *                              or if there are validation errors against the config and no
     *                              prior fallback config is available
     */
    public UpdateResult updateConfiguration(C candidateConfig) throws ConfigStoreException {
        // Get the currently stored target configuration
        UUID targetConfigId;
        try {
            targetConfigId = configStore.getTargetConfig();
        } catch (ConfigStoreException e) {
            LOGGER.debug("No target configuration ID was set. First launch?");
            targetConfigId = null;
        }
        C targetConfig;
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
        for (ConfigurationValidator<C> validator : validators) {
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

        return new UpdateResult(targetConfigId, errors);
    }

    /**
     * Searches for any task configurations which are already identical to the target configuration
     * and updates the embedded config version label in those tasks to point to the current target
     * configuration.
     */
    private void cleanupDuplicateAndUnusedConfigs(C targetConfig, UUID targetConfigId)
            throws ConfigStoreException {
        List<TaskInfo> taskInfosToUpdate = new ArrayList<>();
        Set<UUID> neededConfigs = new HashSet<>();
        neededConfigs.add(targetConfigId);
        // Search task labels for configs which need to be cleaned up.
        for (TaskInfo taskInfo : stateStore.fetchTasks()) {
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
                final C taskConfig = configStore.fetch(taskConfigId);
                if (targetConfig.equals(taskConfig)) {
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
