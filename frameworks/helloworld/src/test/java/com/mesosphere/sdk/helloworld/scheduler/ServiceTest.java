package com.mesosphere.sdk.helloworld.scheduler;

import com.mesosphere.sdk.offer.CommonIdUtils;
import com.mesosphere.sdk.offer.ResourceUtils;
import com.mesosphere.sdk.scheduler.plan.DefaultPhase;
import com.mesosphere.sdk.scheduler.plan.Phase;
import com.mesosphere.sdk.scheduler.plan.Plan;
import com.mesosphere.sdk.scheduler.plan.PodInstanceRequirement;
import com.mesosphere.sdk.scheduler.plan.Status;
import com.mesosphere.sdk.scheduler.plan.Step;
import com.mesosphere.sdk.scheduler.plan.strategy.SerialStrategy;
import com.mesosphere.sdk.scheduler.recovery.RecoveryPlanOverrider;
import com.mesosphere.sdk.scheduler.recovery.RecoveryPlanOverriderFactory;
import com.mesosphere.sdk.scheduler.recovery.RecoveryStep;
import com.mesosphere.sdk.scheduler.recovery.RecoveryType;
import com.mesosphere.sdk.scheduler.recovery.constrain.UnconstrainedLaunchConstrainer;
import com.mesosphere.sdk.state.StateStore;
import com.mesosphere.sdk.state.StateStoreUtils;
import com.mesosphere.sdk.storage.Persister;
import com.mesosphere.sdk.testing.AcceptEntry;
import com.mesosphere.sdk.testing.ClusterState;
import com.mesosphere.sdk.testing.Expect;
import com.mesosphere.sdk.testing.LaunchedTask;
import com.mesosphere.sdk.testing.Send;
import com.mesosphere.sdk.testing.ServiceTestResult;
import com.mesosphere.sdk.testing.ServiceTestRunner;
import com.mesosphere.sdk.testing.SimulationTick;

import org.apache.mesos.Protos;
import org.apache.mesos.SchedulerDriver;
import org.junit.After;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mockito;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Tests for the hello world service and its example yml files.
 */
public class ServiceTest {

  private static final String VALID_HOSTNAME_CONSTRAINT = "[[\"hostname\", \"UNIQUE\"]]";

  private static final String INVALID_HOSTNAME_CONSTRAINT = "[[\\\"hostname\\\", \"UNIQUE\"]]";

  @After
  public void afterTest() {
    Mockito.validateMockitoUsage();
  }

  /**
   * Validates service deployment in the default configuration case.
   */
  @Test
  public void testDefaultDeployment() throws Exception {
    ServiceTestResult result = new ServiceTestRunner().run(getDefaultDeploymentTicks());
    // After deployment completed, service should have stored that fact to ZK:
    Assert.assertTrue(StateStoreUtils.getDeploymentWasCompleted(new StateStore(result.getPersister())));
  }

  /**
   * Validates service deployment in the default configuration case, but within custom namespaces.
   */
  @Test
  public void testDefaultDeploymentWithNamespace() throws Exception {
    // Exercise slashes in name:
    ServiceTestResult result = new ServiceTestRunner()
        .enableMultiService("frameworkName")
        .setOptions("service.name", "/path/to/namespace")
        .run(getDefaultDeploymentTicks());
    // Validate that nothing was stored under the default root persister paths:
    checkNotFound(result.getPersister(), "/Tasks");
    checkNotFound(result.getPersister(), "/Configurations");
    byte[] frameworkId = result.getPersister().get("/FrameworkID");
    checkNamespace(result, "path.to.namespace", "/path/to/namespace", "/Services/path__to__namespace");

    // A different service name (and namespace) should ignore the state of the first namespace:
    result = new ServiceTestRunner()
        .setState(result)
        .enableMultiService("frameworkName")
        .setOptions("service.name", "test-namespace")
        .run(getDefaultDeploymentTicks());
    // Again, nothing stored under the default root persister paths, but prior namespace IS present:
    checkNotFound(result.getPersister(), "/Tasks");
    checkNotFound(result.getPersister(), "/Configurations");
    Assert.assertEquals(3, result.getPersister().getChildren("/Services/path__to__namespace/Tasks").size());
    Assert.assertEquals(1, result.getPersister().getChildren("/Services/path__to__namespace/Configurations").size());
    Assert.assertArrayEquals(frameworkId, result.getPersister().get("/FrameworkID"));
    checkNamespace(result, "test-namespace", "test-namespace", "/Services/test-namespace");

    // No-namespace should ignore both of the above:
    result = new ServiceTestRunner()
        .setState(result)
        // default service name: hello-world
        .run(getDefaultDeploymentTicks());
    // Finally, all three sets should be present. In practice this can't happen because of a schema version check in
    // ServiceRunner, but this test suite doesn't exercise that code.
    Assert.assertEquals(3, result.getPersister().getChildren("/Services/path__to__namespace/Tasks").size());
    Assert.assertEquals(1, result.getPersister().getChildren("/Services/path__to__namespace/Configurations").size());
    Assert.assertEquals(3, result.getPersister().getChildren("/Services/test-namespace/Tasks").size());
    Assert.assertEquals(1, result.getPersister().getChildren("/Services/test-namespace/Configurations").size());
    Assert.assertArrayEquals(frameworkId, result.getPersister().get("/FrameworkID"));
    checkNamespace(result, "hello-world", null, "");
  }

