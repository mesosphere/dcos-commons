package com.mesosphere.sdk.helloworld.scheduler;

import java.io.File;
import java.util.*;
import java.util.stream.Collectors;

import org.apache.mesos.Protos;
import org.apache.mesos.SchedulerDriver;
import org.junit.After;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mockito;

import com.google.protobuf.TextFormat;
import com.mesosphere.sdk.offer.ResourceUtils;
import com.mesosphere.sdk.scheduler.plan.Phase;
import com.mesosphere.sdk.scheduler.plan.Plan;
import com.mesosphere.sdk.scheduler.plan.Status;
import com.mesosphere.sdk.scheduler.plan.Step;
import com.mesosphere.sdk.state.StateStore;
import com.mesosphere.sdk.storage.Persister;
import com.mesosphere.sdk.testing.ClusterState;
import com.mesosphere.sdk.testing.Expect;
import com.mesosphere.sdk.testing.Send;
import com.mesosphere.sdk.testing.ServiceTestResult;
import com.mesosphere.sdk.testing.ServiceTestRunner;
import com.mesosphere.sdk.testing.SimulationTick;

/**
 * Tests for the hello world service and its example yml files.
 */
public class ServiceTest {

    @After
    public void afterTest() {
        Mockito.validateMockitoUsage();
    }

    /**
     * Validates service deployment in the default configuration case.
     */
    @Test
    public void testDefaultDeployment() throws Exception {
        runDefaultDeployment();
    }

    /**
     * Checks that if an unessential task in a pod fails, that the other task in the same pod is unaffected.
     */
    @Test
    public void testNonEssentialTaskFailure() throws Exception {
        Collection<SimulationTick> ticks = new ArrayList<>();

        ticks.add(Send.register());

        ticks.add(Expect.reconciledImplicitly());

        // Verify that service launches 1 hello pod.
        ticks.add(Send.offerBuilder("hello").build());
        ticks.add(Expect.launchedTasks("hello-0-essential", "hello-0-nonessential"));

        // Running, no readiness check is applicable:
        ticks.add(Send.taskStatus("hello-0-essential", Protos.TaskState.TASK_RUNNING).build());
        ticks.add(Send.taskStatus("hello-0-nonessential", Protos.TaskState.TASK_RUNNING).build());

        // No more hellos to launch:
        ticks.add(Send.offerBuilder("hello").setHostname("host-foo").build());
        ticks.add(Expect.declinedLastOffer());
        ticks.add(Expect.allPlansComplete());

        // When non-essential "agent" task fails, only agent task is relaunched, server task is unaffected:
        ticks.add(Send.taskStatus("hello-0-nonessential", Protos.TaskState.TASK_FAILED).build());

        // Turn the crank with an arbitrary offer so that the failure is processed.
        // This also tests that the task is still tied to its prior location by checking that the offer is declined.
        ticks.add(Send.offerBuilder("hello").build());
        ticks.add(Expect.declinedLastOffer());
        // Neither task should be killed: server should be unaffected, and agent is already in a terminal state
        ticks.add(Expect.taskNameNotKilled("hello-0-nonessential"));
        ticks.add(Expect.taskNameNotKilled("hello-0-essential"));

        // Send the matching offer to relaunch ONLY the agent against:
        ticks.add(Send.offerBuilder("hello").setPodIndexToReoffer(0).build());
        ticks.add(Expect.launchedTasks("hello-0-nonessential"));

        ticks.add(Send.taskStatus("hello-0-nonessential", Protos.TaskState.TASK_RUNNING).build());

        ticks.add(Expect.allPlansComplete());

        // Matching ExecutorInfo == same pod:
        ticks.add(new ExpectTasksShareExecutor("hello-0-essential", "hello-0-nonessential"));

        new ServiceTestRunner("examples/nonessential_tasks.yml").run(ticks);
    }

