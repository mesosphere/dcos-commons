package com.mesosphere.sdk.testing;

import com.google.protobuf.TextFormat;
import com.mesosphere.sdk.offer.ResourceUtils;
import com.mesosphere.sdk.scheduler.plan.Phase;
import com.mesosphere.sdk.scheduler.plan.Plan;
import com.mesosphere.sdk.scheduler.plan.Status;
import com.mesosphere.sdk.scheduler.plan.Step;
import com.mesosphere.sdk.scheduler.recovery.DefaultRecoveryPlanManager;
import com.mesosphere.sdk.state.StateStore;
import com.mesosphere.sdk.storage.Persister;

import org.apache.mesos.Protos;
import org.apache.mesos.Protos.TaskStatus;
import org.apache.mesos.SchedulerDriver;
import org.junit.Assert;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.MockitoAnnotations;

import java.util.*;
import java.util.stream.Collectors;

import static org.mockito.Matchers.any;
import static org.mockito.Mockito.*;


/**
 * A type of {@link SimulationTick} that verifies the scheduler did something.
 */
public interface Expect extends SimulationTick {

    /**
     * Verifies that the offers sent to scheduler in the last offer cycle were all declined.
     */
    public static Expect declinedLastOffer() {
        return new Expect() {
            @Override
            public void expect(ClusterState state, SchedulerDriver mockDriver) {
                ArgumentCaptor<Protos.OfferID> offerIdCaptor = ArgumentCaptor.forClass(Protos.OfferID.class);
                Set<String> lastCycleOfferIds = state.getLastOfferCycle().stream()
                        .map(o -> o.getId().getValue())
                        .collect(Collectors.toSet());
                verify(mockDriver, atLeast(lastCycleOfferIds.size())).declineOffer(offerIdCaptor.capture(), any());
                // Check that the offer ids from the last cycle were all declined:
                Set<String> declinedOfferIds = offerIdCaptor.getAllValues().stream()
                        .map(o -> o.getValue())
                        .collect(Collectors.toSet());
                Assert.assertTrue(
                        String.format("Expected all offers from last offer cycle to be declined: %s, got: %s",
                                lastCycleOfferIds, declinedOfferIds),
                        declinedOfferIds.containsAll(lastCycleOfferIds));
            }

            @Override
            public String getDescription() {
                return "Last offer was declined";
            }
        };
    }

    /**
     * Verifies that a pod was launched with exactly the provided task names in the last accept call.
     */
    public static Expect launchedTasks(String... taskNames) {
        return launchedTasks(Arrays.asList(taskNames));
    }

    /**
     * Verifies that a pod was launched with exactly the provided task names in the last accept call.
     */
    public static Expect launchedTasks(Collection<String> taskNames) {
        return launchedTasks(1, taskNames);
    }

    /**
     * Verifies that a pod was launched with exactly the provided task names over the last N accept calls. If the last
     * offer cycle had multiple offers from different agents, then separate accept calls are made on a per-agent basis.
     */
    public static Expect launchedTasks(int acceptsToCheck, String... taskNames) {
        return launchedTasks(acceptsToCheck, Arrays.asList(taskNames));
    }

