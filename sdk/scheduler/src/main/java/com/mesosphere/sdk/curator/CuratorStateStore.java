package com.mesosphere.sdk.curator;

import com.google.common.annotations.VisibleForTesting;
import com.mesosphere.sdk.dcos.DcosConstants;
import com.mesosphere.sdk.offer.CommonTaskUtils;
import com.mesosphere.sdk.offer.TaskException;
import com.mesosphere.sdk.state.*;
import com.mesosphere.sdk.storage.StorageError.Reason;

import org.apache.curator.RetryPolicy;
import org.apache.mesos.Protos;
import org.apache.zookeeper.KeeperException;

import java.io.IOException;
import java.util.*;

/**
 * CuratorStateStore is an implementation of {@link StateStore} which persists data in Zookeeper.
 *
 * The ZNode structure in Zookeeper is as follows:
 * <code>
 * rootPath/
 *     -> FrameworkID
 *     -> Tasks/
 *         -> [TaskName-0]/
 *             -> TaskInfo
 *             -> TaskStatus
 *         -> [TaskName-1]/
 *             -> TaskInfo
 *         -> [TaskName-2]/
 *             -> TaskInfo
 *             -> TaskStatus
 *         -> ...
 * </code>
 *
 * Note that for frameworks which don't use custom executors, the same structure is used, except
 * where ExecutorName values are equal to TaskName values.
 */
public class CuratorStateStore extends CuratorStateStoreReadOnly implements StateStore {

    /**
     * Creates a new {@link StateStore} which uses Curator with a default {@link RetryPolicy} and
     * connection string.
     *
     * @param frameworkName    The name of the framework
     */
    public CuratorStateStore(String frameworkName) {
        this(frameworkName, DcosConstants.MESOS_MASTER_ZK_CONNECTION_STRING);
    }

    /**
     * Creates a new {@link StateStore} which uses Curator with a default {@link RetryPolicy}.
     *
     * @param frameworkName    The name of the framework
     * @param connectionString The host/port of the ZK server, eg "master.mesos:2181"
     */
    public CuratorStateStore(String frameworkName, String connectionString) {
        this(frameworkName, connectionString, CuratorUtils.getDefaultRetry(), "", "");
    }

    public CuratorStateStore(
            String frameworkName,
            String connectionString,
            RetryPolicy retryPolicy) {
        this(frameworkName, connectionString, retryPolicy, "", "");
    }

    public CuratorStateStore(
            String frameworkName,
            String connectionString,
            String username,
            String password) {
        this(frameworkName, connectionString, CuratorUtils.getDefaultRetry(), username, password);
    }

    /**
     * Creates a new {@link StateStore} which uses Curator with a custom {@link RetryPolicy}.
     *
     * @param frameworkName    The name of the framework
     * @param connectionString The host/port of the ZK server, eg "master.mesos:2181"
     * @param retryPolicy      The custom {@link RetryPolicy}
     */
    public CuratorStateStore(
            String frameworkName,
            String connectionString,
            RetryPolicy retryPolicy,
            String username,
            String password) {
        super(frameworkName, connectionString, retryPolicy, username, password);
    }

    // Framework ID

    @Override
    public void storeFrameworkId(Protos.FrameworkID fwkId) throws StateStoreException {
        try {
            logger.debug("Storing FrameworkID in '{}'", fwkIdPath);
            curator.set(fwkIdPath, fwkId.toByteArray());
        } catch (Exception e) {
            throw new StateStoreException(Reason.STORAGE_ERROR, String.format(
                    "Failed to store FrameworkID in '%s'", fwkIdPath), e);
        }
    }

    @Override
    public void clearFrameworkId() throws StateStoreException {
        try {
            logger.debug("Clearing FrameworkID at '{}'", fwkIdPath);
            curator.delete(fwkIdPath);
        } catch (KeeperException.NoNodeException e) {
            // Clearing a non-existent FrameworkID should not result in an exception from us.
            logger.warn("Cleared unset FrameworkID, continuing silently", e);
            return;
        } catch (Exception e) {
            throw new StateStoreException(Reason.STORAGE_ERROR, e);
        }
    }

    // Write Tasks

    @Override
    public void storeTasks(Collection<Protos.TaskInfo> tasks) throws StateStoreException {
        Map<String, byte[]> taskBytesMap = new HashMap<>();
        for (Protos.TaskInfo taskInfo : tasks) {
            String path = taskPathMapper.getTaskInfoPath(taskInfo.getName());
            logger.debug("Storing Taskinfo for {} in '{}'", taskInfo.getName(), path);
            taskBytesMap.put(path, taskInfo.toByteArray());
        }
        try {
            curator.setMany(taskBytesMap);
        } catch (Exception e) {
            throw new StateStoreException(Reason.STORAGE_ERROR, String.format(
                    "Failed to store %d TaskInfos", tasks.size()), e);
        }
    }

