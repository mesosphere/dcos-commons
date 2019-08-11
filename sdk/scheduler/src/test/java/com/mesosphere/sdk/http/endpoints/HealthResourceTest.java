package com.mesosphere.sdk.http.endpoints;

import com.mesosphere.sdk.http.endpoints.HealthResource.ServiceStatusCode;
import com.mesosphere.sdk.http.endpoints.HealthResource.ServiceStatusResult;
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
import com.mesosphere.sdk.scheduler.plan.Step;
import com.mesosphere.sdk.scheduler.plan.strategy.CanaryStrategy;
import com.mesosphere.sdk.scheduler.plan.strategy.ParallelStrategy;
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
 * This class tests the {@link HealthResource} class
 */
public class HealthResourceTest {

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
  public void setupTest() {
    MockitoAnnotations.initMocks(this);
  }

  private HealthResourceTestScenario getRegisteredFrameworkScenario(boolean registerFramework) {
     
    Persister persister = MemPersister.newBuilder().build();
    Optional<FrameworkStore> frameworkStore = Optional.of(new FrameworkStore(persister));
    
    if(registerFramework) {
      frameworkStore.get().storeFrameworkId(TestConstants.FRAMEWORK_ID);
    }
    StateStore stateStore = new StateStore(persister);

    DeploymentStep helloStep = new DeploymentStep("hello-step", helloPodInstanceRequirement, stateStore);
    DeploymentStep world1Step = new DeploymentStep("world-step-1", worldPodInstanceRequirement, stateStore);
    DeploymentStep world2Step = new DeploymentStep("world-step-2", worldPodInstanceRequirement, stateStore);

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
    PlanCoordinator coordinator = new DefaultPlanCoordinator(Arrays.asList(helloWorldPlanManager, recoveryPlanManager));
  
    return new HealthResourceTestScenario(coordinator, frameworkStore);
  }
  
  @Test
  public void testFrameworkNotInitialized() {
    
    HealthResourceTestScenario unregisteredFramework = getRegisteredFrameworkScenario(false);
    HealthResource serviceStatusTracker = Mockito.spy(new HealthResource(unregisteredFramework.getPlanCoordinator(),
        unregisteredFramework.getFrameworkStore()));
 
    ServiceStatusResult serviceResult = serviceStatusTracker.evaluateServiceStatus(false);
    Optional<ServiceStatusCode> expected = Optional.of(HealthResource.ServiceStatusCode.INITIALIZING);
    Optional<ServiceStatusCode> received = serviceResult.getServiceStatusCode();
    
    Assert.assertEquals(expected.get(), received.get());
  }
   
  @Test
  public void testFrameworkInitialized() {
    
    HealthResourceTestScenario unregisteredFramework = getRegisteredFrameworkScenario(true);
    HealthResource serviceStatusTracker = Mockito.spy(new HealthResource(unregisteredFramework.getPlanCoordinator(),
        unregisteredFramework.getFrameworkStore()));
 
    ServiceStatusResult serviceResult = serviceStatusTracker.evaluateServiceStatus(false);
    Optional<ServiceStatusCode> expected = Optional.of(HealthResource.ServiceStatusCode.INITIALIZING);
    Optional<ServiceStatusCode> received = serviceResult.getServiceStatusCode();
    
    Assert.assertNotEquals(expected.get(), received.get());
  }

  @Test
  public void testFrameworkHasErrorCreating() {

    Persister persister = MemPersister.newBuilder().build();

    Optional<FrameworkStore> frameworkStore = Optional.of(new FrameworkStore(persister));
    frameworkStore.get().storeFrameworkId(TestConstants.FRAMEWORK_ID);
    
    StateStore stateStore = new StateStore(persister);

    DeploymentStep helloStep = new DeploymentStep("hello-step", helloPodInstanceRequirement, stateStore);
    DeploymentStep world1Step = new DeploymentStep("world-step-1", worldPodInstanceRequirement, stateStore);
    DeploymentStep world2Step = new DeploymentStep("world-step-2", worldPodInstanceRequirement, stateStore);

    //Decorate various parts for verification later..
    helloStep.updateInitialStatus(Status.ERROR);
    helloStep.addError("Added test error.");
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
    PlanCoordinator coordinator = new DefaultPlanCoordinator(Arrays.asList(helloWorldPlanManager, recoveryPlanManager));

    //Setup complete, verify result.

    HealthResource serviceStatusTracker = Mockito.spy(new HealthResource(coordinator, frameworkStore));

    ServiceStatusResult serviceResult = serviceStatusTracker.evaluateServiceStatus(false);
    Optional<ServiceStatusCode> expected = Optional.of(ServiceStatusCode.ERROR_CREATING_SERVICE);
    Optional<ServiceStatusCode> received = serviceResult.getServiceStatusCode();

    Assert.assertEquals(expected.get(), received.get());
  }

