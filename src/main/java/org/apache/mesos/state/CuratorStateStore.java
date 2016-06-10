package org.apache.mesos.state;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;

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
    public void storeTasks(Collection<Protos.TaskInfo> tasks, String execName) throws StateStoreException {
        for (Protos.TaskInfo taskInfo : tasks) {
            storeTask(taskInfo, execName);
        }
    }

    private void storeTask(Protos.TaskInfo taskInfo, String execName) {
        try {
            store(getTaskInfoPath(taskInfo, execName), taskInfo.toByteArray());
        } catch (Exception e) {
            throw new StateStoreException(e);
        }
    }

    @Override
    public Collection<Protos.TaskInfo> fetchTasks(String execName) throws StateStoreException {
        Collection<Protos.TaskInfo> taskInfos = new ArrayList<>();

        try {
            for (String taskName : getChildren(getExecutorPath(execName))) {
                taskInfos.add(fetchTask(taskName, execName));
            }
        } catch (Exception e) {
            throw new StateStoreException(e);
        }

        return taskInfos;
    }

    @Override
    public Collection<String> fetchExecutorNames() throws StateStoreException {
        try {
            return getChildren(rootPath);
        } catch (KeeperException.NoNodeException e) {
            return Collections.emptyList();
        } catch (Exception e) {
            throw new StateStoreException(e);
        }
    }

    @Override
    public void clearExecutor(String execName) throws StateStoreException {
        try {
            clear(getExecutorPath(execName));
        } catch (KeeperException.NoNodeException e) {
            // Clearing a non-existent ExecutorID should not
            // result in an exception.
            logger.warn("Clearing unset Executor: " + execName);
            return;
        } catch (Exception e) {
            throw new StateStoreException(e);
        }
    }

    private Protos.TaskInfo fetchTask(String taskName, String execName) throws Exception {
        byte[] bytes = fetch(getTaskInfoPath(taskName, execName));
        return Protos.TaskInfo.parseFrom(bytes);
    }

    @Override
    public void storeStatus(
            Protos.TaskStatus status,
            String taskName,
            String execName) throws StateStoreException {

        try {
            store(getTaskStatusPath(taskName, execName), status.toByteArray());
        } catch (Exception e) {
            throw new StateStoreException(e);
        }
    }

    @Override
    public Protos.TaskStatus fetchStatus(String taskName, String execName) throws StateStoreException {
        try {
            byte[] bytes = fetch(getTaskStatusPath(taskName, execName));
            if (bytes.length > 0) {
                return Protos.TaskStatus.parseFrom(bytes);
            } else {
                throw new StateStoreException(
                        String.format(
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
}
