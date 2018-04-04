package com.mesosphere.sdk.offer;

import com.mesosphere.sdk.offer.taskdata.TaskPackingUtils;
import org.apache.commons.lang3.builder.ReflectionToStringBuilder;
import org.apache.mesos.Protos;
import org.apache.mesos.Protos.Offer;
import org.apache.mesos.Protos.Offer.Operation;

import org.apache.mesos.Protos.ExecutorInfo;
import org.apache.mesos.Protos.TaskInfo;

/**
 * This {@link OfferRecommendation} encapsulates a Mesos {@code LAUNCH} Operation.
 */
public class LaunchOfferRecommendation implements OfferRecommendation {
    private final Offer offer;
    private final Operation operation;
    private final TaskInfo taskInfo;
    private final ExecutorInfo executorInfo;
    private final boolean shouldLaunch;

    public LaunchOfferRecommendation(
            Offer offer,
            TaskInfo originalTaskInfo,
            Protos.ExecutorInfo executorInfo,
            boolean shouldLaunch) {
        this.offer = offer;
        this.shouldLaunch = shouldLaunch;

        TaskInfo.Builder taskBuilder = originalTaskInfo.toBuilder();
        if (!shouldLaunch) {
            taskBuilder.getTaskIdBuilder().setValue("");
        }

        taskBuilder.setSlaveId(offer.getSlaveId());

        this.taskInfo = taskBuilder.build();
        this.executorInfo = executorInfo;
        this.operation = getLaunchOperation();
    }

    @Override
    public Operation getOperation() {
        return operation;
    }

    @Override
    public Offer getOffer() {
        return offer;
    }

    public boolean shouldLaunch() {
        return shouldLaunch;
    }

    /**
     * Returns the {@link TaskInfo} to be passed to a StateStore upon launch.
     */
    public TaskInfo getStoreableTaskInfo() {
        return taskInfo.toBuilder()
                .setExecutor(executorInfo)
                .build();
    }

    @Override
    public String toString() {
        return ReflectionToStringBuilder.toString(this);
    }

    private Protos.Offer.Operation getLaunchOperation() {
        Protos.Offer.Operation.Builder builder = Protos.Offer.Operation.newBuilder();
        builder.setType(Protos.Offer.Operation.Type.LAUNCH_GROUP)
                .getLaunchGroupBuilder()
                        .setExecutor(executorInfo)
                        .getTaskGroupBuilder()
                                .addTasks(taskInfo);
        return builder.build();
    }
}
