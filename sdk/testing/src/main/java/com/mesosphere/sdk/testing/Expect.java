package com.mesosphere.sdk.testing;

import static org.mockito.Matchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import org.apache.mesos.Protos;
import org.apache.mesos.SchedulerDriver;
import org.junit.Assert;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.MockitoAnnotations;

import com.google.protobuf.TextFormat;
import com.mesosphere.sdk.offer.ResourceUtils;
import com.mesosphere.sdk.offer.taskdata.TaskPackingUtils;
import com.mesosphere.sdk.scheduler.plan.Plan;


/**
 * A type of {@link SimulationTick} that verifies the scheduler did something.
 */
public interface Expect extends SimulationTick {

    /**
     * Verifies that the last offer sent to the scheduler was declined.
     */
    public static Expect declinedLastOffer() {
        return new Expect() {
            @Override
            public void expect(ClusterState state, SchedulerDriver mockDriver) {
                ArgumentCaptor<Protos.OfferID> offerIdCaptor = ArgumentCaptor.forClass(Protos.OfferID.class);
                verify(mockDriver, atLeastOnce()).declineOffer(offerIdCaptor.capture(), any());
                Assert.assertEquals(state.getLastOffer().getId().getValue(), offerIdCaptor.getValue().getValue());
            }

            @Override
            public String getDescription() {
                return "Last offer was declined";
            }
        };
    }

    /**
     * Verifies that a pod was launched with exactly the provided task names.
     */
    public static Expect launchedTasks(String... taskNames) {
        return launchedTasks(Arrays.asList(taskNames));
    }

    /**
     * Verifies that a pod was launched with exactly the provided task names.
     */
    public static Expect launchedTasks(Collection<String> taskNames) {
        return new Expect() {
            // Use this form instead of using ArgumentCaptor.forClass() to avoid problems with typecasting generics:
            @Captor private ArgumentCaptor<Collection<Protos.OfferID>> offerIdsCaptor;
            @Captor private ArgumentCaptor<Collection<Protos.Offer.Operation>> operationsCaptor;

            @Override
            public void expect(ClusterState state, SchedulerDriver mockDriver) {
                MockitoAnnotations.initMocks(this);
                verify(mockDriver, atLeastOnce())
                        .acceptOffers(offerIdsCaptor.capture(), operationsCaptor.capture(), any());
                Assert.assertEquals(state.getLastOffer().getId(), offerIdsCaptor.getValue().iterator().next());
                Collection<String> launchedTaskNames = new ArrayList<>();
                for (Protos.Offer.Operation operation : operationsCaptor.getValue()) {
                    if (operation.getType().equals(Protos.Offer.Operation.Type.LAUNCH)) {
                        // Old-style launch with custom executor
                        launchedTaskNames.addAll(operation.getLaunch().getTaskInfosList().stream()
                                .map(task -> task.getName())
                                .collect(Collectors.toList()));
                        state.addLaunchedPod(operation.getLaunch().getTaskInfosList().stream()
                                .map(task -> TaskPackingUtils.unpack(task))
                                .collect(Collectors.toList()));
                    } else if (operation.getType().equals(Protos.Offer.Operation.Type.LAUNCH_GROUP)) {
                        // New-style launch with default executor
                        launchedTaskNames.addAll(operation.getLaunch().getTaskInfosList().stream()
                                .map(task -> task.getName())
                                .collect(Collectors.toList()));
                        state.addLaunchedPod(operation.getLaunchGroup().getTaskGroup().getTasksList());
                    }
                }
                Assert.assertTrue(
                        String.format("Expected launched tasks: %s, got tasks: %s", taskNames, launchedTaskNames),
                        launchedTaskNames.containsAll(taskNames) && taskNames.containsAll(launchedTaskNames));
            }

            @Override
            public String getDescription() {
                return String.format("Tasks were launched in a new pod: %s", taskNames);
            }
        };
    }

    /**
     * Verifies that the resources for the provided task names have been unreserved.
     */
    public static Expect unreservedTasks(String... taskNames) {
        return unreservedTasks(Arrays.asList(taskNames));
    }

