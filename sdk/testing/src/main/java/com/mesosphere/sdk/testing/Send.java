package com.mesosphere.sdk.testing;

import org.apache.mesos.Protos;
import org.apache.mesos.Scheduler;
import org.apache.mesos.SchedulerDriver;

/**
 * A type of {@link SimulationTick} that performs an operation against the scheduler.
 */
public interface Send extends SimulationTick {

    public static Send register() {
        return new Send() {
            @Override
            public void send(ClusterState state, SchedulerDriver mockDriver, Scheduler scheduler) {
                scheduler.registered(
                        mockDriver,
                        Protos.FrameworkID.newBuilder()
                                .setValue("test-framework-id")
                                .build(),
                        Protos.MasterInfo.newBuilder()
                                .setId("test-master-id")
                                .setIp(1)
                                .setPort(2)
                                .build());
            }

            @Override
            public String getDescription() {
                return String.format("Framework registration completed");
            }
        };
    }

    public static SendOffer.Builder offerBuilder(String podType) {
        return new SendOffer.Builder(podType);
    }

    public static SendTaskStatus.Builder taskStatus(String taskName, Protos.TaskState taskState) {
        return new SendTaskStatus.Builder(taskName, taskState);
    }

    /**
     * Performs an action against the provided scheduler, optionally updating the provided cluster state to reflect
     * the action.
     *
     * @param state the cluster's current state, containing a history of e.g. recent offers and launched tasks
     * @param mockDriver a mock {@link SchedulerDriver} which should be passed through to the {@code scheduler} when
     *     making any calls
     * @param scheduler the Mesos {@link Scheduler} under test
     */
    public void send(ClusterState state, SchedulerDriver mockDriver, Scheduler scheduler);
}
