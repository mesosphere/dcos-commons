package com.mesosphere.sdk.debug;

import com.mesosphere.sdk.debug.ServiceStatusTracker.ServiceStatusCode;
import com.mesosphere.sdk.debug.ServiceStatusTracker.ServiceStatusResult;
import com.mesosphere.sdk.offer.Constants;
import com.mesosphere.sdk.scheduler.plan.DefaultPhase;
import com.mesosphere.sdk.scheduler.plan.DefaultPlan;
import com.mesosphere.sdk.scheduler.plan.DefaultPlanCoordinator;
import com.mesosphere.sdk.scheduler.plan.DefaultPlanManager;
import com.mesosphere.sdk.scheduler.plan.DefaultPodInstance;
import com.mesosphere.sdk.scheduler.plan.DeploymentStep;
import com.mesosphere.sdk.scheduler.plan.PlanCoordinator;
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
import java.util.stream.Collectors;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

/**
 * This class tests the {@link ServiceStatusTracker} class
 */
public class ServiceStatusTrackerTest {

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

  private PodInstanceRequirement getPodInstanceRequirement(PodSpec podSpec, int index) {
    PodInstance podInstance = new DefaultPodInstance(podSpec, index);
    return PodInstanceRequirement.newBuilder(
        podInstance,
        podSpec.getTasks().stream().map(TaskSpec::getName).collect(Collectors.toList()))
        .build();
  }

  @Before
  public void setupTest() throws Exception {
    MockitoAnnotations.initMocks(this);
  }

  private ServiceStatusTrackerTestScenario getRegisteredFrameworkScenario(boolean registerFramework) {
     
    Persister persister = MemPersister.newBuilder().build();
    FrameworkStore frameworkStore = new FrameworkStore(persister);
    
    if(registerFramework) {
      frameworkStore.storeFrameworkId(TestConstants.FRAMEWORK_ID);
    }
    StateStore stateStore = new StateStore(persister);

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
    helloStep.updateInitialStatus(Status.PENDING);
    world1Step.updateInitialStatus(Status.PENDING);
    world2Step.updateInitialStatus(Status.PENDING);

    DefaultPhase helloPhase = new DefaultPhase("hello-deploy",
        Arrays.asList(helloStep),
        new SerialStrategy<>(),
        Collections.emptyList());
    DefaultPhase worldPhase = new DefaultPhase("world-deploy",
        Arrays.asList(world1Step, world2Step),
        new SerialStrategy<>(),
        Collections.emptyList());
    DefaultPlan helloWorldPlan = new DefaultPlan(Constants.DEPLOY_PLAN_NAME,
        Arrays.asList(helloPhase, worldPhase),
        new SerialStrategy<>(),
        Collections.emptyList());
    DefaultPlan recoveryPlan = new DefaultPlan(Constants.RECOVERY_PLAN_NAME,
        Collections.emptyList(),
        new SerialStrategy<>(),
        Collections.emptyList());

    PlanManager helloWorldPlanManager = DefaultPlanManager.createProceeding(helloWorldPlan);
    PlanManager recoveryPlanManager = DefaultPlanManager.createProceeding(recoveryPlan);
    PlanCoordinator coordinator = new DefaultPlanCoordinator(Optional.empty(), Arrays.asList(helloWorldPlanManager, recoveryPlanManager));
  
    return new ServiceStatusTrackerTestScenario(coordinator, frameworkStore);
  }
  
  @Test
  public void testFrameworkNotInitialized() {
    
    ServiceStatusTrackerTestScenario unregisteredFramework = getRegisteredFrameworkScenario(false);
    ServiceStatusTracker serviceStatusTracker = Mockito.spy(new ServiceStatusTracker(unregisteredFramework.getPlanCoordinator(),
        unregisteredFramework.getFrameworkStore()));
 
    ServiceStatusResult serviceResult = serviceStatusTracker.evaluateServiceStatus(false);
    Optional<ServiceStatusCode> expected = Optional.of(ServiceStatusTracker.ServiceStatusCode.INITIALIZING);
    Optional<ServiceStatusCode> received = serviceResult.getServiceStatusCode();
    
    Assert.assertTrue(expected.equals(received));
  }
   
  @Test
  public void testFrameworkInitialized() {
    
    ServiceStatusTrackerTestScenario unregisteredFramework = getRegisteredFrameworkScenario(true);
    ServiceStatusTracker serviceStatusTracker = Mockito.spy(new ServiceStatusTracker(unregisteredFramework.getPlanCoordinator(),
        unregisteredFramework.getFrameworkStore()));
 
    ServiceStatusResult serviceResult = serviceStatusTracker.evaluateServiceStatus(false);
    Optional<ServiceStatusCode> expected = Optional.of(ServiceStatusTracker.ServiceStatusCode.INITIALIZING);
    Optional<ServiceStatusCode> received = serviceResult.getServiceStatusCode();
    
    Assert.assertFalse(expected.equals(received));
  }
  