    /**
     * Verifies that the resources for the provided task names have been unreserved.
     */
    public static Expect unreservedTasks(Collection<String> taskNames) {
        return new Expect() {
            // Use this form instead of using ArgumentCaptor.forClass() to avoid problems with typecasting generics:
            @Captor private ArgumentCaptor<Collection<Protos.OfferID>> offerIdsCaptor;
            @Captor private ArgumentCaptor<Collection<Protos.Offer.Operation>> operationsCaptor;

            @Override
            public void expect(ClusterState state, SchedulerDriver mockDriver) {
                MockitoAnnotations.initMocks(this);
                verify(mockDriver, atLeastOnce())
                        .acceptOffers(offerIdsCaptor.capture(), operationsCaptor.capture(), any());
                Assert.assertEquals(state.getLastOffer().getId(), offerIdsCaptor.getValue().iterator().next());
                Collection<String> expectedResourceIds = taskNames.stream()
                        .map(taskName ->
                                ResourceUtils.getResourceIds(state.getLastLaunchedTask(taskName).getResourcesList()))
                        .flatMap(List::stream)
                        .collect(Collectors.toList());
                Assert.assertFalse(String.format("Expected some resource ids for tasks: %s, got none", taskNames),
                        expectedResourceIds.isEmpty());
                Collection<String> unreservedResourceIds = new ArrayList<>();
                for (Protos.Offer.Operation operation : operationsCaptor.getValue()) {
                    if (operation.getType().equals(Protos.Offer.Operation.Type.DESTROY)) {
                        // Destroy volume(s)
                        unreservedResourceIds.addAll(
                                ResourceUtils.getResourceIds(operation.getDestroy().getVolumesList()));
                    } else if (operation.getType().equals(Protos.Offer.Operation.Type.UNRESERVE)) {
                        // Unreserve resource(s)
                        unreservedResourceIds.addAll(
                                ResourceUtils.getResourceIds(operation.getUnreserve().getResourcesList()));
                    }
                }
                Assert.assertTrue(
                        String.format("Expected unreserved resource ids: %s, got ids: %s",
                                expectedResourceIds, unreservedResourceIds),
                        unreservedResourceIds.containsAll(expectedResourceIds)
                        && expectedResourceIds.containsAll(unreservedResourceIds));
            }

            @Override
            public String getDescription() {
                return String.format("Resources for tasks have been unreserved: %s", taskNames);
            }
        };
    }

    /**
     * Verifies that the specified task was killed.
     */
    public static Expect killedTask(String taskName) {
        return new Expect() {
            @Override
            public void expect(ClusterState state, SchedulerDriver mockDriver) {
                ArgumentCaptor<Protos.TaskID> taskIdCaptor = ArgumentCaptor.forClass(Protos.TaskID.class);
                verify(mockDriver, atLeastOnce()).killTask(taskIdCaptor.capture());
                Assert.assertEquals(state.getTaskId(taskName).getValue(), taskIdCaptor.getValue().getValue());
            }

            @Override
            public String getDescription() {
                return String.format("Task named %s was killed", taskName);
            }
        };
    }

    /**
     * Verifies that an explicit task reconciliation for the specified task statuses was invoked.
     */
    public static Expect reconciledExplicitly(Collection<Protos.TaskStatus> taskStatuses) {
        return new Expect() {
            @Captor private ArgumentCaptor<Collection<Protos.TaskStatus>> statusCaptor;

            @Override
            public void expect(ClusterState state, SchedulerDriver mockDriver) {
                verify(mockDriver, atLeastOnce()).reconcileTasks(statusCaptor.capture());
                Assert.assertEquals(taskStatuses, statusCaptor.getValue());
            }

            @Override
            public String getDescription() {
                return String.format("Explicit task reconcile call for statuses: %s",
                        taskStatuses.stream()
                                .map(status -> TextFormat.shortDebugString(status))
                                .collect(Collectors.toList()));
            }
        };
    }

    /**
     * Verifies that an implicit task reconciliation was invoked.
     */
    public static Expect reconciledImplicitly() {
        return new Expect() {
            @Override
            public void expect(ClusterState state, SchedulerDriver mockDriver) {
                verify(mockDriver, atLeastOnce()).reconcileTasks(Collections.emptyList());
            }

            @Override
            public String getDescription() {
                return "Implicit task reconcile call occurred";
            }
        };
    }

    /**
     * Verifies that the scheduler's plans are all complete -- that there's no pending work.
     */
    public static Expect allPlansComplete() {
        return new Expect() {
            @Override
            public void expect(ClusterState state, SchedulerDriver mockDriver) {
                Collection<Plan> plans = state.getPlans();
                for (Plan plan : plans) {
                    if (!plan.isComplete()) {
                        Assert.fail(String.format(
                                "%s plan is not complete. Plans: %s",
                                plan.getName(),
                                plans.stream().map(p -> p.toString()).collect(Collectors.toList())));
                    }
                }
            }

            @Override
            public String getDescription() {
                return "All plans complete";
            }
        };
    }

    /**
     * Verifies that a certain event had occurred, optionally updating the provided {@link ClusterState} with a result.
     *
     * @param state the simulated cluster's state
     * @param mockDriver a mockito mock which was passed to the Scheduler under test
     * @throws AssertionError containing a descriptive error if the validation failed
     */
    public void expect(ClusterState state, SchedulerDriver mockDriver) throws AssertionError;
}
