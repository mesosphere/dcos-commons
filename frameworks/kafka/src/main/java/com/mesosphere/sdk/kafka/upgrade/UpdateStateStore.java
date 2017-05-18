package com.mesosphere.sdk.kafka.upgrade;

import com.mesosphere.sdk.offer.TaskUtils;
import com.mesosphere.sdk.state.DefaultStateStore;
import com.mesosphere.sdk.state.StateStoreException;
import com.mesosphere.sdk.storage.Persister;
import com.mesosphere.sdk.storage.StorageError;
import org.apache.mesos.Protos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.mesosphere.sdk.storage.StorageError.Reason;
import java.util.Optional;


/**
 * CuratorStateStoreUpdate provides storeStatus(name,status) method.
 */
public class UpdateStateStore extends DefaultStateStore {
    private static final Logger logger = LoggerFactory.getLogger(UpdateStateStore.class);

    public UpdateStateStore(Persister persister) {
        super(persister);
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
                && TaskUtils.isTerminal(currentStatusOptional.get())) {
            throw new StateStoreException(StorageError.Reason.LOGIC_ERROR,
                    String.format("Ignoring TASK_LOST for Task already in a terminal state %s: %s",
                            currentStatusOptional.get().getState(), taskName));
        }

        String path = getTaskStatusPath(taskName);
        logger.info("Storing status '{}' for '{}' in '{}'", status.getState(), taskName, path);

        try {
            persister.set(path, status.toByteArray());
        } catch (Exception e) {
            throw new StateStoreException(StorageError.Reason.STORAGE_ERROR, e);
        }
    }
}