  @Test
  public void testFrameworkDeployPending() {
    
    Persister persister = MemPersister.newBuilder().build();
    Optional<FrameworkStore> frameworkStore = Optional.of(new FrameworkStore(persister));
    frameworkStore.get().storeFrameworkId(TestConstants.FRAMEWORK_ID);
    StateStore stateStore = new StateStore(persister);

    DeploymentStep hello1Step = new DeploymentStep("hello-step-1", helloPodInstanceRequirement, stateStore);
    DeploymentStep hello2Step = new DeploymentStep("hello-step-2", helloPodInstanceRequirement, stateStore);
    DeploymentStep world1Step = new DeploymentStep("world-step-1", worldPodInstanceRequirement, stateStore);
    DeploymentStep world2Step = new DeploymentStep("world-step-2", worldPodInstanceRequirement, stateStore);
    DeploymentStep world3Step = new DeploymentStep("world-step-3", worldPodInstanceRequirement, stateStore);


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
    PlanCoordinator coordinator = new DefaultPlanCoordinator(Arrays.asList(helloWorldPlanManager, recoveryPlanManager));
    
    //Setup complete, verify result.
     
    HealthResource serviceStatusTracker = Mockito.spy(new HealthResource(coordinator, frameworkStore));
    
    ServiceStatusResult serviceResult = serviceStatusTracker.evaluateServiceStatus(false);
    Optional<ServiceStatusCode> expected = Optional.of(HealthResource.ServiceStatusCode.DEPLOYING_PENDING);
    Optional<ServiceStatusCode> received = serviceResult.getServiceStatusCode();
    
    Assert.assertEquals(expected.get(), received.get());
  }
  
  @Test
  public void testFrameworkDeployStarting() {
    
    Persister persister = MemPersister.newBuilder().build();
        
    Optional<FrameworkStore> frameworkStore = Optional.of(new FrameworkStore(persister));
    frameworkStore.get().storeFrameworkId(TestConstants.FRAMEWORK_ID);
 
    StateStore stateStore = new StateStore(persister);

    DeploymentStep hello1Step = new DeploymentStep("hello-step-1", helloPodInstanceRequirement, stateStore);
    DeploymentStep hello2Step = new DeploymentStep("hello-step-2", helloPodInstanceRequirement, stateStore);
    DeploymentStep world1Step = new DeploymentStep("world-step-1", worldPodInstanceRequirement, stateStore);
    DeploymentStep world2Step = new DeploymentStep("world-step-2", worldPodInstanceRequirement, stateStore);
    DeploymentStep world3Step = new DeploymentStep("world-step-3", worldPodInstanceRequirement, stateStore);

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
    PlanCoordinator coordinator = new DefaultPlanCoordinator(Arrays.asList(helloWorldPlanManager, recoveryPlanManager));

    //Setup complete, verify result.
    
    HealthResource serviceStatusTracker = Mockito.spy(new HealthResource(coordinator, frameworkStore));
    
    ServiceStatusResult serviceResult = serviceStatusTracker.evaluateServiceStatus(false);
    Optional<ServiceStatusCode> expected = Optional.of(HealthResource.ServiceStatusCode.DEPLOYING_STARTING);
    Optional<ServiceStatusCode> received = serviceResult.getServiceStatusCode();
    
    Assert.assertEquals(expected.get(), received.get());
  }
  
  @Test
  public void testFrameworkDeployRunning() {
    
    Persister persister = MemPersister.newBuilder().build();
        
    Optional<FrameworkStore> frameworkStore = Optional.of(new FrameworkStore(persister));
    frameworkStore.get().storeFrameworkId(TestConstants.FRAMEWORK_ID);
 
    StateStore stateStore = new StateStore(persister);

    DeploymentStep hello1Step = new DeploymentStep("hello-step-1", helloPodInstanceRequirement, stateStore);
    DeploymentStep hello2Step = new DeploymentStep("hello-step-2", helloPodInstanceRequirement, stateStore);
    DeploymentStep world1Step = new DeploymentStep("world-step-1", worldPodInstanceRequirement, stateStore);
    DeploymentStep world2Step = new DeploymentStep("world-step-2", worldPodInstanceRequirement, stateStore);
    DeploymentStep world3Step = new DeploymentStep("world-step-3", worldPodInstanceRequirement, stateStore);

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
    PlanCoordinator coordinator = new DefaultPlanCoordinator(Arrays.asList(helloWorldPlanManager, recoveryPlanManager));

    //Setup complete, verify result.
    
    HealthResource serviceStatusTracker = Mockito.spy(new HealthResource(coordinator, frameworkStore));
    
    ServiceStatusResult serviceResult = serviceStatusTracker.evaluateServiceStatus(false);
    Optional<ServiceStatusCode> expected = Optional.of(HealthResource.ServiceStatusCode.RUNNING);
    Optional<ServiceStatusCode> received = serviceResult.getServiceStatusCode();
   
    Assert.assertEquals(expected.get(), received.get());
  } 
 
