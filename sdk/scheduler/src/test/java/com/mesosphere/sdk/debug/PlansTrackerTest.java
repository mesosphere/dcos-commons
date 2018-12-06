package com.mesosphere.sdk.debug;

import com.mesosphere.sdk.debug.PlansTracker.SerializeElement;
import com.mesosphere.sdk.debug.PlansTracker.SerializePlan;
import com.mesosphere.sdk.debug.PlansTracker.SerializePhase;
import com.mesosphere.sdk.debug.PlansTracker.SerializeStep;
import com.mesosphere.sdk.debug.PlansTracker.SerializePlansTracker;
import com.mesosphere.sdk.scheduler.plan.DefaultPhase;
import com.mesosphere.sdk.scheduler.plan.DefaultPlan;
import com.mesosphere.sdk.scheduler.plan.DefaultPlanCoordinator;
import com.mesosphere.sdk.scheduler.plan.DefaultPlanManager;
import com.mesosphere.sdk.scheduler.plan.DefaultPodInstance;
import com.mesosphere.sdk.scheduler.plan.DeploymentStep;
import com.mesosphere.sdk.scheduler.plan.PlanManager;
import com.mesosphere.sdk.scheduler.plan.PodInstanceRequirement;
import com.mesosphere.sdk.scheduler.plan.Status;
import com.mesosphere.sdk.scheduler.plan.strategy.SerialStrategy;
import com.mesosphere.sdk.specification.PodInstance;
import com.mesosphere.sdk.specification.PodSpec;
import com.mesosphere.sdk.specification.TaskSpec;
import com.mesosphere.sdk.state.FrameworkStore;
import com.mesosphere.sdk.state.StateStore;
import com.mesosphere.sdk.storage.MemPersister;
import com.mesosphere.sdk.storage.Persister;
import com.mesosphere.sdk.testutils.TestConstants;
import com.mesosphere.sdk.testutils.TestPodFactory;

import java.util.Arrays;
import java.util.Collections;
import java.util.Optional;
import java.util.List;
import java.util.stream.Collectors;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

/**
 * This class tests the {@link PlansTracker} class
 */
public class PlansTrackerTest {

  //Common task values
  private static final int TASK_COUNT = 1;
  private static final double TASK_CPU = 1.0;
  private static final double TASK_MEM = 1000.0;
  private static final double TASK_DISK = 1500.0;
  
  //Specific values.
  private static final String TASK_HELLO_POD_NAME = "hello";
  private static final String TASK_HELLO_NAME = "hello";
  private static final String TASK_HELLO_CMD = "echo " + TASK_HELLO_NAME; 
  
  private static final String TASK_WORLD_POD_NAME = "world";
  private static final String TASK_WORLD_NAME = "world";
  private static final String TASK_WORLD_CMD = "echo " + TASK_WORLD_NAME; 

  private static final PodSpec helloPodSpec = TestPodFactory.getPodSpec(
          TASK_HELLO_POD_NAME,
          TestConstants.RESOURCE_SET_ID + "-hello",
          TASK_HELLO_NAME,
          TASK_HELLO_CMD,
          TestConstants.SERVICE_USER,
          TASK_COUNT,
          TASK_CPU,
          TASK_MEM,
          TASK_DISK); 
  
  private static final PodSpec worldPodSpec = TestPodFactory.getPodSpec(
          TASK_WORLD_POD_NAME,
          TestConstants.RESOURCE_SET_ID + "-world",
          TASK_WORLD_NAME,
          TASK_WORLD_CMD,
          TestConstants.SERVICE_USER,
          TASK_COUNT,
          TASK_CPU,
          TASK_MEM,
          TASK_DISK); 
 
  private PodInstanceRequirement helloPodInstanceRequirement = getPodInstanceRequirement(helloPodSpec, 0);
  private PodInstanceRequirement worldPodInstanceRequirement = getPodInstanceRequirement(worldPodSpec, 0);
  
  private StateStore stateStore;
  private DefaultPlanCoordinator coordinator;  

