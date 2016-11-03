package org.apache.mesos.curator;

import org.apache.curator.RetryPolicy;
import org.apache.curator.retry.ExponentialBackoffRetry;
import org.apache.mesos.Protos;
import org.apache.mesos.dcos.DcosConstants;
import org.apache.mesos.offer.TaskException;
import org.apache.mesos.offer.TaskUtils;
import org.apache.mesos.state.*;
import org.apache.mesos.storage.Persister;
import org.apache.zookeeper.KeeperException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.annotations.VisibleForTesting;

import java.io.IOException;
import java.util.*;

/**
 * CuratorStateStore is an implementation of {@link StateStore} which persists data in Zookeeper.
 *
 * The ZNode structure in Zookeeper is as follows:
 * <code>
 * rootPath/
 *     -> FrameworkID
 *     -> Pods/
 *         -> [PodName-0]/
 *             -> [TaskName-0]/
 *                -> TaskInfo
 *                -> TaskStatus
 *             -> [TaskName-1]/
 *                -> TaskInfo
 *             -> [TaskName-2]/
 *                -> TaskInfo
 *                -> TaskStatus
 *         -> [PodName-1]/
 *             ...
 *         ...
 * </code>
 *
 * Note that for frameworks which don't use custom executors, the same structure is used, except
 * where ExecutorName values are equal to TaskName values.
 */
public class CuratorStateStore implements StateStore {

    private static final Logger logger = LoggerFactory.getLogger(CuratorStateStore.class);

    /**
     * @see CuratorSchemaVersionStore#CURRENT_SCHEMA_VERSION
     */
    private static final int MIN_SUPPORTED_SCHEMA_VERSION = 1;
    private static final int MAX_SUPPORTED_SCHEMA_VERSION = 1;

    private static final String TASK_INFO_PATH_NAME = "TaskInfo";
    private static final String TASK_STATUS_PATH_NAME = "TaskStatus";
    private static final String FWK_ID_PATH_NAME = "FrameworkID";
    private static final String PROPERTIES_PATH_NAME = "Properties";
    private static final String PODS_ROOT_NAME = "Pods";
    private static final String SUPPRESSED_KEY = "suppressed";

