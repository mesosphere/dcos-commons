package com.mesosphere.sdk.state;

import com.google.protobuf.InvalidProtocolBufferException;
import com.mesosphere.sdk.offer.TaskUtils;
import com.mesosphere.sdk.offer.taskdata.TaskPackingUtils;
import com.mesosphere.sdk.storage.Persister;
import com.mesosphere.sdk.storage.PersisterException;
import com.mesosphere.sdk.storage.PersisterUtils;
import com.mesosphere.sdk.storage.StorageError.Reason;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

import org.apache.mesos.Protos;
import org.apache.mesos.Protos.TaskInfo;
import org.apache.mesos.Protos.TaskState;
import org.apache.mesos.Protos.TaskStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;

/**
 * An implementation of {@link StateStore} which relies on the provided {@link Persister} for data persistence.
 *
 * <p>The ZNode structure in Zookeeper is as follows:
 * <br>rootPath/
 * <br>&nbsp;-> FrameworkID
 * <br>&nbsp;-> Tasks/
 * <br>&nbsp;&nbsp;-> [TaskName-0]/
 * <br>&nbsp;&nbsp;&nbsp;-> TaskInfo
 * <br>&nbsp;&nbsp;&nbsp;-> TaskStatus
 * <br>&nbsp;&nbsp;-> [TaskName-1]/
 * <br>&nbsp;&nbsp;&nbsp;-> TaskInfo
 * <br>&nbsp;&nbsp;-> [TaskName-2]/
 * <br>&nbsp;&nbsp;&nbsp;-> TaskInfo
 * <br>&nbsp;&nbsp;&nbsp;-> TaskStatus
 * <br>&nbsp;&nbsp;-> ...
 */
public class DefaultStateStore implements StateStore {

    private static final Logger logger = LoggerFactory.getLogger(DefaultStateStore.class);

    /**
     * @see DefaultSchemaVersionStore#CURRENT_SCHEMA_VERSION
     */
    private static final int MIN_SUPPORTED_SCHEMA_VERSION = 1;
    private static final int MAX_SUPPORTED_SCHEMA_VERSION = 1;

    private static final String TASK_INFO_PATH_NAME = "TaskInfo";
    private static final String TASK_STATUS_PATH_NAME = "TaskStatus";
    private static final String FWK_ID_PATH_NAME = "FrameworkID";
    private static final String PROPERTIES_PATH_NAME = "Properties";
    private static final String TASKS_ROOT_NAME = "Tasks";
    public static final String LOCK_PATH_NAME = "lock";

    protected final Persister persister;

    /**
     * Creates a new {@link StateStore} which uses the provided {@link Persister} to access state data.
     *
     * @param persister The persister which holds the state data
     */
    public DefaultStateStore(Persister persister) {
        this.persister = persister;

        // Check version up-front:
        int currentVersion = new DefaultSchemaVersionStore(persister).fetch();
        if (!SchemaVersionStore.isSupported(
                currentVersion, MIN_SUPPORTED_SCHEMA_VERSION, MAX_SUPPORTED_SCHEMA_VERSION)) {
            throw new IllegalStateException(String.format(
                    "Storage schema version %d is not supported by this software " +
                            "(support: min=%d, max=%d)",
                    currentVersion, MIN_SUPPORTED_SCHEMA_VERSION, MAX_SUPPORTED_SCHEMA_VERSION));
        }

        repairStateStore();
    }

    // Framework ID

    @Override
    public void storeFrameworkId(Protos.FrameworkID fwkId) throws StateStoreException {
        try {
            persister.set(FWK_ID_PATH_NAME, fwkId.toByteArray());
        } catch (PersisterException e) {
            throw new StateStoreException(e, "Failed to store FrameworkID");
        }
    }

    @Override
    public void clearFrameworkId() throws StateStoreException {
        try {
            persister.deleteAll(FWK_ID_PATH_NAME);
        } catch (PersisterException e) {
            if (e.getReason() == Reason.NOT_FOUND) {
                // Clearing a non-existent FrameworkID should not result in an exception from us.
                logger.warn("Cleared unset FrameworkID, continuing silently", e);
            } else {
                throw new StateStoreException(e);
            }
        }
    }

