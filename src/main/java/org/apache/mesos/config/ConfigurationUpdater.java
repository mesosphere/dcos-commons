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
    private final Collection<ConfigurationValidator<C>> rules;

    public ConfigurationUpdater(
            StateStore stateStore,
            ConfigStore<C> configStore,
            Collection<ConfigurationValidator<C>> rules) {
        this.stateStore = stateStore;
        this.configStore = configStore;
        this.rules = rules;
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
                LOGGER.error("Unable to get JSON representation of old target config object", e);
                // Don't add a validation error: That'd prevent the new config from replacing this
                // one, and we'd be stuck with this config forever! Hopefully the new config will fix things...
            }
        } else {
            LOGGER.info("Old target config: <null>");
        }
        try {
            LOGGER.info("New prospective config: {}", candidateConfig.toJsonString());
        } catch (Exception e) {
            LOGGER.error("Unable to get JSON representation of new prospective config object: " + candidateConfig, e);
            errors.add(ConfigurationValidationError.valueError("NewConfigAsJson", "jsonString",
                    String.format("Unable to serialize new config to JSON for logging: %s", e.getMessage())));
        }

        // Check for any validation errors (including against the prior config, if one is available)
        // NOTE: We ALWAYS run validation regardless of config equality below. This allows the
        // developer to e.g. require that a value be increased, whereas an up-front equality check
        // would short-circuit that validation.
        for (ConfigurationValidator<C> rule : rules) {
            errors.addAll(rule.validate(targetConfig, candidateConfig));
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
                    "configuration {}, with {} errors across {} rules:\n{}",
                    targetConfigId, errors.size(), rules.size(), sj.toString());
            if (targetConfig == null) {
                throw new ConfigStoreException(String.format(
                        "Configuration failed validation without any prior target configuration" +
                        "available for fallback. Initial launch with invalid configuration? " +
                        "%d Errors: %s", errors.size(), sj.toString()));
            }
        } else if (targetConfig != null && targetConfig.equals(candidateConfig)) {
            LOGGER.info("No changes detected between current target configuration '{}' and new " +
                    "configuration. Leaving current configuration as the target.",
                    targetConfigId);
        } else {
            LOGGER.info("Changes detected between current target configuration '{}' and new " +
                    "configuration. Setting target to new configuration.",
                    targetConfigId);
            targetConfigId = configStore.store(candidateConfig);
            targetConfig = candidateConfig;
            configStore.setTargetConfig(targetConfigId);
        }

        // Update config IDs on tasks whose config contents match the current target
        updateTaskConfigIDsWhichMatchTarget(targetConfig, targetConfigId);
        // Clean up configs which are no longer the target nor referenced by active tasks
        clearUnusedConfigs(targetConfigId);

        return new UpdateResult(targetConfigId, errors);
    }

    /**
     * Searches for any task configurations which are already identical to the target configuration
     * and updates the accounting for those tasks to point to the current target configuration.
     */
    private void updateTaskConfigIDsWhichMatchTarget(C targetConfig, UUID targetConfigId) throws ConfigStoreException {
        List<TaskInfo> taskInfosToUpdate = new ArrayList<>();
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
                    LOGGER.info("Task {} config {} is identical to target {}. Updating task configuration to {}.",
                            taskInfo.getName(), taskConfigId, targetConfigId, targetConfigId);
                    taskInfosToUpdate.add(
                            TaskUtils.setTargetConfiguration(taskInfo.toBuilder(), targetConfigId).build());
                } else {
                    LOGGER.info("Task {} config {} differs from target {}. Leaving task as-is.",
                            taskInfo.getName(), taskConfigId, targetConfigId);
                }
            }
        }
        if (!taskInfosToUpdate.isEmpty()) {
            LOGGER.info("Updating {} tasks in StateStore with configuration ID {}",
                    taskInfosToUpdate.size(), targetConfigId);
            stateStore.storeTasks(taskInfosToUpdate);
        }
    }

    /**
     * Searches for any config IDs which are no longer active and removes them from the config
     * store.
     *
     * @throws ConfigStoreException if config access fails
     */
    private void clearUnusedConfigs(UUID targetConfigId) throws ConfigStoreException {
        final Set<UUID> neededConfigs = new HashSet<>();
        neededConfigs.add(targetConfigId);
        for (TaskInfo taskInfo : stateStore.fetchTasks()) {
            final UUID neededConfig;
            try {
                neededConfig = TaskUtils.getTargetConfiguration(taskInfo);
            } catch (TaskException e) {
                LOGGER.warn("Unable to extract config ID from TaskInfo during cleanup checks, skipping: {}",
                        TextFormat.shortDebugString(taskInfo));
                continue;
            }
            neededConfigs.add(neededConfig);
        }

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