  @Test
  public void testFrameworkDeployPending() {
    
    Persister persister = MemPersister.newBuilder().build();
    FrameworkStore frameworkStore = new FrameworkStore(persister);
    frameworkStore.storeFrameworkId(TestConstants.FRAMEWORK_ID);
    StateStore stateStore = new StateStore(persister);

    DeploymentStep hello1Step = new DeploymentStep("hello-step-1",
        helloPodInstanceRequirement,
        stateStore,
        Optional.empty());
    DeploymentStep hello2Step = new DeploymentStep("hello-step-2",
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
    DeploymentStep world3Step = new DeploymentStep("world-step-3",
        worldPodInstanceRequirement,
        stateStore,
        Optional.empty());


    //We have 3 starting but 2 in Pending/Prepared.
    //Pessimistically we expect a service status of PENDING.
    hello1Step.updateInitialStatus(Status.PREPARED);
    hello2Step.updateInitialStatus(Status.PENDING);
    world1Step.updateInitialStatus(Status.STARTING);
    world2Step.updateInitialStatus(Status.STARTING);
    world3Step.updateInitialStatus(Status.STARTING);

    DefaultPhase helloPhase = new DefaultPhase("hello-deploy",
        Arrays.asList(hello1Step, hello2Step),
        new SerialStrategy<>(),
        Collections.emptyList());
    DefaultPhase worldPhase = new DefaultPhase("world-deploy",
        Arrays.asList(world1Step, world2Step, world3Step),
        new SerialStrategy<>(),
        Collections.emptyList());
    DefaultPlan helloWorldPlan = new DefaultPlan(Constants.DEPLOY_PLAN_NAME,
        Arrays.asList(helloPhase, worldPhase),
        new SerialStrategy<>(),
        Collections.emptyList());
    DefaultPlan recoveryPlan = new DefaultPlan(Constants.RECOVERY_PLAN_NAME,
        Collections.emptyList(),
        new SerialStrategy<>(),
        Collections.emptyList());

    PlanManager helloWorldPlanManager = DefaultPlanManager.createProceeding(helloWorldPlan);
    PlanManager recoveryPlanManager = DefaultPlanManager.createProceeding(recoveryPlan);
    PlanCoordinator coordinator = new DefaultPlanCoordinator(Optional.empty(), Arrays.asList(helloWorldPlanManager, recoveryPlanManager));
    
    //Setup complete, verify result.
     
    ServiceStatusTracker serviceStatusTracker = Mockito.spy(new ServiceStatusTracker(coordinator, frameworkStore));
    
    ServiceStatusResult serviceResult = serviceStatusTracker.evaluateServiceStatus(false);
    Optional<ServiceStatusCode> expected = Optional.of(ServiceStatusTracker.ServiceStatusCode.DEPLOYING_PENDING);
    Optional<ServiceStatusCode> received = serviceResult.getServiceStatusCode();
    
    Assert.assertTrue(expected.equals(received));
  }
  
  @Test
  public void testFrameworkDeployStarting() {
    
    Persister persister = MemPersister.newBuilder().build();
    FrameworkStore frameworkStore = new FrameworkStore(persister);
    frameworkStore.storeFrameworkId(TestConstants.FRAMEWORK_ID);
    StateStore stateStore = new StateStore(persister);

    DeploymentStep hello1Step = new DeploymentStep("hello-step-1",
        helloPodInstanceRequirement,
        stateStore,
        Optional.empty());
    DeploymentStep hello2Step = new DeploymentStep("hello-step-2",
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
    DeploymentStep world3Step = new DeploymentStep("world-step-3",
        worldPodInstanceRequirement,
        stateStore,
        Optional.empty());

    //Pessimistically we expect a service status of STARTING.
    hello1Step.updateInitialStatus(Status.STARTING);
    hello2Step.updateInitialStatus(Status.STARTED);
    world1Step.updateInitialStatus(Status.COMPLETE);
    world2Step.updateInitialStatus(Status.COMPLETE);
    world3Step.updateInitialStatus(Status.COMPLETE);

    DefaultPhase helloPhase = new DefaultPhase("hello-deploy",
        Arrays.asList(hello1Step, hello2Step),
        new SerialStrategy<>(),
        Collections.emptyList());
    DefaultPhase worldPhase = new DefaultPhase("world-deploy",
        Arrays.asList(world1Step, world2Step, world3Step),
        new SerialStrategy<>(),
        Collections.emptyList());
    DefaultPlan helloWorldPlan = new DefaultPlan(Constants.DEPLOY_PLAN_NAME,
        Arrays.asList(helloPhase, worldPhase),
        new SerialStrategy<>(),
        Collections.emptyList());
    DefaultPlan recoveryPlan = new DefaultPlan(Constants.RECOVERY_PLAN_NAME,
        Collections.emptyList(),
        new SerialStrategy<>(),
        Collections.emptyList());

    PlanManager helloWorldPlanManager = DefaultPlanManager.createProceeding(helloWorldPlan);
    PlanManager recoveryPlanManager = DefaultPlanManager.createProceeding(recoveryPlan);
    PlanCoordinator coordinator = new DefaultPlanCoordinator(Optional.empty(), Arrays.asList(helloWorldPlanManager, recoveryPlanManager));

    //Setup complete, verify result.
    
    ServiceStatusTracker serviceStatusTracker = Mockito.spy(new ServiceStatusTracker(coordinator, frameworkStore));
    
    ServiceStatusResult serviceResult = serviceStatusTracker.evaluateServiceStatus(false);
    Optional<ServiceStatusCode> expected = Optional.of(ServiceStatusTracker.ServiceStatusCode.DEPLOYING_STARTING);
    Optional<ServiceStatusCode> received = serviceResult.getServiceStatusCode();
    
    Assert.assertTrue(expected.equals(received));
  }
  
  @Test
  public void testFrameworkDeployRunning() {
    
    Persister persister = MemPersister.newBuilder().build();
    FrameworkStore frameworkStore = new FrameworkStore(persister);
    frameworkStore.storeFrameworkId(TestConstants.FRAMEWORK_ID);
    StateStore stateStore = new StateStore(persister);

    DeploymentStep hello1Step = new DeploymentStep("hello-step-1",
        helloPodInstanceRequirement,
        stateStore,
        Optional.empty());
    DeploymentStep hello2Step = new DeploymentStep("hello-step-2",
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
    DeploymentStep world3Step = new DeploymentStep("world-step-3",
        worldPodInstanceRequirement,
        stateStore,
        Optional.empty());

    //Pessimistically we expect a service status of STARTING.
    hello1Step.updateInitialStatus(Status.COMPLETE);
    hello2Step.updateInitialStatus(Status.COMPLETE);
    world1Step.updateInitialStatus(Status.COMPLETE);
    world2Step.updateInitialStatus(Status.COMPLETE);
    world3Step.updateInitialStatus(Status.COMPLETE);

    DefaultPhase helloPhase = new DefaultPhase("hello-deploy",
        Arrays.asList(hello1Step, hello2Step),
        new SerialStrategy<>(),
        Collections.emptyList());
    DefaultPhase worldPhase = new DefaultPhase("world-deploy",
        Arrays.asList(world1Step, world2Step, world3Step),
        new SerialStrategy<>(),
        Collections.emptyList());
    DefaultPlan helloWorldPlan = new DefaultPlan(Constants.DEPLOY_PLAN_NAME,
        Arrays.asList(helloPhase, worldPhase),
        new SerialStrategy<>(),
        Collections.emptyList());
    DefaultPlan recoveryPlan = new DefaultPlan(Constants.RECOVERY_PLAN_NAME,
        Collections.emptyList(),
        new SerialStrategy<>(),
        Collections.emptyList());

    PlanManager helloWorldPlanManager = DefaultPlanManager.createProceeding(helloWorldPlan);
    PlanManager recoveryPlanManager = DefaultPlanManager.createProceeding(recoveryPlan);
    PlanCoordinator coordinator = new DefaultPlanCoordinator(Optional.empty(), Arrays.asList(helloWorldPlanManager, recoveryPlanManager));

    //Setup complete, verify result.
    
    ServiceStatusTracker serviceStatusTracker = Mockito.spy(new ServiceStatusTracker(coordinator, frameworkStore));
    
    ServiceStatusResult serviceResult = serviceStatusTracker.evaluateServiceStatus(false);
    Optional<ServiceStatusCode> expected = Optional.of(ServiceStatusTracker.ServiceStatusCode.RUNNING);
    Optional<ServiceStatusCode> received = serviceResult.getServiceStatusCode();
    
    Assert.assertTrue(expected.equals(received));
  } 
 
  /**
   * Wrapper class for returning {@link PlanCoordinator} and {@link FrameworkStore}
   */
  private static class ServiceStatusTrackerTestScenario {
    private final PlanCoordinator planCoordinator;
    private final FrameworkStore frameworkStore;
    
    public ServiceStatusTrackerTestScenario(PlanCoordinator planCoordinator, FrameworkStore frameworkStore) {
      this.planCoordinator = planCoordinator;
      this.frameworkStore = frameworkStore;
    }
    
    public PlanCoordinator getPlanCoordinator() {
      return planCoordinator;
    }

    public FrameworkStore getFrameworkStore() {
      return frameworkStore;
    }
  }
}