package com.mesosphere.sdk.state;

import com.google.protobuf.InvalidProtocolBufferException;
import com.mesosphere.sdk.offer.LoggingUtils;
import com.mesosphere.sdk.offer.TaskUtils;
import com.mesosphere.sdk.storage.Persister;
import com.mesosphere.sdk.storage.PersisterException;
import com.mesosphere.sdk.storage.PersisterUtils;
import com.mesosphere.sdk.storage.StorageError.Reason;

import org.apache.commons.lang3.StringUtils;
import org.apache.mesos.Protos;
import org.slf4j.Logger;

import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * A {@code StateStore} stores the state of a service, including tasks' TaskInfo and TaskStatus objects. Each
 * distinct Task is expected to have a unique Task Name, determined by the service developer.
 * <p>
 * TaskInfo objects should be persisted when a Task is launched or when reserved resources associated with a potential
 * future Task launch should be recorded.
 * <p>
 * TaskStatus is reported by Mesos to Frameworks at various points including at Task Reconciliation and when Tasks
 * change state.  The TaskStatus of a Task should be recorded so that the state of a Framework's Tasks can be queried.
 *
 * <p>The structure used in the underlying persister is as follows:
 * <br>namespacedPath/ ("Services/NAMESPACE/" or "/")
 * <br>&nbsp; Tasks/
 * <br>&nbsp; &nbsp; task-a/
 * <br>&nbsp; &nbsp; &nbsp; TaskInfo
 * <br>&nbsp; &nbsp; &nbsp; TaskStatus
 * <br>&nbsp; &nbsp; &nbsp; Metadata/
 * <br>&nbsp; &nbsp; &nbsp; &nbsp; goal-state-override
 * <br>&nbsp; &nbsp; &nbsp; &nbsp; override-status
 * <br>&nbsp; &nbsp; task-b/
 * <br>&nbsp; &nbsp; &nbsp; TaskInfo
 * <br>&nbsp; &nbsp; task-c/
 * <br>&nbsp; &nbsp; &nbsp; TaskInfo
 * <br>&nbsp; &nbsp; &nbsp; TaskStatus
 * <br>&nbsp; Properties/
 * <br>&nbsp; &nbsp; some-property
 * <br>&nbsp; &nbsp; another-property
 */
public class StateStore {

    private static final int MAX_VALUE_LENGTH_BYTES = 1024 * 1024; // 1MB

    private static final String TASK_INFO_PATH_NAME = "TaskInfo";
    private static final String TASK_STATUS_PATH_NAME = "TaskStatus";
    private static final String TASK_METADATA_PATH_NAME = "Metadata";

    private static final String TASK_GOAL_OVERRIDE_PATH_NAME = "goal-state-override";
    private static final String TASK_GOAL_OVERRIDE_STATUS_PATH_NAME = "override-status";

    private static final String PROPERTIES_ROOT_NAME = "Properties";
    private static final String TASKS_ROOT_NAME = "Tasks";

    private final Logger logger;
    protected final Persister persister;
    protected final String namespace;

    /**
     * Creates a new {@link StateStore} which uses the provided {@link Persister} to access state data.
     *
     * @param persister The persister which holds the state data
     */
    public StateStore(Persister persister) {
        this(persister, Optional.empty());
    }

    /**
     * Returns the underlying {@link Persister}.
     */
    public Persister getPersister() {
        return persister;
    }

    /**
     * Creates a new {@link StateStore} where data is placed within a namespace under the provided name.
     *
     * @param persister The persister which holds the state data
     * @param namespace The namespace for data to be stored within, or an empty Optional for no namespacing
     */
    public StateStore(Persister persister, Optional<String> namespace) {
        this.logger = LoggingUtils.getLogger(getClass(), namespace);
        this.persister = persister;
        this.namespace = namespace.orElse("");

        StateStoreUtils.repairTaskIDs(this);
    }

    // Write Tasks

