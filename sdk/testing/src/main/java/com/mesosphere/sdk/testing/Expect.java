package com.mesosphere.sdk.testing;

import static org.mockito.Matchers.any;
import static org.mockito.Mockito.verify;

import java.util.Collection;
import java.util.Collections;
import java.util.stream.Collectors;

import org.apache.mesos.Protos;
import org.apache.mesos.SchedulerDriver;
import org.junit.Assert;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;

import com.mesosphere.sdk.offer.taskdata.TaskPackingUtils;

/**
 * A type of {@link Tick} that verifies the scheduler did something.
 */
public interface Expect extends SimulationTick {

    public static Expect declinedLastOffer() {
        return new Expect() {
            @Override
            public void expect(ClusterState state, SchedulerDriver mockDriver) {
                ArgumentCaptor<Protos.OfferID> offerIdCaptor = ArgumentCaptor.forClass(Protos.OfferID.class);
                verify(mockDriver).declineOffer(offerIdCaptor.capture(), any());
                Assert.assertEquals(state.getLastOffer().getId().getValue(), offerIdCaptor.getValue().getValue());
            }
        };
    }

    public static Expect acceptedLastOffer() {
        return new Expect() {
            @Captor private ArgumentCaptor<Collection<Protos.OfferID>> offerIdsCaptor;
            @Captor private ArgumentCaptor<Collection<Protos.Offer.Operation>> operationsCaptor;

            @Override
            public void expect(ClusterState state, SchedulerDriver mockDriver) {
                verify(mockDriver).acceptOffers(offerIdsCaptor.capture(), operationsCaptor.capture(), any());
                Assert.assertEquals(state.getLastOffer(), offerIdsCaptor.getValue().iterator().next());
                boolean foundLaunch = false;
                for (Protos.Offer.Operation operation : operationsCaptor.getValue()) {
                    if (operation.getType().equals(Protos.Offer.Operation.Type.LAUNCH)) {
                        // Old-style launch with custom executor
                        foundLaunch = true;
                        state.addPod(operation.getLaunch().getTaskInfosList().stream()
                                .map(task -> TaskPackingUtils.unpack(task))
                                .collect(Collectors.toList()));
                    } else if (operation.getType().equals(Protos.Offer.Operation.Type.LAUNCH_GROUP)) {
                        // New-style launch with default executor
                        foundLaunch = true;
                        state.addPod(operation.getLaunchGroup().getTaskGroup().getTasksList());
                    }
                }
                Assert.assertTrue(
                        String.format("Missing LAUNCH or LAUNCH_GROUP in operations: %s", operationsCaptor.getValue()),
                        foundLaunch);
            }
        };
    }

    public static Expect killedTask(String taskName) {
        return new Expect() {
            @Override
            public void expect(ClusterState state, SchedulerDriver mockDriver) {
                ArgumentCaptor<Protos.TaskID> taskIdCaptor = ArgumentCaptor.forClass(Protos.TaskID.class);
                verify(mockDriver).killTask(taskIdCaptor.capture());
                Assert.assertEquals(state.getTaskId(taskName), taskIdCaptor.getValue().getValue());
            }
        };
    }

    public static Expect reconciledTask(Collection<Protos.TaskStatus> taskStatuses) {
        return new Expect() {
            @Captor private ArgumentCaptor<Collection<Protos.TaskStatus>> statusCaptor;

            @Override
            public void expect(ClusterState state, SchedulerDriver mockDriver) {
                verify(mockDriver).reconcileTasks(statusCaptor.capture());
                Assert.assertEquals(taskStatuses, statusCaptor.getValue());
            }
        };
    }

    public static Expect reconciledAny() {
        return new Expect() {
            @Override
            public void expect(ClusterState state, SchedulerDriver mockDriver) {
                verify(mockDriver).reconcileTasks(Collections.emptyList());
            }
        };
    }

    public void expect(ClusterState state, SchedulerDriver mockDriver);
}