  @Test
  public void testFrameworkRecoveryPending() {
    
    Persister persister = MemPersister.newBuilder().build();
    
    Optional<FrameworkStore> frameworkStore = Optional.of(new FrameworkStore(persister));
    frameworkStore.get().storeFrameworkId(TestConstants.FRAMEWORK_ID);
    
    StateStore stateStore = new StateStore(persister);

    DeploymentStep hello1Step = new DeploymentStep("hello-step-1", helloPodInstanceRequirement, stateStore);
    DeploymentStep hello2Step = new DeploymentStep("hello-step-2", helloPodInstanceRequirement, stateStore);
    DeploymentStep world1Step = new DeploymentStep("world-step-1", worldPodInstanceRequirement, stateStore);
    DeploymentStep world2Step = new DeploymentStep("world-step-2", worldPodInstanceRequirement, stateStore);
    DeploymentStep world3Step = new DeploymentStep("world-step-3", worldPodInstanceRequirement, stateStore);

    DeploymentStep recoveryWorld1Step = new DeploymentStep("world-step-1",
        worldPodInstanceRequirement,
        stateStore);
    DeploymentStep recoveryWorld2Step = new DeploymentStep("world-step-2",
        worldPodInstanceRequirement,
        stateStore);
    DeploymentStep recoveryWorld3Step = new DeploymentStep("world-step-3",
        worldPodInstanceRequirement,
        stateStore);

    
    //Simulate a pod-replace on the world pod.
    hello1Step.updateInitialStatus(Status.COMPLETE);
    hello2Step.updateInitialStatus(Status.COMPLETE);
    world1Step.updateInitialStatus(Status.COMPLETE);
    world2Step.updateInitialStatus(Status.COMPLETE);
    world3Step.updateInitialStatus(Status.COMPLETE);
    
    recoveryWorld1Step.updateInitialStatus(Status.PREPARED);
    recoveryWorld2Step.updateInitialStatus(Status.PENDING);
    recoveryWorld3Step.updateInitialStatus(Status.STARTED);


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
    DefaultPhase recoveryWorldPhase = new DefaultPhase("permanent-node-failrue-recovery",
        Arrays.asList(recoveryWorld1Step, recoveryWorld2Step, recoveryWorld3Step),
        new SerialStrategy<>(),
        Collections.emptyList());
    DefaultPlan recoveryPlan = new DefaultPlan(Constants.RECOVERY_PLAN_NAME,
        Arrays.asList(recoveryWorldPhase),
        new SerialStrategy<>(),
        Collections.emptyList());

    PlanManager helloWorldPlanManager = DefaultPlanManager.createProceeding(helloWorldPlan);
    PlanManager recoveryPlanManager = DefaultPlanManager.createProceeding(recoveryPlan);
    PlanCoordinator coordinator = new DefaultPlanCoordinator(Arrays.asList(helloWorldPlanManager, recoveryPlanManager));

    //Setup complete, verify result.
    
    HealthResource serviceStatusTracker = Mockito.spy(new HealthResource(coordinator, frameworkStore));
    
    ServiceStatusResult serviceResult = serviceStatusTracker.evaluateServiceStatus(false);
    Optional<ServiceStatusCode> expected = Optional.of(HealthResource.ServiceStatusCode.RECOVERING_PENDING);
    Optional<ServiceStatusCode> received = serviceResult.getServiceStatusCode();
    
    Assert.assertEquals(expected.get(), received.get());
  } 
    
