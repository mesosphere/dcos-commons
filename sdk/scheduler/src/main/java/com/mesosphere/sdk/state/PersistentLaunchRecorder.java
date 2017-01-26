package com.mesosphere.sdk.state;

import com.google.protobuf.TextFormat;
import org.apache.mesos.Protos;
import com.mesosphere.sdk.offer.LaunchOfferRecommendation;
import com.mesosphere.sdk.offer.OfferRecommendation;
import com.mesosphere.sdk.offer.OperationRecorder;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;

/**
 * Records the result of launched tasks to persistent storage.
 */
public class PersistentLaunchRecorder implements OperationRecorder {
    private final Logger logger = LoggerFactory.getLogger(getClass());
    private final StateStore stateStore;

    public PersistentLaunchRecorder(StateStore stateStore) {
        this.stateStore = stateStore;
    }

    @Override
    public void record(OfferRecommendation offerRecommendation) throws Exception {
        if (!(offerRecommendation instanceof LaunchOfferRecommendation)) {
            return;
        }

        Protos.TaskInfo taskInfo = ((LaunchOfferRecommendation) offerRecommendation).getTaskInfo();

        Protos.TaskStatus taskStatus = null;
        if (!taskInfo.getTaskId().getValue().equals("")) {
            // Record initial TaskStatus of STAGING:
            Protos.TaskStatus.Builder taskStatusBuilder = Protos.TaskStatus.newBuilder()
                    .setTaskId(taskInfo.getTaskId())
                    .setState(Protos.TaskState.TASK_STAGING);

            if (taskInfo.hasExecutor()) {
                taskStatusBuilder.setExecutorId(taskInfo.getExecutor().getExecutorId());
            }

            taskStatus = taskStatusBuilder.build();
        }

        logger.info("Persisting launch operation{}: {}",
                taskStatus != null ? " with STAGING status" : "",
                TextFormat.shortDebugString(taskInfo));

        stateStore.storeTasks(Arrays.asList(taskInfo));
        if (taskStatus != null) {
            stateStore.storeStatus(taskStatus);
        }
    }
}
