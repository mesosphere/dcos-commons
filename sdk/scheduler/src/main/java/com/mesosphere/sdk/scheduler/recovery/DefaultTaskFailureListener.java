package com.mesosphere.sdk.scheduler.recovery;

import com.mesosphere.sdk.config.ConfigStore;
import com.mesosphere.sdk.offer.TaskUtils;
import com.mesosphere.sdk.specification.PodInstance;
import org.apache.mesos.Protos;
import com.mesosphere.sdk.offer.CommonIdUtils;
import com.mesosphere.sdk.offer.TaskException;
import com.mesosphere.sdk.state.StateStore;
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
    private final ConfigStore configStore;

    public DefaultTaskFailureListener(StateStore stateStore, ConfigStore configStore) {
        this.stateStore = stateStore;
        this.configStore = configStore;
    }

    @Override
    public void taskFailed(Protos.TaskID taskId) {
        try {
            Optional<Protos.TaskInfo> optionalTaskInfo = stateStore.fetchTask(CommonIdUtils.toTaskName(taskId));
            if (optionalTaskInfo.isPresent()) {
                Protos.TaskInfo taskInfo = optionalTaskInfo.get();
                PodInstance podInstance = TaskUtils.getPodInstance(configStore, taskInfo);
                stateStore.storeTasks(FailureUtils.markFailed(podInstance, stateStore));
            } else {
                logger.error("TaskInfo for TaskID was not present in the StateStore: " + taskId);
            }
        } catch (TaskException e) {
            logger.error("Failed to fetch/store Task for taskId: " + taskId + " with exception:", e);
        }
    }
}