  @Test
  public void testFrameworkRecoveryStarting() {
    
    Persister persister = MemPersister.newBuilder().build();
    
    Optional<FrameworkStore> frameworkStore = Optional.of(new FrameworkStore(persister));
    frameworkStore.get().storeFrameworkId(TestConstants.FRAMEWORK_ID);
    
    StateStore stateStore = new StateStore(persister);

    DeploymentStep hello1Step = new DeploymentStep("hello-step-1", helloPodInstanceRequirement, stateStore);
    DeploymentStep hello2Step = new DeploymentStep("hello-step-2", helloPodInstanceRequirement, stateStore);
    DeploymentStep world1Step = new DeploymentStep("world-step-1", worldPodInstanceRequirement, stateStore);
    DeploymentStep world2Step = new DeploymentStep("world-step-2", worldPodInstanceRequirement, stateStore);
    DeploymentStep world3Step = new DeploymentStep("world-step-3", worldPodInstanceRequirement, stateStore);

    DeploymentStep recoveryWorld1Step = new DeploymentStep("world-step-1",
            worldPodInstanceRequirement,
            stateStore);
    DeploymentStep recoveryWorld2Step = new DeploymentStep("world-step-2",
            worldPodInstanceRequirement,
            stateStore);
    DeploymentStep recoveryWorld3Step = new DeploymentStep("world-step-3",
            worldPodInstanceRequirement,
            stateStore);

    
    //Simulate a pod-replace on the world pod.
    hello1Step.updateInitialStatus(Status.COMPLETE);
    hello2Step.updateInitialStatus(Status.COMPLETE);
    world1Step.updateInitialStatus(Status.COMPLETE);
    world2Step.updateInitialStatus(Status.COMPLETE);
    world3Step.updateInitialStatus(Status.COMPLETE);
    
    recoveryWorld1Step.updateInitialStatus(Status.STARTING);
    recoveryWorld2Step.updateInitialStatus(Status.COMPLETE);
    recoveryWorld3Step.updateInitialStatus(Status.COMPLETE);


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
    DefaultPhase recoveryWorldPhase = new DefaultPhase("permanent-node-failure-recovery",
        Arrays.asList(recoveryWorld1Step, recoveryWorld2Step, recoveryWorld3Step),
        new SerialStrategy<>(),
        Collections.emptyList());
    DefaultPlan recoveryPlan = new DefaultPlan(Constants.RECOVERY_PLAN_NAME,
        Arrays.asList(recoveryWorldPhase),
        new SerialStrategy<>(),
        Collections.emptyList());

    PlanManager helloWorldPlanManager = DefaultPlanManager.createProceeding(helloWorldPlan);
    PlanManager recoveryPlanManager = DefaultPlanManager.createProceeding(recoveryPlan);
    PlanCoordinator coordinator = new DefaultPlanCoordinator(Arrays.asList(helloWorldPlanManager, recoveryPlanManager));

    //Setup complete, verify result.
    
    HealthResource serviceStatusTracker = Mockito.spy(new HealthResource(coordinator, frameworkStore));
    
    ServiceStatusResult serviceResult = serviceStatusTracker.evaluateServiceStatus(false);
    Optional<ServiceStatusCode> expected = Optional.of(HealthResource.ServiceStatusCode.RECOVERING_STARTING);
    Optional<ServiceStatusCode> received = serviceResult.getServiceStatusCode();
    
    Assert.assertEquals(expected.get(), received.get());
  }
   
  @Test
  public void testFrameworkBackingUp() {
    
    Persister persister = MemPersister.newBuilder().build();
    
    Optional<FrameworkStore> frameworkStore = Optional.of(new FrameworkStore(persister));
    frameworkStore.get().storeFrameworkId(TestConstants.FRAMEWORK_ID);
    
    StateStore stateStore = new StateStore(persister);

    DeploymentStep hello1Step = new DeploymentStep("hello-step-1", helloPodInstanceRequirement, stateStore);
    DeploymentStep hello2Step = new DeploymentStep("hello-step-2", helloPodInstanceRequirement, stateStore);
    DeploymentStep world1Step = new DeploymentStep("world-step-1", worldPodInstanceRequirement, stateStore);
    DeploymentStep world2Step = new DeploymentStep("world-step-2", worldPodInstanceRequirement, stateStore);
    DeploymentStep world3Step = new DeploymentStep("world-step-3", worldPodInstanceRequirement, stateStore);

    DeploymentStep backupWorld1Step = new DeploymentStep("backup-world-step-1",
        worldPodInstanceRequirement,
        stateStore);
    DeploymentStep backupWorld2Step = new DeploymentStep("backup-world-step-2",
        worldPodInstanceRequirement,
        stateStore);
    DeploymentStep backupWorld3Step = new DeploymentStep("backup-world-step-3",
        worldPodInstanceRequirement,
        stateStore);

    
    //Simulate a pod-replace on the world pod.
    hello1Step.updateInitialStatus(Status.COMPLETE);
    hello2Step.updateInitialStatus(Status.COMPLETE);
    world1Step.updateInitialStatus(Status.COMPLETE);
    world2Step.updateInitialStatus(Status.COMPLETE);
    world3Step.updateInitialStatus(Status.COMPLETE);
    
    backupWorld1Step.updateInitialStatus(Status.PENDING);
    backupWorld2Step.updateInitialStatus(Status.STARTED);
    backupWorld3Step.updateInitialStatus(Status.COMPLETE);


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
    DefaultPhase backupWorldPhase = new DefaultPhase("backup-world",
        Arrays.asList(backupWorld1Step, backupWorld2Step, backupWorld3Step),
        new SerialStrategy<>(),
        Collections.emptyList());
    DefaultPlan backupPlan = new DefaultPlan("backup-world-s3",
        Arrays.asList(backupWorldPhase),
        new SerialStrategy<>(),
        Collections.emptyList());
    DefaultPlan recoveryPlan = new DefaultPlan(Constants.RECOVERY_PLAN_NAME,
        Collections.emptyList(),
        new SerialStrategy<>(),
        Collections.emptyList());

    PlanManager helloWorldPlanManager = DefaultPlanManager.createProceeding(helloWorldPlan);
    PlanManager recoveryPlanManager = DefaultPlanManager.createProceeding(recoveryPlan);
    PlanManager backupPlanManager = DefaultPlanManager.createProceeding(backupPlan);
    PlanCoordinator coordinator = new DefaultPlanCoordinator(Arrays.asList(helloWorldPlanManager, recoveryPlanManager, backupPlanManager));

    //Setup complete, verify result.
    
    HealthResource serviceStatusTracker = Mockito.spy(new HealthResource(coordinator, frameworkStore));
    
    ServiceStatusResult serviceResult = serviceStatusTracker.evaluateServiceStatus(false);
    Optional<ServiceStatusCode> expected = Optional.of(HealthResource.ServiceStatusCode.BACKING_UP);
    Optional<ServiceStatusCode> received = serviceResult.getServiceStatusCode();
    
    Assert.assertEquals(expected.get(), received.get());
  } 
    