  private static void checkNotFound(Persister persister, String path) {
    try {
      persister.getChildren(path);
      Assert.fail("Expected not found: " + path);
    } catch (Exception e) {
      // expected
    }
  }

  private static void checkNamespace(
      ServiceTestResult result, String sanitizedServiceName, String resourceNamespace, String persisterPrefix) throws Exception
  {
    Collection<String> taskNames = Arrays.asList("hello-0-server", "world-0-server", "world-1-server");
    // Persister: everything under a specified prefix (or no prefix).
    Assert.assertEquals(new TreeSet<>(taskNames),
        result.getPersister().getChildren(persisterPrefix + "/Tasks"));
    Assert.assertEquals(1, result.getPersister().getChildren(persisterPrefix + "/Configurations").size());

    for (String taskName : taskNames) {
      LaunchedTask launchedTask = result.getClusterState().getLastLaunchedTask(taskName);

      // Each task should have a taskId and executorId containing the service name, regardless of namespacing:
      Assert.assertEquals(sanitizedServiceName, CommonIdUtils.toSanitizedServiceName(launchedTask.getExecutor().getExecutorId()).get());
      Assert.assertEquals(sanitizedServiceName, CommonIdUtils.toSanitizedServiceName(launchedTask.getTask().getTaskId()).get());

      if (resourceNamespace != null) {
        // All task+executor resources should have a 'namespace' label
        for (Protos.Resource resource : ResourceUtils.getAllResources(launchedTask.getTask())) {
          Assert.assertEquals(resourceNamespace, ResourceUtils.getNamespace(resource).get());
        }
      } else {
        // All task+executor resources should NOT have a 'namespace' label
        for (Protos.Resource resource : ResourceUtils.getAllResources(launchedTask.getTask())) {
          Assert.assertFalse(ResourceUtils.getNamespace(resource).isPresent());
        }
      }
    }
  }

  /**
   * Validates that the update plan is correctly used (only) after a deployment had completed.
   */
  @Test
  public void testUpdatePlan() throws Exception {

    // First session: Launch one task, then restart scheduler before it's RUNNING:

    Collection<SimulationTick> ticks = new ArrayList<>();
    ticks.add(Send.register());
    ticks.add(Expect.reconciledImplicitly());

    // Verify that service launches 1 hello pod (and nothing else):
    ticks.add(Expect.deployStepStatus("hello-deploy", "hello-0:[server]", Status.PENDING));
    ticks.add(Send.offerBuilder("hello").build());
    ticks.add(Expect.launchedTasks("hello-0-server"));
    ticks.add(Expect.deployStepStatus("hello-deploy", "hello-0:[server]", Status.STARTING));

    // Offers revived due to changed work set (NULL => hello-0):
    ticks.add(Expect.revivedOffers(1));
    ticks.add(Expect.suppressedOffers(0));

    // Pretend that the scheduler was restarted before the task started RUNNING:
    ServiceTestResult result = new ServiceTestRunner("update_plan.yml").run(ticks);
    // Deployment didn't complete:
    Assert.assertFalse(StateStoreUtils.getDeploymentWasCompleted(new StateStore(result.getPersister())));

    // Second session: Scheduler restarts before task starts running.
    // The task gets launched again, and succeeds this time, finishing the deploy plan.

    ticks = new ArrayList<>();
    ticks.add(Send.register());
    ticks.add(Expect.reconciledExplicitly(result.getPersister()));
    // The task is now running, but we still restart it below (shouldn't need to relaunch, could revisit this behavior):
    ticks.add(Send.taskStatus("hello-0-server", Protos.TaskState.TASK_RUNNING).build());
    ticks.add(Expect.reconciledImplicitly());

    // Send an offer to turn the crank. The task gets restarted since it hadn't completed in the last run:
    ticks.add(Expect.deployStepStatus("hello-deploy", "hello-0:[server]", Status.PENDING));
    ticks.add(Send.offerBuilder("hello").build());
    ticks.add(Expect.taskNameKilled("hello-0-server", 1));

    // Now send the resources from hello-0. The task should get relaunched:
    ticks.add(Send.offerBuilder("hello").setPodIndexToReoffer(0).build());
    ticks.add(Expect.launchedTasks("hello-0-server"));
    ticks.add(Expect.deployStepStatus("hello-deploy", "hello-0:[server]", Status.STARTING));
    ticks.add(Send.taskStatus("hello-0-server", Protos.TaskState.TASK_RUNNING).build());

    // Now all tasks are done (and note use of 'hello-deploy' phase name):
    ticks.add(Expect.deployStepStatus("hello-deploy", "hello-0:[server]", Status.COMPLETE));
    ticks.add(Expect.allPlansComplete());

    // Suppress offers after the next offer has turned the crank:
    ticks.add(Expect.suppressedOffers(0));
    ticks.add(Send.offerBuilder("hello").build());
    ticks.add(Expect.declinedLastOffer());
    ticks.add(Expect.suppressedOffers(1));

    result = new ServiceTestRunner("update_plan.yml").setState(result).run(ticks);
    // After deployment completed, service should have stored that fact to ZK:
    Assert.assertTrue(StateStoreUtils.getDeploymentWasCompleted(new StateStore(result.getPersister())));

    // Third session: Scheduler restarts again. This time it's using the update plan as the 'deploy' plan:

    ticks = new ArrayList<>();
    ticks.add(Send.register());
    ticks.add(Expect.reconciledExplicitly(result.getPersister()));
    ticks.add(Send.taskStatus("hello-0-server", Protos.TaskState.TASK_RUNNING).build());
    ticks.add(Expect.reconciledImplicitly());

    // No config changes, nothing left to do:
    ticks.add(Send.offerBuilder("hello").build());
    ticks.add(Expect.declinedLastOffer());

    // "deploy" plan should match our expected update plan (note 'hello-update' phase name):
    ticks.add(Expect.deployStepStatus("hello-update", "hello-0:[server]", Status.COMPLETE));
    ticks.add(Expect.suppressedOffers(1));
    ticks.add(Expect.allPlansComplete());

    new ServiceTestRunner("update_plan.yml").setState(result).run(ticks);
    // Deployment bit still set in ZK:
    Assert.assertTrue(StateStoreUtils.getDeploymentWasCompleted(new StateStore(result.getPersister())));
  }