    /**
     * Checks that if an essential task in a pod fails, that all tasks in the pod are relaunched.
     */
    @Test
    public void testEssentialTaskFailure() throws Exception {
        Collection<SimulationTick> ticks = new ArrayList<>();

        ticks.add(Send.register());

        ticks.add(Expect.reconciledImplicitly());

        // Verify that service launches 1 hello pod.
        ticks.add(Send.offerBuilder("hello").build());
        ticks.add(Expect.launchedTasks("hello-0-essential", "hello-0-nonessential"));

        // Running, no readiness check is applicable:
        ticks.add(Send.taskStatus("hello-0-essential", Protos.TaskState.TASK_RUNNING).build());
        ticks.add(Send.taskStatus("hello-0-nonessential", Protos.TaskState.TASK_RUNNING).build());

        // No more hellos to launch:
        ticks.add(Send.offerBuilder("hello").setHostname("host-foo").build());
        ticks.add(Expect.declinedLastOffer());
        ticks.add(Expect.allPlansComplete());

        // When essential "server" task fails, both server+agent are relaunched:
        ticks.add(Send.taskStatus("hello-0-essential", Protos.TaskState.TASK_FAILED).build());

        // Turn the crank with an arbitrary offer so that the failure is processed.
        // This also tests that the task is still tied to its prior location by checking that the offer is declined.
        ticks.add(Send.offerBuilder("hello").build());
        ticks.add(Expect.declinedLastOffer());
        // Only the agent task is killed: server is already in a terminal state
        ticks.add(Expect.taskNameKilled("hello-0-nonessential"));
        ticks.add(Expect.taskNameNotKilled("hello-0-essential"));

        // Send the matching offer to relaunch both the server and agent:
        ticks.add(Send.offerBuilder("hello").setPodIndexToReoffer(0).build());
        ticks.add(Expect.launchedTasks("hello-0-essential", "hello-0-nonessential"));

        ticks.add(Send.taskStatus("hello-0-nonessential", Protos.TaskState.TASK_RUNNING).build());
        ticks.add(Send.taskStatus("hello-0-essential", Protos.TaskState.TASK_RUNNING).build());

        ticks.add(Expect.allPlansComplete());

        // Matching ExecutorInfo == same pod:
        ticks.add(new ExpectTasksShareExecutor("hello-0-essential", "hello-0-nonessential"));

        new ServiceTestRunner("examples/nonessential_tasks.yml").run(ticks);
    }

    /**
     * Checks that unexpected Tasks are killed.
     */
    @Test
    public void testZombieTaskKilling() throws Exception {
        Collection<SimulationTick> ticks = new ArrayList<>();

        ticks.add(Send.register());

        ticks.add(Expect.reconciledImplicitly());

        // Verify that service launches 1 hello pod.
        ticks.add(Send.offerBuilder("hello").build());
        ticks.add(Expect.launchedTasks("hello-0-server"));

        // Running, no readiness check is applicable:
        ticks.add(Send.taskStatus("hello-0-server", Protos.TaskState.TASK_RUNNING).build());
        String taskId = UUID.randomUUID().toString();
        ticks.add(Send.taskStatus("hello-0-server", Protos.TaskState.TASK_RUNNING)
                .setTaskId(taskId)
                .build());

        ticks.add(Expect.taskIdKilled(taskId));
        ticks.add(Expect.taskNameNotKilled("hello-0-server"));

        new ServiceTestRunner("examples/simple.yml").run(ticks);
    }

    /**
     * Verifies that a set of two or more tasks all share the same ExecutorInfo (i.e. the same pod).
     */
    private static class ExpectTasksShareExecutor implements Expect {

        private final List<String> taskNames;

        private ExpectTasksShareExecutor(String... taskNames) {
            this.taskNames = Arrays.asList(taskNames);
        }

        @Override
        public String getDescription() {
            return String.format("Tasks share the same executor: %s", taskNames);
        }

        @Override
        public void expect(ClusterState state, SchedulerDriver mockDriver) throws AssertionError {
            Set<Protos.ExecutorInfo> executors = taskNames.stream()
                    .map(name -> state.getLastLaunchedTask(name).getExecutor())
                    .collect(Collectors.toSet());
            Assert.assertEquals(String.format(
                    "Expected tasks to share a single matching executor, but had: %s",
                    executors.stream().map(e -> TextFormat.shortDebugString(e)).collect(Collectors.toList())),
                    1, executors.size());
        }
    }

