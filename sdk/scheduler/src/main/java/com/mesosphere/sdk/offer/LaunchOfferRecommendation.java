package com.mesosphere.sdk.offer;

import com.mesosphere.sdk.offer.taskdata.SchedulerLabelWriter;
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
            Offer offer, TaskInfo originalTaskInfo, Protos.ExecutorInfo executorInfo, boolean shouldLaunch) {
        this.offer = offer;
        this.shouldLaunch = shouldLaunch;

        TaskInfo.Builder taskBuilder = originalTaskInfo.toBuilder();
        if (!shouldLaunch) {
            new SchedulerLabelWriter(taskBuilder).setTransient();
            taskBuilder.getTaskIdBuilder().setValue("");
        }

        taskBuilder.setSlaveId(offer.getSlaveId());

        this.taskInfo = taskBuilder.build();
        this.executorInfo = executorInfo;
        this.operation = Operation.newBuilder()
                .setType(Operation.Type.LAUNCH_GROUP)
                .setLaunchGroup(Operation.LaunchGroup.newBuilder()
                        .setExecutor(this.executorInfo)
                        .setTaskGroup(Protos.TaskGroupInfo.newBuilder()
                                .addTasks(this.taskInfo)))
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
     * Returns the {@link TaskInfo} to be launched.
     */
    public TaskInfo getTaskInfo() {
        return taskInfo;
    }

    /**
     * Returns the {@link TaskInfo} to be launched.
     */
    public ExecutorInfo getExecutorInfo() {
        return executorInfo;
    }

    @Override
    public String toString() {
        return ReflectionToStringBuilder.toString(this);
    }
}