  @Test
  public void testFrameworkRestoring() {
    
    Persister persister = MemPersister.newBuilder().build();
    
    Optional<FrameworkStore> frameworkStore = Optional.of(new FrameworkStore(persister));
    frameworkStore.get().storeFrameworkId(TestConstants.FRAMEWORK_ID);
    
    StateStore stateStore = new StateStore(persister);

    DeploymentStep hello1Step = new DeploymentStep("hello-step-1", helloPodInstanceRequirement, stateStore);
    DeploymentStep hello2Step = new DeploymentStep("hello-step-2", helloPodInstanceRequirement, stateStore);
    DeploymentStep world1Step = new DeploymentStep("world-step-1", worldPodInstanceRequirement, stateStore);
    DeploymentStep world2Step = new DeploymentStep("world-step-2", worldPodInstanceRequirement, stateStore);
    DeploymentStep world3Step = new DeploymentStep("world-step-3", worldPodInstanceRequirement, stateStore);

    DeploymentStep restoreWorld1Step = new DeploymentStep("restore-world-step-1",
        worldPodInstanceRequirement,
        stateStore);
    DeploymentStep restoreWorld2Step = new DeploymentStep("restore-world-step-2",
        worldPodInstanceRequirement,
        stateStore);
    DeploymentStep restoreWorld3Step = new DeploymentStep("restore-world-step-3",
        worldPodInstanceRequirement,
        stateStore);

    
    //Simulate a pod-replace on the world pod.
    hello1Step.updateInitialStatus(Status.COMPLETE);
    hello2Step.updateInitialStatus(Status.COMPLETE);
    world1Step.updateInitialStatus(Status.COMPLETE);
    world2Step.updateInitialStatus(Status.COMPLETE);
    world3Step.updateInitialStatus(Status.COMPLETE);
    
    restoreWorld1Step.updateInitialStatus(Status.PENDING);
    restoreWorld2Step.updateInitialStatus(Status.STARTED);
    restoreWorld3Step.updateInitialStatus(Status.COMPLETE);


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
    DefaultPhase restoreWorldPhase = new DefaultPhase("restore-world",
        Arrays.asList(restoreWorld1Step, restoreWorld2Step, restoreWorld3Step),
        new SerialStrategy<>(),
        Collections.emptyList());
    DefaultPlan restorePlan = new DefaultPlan("restore-world-s3",
        Arrays.asList(restoreWorldPhase),
        new SerialStrategy<>(),
        Collections.emptyList());
    DefaultPlan recoveryPlan = new DefaultPlan(Constants.RECOVERY_PLAN_NAME,
        Collections.emptyList(),
        new SerialStrategy<>(),
        Collections.emptyList());


    PlanManager helloWorldPlanManager = DefaultPlanManager.createProceeding(helloWorldPlan);
    PlanManager recoveryPlanManager = DefaultPlanManager.createProceeding(recoveryPlan);
    PlanManager restorePlanManager = DefaultPlanManager.createProceeding(restorePlan);
    PlanCoordinator coordinator = new DefaultPlanCoordinator(
            Arrays.asList(helloWorldPlanManager, recoveryPlanManager, restorePlanManager));

    //Setup complete, verify result.
    
    HealthResource serviceStatusTracker = Mockito.spy(new HealthResource(coordinator, frameworkStore));
    
    ServiceStatusResult serviceResult = serviceStatusTracker.evaluateServiceStatus(false);
    Optional<ServiceStatusCode> expected = Optional.of(HealthResource.ServiceStatusCode.RESTORING);
    Optional<ServiceStatusCode> received = serviceResult.getServiceStatusCode();
    
    Assert.assertEquals(expected.get(), received.get());
  }
  