    /**
     * Tests scheduler behavior when the number of {@code world} pods is reduced.
     */
    @Test
    public void testHelloDecommissionNotAllowed() throws Exception {
        // Simulate an initial deployment with default of 2 world nodes (and 1 hello node):
        ServiceTestResult result = runDefaultDeployment();
        Assert.assertEquals(
                new TreeSet<>(Arrays.asList("hello-0-server", "world-0-server", "world-1-server")),
                result.getPersister().getChildren("/Tasks"));

        // Now test behavior when that's reduced to 1 world node:
        Collection<SimulationTick> ticks = new ArrayList<>();

        ticks.add(Send.register());

        ticks.add(Expect.reconciledExplicitly(result.getPersister()));
        ticks.add(Send.taskStatus("hello-0-server", Protos.TaskState.TASK_RUNNING).build());
        ticks.add(Send.taskStatus("world-0-server", Protos.TaskState.TASK_RUNNING).setReadinessCheckExitCode(0).build());
        ticks.add(Send.taskStatus("world-1-server", Protos.TaskState.TASK_RUNNING).setReadinessCheckExitCode(0).build());

        // Need to send an offer to trigger the implicit reconciliation
        ticks.add(Send.offerBuilder("hello").build());
        ticks.add(Expect.reconciledImplicitly());
        ticks.add(Expect.declinedLastOffer());

        ticks.add(new Expect() {
            @Override
            public void expect(ClusterState state, SchedulerDriver mockDriver) {
                List<String> deployErrors = state.getPlans().stream()
                        .filter(p -> p.isDeployPlan())
                        .findFirst()
                        .get().getErrors();
                Assert.assertTrue(deployErrors.get(0).contains("PodSpec named 'hello' has 0 tasks, expected >=1 tasks"));
            }

            @Override
            public String getDescription() {
                return "check deploy plan error";
            }
        });

        new ServiceTestRunner().setOptions("hello.count", "0").setState(result).run(ticks);
    }