    @Override
    public void storeStatus(Protos.TaskStatus status) throws StateStoreException {
        String taskName;
        try {
            taskName = CommonTaskUtils.toTaskName(status.getTaskId());
        } catch (TaskException e) {
            throw new StateStoreException(Reason.LOGIC_ERROR, String.format(
                    "Failed to parse the Task Name from TaskStatus.task_id: '%s'", status), e);
        }

        // Validate that a TaskInfo with the exact same UUID is currently present. We intentionally
        // ignore TaskStatuses whose TaskID doesn't (exactly) match the current TaskInfo: We will
        // occasionally get these for stale tasks that have since been changed (with new UUIDs).
        Optional<Protos.TaskInfo> optionalTaskInfo;
        try {
            optionalTaskInfo = fetchTask(taskName);
        } catch (Exception e) {
            throw new StateStoreException(Reason.LOGIC_ERROR, String.format(
                    "Unable to retrieve matching TaskInfo for the provided TaskStatus name %s.", taskName), e);
        }

        if (!optionalTaskInfo.isPresent()) {
            throw new StateStoreException(Reason.LOGIC_ERROR,
                    String.format("The following TaskInfo is not present in the StateStore: %s. " +
                            "TaskInfo must be present in order to store a TaskStatus.", taskName));
        }

        if (!optionalTaskInfo.get().getTaskId().getValue().equals(status.getTaskId().getValue())) {
            throw new StateStoreException(Reason.LOGIC_ERROR, String.format(
                    "Task ID '%s' of updated status doesn't match Task ID '%s' of current TaskInfo."
                            + " Task IDs must exactly match before status may be updated."
                            + " NewTaskStatus[%s] CurrentTaskInfo[%s]",
                    status.getTaskId().getValue(), optionalTaskInfo.get().getTaskId().getValue(),
                    status, optionalTaskInfo));
        }

        Optional<Protos.TaskStatus> currentStatusOptional = fetchStatus(taskName);

        if (currentStatusOptional.isPresent()
                && status.getState().equals(Protos.TaskState.TASK_LOST)
                && CommonTaskUtils.isTerminal(currentStatusOptional.get())) {
            throw new StateStoreException(Reason.LOGIC_ERROR,
                    String.format("Ignoring TASK_LOST for Task already in a terminal state %s: %s",
                            currentStatusOptional.get().getState(), taskName));
        }

        String path = taskPathMapper.getTaskStatusPath(taskName);
        logger.info("Storing status '{}' for '{}' in '{}'", status.getState(), taskName, path);

        try {
            curator.set(path, status.toByteArray());
        } catch (Exception e) {
            throw new StateStoreException(Reason.STORAGE_ERROR, e);
        }
    }

    @Override
    public void clearTask(String taskName) throws StateStoreException {
        String path = taskPathMapper.getTaskPath(taskName);
        logger.debug("Clearing Task at '{}'", path);
        try {
            curator.delete(path);
        } catch (KeeperException.NoNodeException e) {
            // Clearing a non-existent Task should not result in an exception from us.
            logger.warn("Cleared nonexistent Task, continuing silently: {}", taskName, e);
            return;
        } catch (Exception e) {
            throw new StateStoreException(Reason.STORAGE_ERROR, e);
        }
    }

    @Override
    public void storeProperty(final String key, final byte[] value) throws StateStoreException {
        StateStoreUtils.validateKey(key);
        StateStoreUtils.validateValue(value);
        try {
            final String path = CuratorUtils.join(this.propertiesPath, key);
            logger.debug("Storing property key: {} into path: {}", key, path);
            curator.set(path, value);
        } catch (Exception e) {
            throw new StateStoreException(Reason.STORAGE_ERROR, e);
        }
    }


    @Override
    public void clearProperty(final String key) throws StateStoreException {
        StateStoreUtils.validateKey(key);
        try {
            final String path = CuratorUtils.join(this.propertiesPath, key);
            logger.debug("Removing property key: {} from path: {}", key, path);
            curator.delete(path);
        } catch (KeeperException.NoNodeException e) {
            // Clearing a non-existent Property should not result in an exception from us.
            logger.warn("Cleared nonexistent Property, continuing silently: {}", key, e);
            return;
        } catch (Exception e) {
            throw new StateStoreException(Reason.STORAGE_ERROR, e);
        }
    }

    @VisibleForTesting
    public void closeForTesting() {
        curator.close();
    }

    // Internals

    public void setSuppressed(final boolean isSuppressed) {
        byte[] bytes;
        Serializer serializer = new JsonSerializer();

        try {
            bytes = serializer.serialize(isSuppressed);
        } catch (IOException e) {
            logger.error(String.format("Error serializing property %s: %s", SUPPRESSED_KEY, isSuppressed), e);
            throw new StateStoreException(Reason.SERIALIZATION_ERROR, e);
        }

        storeProperty(SUPPRESSED_KEY, bytes);
    }
}