    /**
     * Stores TaskInfo objects representing tasks which are desired by the framework. This must be called before {@link
     * #storeStatus(String, Protos.TaskStatus)} for any given task id, and it must behave as an atomic transaction: On
     * success, everything is written, while on failure nothing is written.
     *
     * @param tasks Tasks to be stored, which each meet the above requirements
     * @throws StateStoreException when persisting TaskInfo information fails, or if its TaskId is malformed
     */
    public void storeTasks(Collection<Protos.TaskInfo> tasks) throws StateStoreException {
        Map<String, byte[]> taskBytesMap = new HashMap<>();
        for (Protos.TaskInfo taskInfo : tasks) {
            taskBytesMap.put(getTaskInfoPath(namespace, taskInfo.getName()), taskInfo.toByteArray());
        }
        try {
            persister.setMany(taskBytesMap);
        } catch (PersisterException e) {
            throw new StateStoreException(e, String.format("Failed to store %d TaskInfos", tasks.size()));
        }
    }

    /**
     * Stores the TaskStatus of a particular Task. The {@link Protos.TaskInfo} for this exact task MUST have already
     * been written via {@link #storeTasks(Collection)} beforehand. The TaskId must be well-formatted as produced by
     * {@link com.mesosphere.sdk.offer.CommonIdUtils#toTaskId(String)}.
     *
     * @param status The status to be stored, which meets the above requirements
     * @throws StateStoreException if storing the TaskStatus fails, or if its TaskId is malformed, or if its matching
     *                             TaskInfo wasn't stored first
     */
    public void storeStatus(String taskName, Protos.TaskStatus status) throws StateStoreException {
        Optional<Protos.TaskStatus> currentStatusOptional = fetchStatus(taskName);
        if (currentStatusOptional.isPresent()
                && status.getState().equals(Protos.TaskState.TASK_LOST)
                && TaskUtils.isTerminal(currentStatusOptional.get())) {
            throw new StateStoreException(Reason.LOGIC_ERROR,
                    String.format("Ignoring TASK_LOST for Task already in a terminal state %s: %s",
                            currentStatusOptional.get().getState(), taskName));
        }

        if (!status.getState().equals(Protos.TaskState.TASK_STAGING) &&
                currentStatusOptional.isPresent() &&
                !currentStatusOptional.get().getTaskId().equals(status.getTaskId())) {
            throw new StateStoreException(
                    Reason.NOT_FOUND,
                    String.format("Dropping TaskStatus with unknnown TaskID: %s", status));
        }

        String path = getTaskStatusPath(namespace, taskName);
        logger.info("Storing status '{}' for '{}' in '{}'", status.getState(), taskName, path);

        try {
            persister.set(path, status.toByteArray());
        } catch (PersisterException e) {
            throw new StateStoreException(e);
        }
    }

    /**
     * Removes all data associated with a particular Task including any stored TaskInfo and/or TaskStatus.
     *
     * @param taskName The name of the task to be cleared
     * @throws StateStoreException when clearing the indicated Task's information fails
     */
    public void clearTask(String taskName) throws StateStoreException {
        try {
            persister.recursiveDelete(getTaskPath(namespace, taskName));
        } catch (PersisterException e) {
            if (e.getReason() == Reason.NOT_FOUND) {
                // Clearing a non-existent Task should not result in an exception from us.
                logger.warn("Cleared nonexistent Task, continuing silently: {}", taskName, e);
            } else {
                throw new StateStoreException(e);
            }
        }
    }

    // Read Tasks

    /**
     * Fetches all the Task names listed in the underlying storage. Note that these should always have a TaskInfo, but
     * may lack TaskStatus.
     *
     * @return All the Task names stored so far, or an empty list if none are found
     * @throws StateStoreException when fetching the data fails
     */
    public Collection<String> fetchTaskNames() throws StateStoreException {
        try {
            Collection<String> taskNames = new ArrayList<>();
            taskNames.addAll(persister.getChildren(
                    PersisterUtils.getServiceNamespacedRootPath(namespace, TASKS_ROOT_NAME)));
            return taskNames;
        } catch (PersisterException e) {
            if (e.getReason() == Reason.NOT_FOUND) {
                // Root path doesn't exist yet. Treat as an empty list of tasks. This scenario is
                // expected to commonly occur when the Framework is being run for the first time.
                return Collections.emptyList();
            } else {
                throw new StateStoreException(e);
            }
        }
    }