    private final Persister curator;
    private final PodPathMapper podPathMapper;
    private final String fwkIdPath;
    private final String propertiesPath;

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
        this(frameworkName, connectionString, new ExponentialBackoffRetry(
                CuratorUtils.DEFAULT_CURATOR_POLL_DELAY_MS,
                CuratorUtils.DEFAULT_CURATOR_MAX_RETRIES));
    }

    /**
     * Creates a new {@link StateStore} which uses Curator with a custom {@link RetryPolicy}.
     *
     * @param frameworkName    The name of the framework
     * @param connectionString The host/port of the ZK server, eg "master.mesos:2181"
     * @param retryPolicy      The custom {@link RetryPolicy}
     */
    public CuratorStateStore(
            String frameworkName, String connectionString, RetryPolicy retryPolicy) {
        this.curator = new CuratorPersister(connectionString, retryPolicy);

        // Check version up-front:
        int currentVersion = new CuratorSchemaVersionStore(curator, frameworkName).fetch();
        if (!SchemaVersionStore.isSupported(
                currentVersion, MIN_SUPPORTED_SCHEMA_VERSION, MAX_SUPPORTED_SCHEMA_VERSION)) {
            throw new IllegalStateException(String.format(
                    "Storage schema version %d is not supported by this software " +
                            "(support: min=%d, max=%d)",
                    currentVersion, MIN_SUPPORTED_SCHEMA_VERSION, MAX_SUPPORTED_SCHEMA_VERSION));
        }

        final String rootPath = CuratorUtils.toServiceRootPath(frameworkName);
        this.podPathMapper = new PodPathMapper(rootPath);
        this.fwkIdPath = CuratorUtils.join(rootPath, FWK_ID_PATH_NAME);
        this.propertiesPath = CuratorUtils.join(rootPath, PROPERTIES_PATH_NAME);
    }

    // Framework ID

    @Override
    public void storeFrameworkId(Protos.FrameworkID fwkId) throws StateStoreException {
        try {
            logger.debug("Storing FrameworkID in '{}'", fwkIdPath);
            curator.set(fwkIdPath, fwkId.toByteArray());
        } catch (Exception e) {
            throw new StateStoreException(String.format(
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
            throw new StateStoreException(e);
        }
    }

    @Override
    public Optional<Protos.FrameworkID> fetchFrameworkId() throws StateStoreException {
        try {
            logger.debug("Fetching FrameworkID from '{}'", fwkIdPath);
            byte[] bytes = curator.get(fwkIdPath);
            if (bytes.length > 0) {
                return Optional.of(Protos.FrameworkID.parseFrom(bytes));
            } else {
                throw new StateStoreException(String.format(
                        "Failed to retrieve FrameworkID in '%s'", fwkIdPath));
            }
        } catch (KeeperException.NoNodeException e) {
            logger.warn("No FrameworkId found at: " + fwkIdPath);
            return Optional.empty();
        } catch (Exception e) {
            throw new StateStoreException(e);
        }
    }

    // Write Tasks

    @Override
    public void storeTasks(String podName, Collection<Protos.TaskInfo> tasks) throws StateStoreException {
        Map<String, byte[]> taskBytesMap = new HashMap<>();
        for (Protos.TaskInfo taskInfo : tasks) {
            String taskPath = podPathMapper.getTaskPath(podName, taskInfo.getName());
            String taskInfoPath = podPathMapper.getTaskInfoPath(taskPath);
            logger.debug("Storing Taskinfo for {} in '{}'", taskInfo.getName(), taskInfoPath);
            taskBytesMap.put(taskInfoPath, taskInfo.toByteArray());
        }
        try {
            curator.setMany(taskBytesMap);
        } catch (Exception e) {
            throw new StateStoreException(String.format(
                    "Failed to store %d TaskInfos", tasks.size()), e);
        }
    }

    @Override
    public void storeStatus(String podName, Protos.TaskStatus status) throws StateStoreException {
        String taskName;
        try {
            taskName = TaskUtils.toTaskName(status.getTaskId());
        } catch (TaskException e) {
            throw new StateStoreException(String.format(
                    "Failed to parse the Task Name from TaskStatus.task_id: '%s'", status), e);
        }

        // Validate that a TaskInfo with the exact same UUID is currently present. We intentionally
        // ignore TaskStatuses whose TaskID doesn't (exactly) match the current TaskInfo: We will
        // occasionally get these for stale tasks that have since been changed (with new UUIDs).
        Optional<Protos.TaskInfo> optionalTaskInfo;
        try {
            optionalTaskInfo = fetchTask(podName, taskName);
        } catch (Exception e) {
            throw new StateStoreException(String.format(
                    "Unable to retrieve matching TaskInfo for the provided TaskStatus name %s.", taskName), e);
        }

        if (!optionalTaskInfo.isPresent()) {
            throw new StateStoreException(
                    String.format("The following TaskInfo is not present in the StateStore: %s. " +
                            "TaskInfo must be present in order to store a TaskStatus.", taskName));
        }

        if (!optionalTaskInfo.get().getTaskId().getValue().equals(status.getTaskId().getValue())) {
            throw new StateStoreException(String.format(
                    "Task ID '%s' of updated status doesn't match Task ID '%s' of current TaskInfo."
                            + " Task IDs must exactly match before status may be updated."
                            + " NewTaskStatus[%s] CurrentTaskInfo[%s]",
                    status.getTaskId().getValue(), optionalTaskInfo.get().getTaskId().getValue(),
                    status, optionalTaskInfo));
        }

        String taskPath = podPathMapper.getTaskPath(podName, taskName);
        String taskStatusPath = podPathMapper.getTaskStatusPath(taskPath);
        logger.debug("Storing status for '{}' in '{}'", taskName, taskStatusPath);

        try {
            curator.set(taskStatusPath, status.toByteArray());
        } catch (Exception e) {
            throw new StateStoreException(e);
        }
    }

    @Override
    public void clearTask(String podName, String taskName) throws StateStoreException {
        String path = podPathMapper.getTaskPath(podName, taskName);
        logger.debug("Clearing Task at '{}'", path);
        try {
            curator.delete(path);
        } catch (KeeperException.NoNodeException e) {
            // Clearing a non-existent Task should not result in an exception from us.
            logger.warn("Cleared nonexistent Task, continuing silently: {}", taskName, e);
            return;
        } catch (Exception e) {
            throw new StateStoreException(e);
        }
    }

    // Read Tasks


    @Override
    public Optional<Collection<Protos.TaskInfo>> fetchPod(String podName) throws StateStoreException {
        logger.debug("Fetching pod '{}'", podName);
        try {
            Optional<Protos.TaskInfo> taskInfoOptional;
            Collection<Protos.TaskInfo> taskInfos = new ArrayList<>();
            for (String taskName : fetchTaskNames(podName)) {
                taskInfoOptional = fetchTask(podName, taskName);
                // If we're supposed to have a task but we don't, the pod is incomplete and we return an empty optional
                if (!taskInfoOptional.isPresent()) {
                    return Optional.empty();
                } else {
                    taskInfos.add(taskInfoOptional.get());
                }
            }
            return Optional.of(taskInfos);
        } catch (Exception e) {
            throw new StateStoreException(e);
        }
    }

    @Override
    public Collection<String> fetchTaskNames(String podName) throws StateStoreException {
        String podPath = podPathMapper.getPodPath(podName);
        logger.debug("Fetching task names for pod '{}' from '{}'", podName, podPath);
        try {
            Collection<String> taskNames = new ArrayList<>();
            for (String taskChildName : curator.getChildren(podPath)) {
                taskNames.add(taskChildName);
            }
            return taskNames;
        } catch (KeeperException.NoNodeException e) {
            // Root path doesn't exist yet. Treat as an empty list of tasks. This scenario is
            // expected to commonly occur when the Framework is being run for the first time.
            return Collections.emptyList();
        } catch (Exception e) {
            throw new StateStoreException(e);
        }
    }

    @Override
    public Collection<String> fetchTaskNames() throws StateStoreException {
        String path = podPathMapper.getPodsRootPath();
        logger.debug("Fetching task names from '{}'", path);
        try {
            Collection<String> taskNames = new ArrayList<>();
            for (String podChildNode : curator.getChildren(path)) {
                for (String taskChildNode : curator.getChildren(String.join("/", path, podChildNode))) {
                    taskNames.add(taskChildNode);
                }
            }
            return taskNames;
        } catch (KeeperException.NoNodeException e) {
            // Root path doesn't exist yet. Treat as an empty list of tasks. This scenario is
            // expected to commonly occur when the Framework is being run for the first time.
            return Collections.emptyList();
        } catch (Exception e) {
            throw new StateStoreException(e);
        }
    }

    private Collection<String> fetchTaskPaths() throws StateStoreException {
        String path = podPathMapper.getPodsRootPath();
        logger.debug("Fetching task names from '{}'", path);
        try {
            Collection<String> taskPaths = new ArrayList<>();
            for (String podChildNode : curator.getChildren(path)) {
                for (String taskChildNode : curator.getChildren(String.join("/", path, podChildNode))) {
                    taskPaths.add(String.join("/", path, podChildNode, taskChildNode);
                }
            }
            return taskPaths;
        } catch (KeeperException.NoNodeException e) {
            // Root path doesn't exist yet. Treat as an empty list of tasks. This scenario is
            // expected to commonly occur when the Framework is being run for the first time.
            return Collections.emptyList();
        } catch (Exception e) {
            throw new StateStoreException(e);
        }

    }

    @Override
    public Collection<Protos.TaskInfo> fetchTasks() throws StateStoreException {
        Collection<Protos.TaskInfo> taskInfos = new ArrayList<>();
        for (String taskPath : fetchTaskPaths()) {
            try {
                byte[] bytes = curator.get(podPathMapper.getTaskInfoPath(taskPath));
                taskInfos.add(Protos.TaskInfo.parseFrom(bytes));
            } catch (Exception e) {
                // Throw even for NoNodeException: We should always have a TaskInfo for every entry
                throw new StateStoreException(e);
            }
        }
        return taskInfos;
    }

    @Override
    public Optional<Protos.TaskInfo> fetchTask(String podName, String taskName) throws StateStoreException {
        String taskPath = podPathMapper.getTaskPath(podName, taskName);
        String taskInfoPath = podPathMapper.getTaskInfoPath(taskName);
        logger.debug("Fetching TaskInfo {} from '{}'", taskName, taskInfoPath);
        try {
            byte[] bytes = curator.get(taskInfoPath);
            if (bytes.length > 0) {
                return Optional.of(Protos.TaskInfo.parseFrom(bytes));
            } else {
                throw new StateStoreException(String.format(
                        "Failed to retrieve TaskInfo for TaskName: %s", taskName));
            }
        } catch (KeeperException.NoNodeException e) {
            logger.warn("No TaskInfo found for the requested name: " + taskName + " at: " + taskInfoPath);
            return Optional.empty();
        } catch (Exception e) {
            throw new StateStoreException(e);
        }
    }

    @Override
    public Collection<Protos.TaskStatus> fetchStatuses() throws StateStoreException {
        Collection<Protos.TaskStatus> taskStatuses = new ArrayList<>();
        for (String taskPath : fetchTaskPaths()) {
            try {
                byte[] bytes = curator.get(podPathMapper.getTaskStatusPath(taskPath));
                taskStatuses.add(Protos.TaskStatus.parseFrom(bytes));
            } catch (KeeperException.NoNodeException e) {
                // The task node exists, but it doesn't contain a TaskStatus node. This may occur if
                // the only contents are a TaskInfo.
                continue;
            } catch (Exception e) {
                throw new StateStoreException(e);
            }
        }
        return taskStatuses;
    }

    @Override
    public Optional<Protos.TaskStatus> fetchStatus(String podName, String taskName) throws StateStoreException {
        String taskPath = podPathMapper.getTaskPath(podName, taskName);
        String taskStatusPath = podPathMapper.getTaskStatusPath(taskPath);
        logger.debug("Fetching status for '{}' in '{}'", taskName, taskStatusPath);
        try {
            byte[] bytes = curator.get(taskStatusPath);
            if (bytes.length > 0) {
                return Optional.of(Protos.TaskStatus.parseFrom(bytes));
            } else {
                throw new StateStoreException(String.format(
                        "Failed to retrieve TaskStatus for TaskName: %s", taskName));
            }
        } catch (KeeperException.NoNodeException e) {
            logger.warn("No TaskInfo found for the requested name: " + taskName + " at: " + taskStatusPath);
            return Optional.empty();
        } catch (Exception e) {
            throw new StateStoreException(e);
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
            throw new StateStoreException(e);
        }
    }

    @Override
    public byte[] fetchProperty(final String key) throws StateStoreException {
        StateStoreUtils.validateKey(key);
        try {
            final String path = CuratorUtils.join(this.propertiesPath, key);
            logger.debug("Fetching property key: {} from path: {}", key, path);
            return curator.get(path);
        } catch (Exception e) {
            throw new StateStoreException(e);
        }
    }

    @Override
    public Collection<String> fetchPropertyKeys() throws StateStoreException {
        try {
            return curator.getChildren(this.propertiesPath);
        } catch (KeeperException.NoNodeException e) {
            // Root path doesn't exist yet. Treat as an empty list of properties. This scenario is
            // expected to commonly occur when the Framework is being run for the first time.
            return Collections.emptyList();
        } catch (Exception e) {
            throw new StateStoreException(e);
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
            throw new StateStoreException(e);
        }
    }

    @VisibleForTesting
    public void closeForTesting() {
        curator.close();
    }

    // Internals

    private static class PodPathMapper {
        private final String podsRootPath;

        private PodPathMapper(String rootPath) {
            this.podsRootPath = CuratorUtils.join(rootPath, PODS_ROOT_NAME);
        }

        private String getTaskInfoPath(String taskName) {
            return CuratorUtils.join(taskName, TASK_INFO_PATH_NAME);
        }

        private String getTaskStatusPath(String taskName) {
            return CuratorUtils.join(taskName, TASK_STATUS_PATH_NAME);
        }

        private String getTaskPath(String podName, String taskName) {
            return CuratorUtils.join(getPodPath(podName), taskName);
        }

        private String getPodPath(String podName) {
            return CuratorUtils.join(getPodsRootPath(), podName);
        }

        private String getPodsRootPath() {
            return podsRootPath;
        }
    }

    public boolean isSuppressed() throws StateStoreException {
        byte[] bytes = StateStoreUtils.fetchPropertyOrEmptyArray(this, SUPPRESSED_KEY);
        Serializer serializer = new JsonSerializer();

        boolean suppressed;
        try {
            suppressed = serializer.deserialize(bytes, Boolean.class);
        } catch (IOException e){
            logger.error("Error converting property " + SUPPRESSED_KEY + " to boolean.", e);
            throw new StateStoreException(e);
        }

        return suppressed;
    }

    public void setSuppressed(final boolean isSuppressed) {
        byte[] bytes;
        Serializer serializer = new JsonSerializer();

        try {
            bytes = serializer.serialize(isSuppressed);
        } catch (IOException e) {
            logger.error("Error serializing property " + SUPPRESSED_KEY + ": " + isSuppressed + ".", e);
            throw new StateStoreException(e);
        }

        storeProperty(SUPPRESSED_KEY, bytes);
    }
}
