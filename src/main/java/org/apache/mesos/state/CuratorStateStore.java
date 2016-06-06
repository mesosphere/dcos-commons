package org.apache.mesos.state;

import java.util.ArrayList;
import java.util.Collection;

import org.apache.curator.RetryPolicy;
import org.apache.mesos.Protos;
import org.apache.mesos.storage.CuratorPersister;
import org.apache.zookeeper.KeeperException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A CuratorStateStore stores TaskInfo and TaskStatus data in Zookeeper.
 *
 * The ZNode structure in Zookeeper is as follows:
 * rootPath
 *     -> ExecutorID-0
 *         -> TaskName-0
 *             -> TaskInfo
 *             -> TaskStatus
 *         -> TaskName-1
 *             -> TaskInfo
 *             -> TaskStatus
 *         -> ...
 *    -> ExecutorID-1
 *         -> TaskName-0
 *             -> TaskInfo
 *             -> TaskStatus
 *         -> TaskName-1
 *             -> TaskInfo
 *             -> TaskStatus
 *         -> ...
 *    -> ...
 */
public class CuratorStateStore extends CuratorPersister implements StateStore {
    private static final String TASK_INFO_PATH_NAME = "TaskInfo";
    private static final String TASK_STATUS_PATH_NAME = "TaskStatus";
    private static final String FWK_ID_PATH_NAME = "FrameworkID";

    private final Logger logger = LoggerFactory.getLogger(getClass());
    private final String rootPath;
    private final String fwkIdPath;

    public CuratorStateStore(String rootPath, String connectionString, RetryPolicy retryPolicy) {
        super(connectionString, retryPolicy);
        this.rootPath = rootPath;
        this.fwkIdPath = rootPath + "/" + FWK_ID_PATH_NAME;
    }

    @Override
    public void storeFrameworkId(Protos.FrameworkID fwkId) throws StateStoreException {
        try {
            store(fwkIdPath, fwkId.toByteArray());
        } catch (Exception e) {
            throw new StateStoreException(e);
        }
    }

    @Override
    public Protos.FrameworkID fetchFrameworkId() throws StateStoreException {
        try {
            byte[] bytes = fetch(fwkIdPath);
            if (bytes.length > 0) {
                return Protos.FrameworkID.parseFrom(bytes);
            } else {
               throw new StateStoreException("Failed to retrieve FrameworkID");
            }
        } catch (Exception e) {
            throw new StateStoreException(e);
        }
    }

    @Override
    public void clearFrameworkId() throws StateStoreException {
        try {
            clear(fwkIdPath);
        } catch (KeeperException.NoNodeException e) {
            // Clearing a non-existent FrameworkID should not
            // result in an exception.
            logger.warn("Clearing unset FrameworkID");
            return;
        } catch (Exception e) {
            throw new StateStoreException(e);
        }
    }

    @Override
    public void storeTasks(Collection<Protos.TaskInfo> tasks, Protos.ExecutorID execId) throws StateStoreException {
        for (Protos.TaskInfo taskInfo : tasks) {
            storeTask(taskInfo, execId);
        }
    }

    private void storeTask(Protos.TaskInfo taskInfo, Protos.ExecutorID execId) {
        try {
            store(getTaskInfoPath(taskInfo, execId), taskInfo.toByteArray());
        } catch (Exception e) {
            throw new StateStoreException(e);
        }
    }

    @Override
    public Collection<Protos.TaskInfo> fetchTasks(Protos.ExecutorID execId) throws StateStoreException {
        Collection<Protos.TaskInfo> taskInfos = new ArrayList<>();

        try {
            for (String taskName : getChildren(getExecutorPath(execId))) {
                taskInfos.add(fetchTask(taskName, execId));
            }
        } catch (Exception e) {
            throw new StateStoreException(e);
        }

        return taskInfos;
    }

    @Override
    public void clearExecutor(Protos.ExecutorID execId) throws StateStoreException {
        try {
            clear(getExecutorPath(execId));
        } catch (KeeperException.NoNodeException e) {
            // Clearing a non-existent ExecutorID should not
            // result in an exception.
            logger.warn("Clearing unset Executor: " + execId);
            return;
        } catch (Exception e) {
            throw new StateStoreException(e);
        }
    }

    private Protos.TaskInfo fetchTask(String taskName, Protos.ExecutorID execId) throws Exception {
        byte[] bytes = fetch(getTaskInfoPath(taskName, execId));
        return Protos.TaskInfo.parseFrom(bytes);
    }

    @Override
    public void storeStatus(
            Protos.TaskStatus status,
            String taskName,
            Protos.ExecutorID execId) throws StateStoreException {

        try {
            store(getTaskStatusPath(taskName, execId), status.toByteArray());
        } catch (Exception e) {
            throw new StateStoreException(e);
        }
    }

    @Override
    public Protos.TaskStatus fetchStatus(String taskName, Protos.ExecutorID execId) throws StateStoreException {
        try {
            byte[] bytes = fetch(getTaskStatusPath(taskName, execId));
            if (bytes.length > 0) {
                return Protos.TaskStatus.parseFrom(bytes);
            } else {
                throw new StateStoreException(
                        String.format(
                                "Failed to retrieve TaskStatus for TaskName: %s and ExecutorID: %s",
                                taskName, execId));

            }
        } catch (Exception e) {
            throw new StateStoreException(e);
        }
    }

    private String getExecutorPath(Protos.ExecutorID execId) {
        return rootPath + "/" + execId.getValue();
    }

    private String getTaskInfoPath(Protos.TaskInfo taskInfo, Protos.ExecutorID execId) {
        return getTaskInfoPath(taskInfo.getName(), execId);
    }

    private String getTaskInfoPath(String taskName, Protos.ExecutorID execId) {
        return getTaskRootPath(taskName, execId) + "/" + TASK_INFO_PATH_NAME;
    }

    private String getTaskStatusPath(String taskName, Protos.ExecutorID execId) {
        return getTaskRootPath(taskName, execId) + "/" + TASK_STATUS_PATH_NAME;
    }

    private String getTaskRootPath(String taskName, Protos.ExecutorID execId) {
        return getExecutorPath(execId) + "/" + taskName;
    }
}
