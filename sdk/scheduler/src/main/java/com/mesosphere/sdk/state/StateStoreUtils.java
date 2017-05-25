package com.mesosphere.sdk.state;

import com.google.protobuf.TextFormat;
import com.mesosphere.sdk.config.ConfigStore;
import com.mesosphere.sdk.config.ConfigurationUpdater;
import com.mesosphere.sdk.offer.MesosResource;
import com.mesosphere.sdk.offer.TaskException;
import com.mesosphere.sdk.offer.TaskUtils;
import com.mesosphere.sdk.offer.taskdata.SchedulerLabelReader;
import com.mesosphere.sdk.specification.PodInstance;
import com.mesosphere.sdk.specification.ServiceSpec;
import com.mesosphere.sdk.specification.TaskSpec;
import com.mesosphere.sdk.storage.StorageError.Reason;
import org.apache.commons.lang3.StringUtils;
import org.apache.mesos.Protos;
import org.apache.mesos.Protos.TaskInfo;
import org.apache.mesos.Protos.TaskStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Utilities for implementations and users of {@link StateStore}.
 */
public class StateStoreUtils {

    private static final Logger LOGGER = LoggerFactory.getLogger(StateStoreUtils.class);
    private static final String SUPPRESSED_PROPERTY_KEY = "suppressed";
    private static final String UNINSTALLING_PROPERTY_KEY = "uninstalling";
    private static final String LAST_COMPLETED_UPDATE_TYPE_KEY = "last-completed-update-type";
    private static final int MAX_VALUE_LENGTH_BYTES = 1024 * 1024; // 1MB

    private StateStoreUtils() {
        // do not instantiate
    }


    // Utilities for StateStore users:


    /**
     * Returns the requested property, or an empty array if the property is not present.
     */
    public static byte[] fetchPropertyOrEmptyArray(StateStore stateStore, String key) {
        if (stateStore.fetchPropertyKeys().contains(key)) {
            return stateStore.fetchProperty(key);
        } else {
            return new byte[0];
        }
    }


    /**
     * Fetches and returns all {@link TaskInfo}s for tasks needing recovery.
     *
     * @return Terminated TaskInfos
     */
    public static Collection<TaskInfo> fetchTasksNeedingRecovery(
            StateStore stateStore,
            ConfigStore<ServiceSpec> configStore)
            throws StateStoreException, TaskException {

        Collection<TaskInfo> allInfos = stateStore.fetchTasks();
        Collection<TaskStatus> allStatuses = stateStore.fetchStatuses();

        Map<Protos.TaskID, TaskStatus> statusMap = new HashMap<>();
        for (TaskStatus status : allStatuses) {
            statusMap.put(status.getTaskId(), status);
        }

        List<TaskInfo> results = new ArrayList<>();
        for (TaskInfo info : allInfos) {
            TaskStatus status = statusMap.get(info.getTaskId());
            if (status == null) {
                continue;
            }

            Optional<TaskSpec> taskSpec = TaskUtils.getTaskSpec(
                    TaskUtils.getPodInstance(configStore, info),
                    info.getName());

            if (!taskSpec.isPresent()) {
                throw new TaskException("Failed to determine TaskSpec from TaskInfo: " + info);
            }

            if (TaskUtils.needsRecovery(taskSpec.get(), status)) {
                LOGGER.info("Task: '{}' needs recovery with status: {}.",
                        taskSpec.get().getName(), TextFormat.shortDebugString(status));
                results.add(info);
            }
        }
        return results;
    }

    public static Collection<TaskInfo> fetchTasksFromPod(StateStore stateStore, String pod) throws StateStoreException {
        Collection<TaskInfo> allInfos = stateStore.fetchTasks();

        List<TaskInfo> results = new ArrayList<>();
        for (TaskInfo info : allInfos) {
            String taskPod;
            try {
                taskPod = new SchedulerLabelReader(info).getType();
            } catch (TaskException e) {
                continue;
            }

            if (pod.equals(taskPod)) {
                results.add(info);
            }
        }

        return results;
    }


    // Utilities for StateStore implementations:


    /**
     * Shared implementation for validating property key limits, for use by all StateStore
     * implementations.
     *
     * @see StateStore#storeProperty(String, byte[])
     * @see StateStore#fetchProperty(String)
     */
    public static void validateKey(String key) throws StateStoreException {
        if (StringUtils.isBlank(key)) {
            throw new StateStoreException(Reason.LOGIC_ERROR, "Key cannot be blank or null");
        }
        if (key.contains("/")) {
            throw new StateStoreException(Reason.LOGIC_ERROR, "Key cannot contain '/'");
        }
    }

    /**
     * Shared implementation for validating property value limits, for use by all StateStore
     * implementations.
     *
     * @see StateStore#storeProperty(String, byte[])
     */
    public static void validateValue(byte[] value) throws StateStoreException {
        if (value == null) {
            throw new StateStoreException(Reason.LOGIC_ERROR, "Property value must not be null.");
        }
        if (value.length > MAX_VALUE_LENGTH_BYTES) {
            throw new StateStoreException(Reason.LOGIC_ERROR, String.format(
                    "Property value length %d exceeds limit of %d bytes.",
                    value.length, MAX_VALUE_LENGTH_BYTES));
        }
    }

