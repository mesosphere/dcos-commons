package com.mesosphere.sdk.offer;

import com.mesosphere.sdk.offer.taskdata.SchedulerLabelWriter;
import org.apache.commons.lang3.builder.ReflectionToStringBuilder;
import org.apache.mesos.Protos.Offer;
import org.apache.mesos.Protos.Offer.Operation;

import com.mesosphere.sdk.offer.taskdata.SchedulerLabelReader;
import com.mesosphere.sdk.offer.taskdata.TaskPackingUtils;

import org.apache.mesos.Protos.TaskInfo;

/**
 * This {@link OfferRecommendation} encapsulates a Mesos {@code LAUNCH} Operation.
 */
public class LaunchOfferRecommendation implements OfferRecommendation {
    private final Offer offer;
    private final Operation operation;
    private final TaskInfo taskInfo;
    private final boolean shouldLaunch;

    public LaunchOfferRecommendation(Offer offer, TaskInfo originalTaskInfo, boolean shouldLaunch) {
        this.offer = offer;
        this.shouldLaunch = shouldLaunch;

        TaskInfo.Builder taskBuilder = originalTaskInfo.toBuilder();
        if (!shouldLaunch) {
            new SchedulerLabelWriter(taskBuilder).setTransient();
            taskBuilder.getTaskIdBuilder().setValue("");
        }

        taskBuilder.setSlaveId(offer.getSlaveId());

        this.taskInfo = taskBuilder.build();

        // The packed TaskInfo is only used in the launch operation itself, which is what we pass to Mesos.
        // The rest of the Scheduler uses unpacked TaskInfos.
        this.operation = Operation.newBuilder()
                .setType(Operation.Type.LAUNCH)
                .setLaunch(Operation.Launch.newBuilder()
                        .addTaskInfos(TaskPackingUtils.pack(this.taskInfo)))
                .build();
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
     * Returns the original, unpacked {@link TaskInfo} to be launched. This varies from the {@link TaskInfo} stored
     * within the {@link Operation}, which is packed.
     */
    public TaskInfo getTaskInfo() {
        return taskInfo;
    }

    @Override
    public String toString() {
        return ReflectionToStringBuilder.toString(this);
    }
}
