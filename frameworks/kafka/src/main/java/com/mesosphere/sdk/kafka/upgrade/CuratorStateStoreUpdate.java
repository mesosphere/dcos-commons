package com.mesosphere.sdk.kafka.upgrade;

import com.mesosphere.sdk.curator.CuratorStateStore;
import com.mesosphere.sdk.curator.CuratorUtils;
import com.mesosphere.sdk.dcos.DcosConstants;
import com.mesosphere.sdk.offer.CommonTaskUtils;
import com.mesosphere.sdk.state.StateStoreException;
import com.mesosphere.sdk.storage.StorageError;
import org.apache.curator.RetryPolicy;
import org.apache.mesos.Protos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.mesosphere.sdk.storage.StorageError.Reason;
import java.util.Optional;


/**
 * CuratorStateStoreUpdate provides storeStatus(name,status) method.
 */
public class CuratorStateStoreUpdate extends CuratorStateStore {
    private static final Logger logger = LoggerFactory.getLogger(CuratorStateStoreUpdate.class);

    public CuratorStateStoreUpdate(String frameworkName) {
        this(frameworkName, DcosConstants.MESOS_MASTER_ZK_CONNECTION_STRING);
    }

   public CuratorStateStoreUpdate(String frameworkName, String connectionString) {
        this(frameworkName, connectionString, CuratorUtils.getDefaultRetry(), "", "");
    }

    public CuratorStateStoreUpdate(
            String frameworkName,
            String connectionString,
            RetryPolicy retryPolicy) {
        this(frameworkName, connectionString, retryPolicy, "", "");
    }

    public CuratorStateStoreUpdate(
            String frameworkName,
            String connectionString,
            String username,
            String password) {
        this(frameworkName, connectionString, CuratorUtils.getDefaultRetry(), username, password);
    }

    public CuratorStateStoreUpdate(
            String frameworkName,
            String connectionString,
            RetryPolicy retryPolicy,
            String username,
            String password) {
        super(frameworkName, connectionString, retryPolicy, username, password);
    }

    public void storeStatus(String taskName, Protos.TaskStatus status) throws StateStoreException {
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
            throw new StateStoreException(StorageError.Reason.LOGIC_ERROR,
                    String.format("Ignoring TASK_LOST for Task already in a terminal state %s: %s",
                            currentStatusOptional.get().getState(), taskName));
        }

        String path = taskPathMapper.getTaskStatusPath(taskName);
        logger.info("Storing status '{}' for '{}' in '{}'", status.getState(), taskName, path);

        try {
            curator.set(path, status.toByteArray());
        } catch (Exception e) {
            throw new StateStoreException(StorageError.Reason.STORAGE_ERROR, e);
        }
    }

}