  /**
   * Checks that if an unessential task in a pod fails, that the other task in the same pod is unaffected.
   */
  @Test
  public void testNonessentialTaskFailure() throws Exception {
    testRecoverEssentialOrNonessential(false);
  }

  /**
   * Checks that if an essential task in a pod fails, that all tasks in the pod are relaunched.
   */
  @Test
  public void testEssentialTaskFailure() throws Exception {
    testRecoverEssentialOrNonessential(true);
  }

  private static void testRecoverEssentialOrNonessential(boolean essential) throws Exception {
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

    if (essential) {
      // When essential "server" task fails, both server+agent are relaunched:
      ticks.add(Send.taskStatus("hello-0-essential", Protos.TaskState.TASK_FAILED).build());
    } else {
      // When non-essential "agent" task fails, only agent task is relaunched, server task is unaffected:
      ticks.add(Send.taskStatus("hello-0-nonessential", Protos.TaskState.TASK_FAILED).build());
    }

    // Turn the crank with an arbitrary offer so that the failure is processed.
    // This also tests that the task is still tied to its prior location by checking that the offer is declined.
    ticks.add(Send.offerBuilder("hello").build());
    ticks.add(Expect.declinedLastOffer());
    if (essential) {
      // Only the agent task is killed: server is already in a terminal state
      ticks.add(Expect.taskNameKilled("hello-0-nonessential", 1));
      ticks.add(Expect.taskNameNotKilled("hello-0-essential"));
    } else {
      // Neither task should be killed: server should be unaffected, and agent is already in a terminal state
      ticks.add(Expect.taskNameNotKilled("hello-0-nonessential"));
      ticks.add(Expect.taskNameNotKilled("hello-0-essential"));
    }

    // Send the matching offer to relaunch both the server and agent:
    ticks.add(Send.offerBuilder("hello").setPodIndexToReoffer(0).build());

    if (essential) {
      ticks.add(Expect.launchedTasks("hello-0-essential", "hello-0-nonessential"));
      ticks.add(Send.taskStatus("hello-0-nonessential", Protos.TaskState.TASK_RUNNING).build());
      ticks.add(Send.taskStatus("hello-0-essential", Protos.TaskState.TASK_RUNNING).build());
    } else {
      ticks.add(Expect.launchedTasks("hello-0-nonessential"));
      ticks.add(Send.taskStatus("hello-0-nonessential", Protos.TaskState.TASK_RUNNING).build());
    }

    ticks.add(Expect.allPlansComplete());

    // Matching ExecutorInfo == same pod:
    ticks.add(Expect.samePod("hello-0-essential", "hello-0-nonessential"));

    new ServiceTestRunner("nonessential_tasks.yml").run(ticks);
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

    // Unknown task that we made up above was killed, but the launched task was not killed:
    ticks.add(Expect.taskIdKilled(taskId));
    ticks.add(Expect.taskNameNotKilled("hello-0-server"));

    new ServiceTestRunner("simple.yml").run(ticks);
  }

  /**
   * Tests scheduler behavior when the number of {@code world} pods is reduced.
   */
  @Test
  public void testHelloDecommissionNotAllowed() throws Exception {
    // Simulate an initial deployment with default of 2 world nodes (and 1 hello node):
    ServiceTestResult result = new ServiceTestRunner().run(getDefaultDeploymentTicks());
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

    new ServiceTestRunner()
        .setOptions("hello.count", "0")
        .setState(result)
        .run(ticks);
  }


