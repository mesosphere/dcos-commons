package com.mesosphere.sdk.offer;

import org.apache.commons.lang3.builder.ReflectionToStringBuilder;
import org.apache.mesos.Protos;
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

    public LaunchOfferRecommendation(Offer offer, TaskInfo taskInfo) {
        this.offer = offer;
        this.isTransient = CommonTaskUtils.isTransient(taskInfo);

        if (isTransient) {
            taskInfo = taskInfo.toBuilder()
                    .setTaskId(Protos.TaskID.newBuilder().setValue(""))
                    .build();
        }

        this.operation = Operation.newBuilder()
                .setType(Operation.Type.LAUNCH)
                .setLaunch(Operation.Launch.newBuilder()
                        .addTaskInfos(TaskInfo.newBuilder(taskInfo)
                                .setSlaveId(offer.getSlaveId())
                                .build()))
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

    @Override
    public String toString() {
        return ReflectionToStringBuilder.toString(this);
    }
}
