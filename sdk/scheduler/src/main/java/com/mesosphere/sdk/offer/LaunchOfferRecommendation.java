package com.mesosphere.sdk.offer;

import org.apache.commons.lang3.builder.ReflectionToStringBuilder;
import org.apache.mesos.Protos.Offer;
import org.apache.mesos.Protos.Offer.Operation;
import org.apache.mesos.Protos.TaskInfo;

/**
 * This {@link OfferRecommendation} encapsulates a Mesos {@code LAUNCH} Operation.
 */
public class LaunchOfferRecommendation implements OfferRecommendation {
    private final Offer offer;
    private final Operation operation;
    private final boolean isTransient;
    private final TaskInfo taskInfo;

    public LaunchOfferRecommendation(Offer offer, TaskInfo originalTaskInfo) {
        this.offer = offer;
        this.isTransient = CommonTaskUtils.isTransient(originalTaskInfo);

        TaskInfo.Builder taskInfoBuilder = originalTaskInfo.toBuilder()
                .setSlaveId(offer.getSlaveId());
        if (isTransient) {
            taskInfoBuilder.getTaskIdBuilder().setValue("");
        }
        this.taskInfo = taskInfoBuilder.build();

        // The packed TaskInfo is only used in the launch operation itself, which is what we pass to Mesos.
        // The rest of the Scheduler uses unpacked TaskInfos.
        this.operation = Operation.newBuilder()
                .setType(Operation.Type.LAUNCH)
                .setLaunch(Operation.Launch.newBuilder()
                        .addTaskInfos(CommonTaskUtils.packTaskInfo(this.taskInfo)))
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

    public boolean isTransient() {
        return isTransient;
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
