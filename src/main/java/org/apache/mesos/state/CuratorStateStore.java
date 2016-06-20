package org.apache.mesos.state;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;

import org.apache.curator.RetryPolicy;
import org.apache.curator.retry.ExponentialBackoffRetry;
import org.apache.mesos.Protos;
import org.apache.mesos.storage.CuratorPersister;
import org.apache.zookeeper.KeeperException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * CuratorStateStore is an implementation of {@link StateStore} which persists data in Zookeeper.
 *
 * The ZNode structure in Zookeeper is as follows:
 * <code>
 * rootPath/
 *     -> "FrameworkID"
 *     -> ExecutorID-0/
 *         -> TaskName-0/
 *             -> "TaskInfo"
 *             -> "TaskStatus"
 *         -> TaskName-1/
 *             -> "TaskInfo"
 *             -> "TaskStatus"
 *         -> ...
 *    -> ExecutorID-1/
 *         -> TaskName-0/
 *             -> "TaskInfo"
 *             -> "TaskStatus"
 *         -> TaskName-1/
 *             -> "TaskInfo"
 *             -> "TaskStatus"
 *         -> ...
 *    -> ...
 * </code>
 */
public class CuratorStateStore implements StateStore {

    private static final Logger logger = LoggerFactory.getLogger(CuratorStateStore.class);

    private static final int DEFAULT_CURATOR_POLL_DELAY_MS = 1000;
    private static final int DEFAULT_CURATOR_MAX_RETRIES = 3;

    private static final String TASK_INFO_PATH_NAME = "TaskInfo";
    private static final String TASK_STATUS_PATH_NAME = "TaskStatus";
    private static final String FWK_ID_PATH_NAME = "FrameworkID";

    private final CuratorPersister curator;
    private final String rootPath;
    private final String fwkIdPath;

    /**
     * Creates a new {@link StateStore} which uses Curator with a default {@link RetryPolicy}.
     *
     * @param rootPath The path to store data in, eg "/FrameworkName"
     * @param connectionString The host/port of the ZK server, eg "master.mesos:2181"
     */
    public CuratorStateStore(String rootPath, String connectionString) {
        this(rootPath, connectionString, new ExponentialBackoffRetry(
                DEFAULT_CURATOR_POLL_DELAY_MS, DEFAULT_CURATOR_MAX_RETRIES));
    }

    /**
     * Creates a new {@link StateStore} which uses Curator with a custom {@link RetryPolicy}.
     *
     * @param rootPath The path to store data in, eg "/FrameworkName"
     * @param connectionString The host/port of the ZK server, eg "master.mesos:2181"
     * @param retryPolicy The custom {@link RetryPolicy}
     */
    public CuratorStateStore(String rootPath, String connectionString, RetryPolicy retryPolicy) {
        this.curator = new CuratorPersister(connectionString, retryPolicy);
        this.rootPath = rootPath;
        this.fwkIdPath = rootPath + "/" + FWK_ID_PATH_NAME;
    }

    @Override
    public void storeFrameworkId(Protos.FrameworkID fwkId) throws StateStoreException {
        try {
            logger.debug("Storing FrameworkID in '{}'", fwkIdPath);
            curator.store(fwkIdPath, fwkId.toByteArray());
        } catch (Exception e) {
            throw new StateStoreException(String.format(
                    "Failed to store FrameworkID in '%s'", fwkIdPath), e);
        }
    }

    @Override
    public Protos.FrameworkID fetchFrameworkId() throws StateStoreException {
        try {
            logger.debug("Fetching FrameworkID from '{}'", fwkIdPath);
            byte[] bytes = curator.fetch(fwkIdPath);
            if (bytes.length > 0) {
                return Protos.FrameworkID.parseFrom(bytes);
            } else {
                throw new StateStoreException(String.format(
                        "Failed to retrieve FrameworkID in '%s'", fwkIdPath));
            }
        } catch (Exception e) {
            // Also throws if the FrameworkID isn't found
            throw new StateStoreException(e);
        }
    }

    @Override
    public void clearFrameworkId() throws StateStoreException {
        try {
            logger.debug("Clearing FrameworkID at '{}'", fwkIdPath);
            curator.clear(fwkIdPath);
        } catch (KeeperException.NoNodeException e) {
            // Clearing a non-existent FrameworkID should not result in an exception from us.
            logger.warn("Cleared unset FrameworkID, continuing silently", e);
            return;
        } catch (Exception e) {
            throw new StateStoreException(e);
        }
    }

    @Override
    public void storeTasks(Collection<Protos.TaskInfo> tasks, String execName)
            throws StateStoreException {
        for (Protos.TaskInfo taskInfo : tasks) {
            storeTask(taskInfo, execName);
        }
    }

    private void storeTask(Protos.TaskInfo taskInfo, String execName) {
        String path = getTaskInfoPath(taskInfo, execName);
        logger.debug("Storing Taskinfo for {}/{} in '{}'", execName, taskInfo.getName(), path);
        try {
            curator.store(path, taskInfo.toByteArray());
        } catch (Exception e) {
            throw new StateStoreException(String.format(
                    "Failed to store TaskInfo in '%s'", path), e);
        }
    }

