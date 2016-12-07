package com.mesosphere.sdk.scheduler.recovery;

import org.apache.mesos.Protos;
import com.mesosphere.sdk.config.ConfigStore;
import com.mesosphere.sdk.offer.InvalidRequirementException;
import com.mesosphere.sdk.offer.OfferRequirementProvider;
import com.mesosphere.sdk.offer.TaskException;
import com.mesosphere.sdk.offer.TaskUtils;
import com.mesosphere.sdk.specification.PodInstance;
import com.mesosphere.sdk.specification.ServiceSpec;
import com.mesosphere.sdk.specification.TaskSpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * This class is a default implementation of the RecoveryRequirementProvider interface.
 */
public class DefaultRecoveryRequirementProvider implements RecoveryRequirementProvider {
    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultRecoveryRequirementProvider.class);

    private final OfferRequirementProvider offerRequirementProvider;
    private final ConfigStore<ServiceSpec> configStore;

    public DefaultRecoveryRequirementProvider(
            OfferRequirementProvider offerRequirementProvider,
            ConfigStore<ServiceSpec> configStore) {
        this.offerRequirementProvider = offerRequirementProvider;
        this.configStore = configStore;
    }

    @Override
    public List<RecoveryRequirement> getTransientRecoveryRequirements(List<Protos.TaskInfo> stoppedTasks)
            throws InvalidRequirementException {

        List<RecoveryRequirement> transientRecoveryRequirements = new ArrayList<>();
        Map<PodInstance, List<Protos.TaskInfo>> podMap = null;
        try {
            podMap = TaskUtils.getPodMap(configStore, stoppedTasks);
        } catch (TaskException e) {
            LOGGER.error("Failed to generate pod map.", e);
            return Collections.emptyList();
        }

        for (Map.Entry<PodInstance, List<Protos.TaskInfo>> podEntry : podMap.entrySet()) {
            PodInstance podInstance = podEntry.getKey();

            List<String> tasksToLaunch = podInstance.getPod().getTasks().stream()
                    .filter(taskSpec -> taskSpec.getGoal().equals(TaskSpec.GoalState.RUNNING))
                    .map(taskSpec -> TaskSpec.getInstanceName(podInstance, taskSpec))
                    .collect(Collectors.toList());

            // Note: We intentionally remove any placement rules when performing transient recovery.
            // We aren't interested in honoring placement rules, past or future. We just want to
            // get the task back up and running where it was before.
            transientRecoveryRequirements.add(
                    new DefaultRecoveryRequirement(
                            offerRequirementProvider.getExistingOfferRequirement(podInstance, tasksToLaunch),
                            RecoveryRequirement.RecoveryType.TRANSIENT,
                            podInstance));
        }

        return transientRecoveryRequirements;
    }

    @Override
    public List<RecoveryRequirement> getPermanentRecoveryRequirements(List<Protos.TaskInfo> failedTasks)
            throws InvalidRequirementException {

        List<RecoveryRequirement> permanentRecoveryRequirements = new ArrayList<>();
        Map<PodInstance, List<Protos.TaskInfo>> podMap = null;
        try {
            podMap = TaskUtils.getPodMap(configStore, failedTasks);
        } catch (TaskException e) {
            LOGGER.error("Failed to generate pod map.", e);
            return Collections.emptyList();
        }

        for (PodInstance podInstance : podMap.keySet()) {
            List<String> tasksToLaunch = podInstance.getPod().getTasks().stream()
                    .filter(taskSpec -> taskSpec.getGoal().equals(TaskSpec.GoalState.RUNNING))
                    .map(taskSpec -> TaskSpec.getInstanceName(podInstance, taskSpec))
                    .collect(Collectors.toList());

            permanentRecoveryRequirements.add(
                    new DefaultRecoveryRequirement(
                            offerRequirementProvider.getNewOfferRequirement(podInstance, tasksToLaunch),
                            RecoveryRequirement.RecoveryType.PERMANENT,
                            podInstance));
        }

        return permanentRecoveryRequirements;
    }
}
