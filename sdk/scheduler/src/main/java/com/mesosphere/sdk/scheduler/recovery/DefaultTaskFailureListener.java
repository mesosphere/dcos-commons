package com.mesosphere.sdk.scheduler.recovery;

import com.mesosphere.sdk.offer.TaskUtils;
import com.mesosphere.sdk.specification.PodInstance;
import com.mesosphere.sdk.specification.ServiceSpec;
import org.apache.mesos.Protos;
import com.mesosphere.sdk.offer.CommonIdUtils;
import com.mesosphere.sdk.offer.TaskException;
import com.mesosphere.sdk.state.ConfigStore;
import com.mesosphere.sdk.state.StateStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;

/**
 * This class provides a default implementation of the {@link TaskFailureListener} interface.
 */
public class DefaultTaskFailureListener implements TaskFailureListener {
    private final Logger logger = LoggerFactory.getLogger(getClass());
    private final StateStore stateStore;
    private final ConfigStore<ServiceSpec> configStore;

    public DefaultTaskFailureListener(StateStore stateStore, ConfigStore<ServiceSpec> configStore) {
        this.stateStore = stateStore;
        this.configStore = configStore;
    }

    @Override
    public void taskFailed(Protos.TaskID taskId) {
        try {
            Optional<Protos.TaskInfo> optionalTaskInfo = stateStore.fetchTask(CommonIdUtils.toTaskName(taskId));
            if (optionalTaskInfo.isPresent()) {
                PodInstance podInstance = TaskUtils.getPodInstance(configStore, optionalTaskInfo.get());
                FailureUtils.setPermanentlyFailed(stateStore, podInstance);
            } else {
                logger.error("TaskInfo for TaskID was not present in the StateStore: " + taskId);
            }
        } catch (TaskException e) {
            logger.error("Failed to fetch/store Task for taskId: " + taskId + " with exception:", e);
        }
    }
}
