package com.mesosphere.sdk.config;

import com.mesosphere.sdk.config.validate.ConfigValidationError;

import java.util.Collection;
import java.util.UUID;

/**
 * Handles the validation and update of a new configuration against a prior configuration, if any.
 *
 * @param <C> the type of configuration being used by the service
 */
public interface ConfigurationUpdater<C extends Configuration> {

    /**
     * The result of an {@link ConfigurationUpdater#updateConfiguration(Configuration)} call.
     */
    class UpdateResult {
        private final UUID targetId;
        private final DeploymentType deploymentType;
        private final Collection<ConfigValidationError> errors;

        public UpdateResult(
                UUID targetId,
                DeploymentType deploymentType,
                Collection<ConfigValidationError> errors) {
            this.targetId = targetId;
            this.errors = errors;
            this.deploymentType = deploymentType;
        }

        /**
         * Two types of deployments are differentiated by this type.  Either a services is
         * being deployed for the first time "DEPLOY", or it is updating a previously deployed
         * version of the service, "UPDATE"
         */
        public enum DeploymentType {
            NONE,
            DEPLOY,
            UPDATE
        }

        /**
         * Gets the {@link DeploymentType} detected during this update.
         */
        public DeploymentType getDeploymentType() {
            return deploymentType;
        }

        /**
         * Gets a list of zero or more validation errors with the current configuration. If there were
         * errors, the {@link #targetId} will point to a previous valid configuration.
         */
        public Collection<ConfigValidationError> getErrors() {
            return errors;
        }

        /**
         * Gets the resulting configuration ID which should be used by service tasks.
         */
        public UUID getTargetId() {
            return targetId;
        }
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
    UpdateResult updateConfiguration(C candidateConfig) throws ConfigStoreException;
}