    /**
     * Fetches and returns all {@link Protos.TaskInfo}s from the underlying storage, or an empty list if none are found.
     * This list should be a superset of the list returned by {@link #fetchStatuses()}.
     *
     * @return All TaskInfos
     * @throws StateStoreException if fetching the TaskInfo information otherwise fails
     */
    public Collection<Protos.TaskInfo> fetchTasks() throws StateStoreException {
        Collection<Protos.TaskInfo> taskInfos = new ArrayList<>();
        for (String taskName : fetchTaskNames()) {
            Optional<Protos.TaskInfo> taskInfoOptional = fetchTask(taskName);
            if (taskInfoOptional.isPresent()) {
                taskInfos.add(taskInfoOptional.get());
            } else {
                // We should always have a TaskInfo for every name entry we just got
                throw new StateStoreException(Reason.NOT_FOUND,
                        String.format("Expected task named %s to be present when retrieving all tasks", taskName));
            }
        }
        return taskInfos;
    }

    /**
     * Fetches the TaskInfo for a particular Task, or returns an empty Optional if no matching task is found.
     *
     * @param taskName The name of the Task
     * @return The corresponding TaskInfo object
     * @throws StateStoreException if no data was found for the requested name, or if fetching the TaskInfo otherwise
     *                             fails
     */
    public Optional<Protos.TaskInfo> fetchTask(String taskName) throws StateStoreException {
        String path = getTaskInfoPath(namespace, taskName);
        try {
            byte[] bytes = persister.get(path);
            if (bytes.length > 0) {
                return Optional.of(Protos.TaskInfo.parseFrom(bytes));
            } else {
                throw new StateStoreException(Reason.SERIALIZATION_ERROR, String.format(
                        "Empty TaskInfo for TaskName: %s", taskName));
            }
        } catch (PersisterException e) {
            if (e.getReason() == Reason.NOT_FOUND) {
                logger.warn("No TaskInfo found for the requested name: {} at: {}", taskName, path);
                return Optional.empty();
            } else {
                throw new StateStoreException(e, String.format("Failed to retrieve task named %s", taskName));
            }
        } catch (InvalidProtocolBufferException e) {
            throw new StateStoreException(Reason.SERIALIZATION_ERROR, e);
        }
    }

    /**
     * Fetches all {@link Protos.TaskStatus}es from the underlying storage, or an empty list if none are found. Note
     * that this list may have fewer entries than {@link #fetchTasks()} if some tasks are lacking statuses.
     *
     * @return The TaskStatus objects associated with all tasks
     * @throws StateStoreException if fetching the TaskStatus information fails
     */
    public Collection<Protos.TaskStatus> fetchStatuses() throws StateStoreException {
        Collection<Protos.TaskStatus> taskStatuses = new ArrayList<>();
        for (String taskName : fetchTaskNames()) {
            try {
                byte[] bytes = persister.get(getTaskStatusPath(namespace, taskName));
                taskStatuses.add(Protos.TaskStatus.parseFrom(bytes));
            } catch (PersisterException e) {
                if (e.getReason() == Reason.NOT_FOUND) {
                    // The task node exists, but it doesn't contain a TaskStatus node. This may occur if
                    // the only contents are a TaskInfo.
                    continue;
                } else {
                    throw new StateStoreException(e);
                }
            } catch (InvalidProtocolBufferException e) {
                throw new StateStoreException(Reason.SERIALIZATION_ERROR, e);
            }
        }
        return taskStatuses;
    }

    /**
     * Fetches the TaskStatus for a particular Task, or returns an empty Optional if no matching status is found.
     * A given task may sometimes have {@link Protos.TaskInfo} while lacking {@link Protos.TaskStatus}.
     *
     * @param taskName The name of the Task which should have its status retrieved
     * @return The TaskStatus associated with a particular Task
     * @throws StateStoreException if no data was found for the requested name, or if fetching the TaskStatus
     *                             information otherwise fails
     */
    public Optional<Protos.TaskStatus> fetchStatus(String taskName) throws StateStoreException {
        String path = getTaskStatusPath(namespace, taskName);
        try {
            byte[] bytes = persister.get(path);
            if (bytes.length > 0) {
                return Optional.of(Protos.TaskStatus.parseFrom(bytes));
            } else {
                throw new StateStoreException(Reason.SERIALIZATION_ERROR, String.format(
                        "Empty TaskStatus for TaskName: %s", taskName));
            }
        } catch (PersisterException e) {
            if (e.getReason() == Reason.NOT_FOUND) {
                logger.warn("No TaskStatus found for the requested name: {} at: {}", taskName, path);
                return Optional.empty();
            } else {
                throw new StateStoreException(e);
            }
        } catch (InvalidProtocolBufferException e) {
            throw new StateStoreException(Reason.SERIALIZATION_ERROR, e);
        }
    }