    /**
     * Tests scheduler behavior when the number of {@code world} pods is reduced.
     */
    @Test
    public void testWorldDecommission() throws Exception {
        // Simulate an initial deployment with default of 2 world nodes (and 1 hello node):
        ServiceTestResult result = runDefaultDeployment();
        Assert.assertEquals(
                new TreeSet<>(Arrays.asList("hello-0-server", "world-0-server", "world-1-server")),
                result.getPersister().getChildren("/Tasks"));

        // Now test behavior when that's reduced to 1 world node:
        Collection<SimulationTick> ticks = new ArrayList<>();

        ticks.add(Send.register());

        ticks.add(Expect.reconciledExplicitly(result.getPersister()));
        ticks.add(Send.taskStatus("hello-0-server", Protos.TaskState.TASK_RUNNING).build());
        ticks.add(Send.taskStatus("world-0-server", Protos.TaskState.TASK_RUNNING).setReadinessCheckExitCode(0).build());
        ticks.add(Send.taskStatus("world-1-server", Protos.TaskState.TASK_RUNNING).setReadinessCheckExitCode(0).build());

        // Now, we expect there to be the following plan state:
        // - a deploy plan that's COMPLETE, with only hello-0 (empty world phase)
        // - a recovery plan that's COMPLETE
        // - a decommission plan that's PENDING with phases for world-1 and world-0 (in that order)

        // Check initial plan state
        ticks.add(new ExpectDecommissionPlanProgress(Arrays.asList(
                new StepCount("world-1", 6, 0, 0), new StepCount("world-0", 6, 0, 0))));

        // Need to send an offer to trigger the implicit reconciliation.
        ticks.add(Send.offerBuilder("hello").build());
        ticks.add(Expect.reconciledImplicitly());
        ticks.add(Expect.declinedLastOffer());

        // Check plan state after an offer came through: world-1-server killed
        ticks.add(new ExpectDecommissionPlanProgress(Arrays.asList(
                new StepCount("world-1", 5, 0, 1), new StepCount("world-0", 6, 0, 0))));
        ticks.add(Expect.taskNameKilled("world-1-server"));

        // Offer world-0 resources and check that nothing happens (haven't gotten there yet):
        ticks.add(Send.offerBuilder("world").setPodIndexToReoffer(0).build());
        ticks.add(new ExpectDecommissionPlanProgress(Arrays.asList(
                new StepCount("world-1", 4, 1, 1), new StepCount("world-0", 6, 0, 0))));

        // Offer world-1 resources and check that world-1 resources are wiped:
        ticks.add(Send.offerBuilder("world").setPodIndexToReoffer(1).build());
        ticks.add(Expect.unreservedTasks("world-1-server"));
        ticks.add(new ExpectDecommissionPlanProgress(Arrays.asList(
                new StepCount("world-1", 1, 0, 5), new StepCount("world-0", 6, 0, 0))));
        ticks.add(new ExpectEmptyResources(result.getPersister(), "world-1-server"));

        // Turn the crank with an arbitrary offer to finish erasing world-1:
        ticks.add(Expect.knownTasks(result.getPersister(), "hello-0-server", "world-0-server", "world-1-server"));
        ticks.add(Send.offerBuilder("world").build());
        ticks.add(Expect.declinedLastOffer());
        ticks.add(Expect.knownTasks(result.getPersister(), "hello-0-server", "world-0-server"));
        ticks.add(new ExpectDecommissionPlanProgress(Arrays.asList(
                new StepCount("world-1", 0, 0, 6), new StepCount("world-0", 6, 0, 0))));

        // Now let's proceed with decommissioning world-0. This time a single offer with the correct resources results
        // in both killing/flagging the task, and clearing its resources:
        ticks.add(Send.offerBuilder("world").setPodIndexToReoffer(0).build());
        ticks.add(new ExpectDecommissionPlanProgress(Arrays.asList(
                new StepCount("world-1", 0, 0, 6), new StepCount("world-0", 1, 0, 5))));
        ticks.add(Expect.taskNameKilled("world-0-server"));
        ticks.add(new ExpectEmptyResources(result.getPersister(), "world-0-server"));

        // Turn the crank once again to erase the world-0 stub:
        ticks.add(Expect.knownTasks(result.getPersister(), "hello-0-server", "world-0-server"));
        ticks.add(Send.offerBuilder("world").build());
        ticks.add(Expect.declinedLastOffer());
        ticks.add(Expect.knownTasks(result.getPersister(), "hello-0-server"));
        ticks.add(new ExpectDecommissionPlanProgress(Arrays.asList(
                new StepCount("world-1", 0, 0, 6), new StepCount("world-0", 0, 0, 6))));

        ticks.add(Expect.allPlansComplete());

        new ServiceTestRunner().setOptions("world.count", "0").setState(result).run(ticks);
    }

    private static class StepCount {
        private final String phaseName;
        private final int pendingCount;
        private final int preparedCount;
        private final int completedCount;

        private StepCount(String phaseName, int pendingCount, int preparedCount, int completedCount) {
            this.phaseName = phaseName;
            this.pendingCount = pendingCount;
            this.preparedCount = preparedCount;
            this.completedCount = completedCount;
        }

        private Status statusOfStepIndex(int index) {
            if (completedCount > index) {
                return Status.COMPLETE;
            } else if (completedCount + preparedCount > index) {
                return Status.PREPARED;
            } else {
                return Status.PENDING;
            }
        }

        @Override
        public String toString() {
            return String.format("phase=%s,completed=%d", phaseName, completedCount);
        }
    }

    /**
     * Expects that the specified task has no resources listed in the state store.
     */
    private static class ExpectEmptyResources implements Expect {
        private final Persister persisterWithTasks;
        private final String taskName;

        private ExpectEmptyResources(Persister persisterWithTasks, String taskName) {
            this.persisterWithTasks = persisterWithTasks;
            this.taskName = taskName;
        }

        @Override
        public String getDescription() {
            return String.format("Empty resource list for task: %s", taskName);
        }

