package org.apache.mesos.scheduler.recovery;

import org.apache.mesos.Protos;
import org.apache.mesos.offer.TaskException;
import org.apache.mesos.offer.TaskUtils;
import org.apache.mesos.state.StateStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.Optional;

/**
 * This class provides a default implementation of the TaskFailureListener interface.
 */
public class DefaultTaskFailureListener implements TaskFailureListener {
    private final Logger logger = LoggerFactory.getLogger(getClass());
    private final StateStore stateStore;

    public DefaultTaskFailureListener(StateStore stateStore) {
        this.stateStore = stateStore;
    }

    @Override
    public void taskFailed(Protos.TaskID taskId) {
        try {
            Optional<Protos.TaskInfo> optionalTaskInfo = stateStore.fetchTask(TaskUtils.toTaskName(taskId));
            if (optionalTaskInfo.isPresent()) {
                Protos.TaskInfo taskInfo = FailureUtils.markFailed(optionalTaskInfo.get());
                stateStore.storeTasks(Arrays.asList(taskInfo));
            } else {
                logger.error("TaskInfo for TaskID was not present in the StateStore: " + taskId);
            }
        } catch (TaskException e) {
            logger.error("Failed to fetch/store Task for taskId: " + taskId + " with exception:", e);
        }
    }
}