    @Override
    public Optional<Protos.FrameworkID> fetchFrameworkId() throws StateStoreException {
        try {
            byte[] bytes = persister.get(FWK_ID_PATH_NAME);
            if (bytes.length > 0) {
                return Optional.of(Protos.FrameworkID.parseFrom(bytes));
            } else {
                throw new StateStoreException(Reason.SERIALIZATION_ERROR, String.format(
                        "Empty FrameworkID in '%s'", FWK_ID_PATH_NAME));
            }
        } catch (PersisterException e) {
            if (e.getReason() == Reason.NOT_FOUND) {
                logger.warn("No FrameworkId found at: {}", FWK_ID_PATH_NAME);
                return Optional.empty();
            } else {
                throw new StateStoreException(e);
            }
        } catch (InvalidProtocolBufferException e) {
            throw new StateStoreException(Reason.SERIALIZATION_ERROR, e);
        }
    }

    // Write Tasks

    @Override
    public void storeTasks(Collection<Protos.TaskInfo> tasks) throws StateStoreException {
        Map<String, byte[]> taskBytesMap = new HashMap<>();
        for (Protos.TaskInfo taskInfo : tasks) {
            taskBytesMap.put(getTaskInfoPath(taskInfo.getName()), taskInfo.toByteArray());
        }
        try {
            persister.setMany(taskBytesMap);
        } catch (PersisterException e) {
            throw new StateStoreException(e, String.format("Failed to store %d TaskInfos", tasks.size()));
        }
    }

    @Override
    public void storeStatus(Protos.TaskStatus status) throws StateStoreException {
        Optional<Protos.TaskInfo> taskInfoOptional = Optional.empty();

        for (Protos.TaskInfo taskInfo : fetchTasks()) {
            if (taskInfo.getTaskId().getValue().equals(status.getTaskId().getValue())) {
                if (taskInfoOptional.isPresent()) {
                    logger.error("Found duplicate taskIDs in Task {} and  Task {}",
                            taskInfoOptional.get(), taskInfo.getName());
                    throw new StateStoreException(Reason.LOGIC_ERROR, String.format(
                            "There are more than one tasks with TaskID: %s", status));
                }
                taskInfoOptional = Optional.of(taskInfo);
            }
        }

        if (!taskInfoOptional.isPresent()) {
            throw new StateStoreException(Reason.NOT_FOUND, String.format(
                    "Failed to find a task with TaskID: %s", status));
        }

        String taskName = taskInfoOptional.get().getName();
        Optional<Protos.TaskStatus> currentStatusOptional = fetchStatus(taskName);

        if (currentStatusOptional.isPresent()
                && status.getState().equals(Protos.TaskState.TASK_LOST)
                && TaskUtils.isTerminal(currentStatusOptional.get())) {
            throw new StateStoreException(Reason.LOGIC_ERROR,
                    String.format("Ignoring TASK_LOST for Task already in a terminal state %s: %s",
                            currentStatusOptional.get().getState(), taskName));
        }

        String path = getTaskStatusPath(taskName);
        logger.info("Storing status '{}' for '{}' in '{}'", status.getState(), taskName, path);

        try {
            persister.set(path, status.toByteArray());
        } catch (PersisterException e) {
            throw new StateStoreException(e);
        }
    }

    @Override
    public void clearTask(String taskName) throws StateStoreException {
        try {
            persister.deleteAll(getTaskPath(taskName));
        } catch (PersisterException e) {
            if (e.getReason() == Reason.NOT_FOUND) {
                // Clearing a non-existent Task should not result in an exception from us.
                logger.warn("Cleared nonexistent Task, continuing silently: {}", taskName, e);
            } else {
                throw new StateStoreException(e);
            }
        }
    }

    @Override
    public void clearAllData() throws StateStoreException {
        try {
            persister.deleteAll(PersisterUtils.PATH_DELIM);
        } catch (PersisterException e) {
            if (e.getReason() == Reason.NOT_FOUND) {
                // Nothing to delete, apparently. Treat as a no-op
            } else {
                throw new StateStoreException(e);
            }
        }
    }

    // Read Tasks