        @Override
        public void expect(ClusterState state, SchedulerDriver mockDriver) throws AssertionError {
            Optional<Protos.TaskInfo> task = new StateStore(persisterWithTasks).fetchTask(taskName);
            Assert.assertTrue(String.format("Task %s not found", taskName), task.isPresent());
            Assert.assertEquals(String.format("Expected zero resources, got: %s", task.get().getResourcesList()),
                    0, task.get().getResourcesCount());
        }
    }

    /**
     * Expects that the decommission plan has a specified composition/statuses.
     */
    private static class ExpectDecommissionPlanProgress implements Expect {
        private final List<StepCount> stepCounts;

        private ExpectDecommissionPlanProgress(List<StepCount> stepCounts) {
            this.stepCounts = stepCounts;
        }

        @Override
        public String getDescription() {
            return String.format("Decommission plan with phase steps: %s", stepCounts);
        }

        @Override
        public void expect(ClusterState state, SchedulerDriver mockDriver) throws AssertionError {
            Map<String, Plan> plans = state.getPlans().stream()
                    .collect(Collectors.toMap(Plan::getName, p -> p));
            Assert.assertEquals(3, plans.size());

            // Deploy plan: complete, world phase is empty
            Plan plan = plans.get("deploy");
            Assert.assertEquals(Status.COMPLETE, plan.getStatus());
            Assert.assertEquals(Arrays.asList("hello", "world"),
                    plan.getChildren().stream().map(Phase::getName).collect(Collectors.toList()));
            Map<String, Phase> phases = plan.getChildren().stream()
                    .collect(Collectors.toMap(Phase::getName, p -> p));
            Assert.assertEquals(Arrays.asList("hello-0:[server]"),
                    phases.get("hello").getChildren().stream().map(Step::getName).collect(Collectors.toList()));
            Assert.assertTrue(phases.get("world").getChildren().isEmpty());

            // Recovery plan: still complete and empty
            plan = plans.get("recovery");
            Assert.assertEquals(Status.COMPLETE, plan.getStatus());
            Assert.assertTrue(plan.getChildren().isEmpty());

            // Decommission: in_progress: world-1 sent kill and world-0 still pending
            plan = plans.get("decommission");

            boolean allStepsPending = stepCounts.stream().allMatch(sc -> sc.completedCount == 0 && sc.preparedCount == 0);
            boolean allStepsComplete = stepCounts.stream().allMatch(sc -> sc.pendingCount == 0 && sc.preparedCount == 0);
            final Status expectedPlanStatus;
            if (allStepsPending) {
                expectedPlanStatus = Status.PENDING;
            } else if (allStepsComplete) {
                expectedPlanStatus = Status.COMPLETE;
            } else {
                expectedPlanStatus = Status.IN_PROGRESS;
            }
            Assert.assertEquals(expectedPlanStatus, plan.getStatus());
            Assert.assertEquals(Arrays.asList("world-1", "world-0"),
                    plan.getChildren().stream().map(Phase::getName).collect(Collectors.toList()));
            phases = plan.getChildren().stream()
                    .collect(Collectors.toMap(Phase::getName, p -> p));
            Assert.assertEquals(stepCounts.size(), phases.size());
            for (StepCount stepCount : stepCounts) {
                Assert.assertEquals(getStepStatuses(state, stepCount),
                        phases.get(stepCount.phaseName).getChildren().stream()
                                .collect(Collectors.toMap(Step::getName, Step::getStatus)));
            }
        }

        private static Map<String, Status> getStepStatuses(ClusterState state, StepCount stepCount) {
            Map<String, Status> expectedSteps = new HashMap<>();
            expectedSteps.put(String.format("kill-%s-server", stepCount.phaseName),
                    stepCount.statusOfStepIndex(expectedSteps.size()));
            for (String resourceId :
                ResourceUtils.getResourceIds(ResourceUtils.getAllResources(state.getLastLaunchedPod(stepCount.phaseName)))) {
                expectedSteps.put(String.format("unreserve-%s", resourceId),
                        stepCount.statusOfStepIndex(expectedSteps.size()));
            }
            expectedSteps.put(String.format("erase-%s-server", stepCount.phaseName),
                    stepCount.statusOfStepIndex(expectedSteps.size()));
            return expectedSteps;
        }
    }