  /**
   * Tests scheduler behavior when the number of {@code world} pods is reduced.
   */
  @Test
  public void testWorldDecommission() throws Exception {
    // Simulate an initial deployment with default of 2 world nodes (and 1 hello node):
    ServiceTestResult result = new ServiceTestRunner().run(getDefaultDeploymentTicks());
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

    // When default executor is being used, three additional resources need to be unreserved.
    int stepCount = 9;

    // Check initial plan state
    ticks.add(new ExpectDecommissionPlanProgress(Arrays.asList(
        new StepCount("world-1", stepCount, 0, 0), new StepCount("world-0", stepCount, 0, 0))));

    // Need to send an offer to trigger the implicit reconciliation.
    ticks.add(Send.offerBuilder("hello").build());
    ticks.add(Expect.reconciledImplicitly());
    ticks.add(Expect.declinedLastOffer());

    // Check plan state after an offer came through: world-1-server killed
    ticks.add(new ExpectDecommissionPlanProgress(Arrays.asList(
        new StepCount("world-1", stepCount - 1, 0, 1), new StepCount("world-0", stepCount, 0, 0))));
    ticks.add(Expect.taskNameKilled("world-1-server", 1));

    // Offer world-0 resources and check that nothing happens (haven't gotten there yet):
    ticks.add(Send.offerBuilder("world").setPodIndexToReoffer(0).build());
    ticks.add(new ExpectDecommissionPlanProgress(Arrays.asList(
        new StepCount("world-1", stepCount - 2, 1, 1), new StepCount("world-0", stepCount, 0, 0))));

    // Offer world-1 resources and check that world-1 resources are wiped:
    ticks.add(Send.offerBuilder("world").setPodIndexToReoffer(1).build());
    ticks.add(Expect.unreservedTasks("world-1-server"));
    ticks.add(new ExpectDecommissionPlanProgress(Arrays.asList(
        new StepCount("world-1", 1, 0, stepCount - 1), new StepCount("world-0", stepCount, 0, 0)))); // FAIL
    ticks.add(new ExpectEmptyResources(result.getPersister(), "world-1-server"));

    // Turn the crank with an arbitrary offer to finish erasing world-1:
    ticks.add(Expect.knownTasks(result.getPersister(), "hello-0-server", "world-0-server", "world-1-server"));
    ticks.add(Send.offerBuilder("world").build());
    ticks.add(Expect.declinedLastOffer());
    ticks.add(Expect.knownTasks(result.getPersister(), "hello-0-server", "world-0-server"));
    ticks.add(new ExpectDecommissionPlanProgress(Arrays.asList(
        new StepCount("world-1", 0, 0, stepCount), new StepCount("world-0", stepCount, 0, 0))));

    // Now let's proceed with decommissioning world-0. This time a single offer with the correct resources results
    // in both killing/flagging the task, and clearing its resources:
    ticks.add(Send.offerBuilder("world").setPodIndexToReoffer(0).build());
    ticks.add(new ExpectDecommissionPlanProgress(Arrays.asList(
        new StepCount("world-1", 0, 0, stepCount), new StepCount("world-0", 1, 0, stepCount - 1))));
    ticks.add(Expect.taskNameKilled("world-0-server", 1));
    ticks.add(new ExpectEmptyResources(result.getPersister(), "world-0-server"));

    // Turn the crank once again to erase the world-0 stub:
    ticks.add(Expect.knownTasks(result.getPersister(), "hello-0-server", "world-0-server"));
    ticks.add(Send.offerBuilder("world").build());
    ticks.add(Expect.declinedLastOffer());
    ticks.add(Expect.knownTasks(result.getPersister(), "hello-0-server"));
    ticks.add(new ExpectDecommissionPlanProgress(Arrays.asList(
        new StepCount("world-1", 0, 0, stepCount), new StepCount("world-0", 0, 0, stepCount))));

    ticks.add(Expect.allPlansComplete());

    new ServiceTestRunner()
        .setOptions("world.count", "0")
        .setState(result)
        .run(ticks);
  }

  /**
   * Verifies that the transition from a transient failure (task failed) to permanent failure (task replace issued) is
   * still handled correctly when a custom recovery plan is being used.
   */
  @Test
  public void transientToCustomPermanentFailureTransition() throws Exception {
    final String recoveryPhaseName = "custom-hello-recovery";
    Optional<RecoveryPlanOverriderFactory> overrider = Optional.of(new RecoveryPlanOverriderFactory() {
      @Override
      public RecoveryPlanOverrider create(StateStore stateStore, Collection<Plan> plans) {
        return new RecoveryPlanOverrider() {
          @Override
          public Optional<Phase> override(PodInstanceRequirement podInstanceRequirement) {
            if (podInstanceRequirement.getPodInstance().getPod().getType().equals("hello") &&
                podInstanceRequirement.getRecoveryType().equals(RecoveryType.PERMANENT))
            {
              Phase phase = new DefaultPhase(
                  recoveryPhaseName,
                  Arrays.asList(
                      new RecoveryStep(
                          podInstanceRequirement.getPodInstance().getName(),
                          podInstanceRequirement,
                          new UnconstrainedLaunchConstrainer(),
                          stateStore)),
                  new SerialStrategy<>(),
                  Collections.emptyList());

              return Optional.of(phase);
            }

            return Optional.empty();
          }
        };
      }
    });
    testTemporaryToPermanentFailure(
        overrider, Expect.recoveryStepStatus(recoveryPhaseName, "hello-0", Status.PREPARED));
  }

  /**
   * Verifies that the transition from a transient failure (task failed) to permanent failure (task replace issued) is
   * still handled correctly when the default recovery plan is being used.
   */
  @Test
  public void transientToDefaultPermanentFailureTransition() throws Exception {
    testTemporaryToPermanentFailure(
        Optional.empty(), Expect.recoveryStepStatus("hello-0:[server]", "hello-0:[server]", Status.PREPARED));
  }

