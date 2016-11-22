package com.mesosphere.sdk.state;

import com.google.protobuf.TextFormat;
import org.apache.mesos.Protos;
import org.apache.mesos.Protos.Offer;
import org.apache.mesos.Protos.Offer.Operation;
import com.mesosphere.sdk.offer.CommonTaskUtils;
import com.mesosphere.sdk.offer.OperationRecorder;
import com.mesosphere.sdk.offer.TaskException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * Records the state of accepted offers.
 */
public class PersistentOperationRecorder implements OperationRecorder {
    private final Logger logger = LoggerFactory.getLogger(getClass());
    private final StateStore stateStore;

    public PersistentOperationRecorder(StateStore stateStore) {
        this.stateStore = stateStore;
    }

    @Override
    public void record(Operation operation, Offer offer) throws Exception {
        if (operation.getType() == Operation.Type.LAUNCH) {
            recordTasks(operation.getLaunch().getTaskInfosList());
        }
    }

    private void recordTasks(List<Protos.TaskInfo> taskInfos) throws StateStoreException {
        logger.info(String.format("Recording %d updated TaskInfos/TaskStatuses:", taskInfos.size()));
        List<Protos.TaskStatus> taskStatuses = new ArrayList<>();
        for (Protos.TaskInfo taskInfo : taskInfos) {
            if (!taskInfo.getTaskId().getValue().equals("")) {
                Protos.TaskStatus.Builder taskStatusBuilder = Protos.TaskStatus.newBuilder()
                        .setTaskId(taskInfo.getTaskId())
                        .setState(Protos.TaskState.TASK_STAGING);

                if (taskInfo.hasExecutor()) {
                    taskStatusBuilder.setExecutorId(taskInfo.getExecutor().getExecutorId());
                }

                Protos.TaskStatus taskStatus = taskStatusBuilder.build();
                logger.info("- {} => {}",
                        TextFormat.shortDebugString(taskInfo), TextFormat.shortDebugString(taskStatus));
                taskStatuses.add(taskStatus);
            }
        }

        stateStore.storeTasks(taskInfos);
        for (Protos.TaskStatus taskStatus : taskStatuses) {
            recordTaskStatus(taskStatus);
        }
    }

    private void recordTaskStatus(Protos.TaskStatus taskStatus) throws StateStoreException {
        if (!taskStatus.getState().equals(Protos.TaskState.TASK_STAGING)
                && !taskStatusExists(taskStatus)) {
            logger.warn("Dropping non-STAGING status update because the ZK path doesn't exist: "
                    + taskStatus);
        } else {
            stateStore.storeStatus(taskStatus);
        }
    }

    private boolean taskStatusExists(Protos.TaskStatus taskStatus) throws StateStoreException {
        String taskName;
        try {
            taskName = CommonTaskUtils.toTaskName(taskStatus.getTaskId());
        } catch (TaskException e) {
            throw new StateStoreException(String.format(
                    "Failed to get TaskName/ExecName from TaskStatus %s", taskStatus), e);
        }
        try {
            stateStore.fetchStatus(taskName);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
