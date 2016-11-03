package org.apache.mesos.state;

import org.apache.mesos.Protos;
import org.apache.mesos.Protos.Offer;
import org.apache.mesos.Protos.Offer.Operation;
import org.apache.mesos.offer.OperationRecorder;
import org.apache.mesos.offer.TaskException;
import org.apache.mesos.offer.TaskUtils;
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

    public void record(Operation operation, Offer offer) throws Exception {
        if (operation.getType() == Operation.Type.LAUNCH) {
            recordTasks(operation.getLaunch().getTaskInfosList());
        }
    }

    /**
     * Since the offer was given for a collection of tasks, it was essentially given to a pod
     * so we know that all the given taskInfos belong in the same pod.
     *
     * @param taskInfos The {@link org.apache.mesos.Protos.TaskInfo}s of a given Pod.
     * @throws StateStoreException
     */
    private void recordTasks(List<Protos.TaskInfo> taskInfos) throws StateStoreException, TaskException {
        String podName = null;

        logger.info(String.format("Recording %d updated TaskInfos/TaskStatuses:", taskInfos.size()));
        List<Protos.TaskStatus> taskStatuses = new ArrayList<>();
        for (Protos.TaskInfo taskInfo : taskInfos) {
            Protos.TaskStatus.Builder taskStatusBuilder = Protos.TaskStatus.newBuilder()
                    .setTaskId(taskInfo.getTaskId())
                    .setState(Protos.TaskState.TASK_STAGING);

            if (taskInfo.hasExecutor()) {
                taskStatusBuilder.setExecutorId(taskInfo.getExecutor().getExecutorId());
            }

            Protos.TaskStatus taskStatus = taskStatusBuilder.build();
            logger.info(String.format("- %s => %s", taskInfo, taskStatus));
            taskStatuses.add(taskStatus);

            if (podName == null) {
                podName = TaskUtils.getTaskPodName(taskInfo);
            }
        }

        stateStore.storeTasks(podName, taskInfos);
        for (Protos.TaskStatus taskStatus : taskStatuses) {
            recordTaskStatus(taskStatus, podName);
        }
    }

    /**
     * Records the status for a given task.
     * @param taskStatus The status of the task to record.
     * @param podName The pod name which the task belongs to.
     * @throws StateStoreException
     */
    private void recordTaskStatus(Protos.TaskStatus taskStatus, String podName) throws StateStoreException {
        if (!taskStatus.getState().equals(Protos.TaskState.TASK_STAGING)
                && !taskStatusExists(taskStatus, podName)) {
            logger.warn("Dropping non-STAGING status update because the ZK path doesn't exist: "
                    + taskStatus);
        } else {
            stateStore.storeStatus(podName, taskStatus);
        }
    }

    private boolean taskStatusExists(Protos.TaskStatus taskStatus, String podName) throws StateStoreException {
        String taskName;
        try {
            taskName = TaskUtils.toTaskName(taskStatus.getTaskId());
        } catch (TaskException e) {
            throw new StateStoreException(String.format(
                    "Failed to get TaskName/ExecName from TaskStatus %s", taskStatus), e);
        }
        try {
            stateStore.fetchStatus(podName, taskName);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
