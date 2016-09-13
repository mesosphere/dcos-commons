package org.apache.mesos.state;

import com.google.protobuf.TextFormat;
import org.apache.mesos.Protos.*;
import org.apache.mesos.curator.CuratorStateStore;
import org.apache.mesos.offer.TaskException;
import org.apache.mesos.offer.TaskUtils;
import org.apache.mesos.reconciliation.TaskStatusProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * Read/write interface for the state of a Scheduler.
 */
public class SchedulerState implements TaskStatusProvider {
    private static final Logger log = LoggerFactory.getLogger(SchedulerState.class);
    private static final String SUPPRESSED_KEY = "suppressed";

    private final AbstractStateStore stateStore;

    public SchedulerState(String frameworkName, String mesosZkURI) {
        this(new CuratorStateStore(frameworkName, mesosZkURI));
    }

    public SchedulerState(AbstractStateStore stateStore) {
        this.stateStore = stateStore;
    }

    public Optional<FrameworkID> getFrameworkId() {
        try {
            return stateStore.fetchFrameworkId();
        } catch (StateStoreException ex) {
            log.warn("Failed to get FrameworkID. "
                    + "This is expected when the service is starting for the first time.", ex);
        }
        return Optional.empty();
    }

    public void setFrameworkId(FrameworkID fwkId) throws StateStoreException {
        try {
            log.info(String.format("Storing framework id: %s", fwkId));
            stateStore.storeFrameworkId(fwkId);
        } catch (StateStoreException ex) {
            log.error("Failed to set FrameworkID: " + fwkId, ex);
            throw ex;
        }
    }

    public void recordTasks(List<TaskInfo> taskInfos) throws StateStoreException {
        log.info(String.format("Recording %d updated TaskInfos/TaskStatuses:", taskInfos.size()));
        List<TaskStatus> taskStatuses = new ArrayList<>();
        for (TaskInfo taskInfo : taskInfos) {
            TaskStatus taskStatus = TaskStatus.newBuilder()
                    .setTaskId(taskInfo.getTaskId())
                    .setExecutorId(taskInfo.getExecutor().getExecutorId())
                    .setState(TaskState.TASK_STAGING)
                    .build();
            log.info(String.format("- %s => %s", taskInfo, taskStatus));
            log.info("Marking stopped task as failed: {}", TextFormat.shortDebugString(taskInfo));
            taskStatuses.add(taskStatus);
        }

        stateStore.storeTasks(taskInfos);
        for (TaskStatus taskStatus : taskStatuses) {
            recordTaskStatus(taskStatus);
        }
    }

    public void updateStatus(TaskStatus taskStatus) throws StateStoreException {
        log.info(String.format("Recording updated TaskStatus to state store: %s", taskStatus));
        recordTaskStatus(taskStatus);
    }

    public List<TaskInfo> getTerminatedTaskInfos() throws Exception {
        return new ArrayList(stateStore.fetchTerminatedTasks());
    }

    @Override
    public Set<TaskStatus> getTaskStatuses() throws StateStoreException {
        Set<TaskStatus> taskStatuses = new HashSet<TaskStatus>();
        taskStatuses.addAll(stateStore.fetchStatuses());
        return taskStatuses;
    }

    public List<TaskInfo> getTaskInfos() throws StateStoreException {
        List<TaskInfo> taskInfos = new ArrayList<TaskInfo>();
        taskInfos.addAll(stateStore.fetchTasks());
        return taskInfos;
    }

    public void recordTaskInfo(TaskInfo taskInfo) throws StateStoreException {
        log.info(String.format("Recording updated TaskInfo to state store: %s", taskInfo));
        stateStore.storeTasks(Arrays.asList(taskInfo));
    }

    public boolean isSuppressed() {
        return (Boolean) stateStore.fetchPropertyAsObj(SUPPRESSED_KEY);
    }

    public void setSuppressed(boolean isSuppressed) {
        stateStore.storePropertyAsObj(SUPPRESSED_KEY, isSuppressed);
    }

    private void recordTaskStatus(TaskStatus taskStatus) throws StateStoreException {
        if (!taskStatus.getState().equals(TaskState.TASK_STAGING)
                && !taskStatusExists(taskStatus)) {
            log.warn("Dropping non-STAGING status update because the ZK path doesn't exist: "
                    + taskStatus);
        } else {
            stateStore.storeStatus(taskStatus);
        }
    }

    private boolean taskStatusExists(TaskStatus taskStatus) throws StateStoreException {
        String taskName;
        try {
            taskName = TaskUtils.toTaskName(taskStatus.getTaskId());
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