    public static Collection<Protos.Resource> getReservedResources(Collection<Protos.Resource> resources) {
        Collection<Protos.Resource> reservedResources = new ArrayList<>();
        for (Protos.Resource resource : resources) {
            MesosResource mesosResource = new MesosResource(resource);
            if (mesosResource.getResourceId().isPresent()) {
                reservedResources.add(resource);
            }
        }

        return reservedResources;
    }

    public static Collection<Protos.Resource> getResources(
            StateStore stateStore,
            PodInstance podInstance,
            TaskSpec taskSpec) {
        String resourceSetName = taskSpec.getResourceSet().getId();

        Collection<String> tasksWithResourceSet = podInstance.getPod().getTasks().stream()
                .filter(taskSpec1 -> resourceSetName.equals(taskSpec1.getResourceSet().getId()))
                .map(taskSpec1 -> TaskSpec.getInstanceName(podInstance, taskSpec1))
                .distinct()
                .collect(Collectors.toList());

        LOGGER.info("Tasks with resource set: {}, {}", resourceSetName, tasksWithResourceSet);

        Collection<TaskInfo> taskInfosForPod = stateStore.fetchTasks().stream()
                .filter(taskInfo -> {
                    try {
                        return TaskUtils.isSamePodInstance(taskInfo, podInstance);
                    } catch (TaskException e) {
                        return false;
                    }
                })
                .collect(Collectors.toList());

        LOGGER.info("Tasks for pod: {}",
                taskInfosForPod.stream()
                        .map(TaskInfo::getName)
                        .collect(Collectors.toList()));

        Optional<TaskInfo> taskInfoOptional = taskInfosForPod.stream()
                .filter(taskInfo -> tasksWithResourceSet.contains(taskInfo.getName()))
                .findFirst();

        if (taskInfoOptional.isPresent()) {
            LOGGER.info("Found Task with resource set: {}, {}",
                    resourceSetName,
                    TextFormat.shortDebugString(taskInfoOptional.get()));
            return taskInfoOptional.get().getResourcesList();
        } else {
            LOGGER.error("Failed to find a Task with resource set: {}", resourceSetName);
            return Collections.emptyList();
        }
    }

    /**
     * Returns the current value of the 'suppressed' property in the provided {@link StateStore}.
     */
    public static boolean isSuppressed(StateStore stateStore) throws StateStoreException {
        return fetchBooleanProperty(stateStore, SUPPRESSED_PROPERTY_KEY);
    }

    /**
     * Sets a 'suppressed' property in the provided {@link StateStore} to the provided value.
     */
    public static void setSuppressed(StateStore stateStore, boolean isSuppressed) {
        setBooleanProperty(stateStore, SUPPRESSED_PROPERTY_KEY, isSuppressed);
    }

    /**
     * Returns the current value of the 'uninstall' property in the provided {@link StateStore}.
     */
    public static boolean isUninstalling(StateStore stateStore) throws StateStoreException {
        return fetchBooleanProperty(stateStore, UNINSTALLING_PROPERTY_KEY);
    }

    /**
     * Sets an 'uninstall' property in the provided {@link StateStore} to {@code true}.
     */
    public static void setUninstalling(StateStore stateStore) {
        setBooleanProperty(stateStore, UNINSTALLING_PROPERTY_KEY, true);
    }

    private static boolean fetchBooleanProperty(StateStore stateStore, String propertyName) {
        byte[] bytes = fetchPropertyOrEmptyArray(stateStore, propertyName);
        if (bytes.length == 0) {
            return false;
        } else {
            try {
                return new JsonSerializer().deserialize(bytes, Boolean.class);
            } catch (IOException e) {
                LOGGER.error(String.format("Error converting property '%s' to boolean.", propertyName), e);
                throw new StateStoreException(Reason.SERIALIZATION_ERROR, e);
            }
        }
    }

    private static void setBooleanProperty(StateStore stateStore, String propertyName, boolean value) {
        stateStore.storeProperty(propertyName, new JsonSerializer().serialize(value));
    }

    /**
     * Sets the last completed update type.
     */
    public static void setLastCompletedUpdateType(
            StateStore stateStore,
            ConfigurationUpdater.UpdateResult updateResult) {
        stateStore.storeProperty(
                LAST_COMPLETED_UPDATE_TYPE_KEY,
                updateResult.getDeploymentType().name().getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Gets the last completed update type.
     */
    public static ConfigurationUpdater.UpdateResult.DeploymentType getLastCompletedUpdateType(StateStore stateStore) {
        byte[] bytes = fetchPropertyOrEmptyArray(
                stateStore,
                LAST_COMPLETED_UPDATE_TYPE_KEY);
        if (bytes.length == 0) {
            return ConfigurationUpdater.UpdateResult.DeploymentType.NONE;
        } else {
            String value = new String(bytes, StandardCharsets.UTF_8);
            return ConfigurationUpdater.UpdateResult.DeploymentType.valueOf(value);
        }
    }
}
