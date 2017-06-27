package com.mesosphere.sdk.offer;

import com.mesosphere.sdk.offer.taskdata.SchedulerLabelWriter;
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
    private final boolean useDefaultExecutor;

    public LaunchOfferRecommendation(
            Offer offer,
            TaskInfo originalTaskInfo,
            Protos.ExecutorInfo executorInfo,
            boolean shouldLaunch,
            boolean useDefaultExecutor) {
        this.offer = offer;
        this.shouldLaunch = shouldLaunch;
        this.useDefaultExecutor = useDefaultExecutor;

        TaskInfo.Builder taskBuilder = originalTaskInfo.toBuilder();
        if (!shouldLaunch) {
            new SchedulerLabelWriter(taskBuilder).setTransient();
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
     * Returns the {@link TaskInfo} to be launched.
     */
    public TaskInfo getTaskInfo() {
        return taskInfo;
    }

    public TaskInfo getStoreableTaskInfo() {
        if (useDefaultExecutor) {
            TaskInfo.Builder builder = taskInfo.toBuilder();
            Protos.ExecutorInfo executorInfo = getExecutorInfo();

            builder.setExecutor(executorInfo).build();

            return builder.build();
        }

        return getTaskInfo();
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

    private Protos.Offer.Operation getLaunchOperation() {
        Protos.Offer.Operation.Builder builder = Protos.Offer.Operation.newBuilder();
        Protos.TaskInfo.Builder taskBuilder = taskInfo.toBuilder();

        if (!shouldLaunch) {
            new SchedulerLabelWriter(taskBuilder).setTransient();
            taskBuilder.getTaskIdBuilder().setValue("");
        }
        taskBuilder.setSlaveId(offer.getSlaveId());

        if (useDefaultExecutor) {
            builder.setType(Protos.Offer.Operation.Type.LAUNCH_GROUP)
                    .setLaunchGroup(Protos.Offer.Operation.LaunchGroup.newBuilder()
                            .setExecutor(executorInfo)
                            .setTaskGroup(Protos.TaskGroupInfo.newBuilder()
                                    .addTasks(taskInfo)))
                    .build();
        } else {
            builder.setType(Protos.Offer.Operation.Type.LAUNCH)
                    .setLaunch(
                            Protos.Offer.Operation.Launch.newBuilder()
                                    .addTaskInfos(TaskPackingUtils.pack(taskInfo)));
        }
        return builder.build();
    }
}