  @Test
  public void testFrameworkPriorityRestore() {
    
    Persister persister = MemPersister.newBuilder().build();
    
    Optional<FrameworkStore> frameworkStore = Optional.of(new FrameworkStore(persister));
    frameworkStore.get().storeFrameworkId(TestConstants.FRAMEWORK_ID);
    
    StateStore stateStore = new StateStore(persister);
    DeploymentStep hello1Step = new DeploymentStep("hello-step-1", helloPodInstanceRequirement, stateStore);
    DeploymentStep hello2Step = new DeploymentStep("hello-step-2", helloPodInstanceRequirement, stateStore);
    DeploymentStep world1Step = new DeploymentStep("world-step-1", worldPodInstanceRequirement, stateStore);
    DeploymentStep world2Step = new DeploymentStep("world-step-2", worldPodInstanceRequirement, stateStore);
    DeploymentStep world3Step = new DeploymentStep("world-step-3", worldPodInstanceRequirement, stateStore);

    DeploymentStep recoveryWorld1Step = new DeploymentStep("world-step-1",
            worldPodInstanceRequirement,
            stateStore);
    DeploymentStep recoveryWorld2Step = new DeploymentStep("world-step-2",
            worldPodInstanceRequirement,
            stateStore);
    DeploymentStep recoveryWorld3Step = new DeploymentStep("world-step-3",
            worldPodInstanceRequirement,
            stateStore);

    DeploymentStep restoreWorld1Step = new DeploymentStep("restore-world-step-1",
            worldPodInstanceRequirement,
            stateStore);
    DeploymentStep restoreWorld2Step = new DeploymentStep("restore-world-step-2",
            worldPodInstanceRequirement,
            stateStore);
    DeploymentStep restoreWorld3Step = new DeploymentStep("restore-world-step-3",
            worldPodInstanceRequirement,
            stateStore);

   
    //Simulate a pod-replace during a restore, the recovery plan must have precedence.
    hello1Step.updateInitialStatus(Status.COMPLETE);
    hello2Step.updateInitialStatus(Status.COMPLETE);
    world1Step.updateInitialStatus(Status.COMPLETE);
    world2Step.updateInitialStatus(Status.COMPLETE);
    world3Step.updateInitialStatus(Status.COMPLETE);
     
    recoveryWorld1Step.updateInitialStatus(Status.STARTING);
    recoveryWorld2Step.updateInitialStatus(Status.COMPLETE);
    recoveryWorld3Step.updateInitialStatus(Status.COMPLETE);
   
    restoreWorld1Step.updateInitialStatus(Status.PENDING);
    restoreWorld2Step.updateInitialStatus(Status.STARTED);
    restoreWorld3Step.updateInitialStatus(Status.COMPLETE);
    

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
    DefaultPhase restoreWorldPhase = new DefaultPhase("restore-world",
        Arrays.asList(restoreWorld1Step, restoreWorld2Step, restoreWorld3Step),
        new SerialStrategy<>(),
        Collections.emptyList());
    DefaultPlan restorePlan = new DefaultPlan("recover-s3",
        Arrays.asList(restoreWorldPhase),
        new SerialStrategy<>(),
        Collections.emptyList());
    DefaultPhase recoveryWorldPhase = new DefaultPhase("permanent-node-failure-recovery",
        Arrays.asList(recoveryWorld1Step, recoveryWorld2Step, recoveryWorld3Step),
        new SerialStrategy<>(),
        Collections.emptyList());
    DefaultPlan recoveryPlan = new DefaultPlan(Constants.RECOVERY_PLAN_NAME,
        Arrays.asList(recoveryWorldPhase),
        new SerialStrategy<>(),
        Collections.emptyList());
 
    PlanManager helloWorldPlanManager = DefaultPlanManager.createProceeding(helloWorldPlan);
    PlanManager restorePlanManager = DefaultPlanManager.createProceeding(restorePlan);
    PlanManager recoveryPlanManager = DefaultPlanManager.createProceeding(recoveryPlan);
    PlanCoordinator coordinator = new DefaultPlanCoordinator(
            Arrays.asList(helloWorldPlanManager, restorePlanManager, recoveryPlanManager));

    //Setup complete, verify result.
    
    HealthResource serviceStatusTracker = Mockito.spy(new HealthResource(coordinator, frameworkStore));
    
    ServiceStatusResult serviceResult = serviceStatusTracker.evaluateServiceStatus(false);
    Optional<ServiceStatusCode> expected = Optional.of(HealthResource.ServiceStatusCode.RECOVERING_STARTING);
    Optional<ServiceStatusCode> received = serviceResult.getServiceStatusCode();
    
    Assert.assertEquals(expected.get(), received.get());
  }

