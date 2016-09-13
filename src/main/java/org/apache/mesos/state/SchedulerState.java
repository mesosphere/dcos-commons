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
public class SchedulerState extends CuratorStateStore implements TaskStatusProvider {
    private static final Logger log = LoggerFactory.getLogger(SchedulerState.class);

    private static final String SUPPRESSED_KEY = "suppressed";

    public SchedulerState(String frameworkName, String mesosZkURI) {
        super(frameworkName, mesosZkURI);
    }

    public FrameworkID getFrameworkId() {
        try {
            return fetchFrameworkId().orElse(null);
        } catch (StateStoreException ex) {
            log.warn("Failed to get FrameworkID. "
                    + "This is expected when the service is starting for the first time.", ex);
        }
        return null;
    }

    public void setFrameworkId(FrameworkID fwkId) throws StateStoreException {
        try {
            log.info(String.format("Storing framework id: %s", fwkId));
            storeFrameworkId(fwkId);
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

        storeTasks(taskInfos);
        for (TaskStatus taskStatus : taskStatuses) {
            recordTaskStatus(taskStatus);
        }
    }

    public void updateStatus(TaskStatus taskStatus) throws StateStoreException {
        log.info(String.format("Recording updated TaskStatus to state store: %s", taskStatus));
        recordTaskStatus(taskStatus);
    }

    public List<TaskInfo> getTerminatedTaskInfos() throws Exception {
        return new ArrayList(fetchTerminatedTasks());
    }

    public int getRunningBrokersCount() throws StateStoreException {
        int count = 0;

        for (TaskStatus taskStatus : getTaskStatuses()) {
            if (taskStatus.getState().equals(TaskState.TASK_RUNNING)) {
                count++;
            }
        }

        return count;
    }

    @Override
    public Set<TaskStatus> getTaskStatuses() throws StateStoreException {
        Set<TaskStatus> taskStatuses = new HashSet<TaskStatus>();
        taskStatuses.addAll(fetchStatuses());
        return taskStatuses;
    }

    public List<TaskInfo> getTaskInfos() throws StateStoreException {
        List<TaskInfo> taskInfos = new ArrayList<TaskInfo>();
        taskInfos.addAll(fetchTasks());
        return taskInfos;
    }

    public List<Resource> getExpectedResources() {
        List<Resource> resources = new ArrayList<>();
        try {
            for (TaskInfo taskInfo : getTaskInfos()) {
                resources.addAll(taskInfo.getResourcesList());
                if (taskInfo.hasExecutor()) {
                    resources.addAll(taskInfo.getExecutor().getResourcesList());
                }
            }
        } catch (Exception ex) {
            log.error("Failed to retrieve all Task information", ex);
            return resources;
        }
        return resources;
    }

    public void recordTaskInfo(TaskInfo taskInfo) throws StateStoreException {
        log.info(String.format("Recording updated TaskInfo to state store: %s", taskInfo));
        storeTasks(Arrays.asList(taskInfo));
    }

    public boolean isSuppressed() {
        return (Boolean) fetchPropertyAsObj(SUPPRESSED_KEY);
    }

    public void setSuppressed(boolean isSuppressed) {
        storePropertyAsObj(SUPPRESSED_KEY, isSuppressed);
    }

    private void recordTaskStatus(TaskStatus taskStatus) throws StateStoreException {
        if (!taskStatus.getState().equals(TaskState.TASK_STAGING)
                && !taskStatusExists(taskStatus)) {
            log.warn("Dropping non-STAGING status update because the ZK path doesn't exist: "
                    + taskStatus);
        } else {
            storeStatus(taskStatus);
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
            fetchStatus(taskName);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
