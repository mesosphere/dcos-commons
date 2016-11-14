package org.apache.mesos.scheduler.recovery;

import org.apache.mesos.Protos;
import org.apache.mesos.config.ConfigStore;
import org.apache.mesos.config.ConfigStoreException;
import org.apache.mesos.offer.InvalidRequirementException;
import org.apache.mesos.offer.OfferRequirementProvider;
import org.apache.mesos.offer.TaskException;
import org.apache.mesos.offer.TaskUtils;
import org.apache.mesos.scheduler.plan.DefaultPodInstance;
import org.apache.mesos.specification.PodInstance;
import org.apache.mesos.specification.PodSpec;
import org.apache.mesos.specification.ServiceSpec;
import org.apache.mesos.specification.TaskSpec;
import org.apache.mesos.state.StateStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;

/**
 * This class is a default implementation of the RecoveryRequirementProvider interface.
 */
public class DefaultRecoveryRequirementProvider implements RecoveryRequirementProvider {
    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultRecoveryRequirementProvider.class);

    private final OfferRequirementProvider offerRequirementProvider;
    private final ConfigStore<ServiceSpec> configStore;
    private final StateStore stateStore;

    public DefaultRecoveryRequirementProvider(
            OfferRequirementProvider offerRequirementProvider,
            ConfigStore<ServiceSpec> configStore,
            StateStore stateStore) {
        this.offerRequirementProvider = offerRequirementProvider;
        this.configStore = configStore;
        this.stateStore = stateStore;
    }

    @Override
    public List<RecoveryRequirement> getTransientRecoveryRequirements(List<Protos.TaskInfo> stoppedTasks)
            throws InvalidRequirementException {

        List<RecoveryRequirement> transientRecoveryRequirements = new ArrayList<>();
        Map<PodInstance, List<Protos.TaskInfo>> podMap = getPodMap(stoppedTasks);

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
        Map<PodInstance, List<Protos.TaskInfo>> podMap = getPodMap(failedTasks);

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

    private Map<PodInstance, List<Protos.TaskInfo>> getPodMap(List<Protos.TaskInfo> taskInfos) {
        Map<PodInstance, List<Protos.TaskInfo>> podMap = new HashMap<>();

        for (Protos.TaskInfo taskInfo : taskInfos) {
            try {
                PodInstance podInstance = getPodInstance(taskInfo);
                List<Protos.TaskInfo> taskList = podMap.get(podInstance);

                if (taskList == null) {
                    taskList = Arrays.asList(taskInfo);
                } else {
                    taskList.add(taskInfo);
                }

                podMap.put(podInstance, taskList);
            } catch (TaskException e) {
                LOGGER.error("Failed to construct PodInstance for: {}", taskInfo);
            }
        }

        return podMap;
    }

    private PodInstance getPodInstance(Protos.TaskInfo taskInfo) throws TaskException {
        PodSpec podSpec = getPodSpec(taskInfo);
        Integer index = TaskUtils.getIndex(taskInfo);

        return new DefaultPodInstance(podSpec, index);
    }

    public PodSpec getPodSpec(Protos.TaskInfo taskInfo) throws TaskException {
        UUID configId = TaskUtils.getTargetConfiguration(taskInfo);
        ServiceSpec serviceSpec;

        try {
            serviceSpec = configStore.fetch(configId);
        } catch (ConfigStoreException e) {
            throw new TaskException(String.format(
                    "Unable to retrieve ServiceSpecification ID %s referenced by TaskInfo[%s]",
                    configId, taskInfo.getName()), e);
        }

        PodSpec podSpec = TaskUtils.getPodSpec(serviceSpec, taskInfo);
        if (podSpec == null) {
            throw new TaskException(String.format(
                    "No TaskSpecification found for TaskInfo[%s]", taskInfo.getName()));
        }
        return podSpec;
    }
}