  @Test
  public void testFrameworkSerialCanaryWaiting() {
      //Test canary and parallel-canary strategies that wait on user input.
    
    Persister persister = MemPersister.newBuilder().build();
    
    Optional<FrameworkStore> frameworkStore = Optional.of(new FrameworkStore(persister));
    frameworkStore.get().storeFrameworkId(TestConstants.FRAMEWORK_ID);
    
    StateStore stateStore = new StateStore(persister);
 
    DeploymentStep hello0Step = new DeploymentStep("hello-step-0", helloPodInstanceRequirement, stateStore);
    DeploymentStep hello1Step = new DeploymentStep("hello-step-1", helloPodInstanceRequirement, stateStore);
    DeploymentStep hello2Step = new DeploymentStep("hello-step-2", helloPodInstanceRequirement, stateStore);
    DeploymentStep hello3Step = new DeploymentStep("hello-step-3", helloPodInstanceRequirement, stateStore);
    DeploymentStep hello4Step = new DeploymentStep("hello-step-4", helloPodInstanceRequirement, stateStore);
    
   
    DeploymentStep world0Step = new DeploymentStep("world-step-0", worldPodInstanceRequirement, stateStore);
    DeploymentStep world1Step = new DeploymentStep("world-step-1", worldPodInstanceRequirement, stateStore);
    DeploymentStep world2Step = new DeploymentStep("world-step-2", worldPodInstanceRequirement, stateStore);
    DeploymentStep world3Step = new DeploymentStep("world-step-3", worldPodInstanceRequirement, stateStore);

   
    //Simulate a canary-plan where one step is waiting on user input.
    hello0Step.updateInitialStatus(Status.COMPLETE);
    hello1Step.updateInitialStatus(Status.COMPLETE);
    hello2Step.updateInitialStatus(Status.COMPLETE);
    hello3Step.updateInitialStatus(Status.COMPLETE);
    hello4Step.updateInitialStatus(Status.WAITING);
    
    world0Step.updateInitialStatus(Status.COMPLETE);
    world1Step.updateInitialStatus(Status.COMPLETE);
    world2Step.updateInitialStatus(Status.COMPLETE);
    world3Step.updateInitialStatus(Status.COMPLETE);

    List<Step> helloSteps = Arrays.asList(hello0Step, hello1Step, hello2Step, hello3Step, hello4Step);
    List<Step> worldSteps = Arrays.asList(world0Step, world2Step, world3Step);

    DefaultPhase helloPhase = new DefaultPhase("hello-deploy",
        helloSteps,
        new CanaryStrategy(new SerialStrategy<>(), helloSteps),
        Collections.emptyList());
    DefaultPhase worldPhase = new DefaultPhase("world-deploy",
        worldSteps,
        new CanaryStrategy(new SerialStrategy<>(), worldSteps),
        Collections.emptyList());
    DefaultPlan helloWorldPlan = new DefaultPlan(Constants.DEPLOY_PLAN_NAME,
        Arrays.asList(helloPhase, worldPhase),
        new SerialStrategy<>(),
        Collections.emptyList());
    
    DefaultPlan recoveryPlan = new DefaultPlan(Constants.RECOVERY_PLAN_NAME,
        Collections.emptyList(),
        new SerialStrategy<>(),
        Collections.emptyList());

    PlanManager helloWorldPlanManager = DefaultPlanManager.createInterrupted(helloWorldPlan);
    PlanManager recoveryPlanManager = DefaultPlanManager.createProceeding(recoveryPlan);
    PlanCoordinator coordinator = new DefaultPlanCoordinator(Arrays.asList(helloWorldPlanManager, recoveryPlanManager));

    //Setup complete, verify result.
    
    HealthResource serviceStatusTracker = Mockito.spy(new HealthResource(coordinator, frameworkStore));
    
    ServiceStatusResult serviceResult = serviceStatusTracker.evaluateServiceStatus(false);
    Optional<ServiceStatusCode> expected = Optional.of(HealthResource.ServiceStatusCode.DEPLOYING_WAITING_USER);
    Optional<ServiceStatusCode> received = serviceResult.getServiceStatusCode();
    
    Assert.assertEquals(expected.get(), received.get());
  }

