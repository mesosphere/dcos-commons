package com.mesosphere.sdk.curator;

import com.google.common.annotations.VisibleForTesting;
import com.mesosphere.sdk.dcos.DcosConstants;
import com.mesosphere.sdk.offer.TaskUtils;
import com.mesosphere.sdk.offer.taskdata.TaskPackingUtils;
import com.mesosphere.sdk.state.SchemaVersionStore;
import com.mesosphere.sdk.state.StateStore;
import com.mesosphere.sdk.state.StateStoreException;
import com.mesosphere.sdk.state.StateStoreUtils;
import com.mesosphere.sdk.storage.StorageError.Reason;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import org.apache.curator.RetryPolicy;
import org.apache.curator.framework.api.transaction.CuratorTransaction;
import org.apache.curator.framework.api.transaction.CuratorTransactionFinal;
import org.apache.mesos.Protos;
import org.apache.mesos.Protos.TaskInfo;
import org.apache.mesos.Protos.TaskState;
import org.apache.mesos.Protos.TaskStatus;
import org.apache.zookeeper.KeeperException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;

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
    private static final String TASKS_ROOT_NAME = "Tasks";
    public static final String LOCK_PATH_NAME = "lock";

    protected final CuratorPersister curator;
    protected final TaskPathMapper taskPathMapper;
    private final String fwkIdPath;
    private final String propertiesPath;
    private final String rootPath;

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
        this.curator = new CuratorPersister(connectionString, retryPolicy, username, password);

        // Check version up-front:
        int currentVersion = new CuratorSchemaVersionStore(curator, frameworkName).fetch();
        if (!SchemaVersionStore.isSupported(
                currentVersion, MIN_SUPPORTED_SCHEMA_VERSION, MAX_SUPPORTED_SCHEMA_VERSION)) {
            throw new IllegalStateException(String.format(
                    "Storage schema version %d is not supported by this software " +
                            "(support: min=%d, max=%d)",
                    currentVersion, MIN_SUPPORTED_SCHEMA_VERSION, MAX_SUPPORTED_SCHEMA_VERSION));
        }

        this.rootPath = CuratorUtils.toServiceRootPath(frameworkName);
        this.taskPathMapper = new TaskPathMapper(rootPath);
        this.fwkIdPath = CuratorUtils.join(rootPath, FWK_ID_PATH_NAME);
        this.propertiesPath = CuratorUtils.join(rootPath, PROPERTIES_PATH_NAME);
        repairStateStore();
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

    @Override
    public Optional<Protos.FrameworkID> fetchFrameworkId() throws StateStoreException {
        try {
            logger.debug("Fetching FrameworkID from '{}'", fwkIdPath);
            byte[] bytes = curator.get(fwkIdPath);
            if (bytes.length > 0) {
                return Optional.of(Protos.FrameworkID.parseFrom(bytes));
            } else {
                throw new StateStoreException(Reason.SERIALIZATION_ERROR, String.format(
                        "Empty FrameworkID in '%s'", fwkIdPath));
            }
        } catch (KeeperException.NoNodeException e) {
            logger.warn("No FrameworkId found at: {}", fwkIdPath);
            return Optional.empty();
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
        } catch (Exception e) {
            throw new StateStoreException(Reason.STORAGE_ERROR, e);
        }
    }

    @Override
    public void clearAllData() throws StateStoreException {
        logger.info("Clearing service at '{}'", rootPath);
        try {
             /* Effective 5/8/2017:
              * We cannot delete the root node of a service directly.
              * This is due to the ACLs on the global DC/OS ZK.
              *
              * The root has:
              * [zk: localhost:2181(CONNECTED) 1] getAcl /
              * 'world,'anyone
              * : cr
              * 'ip,'127.0.0.1
              * : cdrwa
              *
              * Our service nodes have:
              * [zk: localhost:2181(CONNECTED) 0] getAcl /dcos-service-hello-world
              * 'world,'anyone
              * : cdrwa
              *
              * The best we can do is to wipe everything under the root node. A proposed way to "fix" things
              * lives at https://jira.mesosphere.com/browse/INFINITY-1470.
              */
            CuratorTransaction transaction = curator.startTransaction();
            curator.commitTransaction(deleteChildren(rootPath, transaction.check()
                    .forPath(rootPath).and()));
            logger.info("Cleared service at '{}'", rootPath);
        } catch (Exception e) {
            logger.error(String.format("Encountered exception while attempting to clear %s", rootPath), e);
            throw new StateStoreException(Reason.STORAGE_ERROR, e);
        }
    }

    private CuratorTransactionFinal deleteChildren(String root, CuratorTransactionFinal curatorTransactionFinal)
            throws Exception {
        ArrayList<String> children = curator.getChildren(root).stream()
                .filter(child -> !child.equals(LOCK_PATH_NAME))
                .collect(Collectors.toCollection(ArrayList::new));

        for (String child : children) {
            curatorTransactionFinal = deleteChildren(CuratorUtils.join(root, child), curatorTransactionFinal);
        }

        // Never try to delete the rootPath. It will fail due to the ACLs.
        if (root.equals(rootPath)) {
            return curatorTransactionFinal;
        }

        return curatorTransactionFinal.delete().forPath(root).and();
    }

    // Read Tasks

    @Override
    public Collection<String> fetchTaskNames() throws StateStoreException {
        String path = taskPathMapper.getTasksRootPath();
        logger.debug("Fetching task names from '{}'", path);
        try {
            Collection<String> taskNames = new ArrayList<>();
            for (String childNode : curator.getChildren(path)) {
                taskNames.add(childNode);
            }
            return taskNames;
        } catch (KeeperException.NoNodeException e) {
            // Root path doesn't exist yet. Treat as an empty list of tasks. This scenario is
            // expected to commonly occur when the Framework is being run for the first time.
            return Collections.emptyList();
        } catch (Exception e) {
            throw new StateStoreException(Reason.STORAGE_ERROR, e);
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
        String path = taskPathMapper.getTaskInfoPath(taskName);
        logger.debug("Fetching TaskInfo {} from '{}'", taskName, path);
        try {
            byte[] bytes = curator.get(path);
            if (bytes.length > 0) {
                // TODO(nick): This unpack operation is no longer needed, but it doesn't hurt anything to leave it in
                // place to support reading older data. Remove this unpack call after services have had time to stop
                // storing packed TaskInfos in zk (after June 2017 or so?).
                return Optional.of(TaskPackingUtils.unpack(Protos.TaskInfo.parseFrom(bytes)));
            } else {
                throw new StateStoreException(Reason.SERIALIZATION_ERROR, String.format(
                        "Empty TaskInfo for TaskName: %s", taskName));
            }
        } catch (KeeperException.NoNodeException e) {
            logger.warn("No TaskInfo found for the requested name: {} at: {}", taskName, path);
            return Optional.empty();
        } catch (Exception e) {
            throw new StateStoreException(Reason.STORAGE_ERROR,
                    String.format("Failed to retrieve task named %s", taskName), e);
        }
    }

    @Override
    public Collection<Protos.TaskStatus> fetchStatuses() throws StateStoreException {
        Collection<Protos.TaskStatus> taskStatuses = new ArrayList<>();
        for (String taskName : fetchTaskNames()) {
            try {
                byte[] bytes = curator.get(taskPathMapper.getTaskStatusPath(taskName));
                taskStatuses.add(Protos.TaskStatus.parseFrom(bytes));
            } catch (KeeperException.NoNodeException e) {
                // The task node exists, but it doesn't contain a TaskStatus node. This may occur if
                // the only contents are a TaskInfo.
                continue;
            } catch (Exception e) {
                throw new StateStoreException(Reason.STORAGE_ERROR, e);
            }
        }
        return taskStatuses;
    }

    @Override
    public Optional<Protos.TaskStatus> fetchStatus(String taskName) throws StateStoreException {
        String path = taskPathMapper.getTaskStatusPath(taskName);
        logger.debug("Fetching status for '{}' in '{}'", taskName, path);
        try {
            byte[] bytes = curator.get(path);
            if (bytes.length > 0) {
                return Optional.of(Protos.TaskStatus.parseFrom(bytes));
            } else {
                throw new StateStoreException(Reason.SERIALIZATION_ERROR, String.format(
                        "Empty TaskStatus for TaskName: %s", taskName));
            }
        } catch (KeeperException.NoNodeException e) {
            logger.warn("No TaskStatus found for the requested name: {} at: {}", taskName, path);
            return Optional.empty();
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
    public byte[] fetchProperty(final String key) throws StateStoreException {
        StateStoreUtils.validateKey(key);
        try {
            final String path = CuratorUtils.join(this.propertiesPath, key);
            logger.debug("Fetching property key: {} from path: {}", key, path);
            return curator.get(path);
        } catch (KeeperException.NoNodeException e) {
            throw new StateStoreException(Reason.NOT_FOUND, e);
        } catch (Exception e) {
            throw new StateStoreException(Reason.STORAGE_ERROR, e);
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

    /**
     * TaskPathMapper.
     */
    protected static class TaskPathMapper {
        private final String tasksRootPath;

        private TaskPathMapper(String rootPath) {
            this.tasksRootPath = CuratorUtils.join(rootPath, TASKS_ROOT_NAME);
        }

        private String getTaskInfoPath(String taskName) {
            return CuratorUtils.join(getTaskPath(taskName), TASK_INFO_PATH_NAME);
        }

        public String getTaskStatusPath(String taskName) {
            return CuratorUtils.join(getTaskPath(taskName), TASK_STATUS_PATH_NAME);
        }

        private String getTaskPath(String taskName) {
            return CuratorUtils.join(getTasksRootPath(), taskName);
        }

        private String getTasksRootPath() {
            return tasksRootPath;
        }
    }

    /**
     * TaskInfo and TaskStatus objects referring to the same Task name are not written to Zookeeper atomicly.
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