    // Read/Write properties

    /**
     * Stores an arbitrary key/value pair.
     *
     * @param key must be a non-blank String without any forward slashes ('/')
     * @param value The value should be a byte array no larger than 1MB (1024 * 1024 bytes)
     * @throws StateStoreException if the key or value fail validation, or if storing the data otherwise fails
     * @see StateStore#validateKey(String)
     * @see StateStore#validateValue(byte[])
     */
    public void storeProperty(final String key, final byte[] value) throws StateStoreException {
        validateKey(key);
        validateValue(value);
        try {
            final String path = getPropertyPath(namespace, key);
            logger.debug("Storing property key: {} into path: {}", key, path);
            persister.set(path, value);
        } catch (PersisterException e) {
            throw new StateStoreException(e);
        }
    }

    /**
     * Stores multiple key/value pairs atomically.
     *
     * @param properties mapping of keys to values
     * @throws StateStoreException if the key or value fail validation, or if storing the data otherwise fails
     * @see StateStore#validateKey(String)
     * @see StateStore#validateValue(byte[])
     */
    public void storeProperties(Map<String, byte[]> properties) throws StateStoreException {
        // Validate values and map paths for each entry, as we would in storeProperty()
        Map<String, byte[]> propertiesWithFixedPaths = new HashMap<>();
        for (Map.Entry<String, byte[]> entry : properties.entrySet()) {
            validateKey(entry.getKey());
            validateValue(entry.getValue());
            propertiesWithFixedPaths.put(getPropertyPath(namespace, entry.getKey()), entry.getValue());
        }
        try {
            logger.debug("Storing properties: {}", propertiesWithFixedPaths.keySet());
            persister.setMany(propertiesWithFixedPaths);
        } catch (PersisterException e) {
            throw new StateStoreException(e);
        }
    }

    /**
     * Fetches the value byte array, stored against the Property {@code key}, or throws an error if no matching {@code
     * key} is found.
     *
     * @param key must be a non-blank String without any forward slashes ('/')
     * @throws StateStoreException if no data was found for the requested key, or if fetching the data otherwise fails
     * @see StateStore#validateKey(String)
     */
    public byte[] fetchProperty(final String key) throws StateStoreException {
        validateKey(key);
        try {
            final String path = getPropertyPath(namespace, key);
            logger.debug("Fetching property key: {} from path: {}", key, path);
            return persister.get(path);
        } catch (PersisterException e) {
            throw new StateStoreException(e);
        }
    }

    /**
     * Fetches the list of Property keys, or an empty list if none are found.
     *
     * @throws StateStoreException if fetching the list otherwise fails
     */
    public Collection<String> fetchPropertyKeys() throws StateStoreException {
        try {
            return persister.getChildren(PersisterUtils.getServiceNamespacedRootPath(namespace, PROPERTIES_ROOT_NAME));
        } catch (PersisterException e) {
            if (e.getReason() == Reason.NOT_FOUND) {
                // Root path doesn't exist yet. Treat as an empty list of properties. This scenario is
                // expected to commonly occur when the Framework is being run for the first time.
                return Collections.emptyList();
            } else {
                throw new StateStoreException(e);
            }
        }
    }

    /**
     * Clears a given property from the StateStore, or does nothing if no such property exists.
     *
     * @param key must be a non-blank String without any forward slashes ('/')
     * @throws StateStoreException if key validation fails or clearing the entry fails
     */
    public void clearProperty(final String key) throws StateStoreException {
        validateKey(key);
        try {
            final String path = getPropertyPath(namespace, key);
            logger.debug("Removing property key: {} from path: {}", key, path);
            persister.recursiveDelete(path);
        } catch (PersisterException e) {
            if (e.getReason() == Reason.NOT_FOUND) {
                // Clearing a non-existent Property should not result in an exception from us.
                logger.warn("Cleared nonexistent Property, continuing silently: {}", key, e);
            } else {
                throw new StateStoreException(e);
            }
        }
    }

