package com.mesosphere.sdk.config;

import com.google.common.base.Joiner;
import com.google.common.base.Splitter;
import com.google.common.collect.Lists;
import com.google.protobuf.TextFormat;
import com.mesosphere.sdk.config.validate.ConfigValidationError;
import com.mesosphere.sdk.config.validate.ConfigValidator;
import com.mesosphere.sdk.dcos.DcosConstants;
import com.mesosphere.sdk.offer.TaskException;
import com.mesosphere.sdk.offer.taskdata.TaskLabelReader;
import com.mesosphere.sdk.offer.taskdata.TaskLabelWriter;
import com.mesosphere.sdk.specification.DefaultPodSpec;
import com.mesosphere.sdk.specification.DefaultServiceSpec;
import com.mesosphere.sdk.specification.PodSpec;
import com.mesosphere.sdk.specification.ServiceSpec;
import com.mesosphere.sdk.state.ConfigStore;
import com.mesosphere.sdk.state.ConfigStoreException;
import com.mesosphere.sdk.state.StateStore;
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

    public DefaultConfigurationUpdater(
            StateStore stateStore,
            ConfigStore<ServiceSpec> configStore,
            ConfigurationComparator<ServiceSpec> configComparator,
            Collection<ConfigValidator<ServiceSpec>> validators) {
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

        Optional<ServiceSpec> targetConfig;
        if (targetConfigId != null) {
            LOGGER.info("Loading current target configuration: {}", targetConfigId);
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
            LOGGER.error("Skipping config diff: New target couldn't be represented as JSON");
        } else {
            LOGGER.info("Prior target config:\n{}", targetConfig.get().toJsonString());
            printConfigDiff(targetConfig.get(), targetConfigId, candidateConfigJson);
        }

        targetConfig = fixServiceSpecUser(targetConfig);

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
            UUID oldTargetId = targetConfigId;
            targetConfigId = configStore.store(candidateConfig);
            LOGGER.info("Updating target configuration: "
                    + "Prior target configuration '{}' is different from new configuration '{}'. ",
                    oldTargetId, targetConfigId);
            targetConfig = Optional.of(candidateConfig);
            configStore.setTargetConfig(targetConfigId);
        } else {
            LOGGER.info("No changes detected between current target configuration '{}' and new configuration. " +
                            "Leaving current configuration as the target.",
                    targetConfigId);
        }

        // Update config IDs on tasks whose config contents match the current target, then clean up
        // leftover configs which are not the target and which are not referenced by any tasks.
        cleanupDuplicateAndUnusedConfigs(targetConfig.get(), targetConfigId);

        return new ConfigurationUpdater.UpdateResult(targetConfigId, errors);
    }

    /**
     * Detects whether the previous {@link ServiceSpec} set the user. If it didn't, we set it to "root"
     * as Mesos treats a non-set user as "root".
     *
     * @param targetConfig The previous service spec from the config
     */
    private Optional<ServiceSpec> fixServiceSpecUser(Optional<ServiceSpec> targetConfig) {
        if (!targetConfig.isPresent()) {
            return Optional.empty();
        }
        DefaultServiceSpec.Builder serviceSpecWithUser = DefaultServiceSpec.newBuilder(targetConfig.get());

        if (targetConfig.get().getUser() == null) {
            serviceSpecWithUser.user(DcosConstants.DEFAULT_SERVICE_USER);
        }

        List<PodSpec> podsWithUser = new ArrayList<>();
        for (PodSpec podSpec : targetConfig.get().getPods()) {
            podsWithUser.add(
                    podSpec.getUser() != null && podSpec.getUser().isPresent() ? podSpec :
                            DefaultPodSpec.newBuilder(podSpec).user(DcosConstants.DEFAULT_SERVICE_USER).build()
            );
        }
        serviceSpecWithUser.pods(podsWithUser);

        return Optional.of(serviceSpecWithUser.build());
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
                taskConfigId = new TaskLabelReader(taskInfo).getTargetConfiguration();
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
                        TaskInfo.Builder taskBuilder = taskInfo.toBuilder();
                        taskBuilder.setLabels(new TaskLabelWriter(taskInfo)
                                .setTargetConfiguration(targetConfigId)
                                .toProto());
                        taskInfosToUpdate.add(taskBuilder.build());
                    } else {
                        // Config isn't the same as the target. Refrain from updating task, mark config as 'needed'.
                        neededConfigs.add(taskConfigId);
                    }
                } catch (Exception e) {
                    LOGGER.error(String.format("Failed to fetch configuration %s for task %s",
                            taskConfigId, taskInfo.getName()), e);
                    // Cannot read this task's config. Do not delete the config.
                    neededConfigs.add(taskConfigId);
                }
            }
        }

        if (!taskInfosToUpdate.isEmpty()) {
            LOGGER.info("Updating {} tasks in StateStore with target configuration ID {}",
                    taskInfosToUpdate.size(), targetConfigId);
            stateStore.storeTasks(taskInfosToUpdate);
        }

        Collection<UUID> configIds = configStore.list();
        LOGGER.info("Testing deserialization of {} listed configurations before cleanup:", configIds.size());
        for (UUID configId : configIds) {
            try {
                configStore.fetch(configId);
                LOGGER.info("- {}: OK", configId);
            } catch (Exception e) {
                LOGGER.info("- {}: FAILED, leaving as-is: {}", configId, e.getMessage());
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
            Protos.TaskInfo taskInfo, ServiceSpec targetConfig, ServiceSpec taskConfig) {
        if (targetConfig.equals(taskConfig)) {
            LOGGER.info("Task '{}' is up to date: Task's target ServiceSpec matches the current ServiceSpec",
                    taskInfo.getName());
            return false;
        }

        final String podType;
        final boolean isPermanentlyFailed;
        try {
            TaskLabelReader reader = new TaskLabelReader(taskInfo);
            podType = reader.getType();
            isPermanentlyFailed = reader.isPermanentlyFailed();
        } catch (TaskException e) {
            LOGGER.error(String.format(
                    "Unable to extract pod type from task '%s'. Will assume the task needs a configuration update",
                    taskInfo.getName()), e);
            return true;
        }

        // Permanently failed tasks should be placed on the target configuration immediately.  They do not need
        // to transition from their former config to the new target.
        if (isPermanentlyFailed) {
            return false;
        }

        Optional<PodSpec> targetSpecOptional = getPodSpec(targetConfig, podType);
        Optional<PodSpec> taskSpecOptional = getPodSpec(taskConfig, podType);
        if (!targetSpecOptional.isPresent() || !taskSpecOptional.isPresent()) {
            LOGGER.info("Task '{}' needs a configuration update: " +
                    "PodSpec '{}' was {} in task's config, but is {} in current target config",
                    taskInfo.getName(),
                    podType,
                    taskSpecOptional.isPresent() ? "present" : "missing",
                    targetSpecOptional.isPresent() ? "present" : "missing");
            return true;
        }

        boolean updateNeeded = !areMatching(targetSpecOptional.get(), taskSpecOptional.get());
        if (updateNeeded) {
            LOGGER.info("Task '{}' needs a configuration update: PodSpec '{}' has changed",
                    taskInfo.getName(), podType);
        } else {
            LOGGER.info("Task '{}' is up to date: PodSpec '{}' is the same", taskInfo.getName(), podType);
        }
        return updateNeeded;
    }

    private static Optional<PodSpec> getPodSpec(ServiceSpec serviceSpecification, String podType) {
        return serviceSpecification.getPods().stream()
                .filter(pod -> pod.getType().equals(podType))
                .findFirst();
    }

    private static boolean areMatching(PodSpec podSpec1, PodSpec podSpec2) {
        if (podSpec1.equals(podSpec2)) {
            // Shortcut: Below modification was not needed to check for equality
            return true;
        }

        return filterIrrelevantFieldsForUpdateComparison(podSpec1)
                .equals(filterIrrelevantFieldsForUpdateComparison(podSpec2));
    }

    /**
     * When evaluating whether a pod should be updated, some PodSpec changes are immaterial:
     * <ol>
     * <li>Count: Extant pods do not care if they will have more fellows</li>
     * <li>Placement Rules: Extant pods should not (immediately) move around due to placement changes</li>
     * <li>Allow decommission: Does not affect the pods themselves, only how we treat them</li>
     * </ol>
     * As such, ignore these fields when checking for differences.
     *
     * @return a new {@link PodSpec} with irrelevant parameters filtered out
     */
    private static PodSpec filterIrrelevantFieldsForUpdateComparison(PodSpec podSpec) {
        // Set arbitrary values. We just want the two spec copies to be equivalent where these fields are concerned:
        return DefaultPodSpec.newBuilder(podSpec)
                .count(0)
                .placementRule(null)
                .allowDecommission(false)
                .build();
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
