package com.mesosphere.sdk.offer;

import com.mesosphere.sdk.offer.taskdata.TaskPackingUtils;
import com.mesosphere.sdk.offer.taskdata.TaskLabelWriter;
import org.apache.mesos.Protos;
import org.apache.mesos.Protos.Offer;
import org.apache.mesos.Protos.Offer.Operation;

import org.apache.mesos.Protos.TaskInfo;

/**
 * This {@link OfferRecommendation} encapsulates a Mesos {@code LAUNCH} Operation.
 */
public class LegacyLaunchOfferRecommendation extends LaunchOfferRecommendation {
    private final Operation operation;
    private final TaskInfo taskInfo;

    public LegacyLaunchOfferRecommendation(
            Offer offer,
            TaskInfo originalTaskInfo,
            boolean shouldLaunch) {
        super(offer, shouldLaunch);

        TaskInfo.Builder taskBuilder = originalTaskInfo.toBuilder();
        if (!shouldLaunch) {
            new TaskLabelWriter(taskBuilder).setTransient();
            taskBuilder.getTaskIdBuilder().setValue("");
        }

        taskBuilder.setSlaveId(offer.getSlaveId());

        this.taskInfo = taskBuilder.build();
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
        return taskInfo;
    }

    private Protos.Offer.Operation getLaunchOperation() {
        Protos.Offer.Operation.Builder builder = Protos.Offer.Operation.newBuilder();
        Protos.TaskInfo.Builder taskBuilder = taskInfo.toBuilder();

        if (!shouldLaunch()) {
            new TaskLabelWriter(taskBuilder).setTransient();
            taskBuilder.getTaskIdBuilder().setValue("");
        }
        taskBuilder.setSlaveId(getOffer().getSlaveId());

        builder.setType(Protos.Offer.Operation.Type.LAUNCH)
                .getLaunchBuilder().addTaskInfos(TaskPackingUtils.pack(taskInfo));

        return builder.build();
    }
}
