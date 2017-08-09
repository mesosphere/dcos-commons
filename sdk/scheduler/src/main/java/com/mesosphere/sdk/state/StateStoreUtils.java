package com.mesosphere.sdk.state;

import com.google.common.annotations.VisibleForTesting;
import com.google.protobuf.TextFormat;
import com.mesosphere.sdk.config.ConfigurationUpdater;
import com.mesosphere.sdk.offer.TaskException;
import com.mesosphere.sdk.offer.TaskUtils;
import com.mesosphere.sdk.specification.ServiceSpec;
import com.mesosphere.sdk.specification.TaskSpec;
import com.mesosphere.sdk.storage.StorageError.Reason;
import org.apache.mesos.Protos;
import org.apache.mesos.Protos.TaskInfo;
import org.apache.mesos.Protos.TaskStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Utilities for implementations and users of {@link StateStore}.
 */
public class StateStoreUtils {

    private static final Logger LOGGER = LoggerFactory.getLogger(StateStoreUtils.class);
    private static final String SUPPRESSED_PROPERTY_KEY = "suppressed";
    private static final String UNINSTALLING_PROPERTY_KEY = "uninstalling";
    private static final String LAST_COMPLETED_UPDATE_TYPE_KEY = "last-completed-update-type";
    private static final String PROPERTY_TASK_INFO_SUFFIX = ":task-status";

    private StateStoreUtils() {
        // do not instantiate
    }

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

    /**
     * Verifies that the supplied TaskStatus corresponds to a single TaskInfo in the provided StateStore and returns the
     * TaskInfo.
     *
     * @return The singular TaskInfo if it is present
     * @throws StateStoreException if no corresponding TaskInfo is found.
     * @throws StateStoreException if multiple corresponding TaskInfo's are found.
     */
    public static TaskInfo getTaskInfo(StateStore stateStore, TaskStatus taskStatus)
            throws StateStoreException {
        Optional<Protos.TaskInfo> taskInfoOptional = Optional.empty();

        for (Protos.TaskInfo taskInfo : stateStore.fetchTasks()) {
            if (taskInfo.getTaskId().getValue().equals(taskStatus.getTaskId().getValue())) {
                if (taskInfoOptional.isPresent()) {
                    LOGGER.error("Found duplicate TaskIDs in Task{} and Task {}",
                            taskInfoOptional.get(), taskInfo.getName());
                    throw new StateStoreException(Reason.LOGIC_ERROR, String.format(
                            "There are more than one tasks with TaskID: %s", taskStatus));
                } else {
                    taskInfoOptional = Optional.of(taskInfo);
                }
            }
        }

        if (!taskInfoOptional.isPresent()) {
            throw new StateStoreException(Reason.NOT_FOUND, String.format(
                    "Failed to find a task with TaskID: %s", taskStatus));
        }

        return taskInfoOptional.get();
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

    @VisibleForTesting
    protected static boolean fetchBooleanProperty(StateStore stateStore, String propertyName) {
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
     * Stores a TaskStatus as a Property in the provided state store.
     */
    public static void storeTaskStatusAsProperty(StateStore stateStore, String taskName, TaskStatus taskStatus)
            throws StateStoreException {
        stateStore.storeProperty(taskName + PROPERTY_TASK_INFO_SUFFIX, taskStatus.toByteArray());
    }

    /**
     * Returns an Optional<TaskStatus> from the properties in the provided state store for the specified
     * task name.
     */
    public static Optional<TaskStatus> getTaskStatusFromProperty(StateStore stateStore, String taskName) {
        try {
            return Optional.of(TaskStatus.parseFrom(
                    stateStore.fetchProperty(taskName + PROPERTY_TASK_INFO_SUFFIX)));
        } catch (Exception e) {
            // Broadly catch exceptions to handle:
            // Invalid TaskStatuses
            // StateStoreExceptions
            LOGGER.error("Unable to decode TaskStatus for taskName=" + taskName, e);
            return Optional.empty();
        }
    }

    /**
     * Sets the last completed update type.
     */
    public static void setLastCompletedUpdateType(
            StateStore stateStore,
            ConfigurationUpdater.UpdateResult.DeploymentType updateResultDeploymentType) {
        stateStore.storeProperty(
                LAST_COMPLETED_UPDATE_TYPE_KEY,
                updateResultDeploymentType.name().getBytes(StandardCharsets.UTF_8));
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