  @Before
  public void setupTest() throws Exception {
    MockitoAnnotations.initMocks(this);

    Persister persister = MemPersister.newBuilder().build();
    FrameworkStore frameworkStore = new FrameworkStore(persister);
    frameworkStore.storeFrameworkId(TestConstants.FRAMEWORK_ID);
    stateStore = new StateStore(persister);
    
    DeploymentStep helloStep = new DeploymentStep("hello-step",
        helloPodInstanceRequirement,
        stateStore,
        Optional.empty());
    DeploymentStep world1Step = new DeploymentStep("world-step-1",
        worldPodInstanceRequirement,
        stateStore,
        Optional.empty());
    DeploymentStep world2Step = new DeploymentStep("world-step-2",
        worldPodInstanceRequirement,
        stateStore,
        Optional.empty());
    
    //Decorate various parts for verification later..
    helloStep.updateInitialStatus(Status.COMPLETE);
    world1Step.updateInitialStatus(Status.IN_PROGRESS);
    world2Step.updateInitialStatus(Status.PENDING);
   
    DefaultPhase helloPhase = new DefaultPhase("hello-deploy",
        Arrays.asList(helloStep),
        new SerialStrategy<>(),
        Collections.emptyList());
    DefaultPhase worldPhase = new DefaultPhase("world-deploy",
        Arrays.asList(world1Step, world2Step),
        new SerialStrategy<>(),
        Collections.emptyList());
    DefaultPlan helloWorldPlan = new DefaultPlan("deploy",
        Arrays.asList(helloPhase, worldPhase),
        new SerialStrategy<>(),
        Collections.emptyList());
    
    PlanManager helloWorldPlanManager = DefaultPlanManager.createProceeding(helloWorldPlan);
    coordinator = new DefaultPlanCoordinator(Optional.empty(), Arrays.asList(helloWorldPlanManager));
  }
      
  private PodInstanceRequirement getPodInstanceRequirement(PodSpec podSpec, int index) {
    PodInstance podInstance = new DefaultPodInstance(podSpec, index);
    return PodInstanceRequirement.newBuilder(
                podInstance,
                podSpec.getTasks().stream().map(TaskSpec::getName).collect(Collectors.toList()))
                .build();
  }
  
  @Test
  public void testHelloWorldPlanUnfiltered()
  {
    PlansTracker spyPlansTracker = Mockito.spy(new PlansTracker(coordinator, stateStore));
    SerializePlansTracker serializedTracker = spyPlansTracker.generateServiceStatus(
        null,
        null,
        null);

    Assert.assertEquals(PlansTracker.SchedulerState.DEPLOYING, serializedTracker.getSchedulerState());
    Assert.assertEquals(1, serializedTracker.getActivePlans().size());
    Assert.assertEquals(true, serializedTracker.getActivePlans().contains("deploy"));

    //Verify plans tree.
    Assert.assertNotNull(serializedTracker.getPlans());
    Assert.assertEquals(1, serializedTracker.getPlans().size());
    SerializePlan plan = serializedTracker.getPlans().get(0);

    Assert.assertEquals(3, plan.getTotalSteps());
    Assert.assertEquals(1, plan.getCompletedSteps());
    Assert.assertEquals("deploy", plan.getName());
    Assert.assertEquals("serial", plan.getStrategy());
    Assert.assertEquals(Status.IN_PROGRESS.name(), plan.getStatus());

    Assert.assertNotNull(plan.getPhases());
    Assert.assertEquals(2, plan.getPhases().size());

    SerializePhase helloPhase = plan.getPhases().get(0);

    Assert.assertEquals("hello-deploy", helloPhase.getName());
    Assert.assertEquals("serial", helloPhase.getStrategy());
    Assert.assertEquals(Status.COMPLETE.name(), helloPhase.getStatus());

    Assert.assertNotNull(helloPhase.getSteps());
    Assert.assertEquals(1, helloPhase.getSteps().size());

    SerializeStep helloStep = helloPhase.getSteps().get(0);

    Assert.assertEquals("hello-step", helloStep.getName());
    Assert.assertEquals(Status.COMPLETE.name(), helloStep.getStatus());
    Assert.assertEquals(Collections.emptyList(),helloStep.getErrors());

    SerializePhase worldPhase = plan.getPhases().get(1);

    Assert.assertEquals("world-deploy", worldPhase.getName());
    Assert.assertEquals("serial", worldPhase.getStrategy());
    Assert.assertEquals(Status.IN_PROGRESS.name(), worldPhase.getStatus());

    Assert.assertNotNull(worldPhase.getSteps());
    Assert.assertEquals(2, worldPhase.getSteps().size());

    SerializeStep world1Step = worldPhase.getSteps().get(0);

    Assert.assertEquals("world-step-1", world1Step.getName());
    Assert.assertEquals(Status.IN_PROGRESS.name(), world1Step.getStatus());
    Assert.assertEquals(Collections.emptyList(), world1Step.getErrors());

    SerializeStep world2Step = worldPhase.getSteps().get(1);

    Assert.assertEquals("world-step-2", world2Step.getName());
    Assert.assertEquals(Status.PENDING.name(), world2Step.getStatus());
    Assert.assertEquals(Collections.emptyList(), world2Step.getErrors());

    //Verify topology tree.
    Assert.assertNotNull(serializedTracker.getServiceTopology());
    List<SerializeElement> planTopology = serializedTracker.getServiceTopology();
    Assert.assertEquals(1, planTopology.size());

    SerializeElement planElement = planTopology.get(0);
    Assert.assertEquals("deploy", planElement.getName());
    Assert.assertEquals("plan", planElement.getType());
    Assert.assertNotNull(planElement.getChildren());
    Assert.assertEquals(true, planElement.getChildren().isPresent());
    Assert.assertEquals(2, planElement.getChildren().get().size());

    SerializeElement helloPhaseElement = planElement.getChildren().get().get(0);
    Assert.assertEquals("hello-deploy", helloPhaseElement.getName());
    Assert.assertEquals("phase", helloPhaseElement.getType());
    Assert.assertNotNull(helloPhaseElement.getChildren());
    Assert.assertEquals(true, helloPhaseElement.getChildren().isPresent());
    Assert.assertEquals(1, helloPhaseElement.getChildren().get().size());

    SerializeElement helloStepElement = helloPhaseElement.getChildren().get().get(0);
    Assert.assertEquals("hello-step", helloStepElement.getName());
    Assert.assertEquals("step", helloStepElement.getType());
    Assert.assertNotNull(helloStepElement.getChildren());
    Assert.assertEquals(false, helloStepElement.getChildren().isPresent());


    SerializeElement worldPhaseElement = planElement.getChildren().get().get(1);
    Assert.assertEquals("world-deploy", worldPhaseElement.getName());
    Assert.assertEquals("phase", worldPhaseElement.getType());
    Assert.assertNotNull(worldPhaseElement.getChildren());
    Assert.assertEquals(true, worldPhaseElement.getChildren().isPresent());
    Assert.assertEquals(2, worldPhaseElement.getChildren().get().size());

    SerializeElement world1StepElement = worldPhaseElement.getChildren().get().get(0);
    Assert.assertEquals("world-step-1", world1StepElement.getName());
    Assert.assertEquals("step", world1StepElement.getType());
    Assert.assertNotNull(world1StepElement.getChildren());
    Assert.assertEquals(false, world1StepElement.getChildren().isPresent());

    SerializeElement world2StepElement = worldPhaseElement.getChildren().get().get(1);
    Assert.assertEquals("world-step-2", world2StepElement.getName());
    Assert.assertEquals("step", world2StepElement.getType());
    Assert.assertNotNull(world2StepElement.getChildren());
    Assert.assertEquals(false, world2StepElement.getChildren().isPresent());
    //Done verification of the entire object.
  }