    /**
     * Verifies that a pod was launched with exactly the provided task names over the last N accept calls. If the last
     * offer cycle had multiple offers from different agents, then separate accept calls are made on a per-agent basis.
     */
    public static Expect launchedTasks(int acceptsToCheck, Collection<String> taskNames) {
        return new Expect() {
            // Use this form instead of using ArgumentCaptor.forClass() to avoid problems with typecasting generics:
            @Captor private ArgumentCaptor<Collection<Protos.OfferID>> offerIdsCaptor;
            @Captor private ArgumentCaptor<Collection<Protos.Offer.Operation>> operationsCaptor;

            @Override
            public void expect(ClusterState state, SchedulerDriver mockDriver) {
                MockitoAnnotations.initMocks(this);

                // Get the params from the last N accept calls:
                verify(mockDriver, atLeast(acceptsToCheck))
                        .acceptOffers(offerIdsCaptor.capture(), operationsCaptor.capture(), any());
                // With the above retrieval, we will have >=acceptsToCheck calls in forward chronological order.
                // We need to manually cut that down to just the LAST acceptsToCheck calls:
                List<Collection<Protos.OfferID>> allOfferIdAcceptCalls = offerIdsCaptor.getAllValues();
                Collection<String> acceptedOfferIds = allOfferIdAcceptCalls
                        .subList(allOfferIdAcceptCalls.size() - acceptsToCheck, allOfferIdAcceptCalls.size())
                        .stream()
                        .flatMap(Collection::stream)
                        .map(Protos.OfferID::getValue)
                        .collect(Collectors.toList());

                List<Collection<Protos.Offer.Operation>> allOperationAcceptCalls = operationsCaptor.getAllValues();
                List<Collection<Protos.Offer.Operation>> selectedOperationAcceptCalls = allOperationAcceptCalls
                        .subList(allOperationAcceptCalls.size() - acceptsToCheck, allOperationAcceptCalls.size());

                // As a sanity check, verify that the accepted ids were all from the most recent offer cycle. This
                // ensures that we aren't looking at accepted offers from a prior offer cycle.
                Set<String> lastCycleOfferIds = state.getLastOfferCycle().stream()
                        .map(o -> o.getId().getValue())
                        .collect(Collectors.toSet());
                Assert.assertTrue(String.format(
                            "Expected last accepted offer in last offer cycle: %s, but last %d accepted %s %s",
                            lastCycleOfferIds,
                            acceptsToCheck,
                            acceptsToCheck == 1 ? "offer was" : "offers were",
                            acceptedOfferIds),
                        lastCycleOfferIds.containsAll(acceptedOfferIds));

                // Check (and capture) task launch operations:
                Collection<String> launchedTaskNames = new ArrayList<>();
                // Iterate over acceptOffers() calls, one per agent:
                for (Collection<Protos.Offer.Operation> acceptCallOperations : selectedOperationAcceptCalls) {
                    // A single acceptOffers() call may contain multiple LAUNCH_GROUP operations.
                    // We want to ensure they're all counted as a unit when tallying the pod.
                    // TODO(nickbp): DCOS-37508 We currently produce multiple LAUNCH_GROUPs (each with identical copies
                    // of the same ExecutorInfo) when launching multiple tasks in a pod. As a temporary measure, this
                    // de-dupes executors by their ExecutorID. Remove this de-dupe once DCOS-37508 is fixed.
                    Map<String, Protos.ExecutorInfo> executorsById = new HashMap<>();
                    Collection<Protos.TaskInfo> launchedTaskInfos = new ArrayList<>();
                    Collection<Protos.Resource> reservedResources = new ArrayList<>();
                    for (Protos.Offer.Operation operation : acceptCallOperations) {
                        switch (operation.getType()) {
                        case LAUNCH_GROUP: {
                            Protos.ExecutorInfo executor = operation.getLaunchGroup().getExecutor();
                            executorsById.put(executor.getExecutorId().getValue(), executor);

                            Collection<Protos.TaskInfo> taskInfos =
                                    operation.getLaunchGroup().getTaskGroup().getTasksList();

                            launchedTaskNames.addAll(taskInfos.stream()
                                    .map(task -> task.getName())
                                    .collect(Collectors.toList()));
                            launchedTaskInfos.addAll(taskInfos);
                            break;
                        }
                        case RESERVE:
                            reservedResources.addAll(operation.getReserve().getResourcesList());
                            break;
                        default:
                            break;
                        }
                    }
                    // Record the accept operation if anything happened:
                    if (!executorsById.isEmpty() || !launchedTaskInfos.isEmpty() || !reservedResources.isEmpty()) {
                        state.addAcceptCall(
                                new AcceptEntry(executorsById.values(), launchedTaskInfos, reservedResources));
                    }
                }

                // Finally, verify that exactly the expected tasks were launched across these acceptOffers() calls:
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

                // Check last accepted offer ID was in last offer cycle:
                Protos.OfferID lastAcceptedOfferId = offerIdsCaptor.getValue().iterator().next();
                Set<String> lastCycleOfferIds = state.getLastOfferCycle().stream()
                        .map(o -> o.getId().getValue())
                        .collect(Collectors.toSet());
                Assert.assertTrue(String.format(
                            "Expected last accepted offer in last offer cycle: %s, but last accepted offer was %s",
                            lastCycleOfferIds, lastAcceptedOfferId.getValue()),
                        lastCycleOfferIds.contains(lastAcceptedOfferId.getValue()));

                // Check unreserved/destroyed resources in operations:
                Collection<String> expectedResourceIds = new ArrayList<>();
                for (String taskName : taskNames) {
                    LaunchedTask task = state.getLastLaunchedTask(taskName);
                    expectedResourceIds.addAll(ResourceUtils.getResourceIds(task.getTask().getResourcesList()));
                    expectedResourceIds.addAll(ResourceUtils.getResourceIds(task.getExecutor().getResourcesList()));
                }
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
                // Iterate over all reconcile calls, look for any call that had matching arguments.
                // We do this arg ourselves, since the in-mock comparison never matches.
                for (Collection<Protos.TaskStatus> reconcileArgs : statusCaptor.getAllValues()) {
                    Set<Protos.TaskStatus> got = new TreeSet<>(statusComparator);
                    got.addAll(reconcileArgs);
                    if (expected.equals(got)) {
                        return; // Found matching call
                    }
                }
                Assert.fail(String.format("Expected a task reconcile with arguments: %s, but actual calls were: %s",
                        expected, statusCaptor.getAllValues()));
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

    public static Expect planStatus(String planName, Status status) {
        return new Expect() {
            @Override
            public void expect(ClusterState state, SchedulerDriver mockDriver) throws AssertionError {
                Plan plan = state.getPlans().stream()
                        .filter(p -> p.getName().equals(planName))
                        .findFirst().get();
                Assert.assertEquals(status, plan.getStatus());
            }

            @Override
            public String getDescription() {
                return String.format("Plan %s has status %s", planName, status);
            }
        };
    }

    /**
     * Verifies that the indicated recovery phase.step has the expected status.
     */
    public static Expect recoveryStepStatus(String phaseName, String stepName, Status expectedStatus) {
        return stepStatus(DefaultRecoveryPlanManager.DEFAULT_RECOVERY_PLAN_NAME, phaseName, stepName, expectedStatus);
    }

    /**
     * Verifies that the indicated plan.phase.step has the expected status.
     */
    public static Expect stepStatus(
            String planName,
            String phaseName,
            String stepName,
            Status expectedStatus) {
        return new Expect() {
            @Override
            public void expect(ClusterState state, SchedulerDriver mockDriver) {
                Plan recoveryPlan = state.getPlans().stream()
                        .filter(plan -> plan.getName().equals(planName))
                        .findAny().get();

                Phase phase = recoveryPlan.getChildren().stream()
                        .filter(p -> p.getName().equals(phaseName))
                        .findAny().get();

                Step step = phase.getChildren().stream()
                        .filter(s -> s.getName().equals(stepName))
                        .findAny().get();

                Assert.assertEquals(expectedStatus, step.getStatus());
            }

            @Override
            public String getDescription() {
                return String.format("For Phase: %s, Step: %s, Status is %s", phaseName, stepName, expectedStatus);
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
     * Verifies that a set of two or more tasks all share the same ExecutorInfo (i.e. the same pod).
     */
    public static Expect samePod(String... taskNames) {
        return new Expect() {
            @Override
            public void expect(ClusterState state, SchedulerDriver mockDriver) throws AssertionError {
                Set<Protos.ExecutorInfo> executors = Arrays.asList(taskNames).stream()
                        .map(name -> state.getLastLaunchedTask(name).getExecutor())
                        .collect(Collectors.toSet());
                Assert.assertEquals(String.format(
                        "Expected tasks to share a single matching executor, but had: %s",
                        executors.stream().map(e -> TextFormat.shortDebugString(e)).collect(Collectors.toList())),
                        1, executors.size());
            }

            @Override
            public String getDescription() {
                return String.format("Tasks share the same executor: %s", Arrays.asList(taskNames));
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