    /**
     * Runs a default hello world deployment and returns the persisted state that resulted.
     */
    private ServiceTestResult runDefaultDeployment() throws Exception {
        Collection<SimulationTick> ticks = new ArrayList<>();

        ticks.add(Send.register());

        ticks.add(Expect.reconciledImplicitly());

        // Verify that service launches 1 hello pod then 2 world pods.
        ticks.add(Send.offerBuilder("hello").build());
        ticks.add(Expect.launchedTasks("hello-0-server"));

        // Send another offer before hello-0 is finished:
        ticks.add(Send.offerBuilder("world").build());
        ticks.add(Expect.declinedLastOffer());

        // Running, no readiness check is applicable:
        ticks.add(Send.taskStatus("hello-0-server", Protos.TaskState.TASK_RUNNING).build());

        // Now world-0 will deploy:
        ticks.add(Send.offerBuilder("world").build());
        ticks.add(Expect.launchedTasks("world-0-server"));

        // world-0 has a readiness check, so the scheduler is waiting for that:
        ticks.add(Send.taskStatus("world-0-server", Protos.TaskState.TASK_RUNNING).build());
        ticks.add(Send.offerBuilder("world").build());
        ticks.add(Expect.declinedLastOffer());

        // With world-0's readiness check passing, world-1 still won't launch due to a hostname placement constraint:
        ticks.add(Send.taskStatus("world-0-server", Protos.TaskState.TASK_RUNNING).setReadinessCheckExitCode(0).build());
        ticks.add(Send.offerBuilder("world").build());
        ticks.add(Expect.declinedLastOffer());

        // world-1 will finally launch if the offered hostname is different:
        ticks.add(Send.offerBuilder("world").setHostname("host-foo").build());
        ticks.add(Expect.launchedTasks("world-1-server"));
        ticks.add(Send.taskStatus("world-1-server", Protos.TaskState.TASK_RUNNING).setReadinessCheckExitCode(0).build());

        // No more worlds to launch:
        ticks.add(Send.offerBuilder("world").setHostname("host-bar").build());
        ticks.add(Expect.declinedLastOffer());

        ticks.add(Expect.allPlansComplete());

        return new ServiceTestRunner().run(ticks);
    }

    /**
     * Validates all service specs in the hello-world examples/ directory.
     */
    @Test
    public void testExampleSpecs() throws Exception {
        // Some example files may require additional custom scheduler envvars:
        Map<String, Map<String, String>> schedulerEnvForExamples = new HashMap<>();
        schedulerEnvForExamples.put("secrets.yml", toMap(
                "HELLO_SECRET1", "hello-world/secret1",
                "HELLO_SECRET2", "hello-world/secret2",
                "WORLD_SECRET1", "hello-world/secret1",
                "WORLD_SECRET2", "hello-world/secret2",
                "WORLD_SECRET3", "hello-world/secret3"));

        // Iterate over yml files in dist/examples/, run sanity check for each:
        File[] exampleFiles = ServiceTestRunner.getDistFile("examples").listFiles();
        Assert.assertNotNull(exampleFiles);
        Assert.assertTrue(exampleFiles.length != 0);
        for (File examplesFile : exampleFiles) {
            ServiceTestRunner serviceTestRunner = new ServiceTestRunner(examplesFile);
            Map<String, String> schedulerEnv = schedulerEnvForExamples.get(examplesFile.getName());
            if (schedulerEnv != null) {
                serviceTestRunner.setSchedulerEnv(schedulerEnv);
            }
            try {
                serviceTestRunner.run();
            } catch (Exception e) {
                throw new Exception(String.format(
                        "Failed to render %s: %s", examplesFile.getAbsolutePath(), e.getMessage()), e);
            }
        }
    }

    private static Map<String, String> toMap(String... keyVals) {
        Map<String, String> map = new HashMap<>();
        if (keyVals.length % 2 != 0) {
            throw new IllegalArgumentException(String.format(
                    "Expected an even number of arguments [key, value, key, value, ...], got: %d",
                    keyVals.length));
        }
        for (int i = 0; i < keyVals.length; i += 2) {
            map.put(keyVals[i], keyVals[i + 1]);
        }
        return map;
    }
}
