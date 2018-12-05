package com.mesosphere.sdk.debug;

import com.google.common.collect.ImmutableList;
import com.mesosphere.sdk.offer.CommonIdUtils;
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

import org.apache.mesos.Protos;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;


/**
 * This class tests the {@link TaskStatusesTracker} class
 */
public class TaskStatusesTrackerTest {
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

  private PodInstanceRequirement getPodInstanceRequirement(PodSpec podSpec, int index) {
    PodInstance podInstance = new DefaultPodInstance(podSpec, index);
    return PodInstanceRequirement.newBuilder(
        podInstance,
        podSpec.getTasks().stream().map(TaskSpec::getName).collect(Collectors.toList()))
        .build();
  }

  private static Protos.TaskInfo newTaskInfo(final String taskName, final Protos.TaskID taskID) {
    Protos.TaskInfo.Builder taskBuilder = Protos.TaskInfo.newBuilder()
        .setName(taskName)
        .setTaskId(taskID);
    taskBuilder.getSlaveIdBuilder().setValue("proto-field-required");
    return taskBuilder.build();
  }

  private static Protos.TaskStatus newTaskStatus(final Protos.TaskID taskID, final Protos.TaskState taskState) {
    return Protos.TaskStatus.newBuilder()
        .setTaskId(taskID)
        .setState(taskState)
        .build();
  }

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
    world2Step.updateInitialStatus(Status.IN_PROGRESS);


    String helloStepInstance = TaskSpec.getInstanceName(
        helloStep.getPodInstanceRequirement().get().getPodInstance(),
        TASK_HELLO_NAME
    );

    String world1StepInstance = TaskSpec.getInstanceName(
        world1Step.getPodInstanceRequirement().get().getPodInstance(),
        TASK_WORLD_NAME
    );

    String world2StepInstance = TaskSpec.getInstanceName(
        world2Step.getPodInstanceRequirement().get().getPodInstance(),
        TASK_WORLD_NAME
    );

    Protos.TaskID helloTaskId = CommonIdUtils.toTaskId("helloworld", TASK_HELLO_NAME);
    Protos.TaskID world1TaskId = CommonIdUtils.toTaskId("helloworld", TASK_HELLO_NAME);
    Protos.TaskID world2TaskId = CommonIdUtils.toTaskId("helloworld", TASK_HELLO_NAME);

    stateStore.storeTasks(ImmutableList.of(newTaskInfo(TASK_HELLO_NAME, helloTaskId)));
    stateStore.storeTasks(ImmutableList.of(newTaskInfo(TASK_WORLD_NAME, world1TaskId)));
    stateStore.storeTasks(ImmutableList.of(newTaskInfo(TASK_WORLD_NAME, world2TaskId)));

    Protos.TaskStatus helloTaskStatus = newTaskStatus(helloTaskId, Protos.TaskState.TASK_FINISHED);
    Protos.TaskStatus world1TaskStatus = newTaskStatus(world1TaskId, Protos.TaskState.TASK_RUNNING);
    Protos.TaskStatus world2TaskStatus = newTaskStatus(world1TaskId, Protos.TaskState.TASK_UNKNOWN);

    stateStore.storeStatus(helloStepInstance, helloTaskStatus);
    stateStore.storeStatus(world1StepInstance, world1TaskStatus);
    stateStore.storeStatus(world2StepInstance, world2TaskStatus);

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

  @Test
  public void testHelloWorldTaskStatusesStructure()
  {
    TaskStatusesTracker taskStatusesTracker = Mockito.spy(new TaskStatusesTracker(coordinator, stateStore));

    List<TaskStatusesTracker.PlanResponse> response =
        taskStatusesTracker.getTaskStatuses(null, null, null);

    TaskStatusesTracker.PlanResponse deployPlanResponse = response.get(0);
    assert deployPlanResponse.getName() == "deploy";
    //verify phases
    assert deployPlanResponse.getPhases().size() == 2;
    assert deployPlanResponse.getPhases().get(0).getName() == "hello-deploy";
    assert deployPlanResponse.getPhases().get(1).getName() == "world-deploy";

    assert deployPlanResponse.getPhases().get(0).getSteps().get(0).getName() == "hello-step";
    assert deployPlanResponse.getPhases().get(1).getSteps().get(0).getName() == "world-step-1";
    assert deployPlanResponse.getPhases().get(1).getSteps().get(1).getName() == "world-step-2";

    assert deployPlanResponse.getPhases().get(0).getSteps().get(0).getTaskStatus().get(0).getTaskStatus()
        == Protos.TaskState.TASK_FINISHED;
    assert deployPlanResponse.getPhases().get(1).getSteps().get(0).getTaskStatus().get(0).getTaskStatus()
        == Protos.TaskState.TASK_UNKNOWN;
  }
}
