package org.apache.mesos.state;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;

import org.apache.commons.lang3.StringUtils;
import org.apache.curator.RetryPolicy;
import org.apache.curator.retry.ExponentialBackoffRetry;
import org.apache.mesos.Protos;
import org.apache.mesos.executor.ExecutorTaskException;
import org.apache.mesos.executor.ExecutorUtils;
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
 *     -> "FrameworkID"
 *     -> "Tasks"
 *         -> ExecutorName-0/
 *             -> TaskName-0/
 *                 -> "TaskInfo"
 *                 -> "TaskStatus"
 *             -> TaskName-1/
 *                 -> "TaskInfo"
 *                 -> "TaskStatus"
 *             -> ...
 *        -> ExecutorName-1/
 *             -> TaskName-0/
 *                 -> "TaskInfo"
 *                 -> "TaskStatus"
 *             -> TaskName-1/
 *                 -> "TaskInfo"
 *                 -> "TaskStatus"
 *             -> ...
 *        -> ...
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

    // TaskInfo

    @Override
    public void storeTasks(Collection<Protos.TaskInfo> tasks) throws StateStoreException {
        for (Protos.TaskInfo taskInfo : tasks) {
            String path = taskPathMapper.getTaskInfoPath(taskInfo.getName(), getExecutorName(taskInfo));
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
    public Collection<Protos.TaskInfo> fetchTasks(String execName) throws StateStoreException {
        Collection<Protos.TaskInfo> taskInfos = new ArrayList<>();

        try {
            for (String taskName : curator.getChildren(taskPathMapper.getExecutorPath(execName))) {
                taskInfos.add(fetchTask(taskName, execName));
            }
        } catch (Exception e) {
            // Requested executor not found, or other storage failure
            throw new StateStoreException(e);
        }

        return taskInfos;
    }

    @Override
    public Protos.TaskInfo fetchTask(String taskName, String execName) throws StateStoreException {
        String path = taskPathMapper.getTaskInfoPath(taskName, execName);
        logger.debug("Fetching TaskInfo {}/{} from '{}'", execName, taskName, path);
        try {
            byte[] bytes = curator.fetch(path);
            return Protos.TaskInfo.parseFrom(bytes);
        } catch (Exception e) {
            throw new StateStoreException(e);
        }
    }

    // Executor

    @Override
    public Collection<String> fetchExecutorNames() throws StateStoreException {
        try {
            String path = taskPathMapper.getTasksRootPath();
            logger.debug("Fetching Executors from '{}'", path);
            // Be careful to exclude the Framework ID node, which is also a child of the root:
            Collection<String> executorNames = new ArrayList<>();
            for (String childNode : curator.getChildren(path)) {
                executorNames.add(childNode);
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
            String path = taskPathMapper.getExecutorPath(execName);
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

    // TaskStatus

    @Override
    public void storeStatus(Protos.TaskStatus status) throws StateStoreException {
        String taskName;
        try {
            taskName = TaskUtils.toTaskName(status.getTaskId());
        } catch (TaskException e) {
            throw new StateStoreException(String.format(
                    "Failed to parse the Task Name from TaskStatus.task_id: '%s' %s", status, e));
        }

        String execName;
        try {
            execName = ExecutorUtils.toExecutorName(status.getExecutorId());
        } catch (ExecutorTaskException e) {
            throw new StateStoreException(String.format(
                    "Failed to parse the Executor Name from TaskStatus.executor_id: '%s' %s",
                    status, e));
        }

        String path = taskPathMapper.getTaskStatusPath(taskName, execName);
        logger.debug("Storing status for '{}/{}' in '{}'", execName, taskName, path);

        try {
            curator.store(path, status.toByteArray());
        } catch (Exception e) {
            throw new StateStoreException(e);
        }
    }

    @Override
    public Collection<Protos.TaskStatus> fetchStatuses(String execName)
            throws StateStoreException {
        Collection<Protos.TaskStatus> taskStatuses = new ArrayList<>();

        try {
            for (String taskName : curator.getChildren(taskPathMapper.getExecutorPath(execName))) {
                taskStatuses.add(fetchStatus(taskName, execName));
            }
        } catch (Exception e) {
            // Requested executor not found, or other storage failure
            throw new StateStoreException(e);
        }

        return taskStatuses;
    }

    @Override
    public Protos.TaskStatus fetchStatus(String taskName, String execName)
            throws StateStoreException {
        try {
            String path = taskPathMapper.getTaskStatusPath(taskName, execName);
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

    // Internals

    private static class TaskPathMapper {
        private final String tasksRootPath;

        private TaskPathMapper(String rootPath) {
            this.tasksRootPath = rootPath + "/" + TASKS_ROOT_NAME;
        }

        private String getTaskInfoPath(String taskName, String execName) {
            return getTaskPath(taskName, execName) + "/" + TASK_INFO_PATH_NAME;
        }

        private String getTaskStatusPath(String taskName, String execName) {
            return getTaskPath(taskName, execName) + "/" + TASK_STATUS_PATH_NAME;
        }

        private String getTaskPath(String taskName, String execName) {
            return getExecutorPath(execName) + "/" + taskName;
        }

        private String getExecutorPath(String execName) {
            return getTasksRootPath() + "/" + execName;
        }

        private String getTasksRootPath() {
            return tasksRootPath;
        }
    }

    /**
     * Fallback logic for correctly extracting the Executor Name from a {@link TaskInfo}. This may
     * be moved up into StateStore as a package-private function if it's useful to other
     * implementations.
     *
     * <ol>
     * <li>TaskInfo for custom executor: Uses TaskInfo.executor.name</li>
     * <li>TaskInfo for command executor: Uses TaskInfo.name as a fallback</li>
     * </ol>
     */
    private static String getExecutorName(Protos.TaskInfo taskInfo) throws StateStoreException {
        if (taskInfo.hasExecutor()) {
            // custom executor: use executor name
            if (!taskInfo.getExecutor().hasName()
                    || StringUtils.isEmpty(taskInfo.getExecutor().getName())) {
                throw new StateStoreException(String.format(
                        "TaskInfo.executor.name must be populated when TaskInfo.executor is present: %s",
                        taskInfo));
            }
            return taskInfo.getExecutor().getName();
        } else if (taskInfo.hasCommand()) {
            // command executor: use task name as executor name
            return taskInfo.getName();
        } else {
            throw new StateStoreException(String.format(
                    "Either TaskInfo.executor.name or TaskInfo.command must be provided: %s",
                    taskInfo));
        }
    }
}
