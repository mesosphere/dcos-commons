package com.mesosphere.sdk.scheduler.recovery;

import com.mesosphere.sdk.offer.CommonIdUtils;
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
import java.util.Optional;
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
    public void tasksFailed(Collection<Protos.TaskID> taskIds) {
        getPods(taskIds).forEach(podInstance -> FailureUtils.setPermanentlyFailed(stateStore, podInstance));
    }

    private Set<PodInstance> getPods(Collection<Protos.TaskID> taskIds) {
        Set<PodInstance> podInstances = new HashSet<>();
        for (Protos.TaskID taskId : taskIds) {
            try {
                Optional<Protos.TaskInfo> taskInfo = stateStore.fetchTask(CommonIdUtils.toTaskName(taskId));
                if (taskInfo.isPresent()) {
                    PodInstance podInstance = TaskUtils.getPodInstance(configStore, taskInfo.get());
                    podInstances.add(podInstance);
                }
            } catch (TaskException e) {
                logger.error("Failed to fetch/store Task for taskId: " + taskId + " with exception:", e);
            }
        }

        return podInstances;
    }
}