  @Test
  public void testHelloWorldPlanFiltered()
  {
    PlansTracker spyPlansTracker = Mockito.spy(new PlansTracker(coordinator, stateStore));

    //Issue invalid inputs and ensure we the the correct behaviour.

    SerializePlansTracker invalidPlanTracker = spyPlansTracker.generateServiceStatus(
        "invalid-plan-name",
        null,
        null);

    //Verify we don't get any plans returned
    Assert.assertNotNull(invalidPlanTracker.getPlans());
    Assert.assertEquals(0, invalidPlanTracker.getPlans().size());

    SerializePlansTracker invalidPhaseTracker = spyPlansTracker.generateServiceStatus(
        "deploy",
        "invalid-phase-name",
        null);

    //Verify we don't get any phases returned
    Assert.assertNotNull(invalidPhaseTracker.getPlans());
    Assert.assertEquals(1, invalidPhaseTracker.getPlans().size());
    Assert.assertEquals(0, invalidPhaseTracker.getPlans().get(0).getPhases().size());

    SerializePlansTracker invalidStepTracker = spyPlansTracker.generateServiceStatus(
        "deploy",
        "hello-deploy",
        "invalid-step-name");

    //Verify we don't get any steps returned
    Assert.assertNotNull(invalidStepTracker.getPlans());
    Assert.assertEquals(1, invalidStepTracker.getPlans().size());
    Assert.assertEquals(1, invalidStepTracker.getPlans().get(0).getPhases().size());
    Assert.assertEquals(0, invalidStepTracker.getPlans().get(0).getPhases().get(0).getSteps().size());

    //Done verifying invalid inputs.

    //Issue a valid drill down and ensure the correct elements are returned.
    SerializePlansTracker filteredTracker = spyPlansTracker.generateServiceStatus(
        "deploy",
        "hello-deploy",
        "hello-step");

    Assert.assertEquals(PlansTracker.SchedulerState.DEPLOYING, filteredTracker.getSchedulerState());
    Assert.assertEquals(1, filteredTracker.getActivePlans().size());
    Assert.assertEquals(true, filteredTracker.getActivePlans().contains("deploy"));

    //Verify plans tree.
    Assert.assertNotNull(filteredTracker.getPlans());
    Assert.assertEquals(1, filteredTracker.getPlans().size());
    SerializePlan plan = filteredTracker.getPlans().get(0);

    //Filtering does not affect the rollup steps returned.
    Assert.assertEquals(3, plan.getTotalSteps());
    Assert.assertEquals(1, plan.getCompletedSteps());
    Assert.assertEquals("deploy", plan.getName());
    Assert.assertEquals("serial", plan.getStrategy());
    Assert.assertEquals(Status.IN_PROGRESS.name(), plan.getStatus());

    //Ensure only one phase is returned due to drill down.
    Assert.assertNotNull(plan.getPhases());
    Assert.assertEquals(1, plan.getPhases().size());

    SerializePhase helloPhase = plan.getPhases().get(0);

    Assert.assertEquals("hello-deploy", helloPhase.getName());
    Assert.assertEquals("serial", helloPhase.getStrategy());
    Assert.assertEquals(Status.COMPLETE.name(), helloPhase.getStatus());

    Assert.assertNotNull(helloPhase.getSteps());
    Assert.assertEquals(1, helloPhase.getSteps().size());

    SerializeStep helloStep = helloPhase.getSteps().get(0);

    Assert.assertEquals("hello-step", helloStep.getName());
    Assert.assertEquals(Status.COMPLETE.name(), helloStep.getStatus());
    Assert.assertEquals(Collections.emptyList(),helloStep.getErrors());

    //Verify topology tree is always returned, even on filtered plans.
    Assert.assertNotNull(filteredTracker.getServiceTopology());
    List<SerializeElement> planTopology = filteredTracker.getServiceTopology();
    Assert.assertEquals(1, planTopology.size());

    SerializeElement planElement = planTopology.get(0);
    Assert.assertEquals("deploy", planElement.getName());
    Assert.assertEquals("plan", planElement.getType());
    Assert.assertNotNull(planElement.getChildren());
    Assert.assertEquals(true, planElement.getChildren().isPresent());
    Assert.assertEquals(2, planElement.getChildren().get().size());

    SerializeElement helloPhaseElement = planElement.getChildren().get().get(0);
    Assert.assertEquals("hello-deploy", helloPhaseElement.getName());
    Assert.assertEquals("phase", helloPhaseElement.getType());
    Assert.assertNotNull(helloPhaseElement.getChildren());
    Assert.assertEquals(true, helloPhaseElement.getChildren().isPresent());
    Assert.assertEquals(1, helloPhaseElement.getChildren().get().size());

    SerializeElement helloStepElement = helloPhaseElement.getChildren().get().get(0);
    Assert.assertEquals("hello-step", helloStepElement.getName());
    Assert.assertEquals("step", helloStepElement.getType());
    Assert.assertNotNull(helloStepElement.getChildren());
    Assert.assertEquals(false, helloStepElement.getChildren().isPresent());


    SerializeElement worldPhaseElement = planElement.getChildren().get().get(1);
    Assert.assertEquals("world-deploy", worldPhaseElement.getName());
    Assert.assertEquals("phase", worldPhaseElement.getType());
    Assert.assertNotNull(worldPhaseElement.getChildren());
    Assert.assertEquals(true, worldPhaseElement.getChildren().isPresent());
    Assert.assertEquals(2, worldPhaseElement.getChildren().get().size());

    SerializeElement world1StepElement = worldPhaseElement.getChildren().get().get(0);
    Assert.assertEquals("world-step-1", world1StepElement.getName());
    Assert.assertEquals("step", world1StepElement.getType());
    Assert.assertNotNull(world1StepElement.getChildren());
    Assert.assertEquals(false, world1StepElement.getChildren().isPresent());

    SerializeElement world2StepElement = worldPhaseElement.getChildren().get().get(1);
    Assert.assertEquals("world-step-2", world2StepElement.getName());
    Assert.assertEquals("step", world2StepElement.getType());
    Assert.assertNotNull(world2StepElement.getChildren());
    Assert.assertEquals(false, world2StepElement.getChildren().isPresent());

    //Done testing filtered plans.
  }
}
