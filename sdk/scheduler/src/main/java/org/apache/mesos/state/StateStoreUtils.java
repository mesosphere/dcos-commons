package org.apache.mesos.state;

import org.apache.commons.lang3.StringUtils;
import org.apache.mesos.Protos;
import org.apache.mesos.Protos.TaskInfo;
import org.apache.mesos.Protos.TaskStatus;
import org.apache.mesos.config.ConfigStore;
import org.apache.mesos.offer.TaskException;
import org.apache.mesos.offer.TaskUtils;
import org.apache.mesos.specification.ServiceSpec;
import org.apache.mesos.specification.TaskSpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * Utilities for implementations and users of {@link StateStore}.
 */
public class StateStoreUtils {

    private static final Logger LOGGER = LoggerFactory.getLogger(StateStoreUtils.class);
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
                LOGGER.info("Task: {} needs recovery.", taskSpec.get());
                results.add(info);
            }
        }
        return results;
    }

    public static Collection<TaskInfo> fetchTasksFromPod(StateStore stateStore, String pod) throws StateStoreException {
        Collection<TaskInfo> allInfos = stateStore.fetchTasks();

        List<TaskInfo> results = new ArrayList<>();
        for (TaskInfo info : allInfos) {
            String taskPod = null;
            try {
                taskPod = TaskUtils.getType(info);
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
            throw new StateStoreException("Key cannot be blank or null");
        }
        if (key.contains("/")) {
            throw new StateStoreException("Key cannot contain '/'");
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
            throw new StateStoreException("Property value must not be null.");
        }
        if (value.length > MAX_VALUE_LENGTH_BYTES) {
            throw new StateStoreException(String.format(
                    "Property value length %d exceeds limit of %d bytes.",
                    value.length, MAX_VALUE_LENGTH_BYTES));
        }
    }
}
