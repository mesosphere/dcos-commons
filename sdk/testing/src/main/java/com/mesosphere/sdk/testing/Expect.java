package com.mesosphere.sdk.testing;

import static org.mockito.Matchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

import org.apache.mesos.Protos;
import org.apache.mesos.Protos.TaskStatus;
import org.apache.mesos.SchedulerDriver;
import org.junit.Assert;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.MockitoAnnotations;

import com.mesosphere.sdk.offer.ResourceUtils;
import com.mesosphere.sdk.offer.taskdata.TaskPackingUtils;
import com.mesosphere.sdk.scheduler.plan.Plan;
import com.mesosphere.sdk.state.StateStore;
import com.mesosphere.sdk.storage.Persister;


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
                Protos.OfferID lastAcceptedOfferId = offerIdsCaptor.getValue().iterator().next();
                Assert.assertEquals(String.format(
                            "Expected last offer with ID %s to be accepted, but last accepted offer was %s",
                            state.getLastOffer().getId().getValue(), lastAcceptedOfferId.getValue()),
                        state.getLastOffer().getId(), lastAcceptedOfferId);
                Collection<String> launchedTaskNames = new ArrayList<>();
                // A single acceptOffers() call may contain multiple LAUNCH/LAUNCH_GROUP operations.
                // We want to ensure they're all counted as a unit when tallying the pod.
                Collection<Protos.TaskInfo> launchedTaskInfos = new ArrayList<>();
                for (Protos.Offer.Operation operation : operationsCaptor.getValue()) {
                    if (operation.getType().equals(Protos.Offer.Operation.Type.LAUNCH)) {
                        // Old-style launch with custom executor
                        launchedTaskNames.addAll(operation.getLaunch().getTaskInfosList().stream()
                                .map(task -> task.getName())
                                .collect(Collectors.toList()));
                        launchedTaskInfos.addAll(operation.getLaunch().getTaskInfosList().stream()
                                .map(task -> TaskPackingUtils.unpack(task))
                                .collect(Collectors.toList()));
                    } else if (operation.getType().equals(Protos.Offer.Operation.Type.LAUNCH_GROUP)) {
                        // New-style launch with default executor
                        launchedTaskNames.addAll(operation.getLaunch().getTaskInfosList().stream()
                                .map(task -> task.getName())
                                .collect(Collectors.toList()));
                        launchedTaskInfos.addAll(operation.getLaunchGroup().getTaskGroup().getTasksList());
                    }
                }
                if (!launchedTaskInfos.isEmpty()) {
                    state.addLaunchedPod(launchedTaskInfos);
                }
                Assert.assertTrue(
                        String.format("Expected launched tasks: %s, got tasks: %s", taskNames, launchedTaskNames),
                        launchedTaskNames.containsAll(taskNames) && taskNames.containsAll(launchedTaskNames));
            }

            @Override
            public String getDescription() {
                return String.format("Tasks were launched into a pod: %s", taskNames);
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
    public static Expect taskNameKilled(String taskName) {
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

    public static Expect taskIdKilled(String taskId) {
        return new Expect() {
            @Override
            public void expect(ClusterState state, SchedulerDriver mockDriver) {
                ArgumentCaptor<Protos.TaskID> taskIdCaptor = ArgumentCaptor.forClass(Protos.TaskID.class);
                verify(mockDriver, atLeastOnce()).killTask(taskIdCaptor.capture());
                Assert.assertEquals(taskId, taskIdCaptor.getValue().getValue());
            }

            @Override
            public String getDescription() {
                return String.format("Task with ID %s was killed", taskId);
            }
        };
    }

    /**
     * Verifies that the specified task was not killed. Note that this applies to the whole simulation as of this point.
     */
    public static Expect taskNameNotKilled(String taskName) {
        return new Expect() {
            @Override
            public void expect(ClusterState state, SchedulerDriver mockDriver) {
                verify(mockDriver, never()).killTask(state.getTaskId(taskName));
            }

            @Override
            public String getDescription() {
                return String.format("Task named %s was not killed", taskName);
            }
        };
    }

    /**
     * Verifies that an explicit task reconciliation for the task statuses in the provided persister was invoked.
     */
    public static Expect reconciledExplicitly(Persister persisterWithStatuses) {
        // Use a custom comparator for sorting: Protos don't implement Comparable
        final Comparator<Protos.TaskStatus> statusComparator = new Comparator<Protos.TaskStatus>() {
            @Override
            public int compare(TaskStatus o1, TaskStatus o2) {
                return o1.getTaskId().getValue().compareTo(o2.getTaskId().getValue());
            }
        };

        return new Expect() {
            // Use this form instead of using ArgumentCaptor.forClass() to avoid problems with typecasting generics:
            @Captor private ArgumentCaptor<Collection<Protos.TaskStatus>> statusCaptor;

            @Override
            public void expect(ClusterState state, SchedulerDriver mockDriver) {
                MockitoAnnotations.initMocks(this);
                verify(mockDriver, atLeastOnce()).reconcileTasks(statusCaptor.capture());
                Set<Protos.TaskStatus> expected = new TreeSet<>(statusComparator);
                expected.addAll(new StateStore(persisterWithStatuses).fetchStatuses());
                Set<Protos.TaskStatus> got = new TreeSet<>(statusComparator);
                got.addAll(statusCaptor.getValue());
                Assert.assertEquals(expected, got);
            }

            @Override
            public String getDescription() {
                return String.format("Explicit task reconcile call for statuses: %s",
                        new StateStore(persisterWithStatuses).fetchStatuses().stream()
                                .map(status -> String.format("%s=%s", status.getTaskId().getValue(), status.getState()))
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
     * Verifies that the scheduler's list of tasks in the state store matches the provided set.
     */
    public static Expect knownTasks(Persister persisterWithTasks, String... taskNames) {
        return new Expect() {
            @Override
            public void expect(ClusterState state, SchedulerDriver mockDriver) {
                Set<String> expectedTasks = new HashSet<>(Arrays.asList(taskNames));
                Set<String> tasks = new StateStore(persisterWithTasks).fetchTasks().stream()
                        .map(Protos.TaskInfo::getName)
                        .collect(Collectors.toSet());
                Assert.assertEquals(expectedTasks, tasks);
            }

            @Override
            public String getDescription() {
                return String.format("State store task names: %s",
                        new StateStore(persisterWithTasks).fetchTasks().stream()
                                .map(Protos.TaskInfo::getName)
                                .collect(Collectors.toList()));
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