  @Test
  public void testFrameworkParallelCanaryWaiting() {
      //Test canary and parallel-canary strategies that wait on user input.

    Persister persister = MemPersister.newBuilder().build();

    Optional<FrameworkStore> frameworkStore = Optional.of(new FrameworkStore(persister));
    frameworkStore.get().storeFrameworkId(TestConstants.FRAMEWORK_ID);

    StateStore stateStore = new StateStore(persister);

    DeploymentStep hello0Step = new DeploymentStep("hello-step-0", helloPodInstanceRequirement, stateStore);
    DeploymentStep hello1Step = new DeploymentStep("hello-step-1", helloPodInstanceRequirement, stateStore);
    DeploymentStep hello2Step = new DeploymentStep("hello-step-2", helloPodInstanceRequirement, stateStore);
    DeploymentStep hello3Step = new DeploymentStep("hello-step-3", helloPodInstanceRequirement, stateStore);
    DeploymentStep hello4Step = new DeploymentStep("hello-step-4", helloPodInstanceRequirement, stateStore);
    DeploymentStep world0Step = new DeploymentStep("world-step-0", worldPodInstanceRequirement, stateStore);
    DeploymentStep world1Step = new DeploymentStep("world-step-1", worldPodInstanceRequirement, stateStore);
    DeploymentStep world2Step = new DeploymentStep("world-step-2", worldPodInstanceRequirement, stateStore);
    DeploymentStep world3Step = new DeploymentStep("world-step-3", worldPodInstanceRequirement, stateStore);

    //Simulate a canary-plan where one step is waiting on user input.
    hello0Step.updateInitialStatus(Status.COMPLETE);
    hello1Step.updateInitialStatus(Status.COMPLETE);
    hello2Step.updateInitialStatus(Status.COMPLETE);
    hello3Step.updateInitialStatus(Status.COMPLETE);
    hello4Step.updateInitialStatus(Status.WAITING);

    world0Step.updateInitialStatus(Status.COMPLETE);
    world1Step.updateInitialStatus(Status.COMPLETE);
    world2Step.updateInitialStatus(Status.COMPLETE);
    world3Step.updateInitialStatus(Status.COMPLETE);

    List<Step> helloSteps = Arrays.asList(hello0Step, hello1Step, hello2Step, hello3Step, hello4Step);
    List<Step> worldSteps = Arrays.asList(world0Step, world2Step, world3Step);

    DefaultPhase helloPhase = new DefaultPhase("hello-deploy",
        helloSteps,
        new CanaryStrategy(new ParallelStrategy<>(), helloSteps),
        Collections.emptyList());
    DefaultPhase worldPhase = new DefaultPhase("world-deploy",
        worldSteps,
        new CanaryStrategy(new SerialStrategy<>(), worldSteps),
        Collections.emptyList());
    DefaultPlan helloWorldPlan = new DefaultPlan(Constants.DEPLOY_PLAN_NAME,
        Arrays.asList(helloPhase, worldPhase),
        new SerialStrategy<>(),
        Collections.emptyList());

    DefaultPlan recoveryPlan = new DefaultPlan(Constants.RECOVERY_PLAN_NAME,
        Collections.emptyList(),
        new SerialStrategy<>(),
        Collections.emptyList());

    PlanManager helloWorldPlanManager = DefaultPlanManager.createInterrupted(helloWorldPlan);
    PlanManager recoveryPlanManager = DefaultPlanManager.createProceeding(recoveryPlan);
    PlanCoordinator coordinator = new DefaultPlanCoordinator(Arrays.asList(helloWorldPlanManager, recoveryPlanManager));

    //Setup complete, verify result.

    HealthResource serviceStatusTracker = Mockito.spy(new HealthResource(coordinator, frameworkStore));

    ServiceStatusResult serviceResult = serviceStatusTracker.evaluateServiceStatus(false);
    Optional<ServiceStatusCode> expected = Optional.of(HealthResource.ServiceStatusCode.DEPLOYING_WAITING_USER);
    Optional<ServiceStatusCode> received = serviceResult.getServiceStatusCode();

    Assert.assertEquals(expected.get(), received.get());
  }

  /**
   * Wrapper class for returning {@link PlanCoordinator} and {@link FrameworkStore}
   */
  private static class HealthResourceTestScenario {
    private final PlanCoordinator planCoordinator;
    private final Optional<FrameworkStore> frameworkStore;
    
    public HealthResourceTestScenario(PlanCoordinator planCoordinator, Optional<FrameworkStore> frameworkStore) {
      this.planCoordinator = planCoordinator;
      this.frameworkStore = frameworkStore;
    }
    
    public PlanCoordinator getPlanCoordinator() {
      return planCoordinator;
    }

    public Optional<FrameworkStore> getFrameworkStore() {
      return frameworkStore;
    }
  }
}
