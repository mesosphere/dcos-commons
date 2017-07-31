package com.mesosphere.sdk.offer;

import com.mesosphere.sdk.offer.taskdata.TaskLabelWriter;
import org.apache.mesos.Protos;
import org.apache.mesos.Protos.Offer;
import org.apache.mesos.Protos.Offer.Operation;

import org.apache.mesos.Protos.ExecutorInfo;
import org.apache.mesos.Protos.TaskInfo;

/**
 * This {@link OfferRecommendation} encapsulates a Mesos {@code LAUNCH} Operation.
 */
public class LaunchGroupOfferRecommendation extends LaunchOfferRecommendation {
    private final Operation operation;
    private final TaskInfo taskInfo;
    private final ExecutorInfo executorInfo;

    public LaunchGroupOfferRecommendation(
            Offer offer,
            TaskInfo originalTaskInfo,
            Protos.ExecutorInfo executorInfo,
            boolean shouldLaunch) {
        super(offer, shouldLaunch);

        TaskInfo.Builder taskBuilder = originalTaskInfo.toBuilder();
        if (!shouldLaunch) {
            new TaskLabelWriter(taskBuilder).setTransient();
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

    /**
     * Returns the {@link TaskInfo} to be passed to a StateStore upon launch.
     */
    public TaskInfo getStoreableTaskInfo() {
        return taskInfo.toBuilder()
                .setExecutor(executorInfo)
                .build();
    }

    private Protos.Offer.Operation getLaunchOperation() {
        Protos.Offer.Operation.Builder builder = Protos.Offer.Operation.newBuilder();
        Protos.TaskInfo.Builder taskBuilder = taskInfo.toBuilder();

        if (!shouldLaunch()) {
            new TaskLabelWriter(taskBuilder).setTransient();
            taskBuilder.getTaskIdBuilder().setValue("");
        }
        taskBuilder.setSlaveId(getOffer().getSlaveId());

        builder.setType(Protos.Offer.Operation.Type.LAUNCH_GROUP)
                .getLaunchGroupBuilder()
                .setExecutor(executorInfo)
                .getTaskGroupBuilder()
                .addTasks(taskInfo);
        return builder.build();
    }
}
