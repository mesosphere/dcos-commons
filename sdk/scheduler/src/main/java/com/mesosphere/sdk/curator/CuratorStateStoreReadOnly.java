package com.mesosphere.sdk.curator;

import com.mesosphere.sdk.dcos.DcosConstants;
import com.mesosphere.sdk.offer.CommonTaskUtils;
import com.mesosphere.sdk.state.*;
import com.mesosphere.sdk.storage.Persister;
import com.mesosphere.sdk.storage.StorageError;
import com.mesosphere.sdk.storage.StorageError.Reason;
import org.apache.curator.RetryPolicy;
import org.apache.mesos.Protos;
import org.apache.zookeeper.KeeperException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Optional;

/**
 * CuratorStateStoreReadonly is an implementation of {@link StateStoreReadOnly} which reads data persisted in Zookeeper.
 * See {@link CuratorStateStore} for a description of the structure of the data persited in Zookeeper.
 */
public class CuratorStateStoreReadOnly implements StateStoreReadOnly {
    protected Logger logger = LoggerFactory.getLogger(getClass());

    /**
     * @see CuratorSchemaVersionStore#CURRENT_SCHEMA_VERSION
     */
    protected static final int MIN_SUPPORTED_SCHEMA_VERSION = 1;
    protected static final int MAX_SUPPORTED_SCHEMA_VERSION = 1;

    protected static final String TASK_INFO_PATH_NAME = "TaskInfo";
    protected static final String TASK_STATUS_PATH_NAME = "TaskStatus";
    protected static final String FWK_ID_PATH_NAME = "FrameworkID";
    protected static final String PROPERTIES_PATH_NAME = "Properties";
    protected static final String TASKS_ROOT_NAME = "Tasks";
    protected static final String SUPPRESSED_KEY = "suppressed";

    protected final Persister curator;
    protected final TaskPathMapper taskPathMapper;
    protected final String fwkIdPath;
    protected final String propertiesPath;

    /**
     * Creates a new {@link StateStore} which uses Curator with a default {@link RetryPolicy} and
     * connection string.
     *
     * @param frameworkName    The name of the framework
     */
    public CuratorStateStoreReadOnly(String frameworkName) {
        this(frameworkName, DcosConstants.MESOS_MASTER_ZK_CONNECTION_STRING);
    }

    /**
     * Creates a new {@link StateStore} which uses Curator with a default {@link RetryPolicy}.
     *
     * @param frameworkName    The name of the framework
     * @param connectionString The host/port of the ZK server, eg "master.mesos:2181"
     */
    public CuratorStateStoreReadOnly(String frameworkName, String connectionString) {
        this(frameworkName, connectionString, CuratorUtils.getDefaultRetry(), "", "");
    }

    public CuratorStateStoreReadOnly(
            String frameworkName,
            String connectionString,
            RetryPolicy retryPolicy) {
        this(frameworkName, connectionString, retryPolicy, "", "");
    }

    public CuratorStateStoreReadOnly(
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
    public CuratorStateStoreReadOnly(
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

        final String rootPath = CuratorUtils.toServiceRootPath(frameworkName);
        this.taskPathMapper = new TaskPathMapper(rootPath);
        this.fwkIdPath = CuratorUtils.join(rootPath, FWK_ID_PATH_NAME);
        this.propertiesPath = CuratorUtils.join(rootPath, PROPERTIES_PATH_NAME);
    }

    @Override
    public Optional<Protos.FrameworkID> fetchFrameworkId() throws StateStoreException {
        try {
            logger.debug("Fetching FrameworkID from '{}'", fwkIdPath);
            byte[] bytes = curator.get(fwkIdPath);
            if (bytes.length > 0) {
                return Optional.of(Protos.FrameworkID.parseFrom(bytes));
            } else {
                throw new StateStoreException(StorageError.Reason.SERIALIZATION_ERROR, String.format(
                        "Empty FrameworkID in '%s'", fwkIdPath));
            }
        } catch (KeeperException.NoNodeException e) {
            logger.warn("No FrameworkId found at: {}", fwkIdPath);
            return Optional.empty();
        } catch (Exception e) {
            throw new StateStoreException(StorageError.Reason.STORAGE_ERROR, e);
        }
    }


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
                return Optional.of(CommonTaskUtils.unpackTaskInfo(Protos.TaskInfo.parseFrom(bytes)));
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

    public boolean isSuppressed() throws StateStoreException {
        byte[] bytes = StateStoreUtils.fetchPropertyOrEmptyArray(this, SUPPRESSED_KEY);
        Serializer serializer = new JsonSerializer();

        boolean suppressed;
        try {
            suppressed = serializer.deserialize(bytes, Boolean.class);
        } catch (IOException e){
            logger.error(String.format("Error converting property %s to boolean.", SUPPRESSED_KEY), e);
            throw new StateStoreException(Reason.SERIALIZATION_ERROR, e);
        }

        return suppressed;
    }

    /**
     * Provides paths for the various hierarchies of data stored in ZK.
     */
    protected static class TaskPathMapper {
        private final String tasksRootPath;

        protected TaskPathMapper(String rootPath) {
            this.tasksRootPath = CuratorUtils.join(rootPath, TASKS_ROOT_NAME);
        }

        protected String getTaskInfoPath(String taskName) {
            return CuratorUtils.join(getTaskPath(taskName), TASK_INFO_PATH_NAME);
        }

        protected String getTaskStatusPath(String taskName) {
            return CuratorUtils.join(getTaskPath(taskName), TASK_STATUS_PATH_NAME);
        }

        protected String getTaskPath(String taskName) {
            return CuratorUtils.join(getTasksRootPath(), taskName);
        }

        protected String getTasksRootPath() {
            return tasksRootPath;
        }
    }
}