    // Read/Write task metadata

    /**
     * Stores the goal state override status of a particular Task. The {@link Protos.TaskInfo} for this exact task MUST
     * have already been written via {@link #storeTasks(Collection)} beforehand.
     *
     * @throws StateStoreException in the event of a storage error
     */
    public void storeGoalOverrideStatus(String taskName, GoalStateOverride.Status status)
            throws StateStoreException {
        try {
            if (GoalStateOverride.Status.INACTIVE.equals(status)) {
                // Mark inactive state by clearing any override bits.
                persister.recursiveDeleteMany(Arrays.asList(
                        getGoalOverridePath(namespace, taskName),
                        getGoalOverrideStatusPath(namespace, taskName)));
            } else {
                Map<String, byte[]> values = new TreeMap<>();
                values.put(getGoalOverridePath(namespace, taskName),
                        status.target.getSerializedName().getBytes(StandardCharsets.UTF_8));
                values.put(getGoalOverrideStatusPath(namespace, taskName),
                        status.progress.getSerializedName().getBytes(StandardCharsets.UTF_8));
                persister.setMany(values);
            }
        } catch (PersisterException e) {
            throw new StateStoreException(e);
        }
    }

    /**
     * Retrieves the goal state override status of a particular task. A lack of override will result in a
     * {@link GoalStateOverride.Status} with {@code override=NONE} and {@code state=NONE}.
     *
     * @throws StateStoreException in the event of a storage error
     */
    public GoalStateOverride.Status fetchGoalOverrideStatus(String taskName) throws StateStoreException {
        try {
            String goalOverridePath = getGoalOverridePath(namespace, taskName);
            String goalOverrideStatusPath = getGoalOverrideStatusPath(namespace, taskName);
            Map<String, byte[]> values = persister.getMany(Arrays.asList(goalOverridePath, goalOverrideStatusPath));
            byte[] nameBytes = values.get(goalOverridePath);
            byte[] statusBytes = values.get(goalOverrideStatusPath);
            if (nameBytes == null && statusBytes == null) {
                // Cleared override bits => Inactive state
                return GoalStateOverride.Status.INACTIVE;
            } else if (nameBytes == null || statusBytes == null) {
                // This shouldn't happen, but let's just play it safe and assume that the override shouldn't be set.
                logger.error("Task is missing override name or override status. Expected either both or neither: {}",
                        values);
                return GoalStateOverride.Status.INACTIVE;
            }
            return parseOverrideName(taskName, nameBytes).newStatus(parseOverrideProgress(taskName, statusBytes));
        } catch (PersisterException e) {
            throw new StateStoreException(e);
        }
    }

    /**
     * Deletes all data in the state store, but only if it's under a namespace. This is used to clear a namespaced
     * service's data when it's being removed from a multi-service system.
     */
    public void deleteAllDataIfNamespaced() {
        if (namespace.isEmpty()) {
            return; // Not namespaced, no-op
        }
        try {
            // Delete data WITHIN THE NAMESPACE
            persister.recursiveDelete(PersisterUtils.getServiceNamespacedRoot(namespace));
        } catch (PersisterException e) {
            if (e.getReason() == Reason.NOT_FOUND) {
                // Nothing to delete, apparently. Treat as a no-op
            } else {
                throw new StateStoreException(e);
            }
        }
    }

    private GoalStateOverride parseOverrideName(String taskName, byte[] nameBytes) throws StateStoreException {
        String overrideName = new String(nameBytes, StandardCharsets.UTF_8);
        for (GoalStateOverride override : GoalStateOverride.values()) {
            if (override.getSerializedName().equals(overrideName)) {
                return override;
            }
        }
        // The override name isn't recognized. This could happen during a downgrade or similar scenario where a task
        // previously has an override that is no longer recognized by the scheduler. The most reasonable thing in this
        // case would be to fall back to a no-override state.
        logger.warn("Task '{}' has unrecognized override named '{}'. Left over from a recent upgrade/downgrade? "
                + "Falling back to inactive override target.", taskName, overrideName);
        return GoalStateOverride.Status.INACTIVE.target;
    }

