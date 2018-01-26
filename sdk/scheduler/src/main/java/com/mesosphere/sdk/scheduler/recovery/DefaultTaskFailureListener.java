package com.mesosphere.sdk.scheduler.recovery;

import com.mesosphere.sdk.offer.TaskException;
import com.mesosphere.sdk.offer.TaskUtils;
import com.mesosphere.sdk.specification.PodInstance;
import com.mesosphere.sdk.specification.ServiceSpec;
import com.mesosphere.sdk.state.ConfigStore;
import com.mesosphere.sdk.state.StateStore;
import org.apache.mesos.Protos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

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
    public void tasksFailed(Collection<Protos.TaskInfo> taskInfos) {
        getPods(taskInfos).forEach(podInstance -> FailureUtils.setPermanentlyFailed(stateStore, podInstance));
    }

    private Set<PodInstance> getPods(Collection<Protos.TaskInfo> taskInfos) {
        Set<PodInstance> podInstances = new HashSet<>();
        for (Protos.TaskInfo taskInfo : taskInfos) {
            if (taskInfo.getTaskId().getValue().isEmpty()) {
                // Skip marking 'stub' tasks which haven't been launched as permanently failed:
                logger.info("Not marking task {} as failed due to empty taskId", taskInfo.getName());
                continue;
            }
            try {
                podInstances.add(TaskUtils.getPodInstance(configStore, taskInfo));
            } catch (TaskException e) {
                logger.error(String.format("Failed to get pod for task %s", taskInfo.getTaskId().getValue()), e);
            }
        }

        return podInstances;
    }
}