  private void testTemporaryToPermanentFailure(
      Optional<RecoveryPlanOverriderFactory> overrider, Expect expectedRecoveryState) throws Exception
  {
    Collection<SimulationTick> ticks = new ArrayList<>();
    ticks.addAll(getDefaultDeploymentTicks());

    // Kill hello-0 to trigger transient recovery
    ticks.add(Send.taskStatus("hello-0-server", Protos.TaskState.TASK_FAILED).build());
    // Offers should have been revived (again) due to the failure.
    ticks.add(Expect.revivedOffers(3));
    // Send an unused offer to trigger an evaluation of the recovery plan
    ticks.add(Send.emptyOffers());
    // Expect default transient recovery triggered
    ticks.add(Expect.recoveryStepStatus("hello-0:[server]", "hello-0:[server]", Status.PREPARED));

    // Now trigger custom permanent replacement of that pod
    ticks.add(Send.replacePod("hello-0"));
    // Offers should have been revived again.
    ticks.add(Expect.revivedOffers(4));

    // Send an unused offer to trigger an evaluation of the recovery plan
    ticks.add(Send.emptyOffers());

    // Custom expectation not relevant to other tests
    Expect expectSingleRecoveryPhase = new Expect() {
      @Override
      public void expect(ClusterState state, SchedulerDriver mockDriver) throws AssertionError {
        Plan recoveryPlan = state.getPlans().stream()
            .filter(plan -> plan.getName().equals("recovery"))
            .findAny().get();

        Assert.assertEquals(1, recoveryPlan.getChildren().size());
      }

      @Override
      public String getDescription() {
        return "Single recovery phase";
      }
    };

    ticks.add(expectSingleRecoveryPhase);
    ticks.add(expectedRecoveryState);

    // Complete recovery
    ticks.add(Send.offerBuilder("hello").build());
    ticks.add(Expect.launchedTasks("hello-0-server"));
    ticks.add(Send.taskStatus("hello-0-server", Protos.TaskState.TASK_RUNNING).build());
    ticks.add(Expect.allPlansComplete());

    ServiceTestRunner runner = new ServiceTestRunner();
    if (overrider.isPresent()) {
      runner.setRecoveryManagerFactory(overrider.get());
    }
    runner.run(ticks);
  }