    private GoalStateOverride.Progress parseOverrideProgress(String taskName, byte[] progressBytes)
            throws StateStoreException {
        String progressName = new String(progressBytes, StandardCharsets.UTF_8);
        for (GoalStateOverride.Progress state : GoalStateOverride.Progress.values()) {
            if (state.getSerializedName().equals(progressName)) {
                return state;
            }
        }
        // The progress name isn't recognized. This could happen during a downgrade or similar scenario where a task
        // previously has a state that is no longer recognized by the scheduler. The most reasonable thing in this
        // case is to fall back to a no-override state.
        logger.warn("Task '{}' has unrecognized override progress '{}'. Left over from a recent upgrade/downgrade? "
                + "Falling back to inactive override progress.", taskName, progressName);
        return GoalStateOverride.Status.INACTIVE.progress;
    }

    // Internals

    /**
     * @return Services/[namespace]/Tasks/[taskName]/TaskInfo, or Tasks/[taskName]/TaskInfo
     */
    protected static String getTaskInfoPath(String namespace, String taskName) {
        return PersisterUtils.join(getTaskPath(namespace, taskName), TASK_INFO_PATH_NAME);
    }

    /**
     * @return Services/[namespace]/Tasks/[taskName]/TaskStatus, or Tasks/[taskName]/TaskStatus
     */
    protected static String getTaskStatusPath(String namespace, String taskName) {
        return PersisterUtils.join(getTaskPath(namespace, taskName), TASK_STATUS_PATH_NAME);
    }

    /**
     * @return Services/[namespace]/Tasks/[taskName]/Metadata/goal-state-override, or
     *         Tasks/[taskName]/Metadata/goal-state-override
     */
    protected static String getGoalOverridePath(String namespace, String taskName) {
        return PersisterUtils.join(
                PersisterUtils.join(getTaskPath(namespace, taskName), TASK_METADATA_PATH_NAME),
                TASK_GOAL_OVERRIDE_PATH_NAME);
    }

    /**
     * @return Services/[namespace]/Tasks/[taskName]/Metadata/override-status, or
     *         Tasks/[taskName]/Metadata/override-status
     */
    protected static String getGoalOverrideStatusPath(String namespace, String taskName) {
        return PersisterUtils.join(
                PersisterUtils.join(getTaskPath(namespace, taskName), TASK_METADATA_PATH_NAME),
                TASK_GOAL_OVERRIDE_STATUS_PATH_NAME);
    }

    /**
     * @return Services/[namespace]/Tasks/[taskName], or Tasks/[taskName]
     */
    protected static String getTaskPath(String namespace, String taskName) {
        return PersisterUtils.join(PersisterUtils.getServiceNamespacedRootPath(namespace, TASKS_ROOT_NAME), taskName);
    }

    /**
     * @return Services/[namespace]/Properties/[propertyName], or Properties/[propertyName]
     */
    protected static String getPropertyPath(String namespace, String propertyName) {
        return PersisterUtils.join(
                PersisterUtils.getServiceNamespacedRootPath(namespace, PROPERTIES_ROOT_NAME), propertyName);
    }

    private static void validateKey(String key) throws StateStoreException {
        if (StringUtils.isBlank(key)) {
            throw new StateStoreException(Reason.LOGIC_ERROR, "Key cannot be blank or null");
        }
        if (key.contains("/")) {
            throw new StateStoreException(Reason.LOGIC_ERROR, "Key cannot contain '/'");
        }
    }

    private static void validateValue(byte[] value) throws StateStoreException {
        if (value == null) {
            throw new StateStoreException(Reason.LOGIC_ERROR, "Property value must not be null.");
        }
        if (value.length > MAX_VALUE_LENGTH_BYTES) {
            throw new StateStoreException(Reason.LOGIC_ERROR, String.format(
                    "Property value length %d exceeds limit of %d bytes.",
                    value.length, MAX_VALUE_LENGTH_BYTES));
        }
    }
}
