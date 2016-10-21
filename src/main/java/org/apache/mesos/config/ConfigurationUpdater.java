package org.apache.mesos.config;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.StringJoiner;
import java.util.UUID;

import org.apache.mesos.config.validate.ConfigurationValidationError;
import org.apache.mesos.config.validate.ConfigurationValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Handles the validation and update of a new configuration against a prior configuration, if any.
 *
 * @param <C> the type of configuration being used by the service
 */
public class ConfigurationUpdater<C extends Configuration> {
    private static final Logger LOGGER = LoggerFactory.getLogger(ConfigurationUpdater.class);

    private final ConfigStore<C> configStore;
    private final ConfigurationFactory<C> configFactory;
    private final Collection<ConfigurationValidator<C>> rules;

    public ConfigurationUpdater(
            ConfigStore<C> configStore,
            ConfigurationFactory<C> configFactory,
            C newConfig,
            Collection<ConfigurationValidator<C>> rules) {
        this.configStore = configStore;
        this.configFactory = configFactory;
        this.rules = rules;
    }

    /**
     * Validates the provided {@code newConfig}, and updates the target configuration if the
     * validation passes.
     *
     * @param newConfig the new candidate configuration to be validated and potentially marked as
     *                  the target configuration
     * @return a list of errors if the new candidate configuration failed validation, or an empty
     *         list if the configuration passed validation and was updated to the config store
     * @throws ConfigStoreException if there's an error when reading or writing to the config store
     */
    public Collection<ConfigurationValidationError> update(C newConfig) throws ConfigStoreException {
        UUID targetConfigId;
        try {
            targetConfigId = configStore.getTargetConfig();
        } catch (ConfigStoreException e) {
            LOGGER.debug("No target configuration ID was set. First launch?");
            targetConfigId = null;
        }

        final C oldConfig;
        if (targetConfigId != null) {
            oldConfig = configStore.fetch(targetConfigId, configFactory);
        } else {
            oldConfig = null;
        }

        final List<ConfigurationValidationError> errors = new ArrayList<>();
        try {
            LOGGER.info("Old target config: {}", oldConfig.toJsonString());
        } catch (Exception e) {
            LOGGER.error("Unable to get JSON representation of old target config object", e);
            // Don't add a validation error: We're stuck anyway. Hopefully the new config will fix this...
        }
        try {
            LOGGER.info("New prospective config: {}", newConfig.toJsonString());
        } catch (Exception e) {
            LOGGER.error("Unable to get JSON representation of new prospective config object", e);
            errors.add(ConfigurationValidationError.valueError("NewConfigAsJson", "jsonString",
                    String.format("Unable to serialize new config to JSON for logging: %s", e.getMessage())));
        }

        if (oldConfig != null && oldConfig.equals(newConfig)) {
            LOGGER.info("No changes detected against current target configuration {}. " +
                    "Leaving configuration unchanged.", targetConfigId);
            return errors;
        }

        for (ConfigurationValidator<C> rule : rules) {
            errors.addAll(rule.validate(oldConfig, newConfig));
        }

        if (errors.isEmpty()) {
            UUID newConfigId = configStore.store(newConfig);
            LOGGER.info("New configuration {} passed validation against current target configuration {}. " +
                    "Updating tasks to configuration {}.", newConfigId, targetConfigId, newConfigId);
            configStore.setTargetConfig(newConfigId);
            //TODO update tasks with new target...
            //TODO clean up configs that arent referenced anymore...
        } else {
            StringJoiner sj = new StringJoiner("\n");
            int i = 1;
            for (ConfigurationValidationError error : errors) {
                sj.add(String.format("%d: %s", i++, error.toString()));
            }
            LOGGER.warn("New configuration failed validation against current target configuration {} " +
                    "with {} errors across {} rules:\n{}", errors.size(), rules.size(), sj.toString());
        }

        return errors;
    }
}