  /**
   * Verify recovery behavior when tasks need repair before, after, and during a configuration change.
   * - hello-0: Fail after reconfiguration is triggered, replaced to new location
   * - world-0: Fail before reconfiguration is triggered, restart in prior location
   * - world-1: Fail after reconfiguration is triggered, restart in prior location
   */
  @Test
  public void testFailureDuringDeployment() throws Exception {
    String oldEnv = "1000";
    String newEnv = "2000";

    // First run: service deploys successfully, then world-0 comes down.

    Collection<SimulationTick> ticks = new ArrayList<>();
    ticks.addAll(getDefaultDeploymentTicks());

    // Kill world-0 to trigger transient recovery before reconfiguration starts
    ticks.add(Send.taskStatus("world-0-server", Protos.TaskState.TASK_FAILED).build());
    // Offers should have been revived (again) due to the failure.
    ticks.add(Expect.revivedOffers(3));
    // Send an unused offer to trigger an evaluation of the recovery plan
    ticks.add(Send.emptyOffers());
    // Expect default transient recovery triggered
    ticks.add(Expect.recoveryStepStatus("world-0:[server]", "world-0:[server]", Status.PREPARED));

    ServiceTestResult result = new ServiceTestRunner().run(ticks);

    // Second run (with config change):
    // - world-0 already failed from previous run, and then gets bumped to permanent recovery by a replace call.
    //   Replaced in prior configuration before getting reconfigured.
    // - hello-0 failed while scheduler was gone, as manifested during reconciliation.
    //   Deployed in new configuration instead of getting recovered.
    // - world-1 failed after scheduler came back.
    //   Similarly to world-0 this is recovered before getting reconfigured, but as a transient recovery.

    ticks = new ArrayList<>();

    ticks.add(Send.register());

    // At reconciliation, world-1 has failed, while world-0 is not explicitly reconciled due to its terminal state
    ticks.add(Expect.reconciledExplicitly(result.getPersister()));
    ticks.add(Send.taskStatus("hello-0-server", Protos.TaskState.TASK_RUNNING).build());
    ticks.add(Send.taskStatus("world-1-server", Protos.TaskState.TASK_FAILED).build());

    // Send an unused offer to trigger an evaluation of the deploy/recovery plans:
    ticks.add(Send.emptyOffers());

    // Offers should have been revived due to the work change.
    ticks.add(Expect.revivedOffers(1));

    // Deployment and recovery now ready:
    ticks.add(Expect.taskNameKilled("hello-0-server", 1)); // killed for deploy plan
    ticks.add(Expect.deployStepCount(3));
    ticks.add(Expect.deployStepStatus("hello", "hello-0:[server]", Status.PREPARED));
    ticks.add(Expect.deployStepStatus("world", "world-0:[server]", Status.PENDING));
    ticks.add(Expect.deployStepStatus("world", "world-1:[server]", Status.PENDING));
    ticks.add(Expect.recoveryStepCount(2));
    ticks.add(Expect.recoveryStepStatus("world-0:[server]", "world-0:[server]", Status.PREPARED));
    ticks.add(Expect.recoveryStepStatus("world-1:[server]", "world-1:[server]", Status.PREPARED));

    // Finally, have the current hello-0 fail too, and upgrade world-0 to a permanent recovery:
    ticks.add(Send.taskStatus("hello-0-server", Protos.TaskState.TASK_FAILED).build());
    // Verify no additional revives from the status alone, since the active work set technically hasn't changed
    // (hello-0 was already deploying):
    ticks.add(Send.emptyOffers());
    ticks.add(Expect.revivedOffers(1));

    // Mark world-0 for permanent replacement, verify that this specifically triggers a revive.
    ticks.add(Send.replacePod("world-0"));
    // Send an unused offer to trigger an evaluation of the recovery plan and revive check.
    ticks.add(Send.emptyOffers());
    // Another revive at offer processing due to the recovery type upgrade.
    ticks.add(Expect.revivedOffers(2));

    // Now we're fully loaded, with hello-0 omitted from the recovery plan because it's dirtied by the deploy plan:
    ticks.add(Expect.deployStepCount(3));
    ticks.add(Expect.deployStepStatus("hello", "hello-0:[server]", Status.PREPARED));
    ticks.add(Expect.deployStepStatus("world", "world-0:[server]", Status.PENDING));
    ticks.add(Expect.deployStepStatus("world", "world-1:[server]", Status.PENDING));
    ticks.add(Expect.taskNameKilled("world-0-server", 1)); // killed regardless of current TASK_FAILED state
    ticks.add(Expect.recoveryStepCount(2));
    ticks.add(Expect.recoveryStepStatus("world-0:[server]", "world-0:[server]", Status.PREPARED));
    ticks.add(Expect.recoveryStepStatus("world-1:[server]", "world-1:[server]", Status.PREPARED));

    // Exercise recovery of world-0 before the deployment plan gets to it. Send a fresh offer since world-0 is being replaced:
    ticks.add(Send.offerBuilder("world").build());
    ticks.add(Expect.recoveryStepStatus("world-0:[server]", "world-0:[server]", Status.STARTING));
    ticks.add(Expect.launchedTasks("world-0-server"));
    ticks.add(Expect.taskEnv(result.getPersister(), "world-0-server", "SLEEP_DURATION", oldEnv));
    ticks.add(Send.taskStatus("world-0-server", Protos.TaskState.TASK_RUNNING).build());

    // Recovery shows world-0 is recovered while deploy plan is still waiting:
    ticks.add(Expect.deployStepCount(3));
    ticks.add(Expect.deployStepStatus("hello", "hello-0:[server]", Status.PREPARED));
    ticks.add(Expect.deployStepStatus("world", "world-0:[server]", Status.PENDING));
    ticks.add(Expect.deployStepStatus("world", "world-1:[server]", Status.PENDING));
    ticks.add(Expect.recoveryStepCount(2));
    ticks.add(Expect.recoveryStepStatus("world-0:[server]", "world-0:[server]", Status.COMPLETE));
    ticks.add(Expect.recoveryStepStatus("world-1:[server]", "world-1:[server]", Status.PREPARED));

    // hello-0 gets an offer. It's redeployed instead of recovered since deployment is given priority:
    ticks.add(Expect.taskNameKilled("hello-0-server", 1)); // killed issued to ensure resources are reoffered
    ticks.add(Send.offerBuilder("hello").setPodIndexToReoffer(0).build());
    ticks.add(Expect.deployStepStatus("hello", "hello-0:[server]", Status.STARTING));
    ticks.add(Expect.launchedTasks("hello-0-server"));
    ticks.add(Expect.taskEnv(result.getPersister(), "hello-0-server", "SLEEP_DURATION", newEnv));
    ticks.add(Send.taskStatus("hello-0-server", Protos.TaskState.TASK_RUNNING).build());

    // Check plans again with hello-0 deployed against the new config:
    ticks.add(Expect.deployStepCount(3));
    ticks.add(Expect.deployStepStatus("hello", "hello-0:[server]", Status.COMPLETE));
    ticks.add(Expect.deployStepStatus("world", "world-0:[server]", Status.PENDING));
    ticks.add(Expect.deployStepStatus("world", "world-1:[server]", Status.PENDING));
    ticks.add(Expect.recoveryStepCount(2));
    ticks.add(Expect.recoveryStepStatus("world-0:[server]", "world-0:[server]", Status.COMPLETE));
    ticks.add(Expect.recoveryStepStatus("world-1:[server]", "world-1:[server]", Status.PREPARED));


    // world-0 is now eligible for the new deployment and gets an offer:
    ticks.add(Send.emptyOffers()); // ping the offer queue to trigger the world-0-server deploy step
    ticks.add(Expect.taskNameKilled("world-0-server", 1)); // kill issued to ensure resources are reoffered
    ticks.add(Send.offerBuilder("world").setPodIndexToReoffer(0).build());
    ticks.add(Expect.deployStepStatus("world", "world-0:[server]", Status.STARTING));
    ticks.add(Expect.launchedTasks("world-0-server"));
    ticks.add(Expect.taskEnv(result.getPersister(), "world-0-server", "SLEEP_DURATION", newEnv));
    ticks.add(Send.taskStatus("world-0-server", Protos.TaskState.TASK_RUNNING).build());

    ticks.add(Expect.deployStepCount(3));
    ticks.add(Expect.deployStepStatus("hello", "hello-0:[server]", Status.COMPLETE));
    ticks.add(Expect.deployStepStatus("world", "world-0:[server]", Status.COMPLETE));
    ticks.add(Expect.deployStepStatus("world", "world-1:[server]", Status.PENDING));
    ticks.add(Expect.recoveryStepCount(2));
    ticks.add(Expect.recoveryStepStatus("world-0:[server]", "world-0:[server]", Status.COMPLETE));
    ticks.add(Expect.recoveryStepStatus("world-1:[server]", "world-1:[server]", Status.PREPARED));

    // now it's time for world-1, which is currently in a TASK_FAILED state. as such a kill request should NOT be issued:
    ticks.add(Send.emptyOffers()); // send a ping just to validate that nothing will happen
    ticks.add(Expect.taskNameNotKilled("world-1-server")); // should not be killed, already TASK_FAILED

    // world-1 now gets its offer as well, which gets handled by the recovery plan rather than the deploy plan:
    ticks.add(Send.offerBuilder("world").setPodIndexToReoffer(1).build());
    ticks.add(Expect.recoveryStepStatus("world-1:[server]", "world-1:[server]", Status.STARTING));
    ticks.add(Expect.launchedTasks("world-1-server"));
    // The task is not deployed into the new config yet:
    ticks.add(Expect.taskEnv(result.getPersister(), "world-1-server", "SLEEP_DURATION", oldEnv));
    ticks.add(Send.taskStatus("world-1-server", Protos.TaskState.TASK_RUNNING).build());

    ticks.add(Expect.deployStepCount(3));
    ticks.add(Expect.deployStepStatus("hello", "hello-0:[server]", Status.COMPLETE));
    ticks.add(Expect.deployStepStatus("world", "world-0:[server]", Status.COMPLETE));
    ticks.add(Expect.deployStepStatus("world", "world-1:[server]", Status.PENDING));
    ticks.add(Expect.recoveryStepCount(2));
    ticks.add(Expect.recoveryStepStatus("world-0:[server]", "world-0:[server]", Status.COMPLETE));
    ticks.add(Expect.recoveryStepStatus("world-1:[server]", "world-1:[server]", Status.COMPLETE));

    // Following the recovery, the world-1 task gets picked up by the deploy plan and bumped to the new config.
    ticks.add(Send.offerBuilder("world").setPodIndexToReoffer(1).build());
    ticks.add(Expect.deployStepStatus("world", "world-1:[server]", Status.STARTING));
    ticks.add(Expect.launchedTasks("world-1-server"));
    // Now the task has the new config:
    ticks.add(Expect.taskEnv(result.getPersister(), "world-1-server", "SLEEP_DURATION", newEnv));
    ticks.add(Send.taskStatus("world-1-server", Protos.TaskState.TASK_RUNNING).build());

    ticks.add(Expect.deployStepCount(3));
    ticks.add(Expect.deployStepStatus("hello", "hello-0:[server]", Status.COMPLETE));
    ticks.add(Expect.deployStepStatus("world", "world-0:[server]", Status.COMPLETE));
    ticks.add(Expect.deployStepStatus("world", "world-1:[server]", Status.COMPLETE));
    ticks.add(Expect.recoveryStepCount(2));
    ticks.add(Expect.recoveryStepStatus("world-0:[server]", "world-0:[server]", Status.COMPLETE));
    ticks.add(Expect.recoveryStepStatus("world-1:[server]", "world-1:[server]", Status.COMPLETE));

    new ServiceTestRunner()
        .setOptions("service.sleep", newEnv)
        .setState(result)
        .run(ticks);
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
      return String.format("phase=%s[pending=%d,prepared=%d,completed=%d]",
          phaseName, pendingCount, preparedCount, completedCount);
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
      Assert.assertEquals(stepCounts.stream().map(s -> s.phaseName).collect(Collectors.toList()),
          plan.getChildren().stream().map(Phase::getName).collect(Collectors.toList()));
      phases = plan.getChildren().stream()
          .collect(Collectors.toMap(Phase::getName, p -> p));
      Assert.assertEquals(stepCounts.size(), phases.size());
      for (StepCount stepCount : stepCounts) {
        Phase phase = phases.get(stepCount.phaseName);
        Map<String, Status> stepStatuses = phase.getChildren().stream()
            .collect(Collectors.toMap(
                Step::getName,
                Step::getStatus,
                (u, v) -> {
                  throw new IllegalStateException(String.format("Duplicate key %s", u));
                },
                TreeMap::new));
        Assert.assertEquals(
            String.format("Number of steps doesn't match expectation in %s: %s", stepCount, stepStatuses),
            stepCount.pendingCount + stepCount.preparedCount + stepCount.completedCount,
            phase.getChildren().size());
        Assert.assertEquals(
            String.format("Step statuses don't match expectation in %s", stepCount),
            getExpectedStepStatuses(state, stepCount),
            stepStatuses);
      }
    }