    @Override
    public Collection<String> fetchTaskNames() throws StateStoreException {
        try {
            Collection<String> taskNames = new ArrayList<>();
            taskNames.addAll(persister.getChildren(TASKS_ROOT_NAME));
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

    @Override
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

    @Override
    public Optional<Protos.TaskInfo> fetchTask(String taskName) throws StateStoreException {
        String path = getTaskInfoPath(taskName);
        try {
            byte[] bytes = persister.get(path);
            if (bytes.length > 0) {
                // TODO(nick): This unpack operation is no longer needed, but it doesn't hurt anything to leave it in
                // place to support reading older data. Remove this unpack call after services have had time to stop
                // storing packed TaskInfos in zk (after June 2017 or so?).
                return Optional.of(TaskPackingUtils.unpack(Protos.TaskInfo.parseFrom(bytes)));
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

    @Override
    public Collection<Protos.TaskStatus> fetchStatuses() throws StateStoreException {
        Collection<Protos.TaskStatus> taskStatuses = new ArrayList<>();
        for (String taskName : fetchTaskNames()) {
            try {
                byte[] bytes = persister.get(getTaskStatusPath(taskName));
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

    @Override
    public Optional<Protos.TaskStatus> fetchStatus(String taskName) throws StateStoreException {
        String path = getTaskStatusPath(taskName);
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

    @Override
    public void storeProperty(final String key, final byte[] value) throws StateStoreException {
        StateStoreUtils.validateKey(key);
        StateStoreUtils.validateValue(value);
        try {
            final String path = PersisterUtils.join(PROPERTIES_PATH_NAME, key);
            logger.debug("Storing property key: {} into path: {}", key, path);
            persister.set(path, value);
        } catch (PersisterException e) {
            throw new StateStoreException(e);
        }
    }

    @Override
    public byte[] fetchProperty(final String key) throws StateStoreException {
        StateStoreUtils.validateKey(key);
        try {
            final String path = PersisterUtils.join(PROPERTIES_PATH_NAME, key);
            logger.debug("Fetching property key: {} from path: {}", key, path);
            return persister.get(path);
        } catch (PersisterException e) {
            throw new StateStoreException(e);
        }
    }

    @Override
    public Collection<String> fetchPropertyKeys() throws StateStoreException {
        try {
            return persister.getChildren(PROPERTIES_PATH_NAME);
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

    @Override
    public void clearProperty(final String key) throws StateStoreException {
        StateStoreUtils.validateKey(key);
        try {
            final String path = PersisterUtils.join(PROPERTIES_PATH_NAME, key);
            logger.debug("Removing property key: {} from path: {}", key, path);
            persister.deleteAll(path);
        } catch (PersisterException e) {
            if (e.getReason() == Reason.NOT_FOUND) {
                // Clearing a non-existent Property should not result in an exception from us.
                logger.warn("Cleared nonexistent Property, continuing silently: {}", key, e);
            } else {
                throw new StateStoreException(e);
            }
        }
    }

    /**
     * Returns the underlying {@link Persister} object for direct access.
     * @return
     */
    public Persister getPersister() {
        return persister;
    }

    // Internals

    protected static String getTaskInfoPath(String taskName) {
        return PersisterUtils.join(getTaskPath(taskName), TASK_INFO_PATH_NAME);
    }

    protected static String getTaskStatusPath(String taskName) {
        return PersisterUtils.join(getTaskPath(taskName), TASK_STATUS_PATH_NAME);
    }

    protected static String getTaskPath(String taskName) {
        return PersisterUtils.join(TASKS_ROOT_NAME, taskName);
    }

    /**
     * TaskInfo and TaskStatus objects referring to the same Task name are not written to Zookeeper atomically.
     * It is therefore possible for the TaskIDs contained within these elements to become out of sync.  While
     * the scheduler process is up they remain in sync.  This method guarantees produces an initial synchronized
     * state.
     */
    @SuppressFBWarnings("UC_USELESS_OBJECT")
    private void repairStateStore() {
        // Findbugs thinks this isn't used, but it is used in the forEach call at the bottom of this method.
        List<TaskStatus> repairedStatuses = new ArrayList<>();
        List<TaskInfo> repairedTasks = new ArrayList<>();

        for (TaskInfo task : fetchTasks()) {
            Optional<TaskStatus> statusOptional = fetchStatus(task.getName());

            if (statusOptional.isPresent()) {
                TaskStatus status = statusOptional.get();
                if (!status.getTaskId().equals(task.getTaskId())) {
                    logger.warn("Found StateStore status inconsistency: task.taskId={}, taskStatus.taskId={}",
                            task.getTaskId(), status.getTaskId());
                    repairedTasks.add(task.toBuilder().setTaskId(status.getTaskId()).build());
                    repairedStatuses.add(status.toBuilder().setState(TaskState.TASK_FAILED).build());
                }
            } else {
                logger.warn("Found StateStore status inconsistency: task.taskId={}", task.getTaskId());
                TaskStatus status = TaskStatus.newBuilder()
                        .setTaskId(task.getTaskId())
                        .setState(TaskState.TASK_FAILED)
                        .setMessage("Assuming failure for inconsistent TaskIDs")
                        .build();
                repairedStatuses.add(status);
            }
        }

        storeTasks(repairedTasks);
        repairedStatuses = repairedStatuses.stream()
                .filter(status -> !status.getTaskId().getValue().equals(""))
                .collect(Collectors.toList());
        repairedStatuses.forEach(taskStatus -> storeStatus(taskStatus));
    }
}
