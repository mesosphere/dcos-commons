package com.mesosphere.sdk.testing;

import java.util.Arrays;
import java.util.Optional;

import org.apache.mesos.Protos;
import org.apache.mesos.Scheduler;
import org.apache.mesos.SchedulerDriver;

import com.mesosphere.sdk.offer.ResourceBuilder;
import com.mesosphere.sdk.specification.PodSpec;
import com.mesosphere.sdk.specification.ResourceSpec;
import com.mesosphere.sdk.specification.TaskSpec;
import com.mesosphere.sdk.specification.VolumeSpec;
import com.mesosphere.sdk.testutils.TestConstants;

/**
 * A type of {@link Tick} that performs an operation against the scheduler.
 */
public interface Send extends SimulationTick {

    public static Send register() {
        return new Send() {
            @Override
            public void run(ClusterState state, SchedulerDriver mockDriver, Scheduler scheduler) {
                scheduler.registered(
                        mockDriver,
                        Protos.FrameworkID.newBuilder().setValue("test-framework-id").build(),
                        Protos.MasterInfo.newBuilder().build());
            }
        };
    }

    public static Send offerForPod(PodSpec podSpec) {
        Protos.Offer.Builder offerBuilder = Protos.Offer.newBuilder()
                .setId(TestConstants.OFFER_ID)
                .setFrameworkId(TestConstants.FRAMEWORK_ID)
                .setSlaveId(TestConstants.AGENT_ID)
                .setHostname(TestConstants.HOSTNAME);
        for (TaskSpec task : podSpec.getTasks()) {
            for (ResourceSpec resource : task.getResourceSet().getResources()) {
                offerBuilder.addResources(ResourceBuilder.fromSpec(resource, Optional.empty()).build());
            }
            for (VolumeSpec volume : task.getResourceSet().getVolumes()) {
                offerBuilder.addResources(
                        ResourceBuilder.fromSpec(volume, Optional.empty(), Optional.empty(), Optional.empty()).build());
            }
        }

        return new Send() {
            @Override
            public void run(ClusterState state, SchedulerDriver mockDriver, Scheduler scheduler) {
                scheduler.resourceOffers(mockDriver, Arrays.asList(offerBuilder.build()));
            }
        };
    }

    public static Send taskStatus(String taskName, Protos.TaskState taskState) {
        return new Send() {
            @Override
            public void run(ClusterState state, SchedulerDriver mockDriver, Scheduler scheduler) {
                scheduler.statusUpdate(mockDriver, Protos.TaskStatus.newBuilder()
                        .setTaskId(state.getTaskId(taskName))
                        .setState(taskState)
                        .setMessage("This is a test status")
                        .build());
            }
        };
    }

    public void run(ClusterState state, SchedulerDriver mockDriver, Scheduler scheduler);
}