    private static Map<String, Status> getExpectedStepStatuses(ClusterState state, StepCount stepCount) {
      Map<String, Status> expectedSteps = new TreeMap<>();
      expectedSteps.put(String.format("kill-%s-server", stepCount.phaseName),
          stepCount.statusOfStepIndex(expectedSteps.size()));
      List<AcceptEntry> acceptCalls = state.getAcceptCalls(stepCount.phaseName);
      // Get information based on the LAST operation. This wouldn't work if the last operation didn't reserve
      // anything, as would occur when a task is restarted, but this is close enough for our purposes.
      AcceptEntry acceptCall = acceptCalls.get(acceptCalls.size() - 1);

      Collection<String> resourceIds = new ArrayList<>();
      resourceIds.addAll(ResourceUtils.getResourceIds(ResourceUtils.getAllResources(acceptCall.getTasks())));
      resourceIds.addAll(acceptCall.getExecutors().stream()
          .map(e -> ResourceUtils.getResourceIds(e.getResourcesList()))
          .flatMap(Collection::stream)
          .collect(Collectors.toList()));
      for (String resourceId : resourceIds) {
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
  private Collection<SimulationTick> getDefaultDeploymentTicks() throws Exception {
    Collection<SimulationTick> ticks = new ArrayList<>();

    ticks.add(Send.register());
    ticks.add(Expect.reconciledImplicitly());

    // Verify that service launches 1 hello pod then 2 world pods.
    ticks.add(Send.offerBuilder("hello").build());
    ticks.add(Expect.launchedTasks("hello-0-server"));

    // Offers revived due to changed work set (NULL => hello-0):
    ticks.add(Expect.revivedOffers(1));

    // Send another offer before hello-0 is finished:
    ticks.add(Send.offerBuilder("world").build());
    ticks.add(Expect.declinedLastOffer());

    // Running, no readiness check is applicable:
    ticks.add(Send.taskStatus("hello-0-server", Protos.TaskState.TASK_RUNNING).build());

    // Now world-0 will deploy:
    ticks.add(Send.offerBuilder("world").build());
    ticks.add(Expect.launchedTasks("world-0-server"));

    // Offers revived again due to changed work set (hello-0 => world-0):
    ticks.add(Expect.revivedOffers(2));

    // world-0 has a readiness check, so the scheduler is waiting for that:
    ticks.add(Send.taskStatus("world-0-server", Protos.TaskState.TASK_RUNNING).build());
    ticks.add(Send.offerBuilder("world").build());
    ticks.add(Expect.declinedLastOffer());

    // With world-0's readiness check passing, world-1 still won't launch due to a hostname placement constraint:
    ticks.add(Send.taskStatus("world-0-server", Protos.TaskState.TASK_RUNNING).setReadinessCheckExitCode(0).build());
    // Offers revived once more due to changed work set (world-0 => world-1):
    ticks.add(Expect.revivedOffers(3));
    ticks.add(Send.offerBuilder("world").build());
    ticks.add(Expect.declinedLastOffer());

    // world-1 will finally launch if the offered hostname is different:
    ticks.add(Send.offerBuilder("world").setHostname("host-foo").build());
    ticks.add(Expect.launchedTasks("world-1-server"));
    ticks.add(Send.taskStatus("world-1-server", Protos.TaskState.TASK_RUNNING).setReadinessCheckExitCode(0).build());

    // No more worlds to launch:
    ticks.add(Send.offerBuilder("world").setHostname("host-bar").build());
    ticks.add(Expect.declinedLastOffer());

    ticks.add(Expect.suppressedOffers(1));
    ticks.add(Expect.allPlansComplete());

    return ticks;
  }

  @Test
  public void testValidPlacementConstraint() throws Exception {
    new ServiceTestRunner()
        .setSchedulerEnv("HELLO_PLACEMENT", VALID_HOSTNAME_CONSTRAINT)
        .run(getDefaultDeploymentTicks());
  }

  @Test(expected = IllegalStateException.class)
  public void testInvalidPlacementConstraint() throws Exception {
    new ServiceTestRunner()
        .setSchedulerEnv("HELLO_PLACEMENT", INVALID_HOSTNAME_CONSTRAINT)
        .run(getDefaultDeploymentTicks());
  }

  @Test
  public void testSwitchToInvalidPlacementConstraint() throws Exception {
    ServiceTestResult initial = new ServiceTestRunner()
        .setSchedulerEnv("HELLO_PLACEMENT", VALID_HOSTNAME_CONSTRAINT)
        .run(getDefaultDeploymentTicks());

    Collection<SimulationTick> ticks = new ArrayList<>();
    ticks.add(Send.register());
    ticks.add(Expect.planStatus("deploy", Status.ERROR));

    new ServiceTestRunner()
        .setState(initial)
        .setSchedulerEnv("HELLO_PLACEMENT", INVALID_HOSTNAME_CONSTRAINT)
        .run(ticks);
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
    schedulerEnvForExamples.put("custom_steps.yml", toMap(
        "DEPLOY_STRATEGY", "serial",
        "DEPLOY_STEPS", "[[first, second, third]]"));
    schedulerEnvForExamples.put("pod-profile-mount-volume.yml", toMap(
        "HELLO_VOLUME_PROFILE", "xfs"));

    // Iterate over yml files in dist/examples/, run sanity check for each:
    File[] exampleFiles = ServiceTestRunner.getDistDir().listFiles();
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

  /**
   * Validates the default service spec.
   */
  @Test
  public void testDefaultSpec() throws Exception {
    new ServiceTestRunner().run();
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
