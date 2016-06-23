package org.apache.mesos.state;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;

import org.apache.curator.RetryPolicy;
import org.apache.curator.retry.ExponentialBackoffRetry;
import org.apache.mesos.Protos;
import org.apache.mesos.offer.TaskException;
import org.apache.mesos.offer.TaskUtils;
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

    private static final int DEFAULT_CURATOR_POLL_DELAY_MS = 1000;
    private static final int DEFAULT_CURATOR_MAX_RETRIES = 3;

    private static final String TASK_INFO_PATH_NAME = "TaskInfo";
    private static final String TASK_STATUS_PATH_NAME = "TaskStatus";
    private static final String FWK_ID_PATH_NAME = "FrameworkID";
    private static final String TASKS_ROOT_NAME = "Tasks";

    private final CuratorPersister curator;
    private final TaskPathMapper taskPathMapper;
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
        this.taskPathMapper = new TaskPathMapper(rootPath);
        this.fwkIdPath = rootPath + "/" + FWK_ID_PATH_NAME;
    }

    // Framework ID

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

    // Write Tasks

    @Override
    public void storeTasks(Collection<Protos.TaskInfo> tasks) throws StateStoreException {
        for (Protos.TaskInfo taskInfo : tasks) {
            String path = taskPathMapper.getTaskInfoPath(taskInfo.getName());
            logger.debug("Storing Taskinfo for {} in '{}'", taskInfo.getName(), path);
            try {
                curator.store(path, taskInfo.toByteArray());
            } catch (Exception e) {
                // exit early, without proceeding to other tasks:
                throw new StateStoreException(String.format(
                        "Failed to store TaskInfo in '%s'", path), e);
            }
        }
    }

    @Override
    public void storeStatus(Protos.TaskStatus status) throws StateStoreException {
        String taskName;
        try {
            taskName = TaskUtils.toTaskName(status.getTaskId());
        } catch (TaskException e) {
            throw new StateStoreException(String.format(
                    "Failed to parse the Task Name from TaskStatus.task_id: '%s'", status), e);
        }

        String path = taskPathMapper.getTaskStatusPath(taskName);
        logger.debug("Storing status for '{}' in '{}'", taskName, path);

        try {
            curator.store(path, status.toByteArray());
        } catch (Exception e) {
            throw new StateStoreException(e);
        }
    }

    @Override
    public void clearTask(String taskName) throws StateStoreException {
        String path = taskPathMapper.getTaskPath(taskName);
        logger.debug("Clearing Task at '{}'", path);
        try {
            curator.clear(path);
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
            throw new StateStoreException(e);
        }
    }

    @Override
    public Collection<Protos.TaskInfo> fetchTasks() throws StateStoreException {
        Collection<Protos.TaskInfo> taskInfos = new ArrayList<>();
        for (String taskName : fetchTaskNames()) {
            try {
                byte[] bytes = curator.fetch(taskPathMapper.getTaskInfoPath(taskName));
                taskInfos.add(Protos.TaskInfo.parseFrom(bytes));
            } catch (KeeperException.NoNodeException e) {
                // The task node exists, but it doesn't contain a TaskInfo node. This may occur if
                // the only contents are a TaskStatus.
                continue;
            } catch (Exception e) {
                throw new StateStoreException(e);
            }
        }
        return taskInfos;
    }

    @Override
    public Protos.TaskInfo fetchTask(String taskName) throws StateStoreException {
        String path = taskPathMapper.getTaskInfoPath(taskName);
        logger.debug("Fetching TaskInfo {} from '{}'", taskName, path);
        try {
            byte[] bytes = curator.fetch(path);
            if (bytes.length > 0) {
                return Protos.TaskInfo.parseFrom(bytes);
            } else {
                throw new StateStoreException(String.format(
                        "Failed to retrieve TaskInfo for TaskName: %s", taskName));
            }
        } catch (Exception e) {
            // Not found, or other error
            throw new StateStoreException(e);
        }
    }

    @Override
    public Collection<Protos.TaskStatus> fetchStatuses() throws StateStoreException {
        Collection<Protos.TaskStatus> taskStatuses = new ArrayList<>();
        for (String taskName : fetchTaskNames()) {
            try {
                byte[] bytes = curator.fetch(taskPathMapper.getTaskStatusPath(taskName));
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
    public Protos.TaskStatus fetchStatus(String taskName) throws StateStoreException {
        String path = taskPathMapper.getTaskStatusPath(taskName);
        logger.debug("Fetching status for '{}' in '{}'", taskName, path);
        try {
            byte[] bytes = curator.fetch(path);
            if (bytes.length > 0) {
                return Protos.TaskStatus.parseFrom(bytes);
            } else {
                throw new StateStoreException(String.format(
                        "Failed to retrieve TaskStatus for TaskName: %s", taskName));
            }
        } catch (Exception e) {
            throw new StateStoreException(e);
        }
    }

    // Internals

    private static class TaskPathMapper {
        private final String tasksRootPath;

        private TaskPathMapper(String rootPath) {
            this.tasksRootPath = rootPath + "/" + TASKS_ROOT_NAME;
        }

        private String getTaskInfoPath(String taskName) {
            return getTaskPath(taskName) + "/" + TASK_INFO_PATH_NAME;
        }

        private String getTaskStatusPath(String taskName) {
            return getTaskPath(taskName) + "/" + TASK_STATUS_PATH_NAME;
        }

        private String getTaskPath(String taskName) {
            return getTasksRootPath() + "/" + taskName;
        }

        private String getTasksRootPath() {
            return tasksRootPath;
        }
    }
}