    @Override
    public Collection<Protos.TaskInfo> fetchTasks(String execName) throws StateStoreException {
        Collection<Protos.TaskInfo> taskInfos = new ArrayList<>();

        try {
            for (String taskName : curator.getChildren(getExecutorPath(execName))) {
                taskInfos.add(fetchTask(taskName, execName));
            }
        } catch (Exception e) {
            // Requested executor not found, or other storage failure
            throw new StateStoreException(e);
        }

        return taskInfos;
    }

    private Protos.TaskInfo fetchTask(String taskName, String execName) throws Exception {
        String path = getTaskInfoPath(taskName, execName);
        logger.debug("Fetching TaskInfo {}/{} from '{}'", execName, taskName, path);
        byte[] bytes = curator.fetch(path);
        return Protos.TaskInfo.parseFrom(bytes);
    }

    @Override
    public Collection<String> fetchExecutorNames() throws StateStoreException {
        try {
            logger.debug("Fetching Executors from '{}'", rootPath);
            // Be careful to exclude the Framework ID node, which is also a child of the root:
            Collection<String> executorNames = new ArrayList<>();
            for (String childNode : curator.getChildren(rootPath)) {
                if (!childNode.equals(FWK_ID_PATH_NAME)) {
                    executorNames.add(childNode);
                }
            }
            return executorNames;
        } catch (KeeperException.NoNodeException e) {
            // Root path doesn't exist yet. Treat as an empty list of executors. This scenario is
            // expected to commonly occur when the Framework is being run for the first time.
            return Collections.emptyList();
        } catch (Exception e) {
            throw new StateStoreException(e);
        }
    }

    @Override
    public void clearExecutor(String execName) throws StateStoreException {
        try {
            String path = getExecutorPath(execName);
            logger.debug("Clearing Executor at '{}'", path);
            curator.clear(path);
        } catch (KeeperException.NoNodeException e) {
            // Clearing a non-existent ExecutorID should not result in an exception from us.
            logger.warn("Cleared unset Executor, continuing silently: {}", execName, e);
            return;
        } catch (Exception e) {
            throw new StateStoreException(e);
        }
    }

    private Protos.TaskInfo getTaskInfo(Protos.TaskID taskId) {
        for (String execName : fetchExecutorNames()) {
            for (Protos.TaskInfo taskInfo : fetchTasks(execName)) {
                if (taskInfo.getTaskId().equals(taskId)) {
                    return taskInfo;
                }
            }
        }

        return null;
    }

    @Override
    public void storeStatus(
            Protos.TaskStatus status,
            String taskName) throws StateStoreException {

        Protos.TaskInfo taskInfo = getTaskInfo(status.getTaskId());
        if (taskInfo == null) {
            throw new StateStoreException(
                    String.format("Failed to retrieve TaskInfo for TaskStatus: '%s'", status));
        }

        String execName = getExecutorName(taskInfo);
        if (execName == null) {
            throw new StateStoreException(String.format(
                    "Failed to retrieve the Executor name for TaskInfo: %s",
                    taskInfo));
        }

        String path = getTaskStatusPath(taskName, execName);
        logger.debug("Storing status for '{}/{}' in '{}'", execName, taskName, path);

        try {
            curator.store(path, status.toByteArray());
        } catch (Exception e) {
            throw new StateStoreException(e);
        }
    }

    @Override
    public Protos.TaskStatus fetchStatus(String taskName, String execName)
            throws StateStoreException {
        try {
            String path = getTaskStatusPath(taskName, execName);
            logger.debug("Fetching status for {}/{} in '{}'", execName, taskName, path);
            byte[] bytes = curator.fetch(path);
            if (bytes.length > 0) {
                return Protos.TaskStatus.parseFrom(bytes);
            } else {
                throw new StateStoreException(String.format(
                        "Failed to retrieve TaskStatus for TaskName: %s and ExecutorID: %s",
                        taskName, execName));

            }
        } catch (Exception e) {
            throw new StateStoreException(e);
        }
    }

    private String getExecutorPath(String execName) {
        return rootPath + "/" + execName;
    }

    private String getTaskInfoPath(Protos.TaskInfo taskInfo, String execName) {
        return getTaskInfoPath(taskInfo.getName(), execName);
    }

    private String getTaskInfoPath(String taskName, String execName) {
        return getTaskRootPath(taskName, execName) + "/" + TASK_INFO_PATH_NAME;
    }

    private String getTaskStatusPath(String taskName, String execName) {
        return getTaskRootPath(taskName, execName) + "/" + TASK_STATUS_PATH_NAME;
    }

    private String getTaskRootPath(String taskName, String execName) {
        return getExecutorPath(execName) + "/" + taskName;
    }

    private Protos.TaskInfo getTaskInfo(Protos.TaskID taskId) {
        for (String execName : fetchExecutorNames()) {
            for (Protos.TaskInfo taskInfo : fetchTasks(execName)) {
                if (taskInfo.getTaskId().equals(taskId)) {
                    return taskInfo;
                }
            }
        }

        return null;
    }

    private String getExecutorName(Protos.TaskInfo taskInfo) {
        if (taskInfo.hasExecutor()) {
            return taskInfo.getExecutor().getName();
        } else {
            for (String execName : fetchExecutorNames()) {
                for (Protos.TaskInfo info : fetchTasks(execName)) {
                    if (taskInfo.getTaskId().equals(info.getTaskId())) {
                        return execName;
                    }
                }
            }
        }

        return null;
    }
}
