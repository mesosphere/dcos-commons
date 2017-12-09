package com.mesosphere.sdk.scheduler.plan;

import com.mesosphere.sdk.offer.CommonIdUtils;
import com.mesosphere.sdk.offer.LaunchOfferRecommendation;
import com.mesosphere.sdk.offer.TaskUtils;
import com.mesosphere.sdk.specification.*;
import com.mesosphere.sdk.state.GoalStateOverride;
import com.mesosphere.sdk.state.StateStore;
import com.mesosphere.sdk.testutils.OfferTestUtils;
import com.mesosphere.sdk.testutils.TestConstants;
import com.mesosphere.sdk.testutils.TestPodFactory;
import org.apache.mesos.Protos;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.*;

import static org.mockito.Matchers.any;
import static org.mockito.Mockito.when;

/**
 * Tests for {@link DeploymentStep}.
 */
public class DeploymentStepTest {
    private static final String TEST_STEP_NAME = "test-step";
    private static final String TASK_NAME_0 = TestConstants.TASK_NAME + 0;
    private static final String TASK_NAME_1 = TestConstants.TASK_NAME + 1;

    @Mock private PodSpec mockPodSpec;
    @Mock private PodInstance mockPodInstance;
    @Mock private TaskSpec mockTaskSpec;
    @Mock private StateStore mockStateStore;
    private String taskName;
    private Protos.TaskID taskID;

    @Before
    public void beforeEach() {
        MockitoAnnotations.initMocks(this);
        when(mockPodSpec.getTasks()).thenReturn(Arrays.asList(mockTaskSpec));
        when(mockPodSpec.getType()).thenReturn(TestConstants.POD_TYPE);
        when(mockTaskSpec.getName()).thenReturn(TestConstants.TASK_NAME);
        when(mockTaskSpec.getGoal()).thenReturn(GoalState.RUNNING);
        when(mockPodInstance.getPod()).thenReturn(mockPodSpec);
        String podInstanceName = PodInstance.getName(TestConstants.POD_TYPE, 0);
        when(mockPodInstance.getName()).thenReturn(podInstanceName);
        taskName = TaskSpec.getInstanceName(mockPodInstance, mockTaskSpec);
        taskID = CommonIdUtils.toTaskId(taskName);

        when(mockStateStore.fetchGoalOverrideStatus(podInstanceName + "-" + TASK_NAME_0))
                .thenReturn(GoalStateOverride.Status.INACTIVE);
        when(mockStateStore.fetchGoalOverrideStatus(podInstanceName + "-" + TASK_NAME_1))
                .thenReturn(GoalStateOverride.PAUSED.newStatus(GoalStateOverride.Progress.IN_PROGRESS));
        when(mockStateStore.fetchGoalOverrideStatus(any()))
                .thenReturn(GoalStateOverride.Status.INACTIVE);
    }

    @Test
    public void testGetStatusReturnsMinimumState() {
        Assert.assertEquals(Status.PENDING, DeploymentStep.getStatus(Collections.emptySet(), false, false).get());
        Assert.assertEquals(Status.PREPARED, DeploymentStep.getStatus(Collections.emptySet(), false, true).get());
        Assert.assertEquals(Status.ERROR, DeploymentStep.getStatus(Collections.emptySet(), true, false).get());
        Assert.assertEquals(Status.ERROR, DeploymentStep.getStatus(Collections.emptySet(), true, true).get());

        Assert.assertEquals(Status.ERROR,
                DeploymentStep.getStatus(toSet(Status.PREPARED, Status.ERROR, Status.COMPLETE), false, false).get());

        Assert.assertEquals(Status.PENDING,
                DeploymentStep.getStatus(toSet(Status.PREPARED, Status.PENDING, Status.PENDING), false, false).get());

        Assert.assertEquals(Status.PREPARED,
                DeploymentStep.getStatus(toSet(Status.PREPARED, Status.STARTING, Status.PREPARED), false, false).get());

        Assert.assertEquals(Status.PENDING,
                DeploymentStep.getStatus(toSet(Status.PENDING, Status.STARTING, Status.STARTING), false, false).get());

        Assert.assertEquals(Status.STARTING,
                DeploymentStep.getStatus(toSet(Status.STARTING, Status.STARTING, Status.COMPLETE), false, false).get());

        Assert.assertEquals(Status.COMPLETE,
                DeploymentStep.getStatus(toSet(Status.COMPLETE, Status.COMPLETE, Status.COMPLETE), false, false).get());

        // Invalid state
        Assert.assertFalse(DeploymentStep.getStatus(
                toSet(Status.COMPLETE, Status.COMPLETE, Status.IN_PROGRESS), false, false).isPresent());
    }

    @Test
    public void testGetDisplayStatus() {
        when(mockStateStore.fetchGoalOverrideStatus("no-override-0")).thenReturn(GoalStateOverride.Status.INACTIVE);
        when(mockStateStore.fetchGoalOverrideStatus("no-override-1")).thenReturn(GoalStateOverride.Status.INACTIVE);
        // Note that the override progress shouldn't have any effect:
        when(mockStateStore.fetchGoalOverrideStatus("paused-0")).thenReturn(
                GoalStateOverride.PAUSED.newStatus(GoalStateOverride.Progress.PENDING));
        when(mockStateStore.fetchGoalOverrideStatus("paused-1")).thenReturn(
                GoalStateOverride.PAUSED.newStatus(GoalStateOverride.Progress.COMPLETE));

        Assert.assertEquals("IN_PROGRESS",
                DeploymentStep.getDisplayStatus(mockStateStore, Status.IN_PROGRESS, Collections.emptyList()));
        Assert.assertEquals("COMPLETE",
                DeploymentStep.getDisplayStatus(mockStateStore, Status.COMPLETE, Collections.emptyList()));

        Assert.assertEquals("PAUSING", DeploymentStep.getDisplayStatus(mockStateStore, Status.IN_PROGRESS,
                Arrays.asList("paused-0")));
        Assert.assertEquals("PAUSING", DeploymentStep.getDisplayStatus(mockStateStore, Status.IN_PROGRESS,
                Arrays.asList("paused-1")));
        Assert.assertEquals("PAUSING", DeploymentStep.getDisplayStatus(mockStateStore, Status.IN_PROGRESS,
                Arrays.asList("paused-0", "paused-1")));
        Assert.assertEquals("IN_PROGRESS", DeploymentStep.getDisplayStatus(mockStateStore, Status.IN_PROGRESS,
                Arrays.asList("no-override-0", "no-override-1")));
        Assert.assertEquals("IN_PROGRESS", DeploymentStep.getDisplayStatus(mockStateStore, Status.IN_PROGRESS,
                Arrays.asList("no-override-0", "paused-0")));
        Assert.assertEquals("IN_PROGRESS", DeploymentStep.getDisplayStatus(mockStateStore, Status.IN_PROGRESS,
                Arrays.asList("no-override-0", "paused-1")));
        Assert.assertEquals("IN_PROGRESS", DeploymentStep.getDisplayStatus(mockStateStore, Status.IN_PROGRESS,
                Arrays.asList("no-override-0", "paused-0", "paused-1")));

        Assert.assertEquals("PAUSED", DeploymentStep.getDisplayStatus(mockStateStore, Status.COMPLETE,
                Arrays.asList("paused-0")));
        Assert.assertEquals("PAUSED", DeploymentStep.getDisplayStatus(mockStateStore, Status.COMPLETE,
                Arrays.asList("paused-1")));
        Assert.assertEquals("PAUSED", DeploymentStep.getDisplayStatus(mockStateStore, Status.COMPLETE,
                Arrays.asList("paused-0", "paused-1")));
        Assert.assertEquals("COMPLETE", DeploymentStep.getDisplayStatus(mockStateStore, Status.COMPLETE,
                Arrays.asList("no-override-0", "no-override-1")));
        Assert.assertEquals("COMPLETE", DeploymentStep.getDisplayStatus(mockStateStore, Status.COMPLETE,
                Arrays.asList("no-override-0", "paused-0")));
        Assert.assertEquals("COMPLETE", DeploymentStep.getDisplayStatus(mockStateStore, Status.COMPLETE,
                Arrays.asList("no-override-0", "paused-1")));
        Assert.assertEquals("COMPLETE", DeploymentStep.getDisplayStatus(mockStateStore, Status.COMPLETE,
                Arrays.asList("no-override-0", "paused-0", "paused-1")));
    }

    private static Set<Status> toSet(Status... elems) {
        return new HashSet<>(Arrays.asList(elems));
    }

    @Test
    public void testCompleteTerminal() {
        Step step = getStartingStep();
        testStepTransition(step, Protos.TaskState.TASK_RUNNING, Status.STARTING, Status.COMPLETE);

        step.update(Protos.TaskStatus.newBuilder()
                .setTaskId(taskID)
                .setState(Protos.TaskState.TASK_FAILED)
                .build());

        Assert.assertTrue(step.isComplete());
        Assert.assertEquals(Status.COMPLETE.toString(), step.getDisplayStatus());
    }

    @Test
    public void testErrorRetainedAcrossUpdates() {
        // once an ERROR, always an ERROR
        Step step = getStartingStep().addError("an error");
        testStepTransition(step, Protos.TaskState.TASK_RUNNING, Status.ERROR, Status.ERROR);

        step.update(Protos.TaskStatus.newBuilder()
                .setTaskId(taskID)
                .setState(Protos.TaskState.TASK_FAILED)
                .build());

        Assert.assertEquals(Status.ERROR, step.getStatus());
        Assert.assertEquals(Status.ERROR.toString(), step.getDisplayStatus());
    }

    @Test
    public void testErrorCausesStartingToPending() {
        Protos.TaskState[] errorStates = {
                Protos.TaskState.TASK_ERROR,
                Protos.TaskState.TASK_FAILED,
                Protos.TaskState.TASK_KILLED,
                Protos.TaskState.TASK_KILLING,
                Protos.TaskState.TASK_LOST};

        for (Protos.TaskState state : errorStates) {
            Step step = getStartingStep();
            testStepTransition(step, state, Status.STARTING, Status.PENDING);
        }
    }

    @Test
    public void testIsEligible() {
        TaskSpec taskSpec0 =
                TestPodFactory.getTaskSpec(
                        TASK_NAME_0, TestConstants.RESOURCE_SET_ID + 0, TestConstants.TASK_DNS_PREFIX);
        TaskSpec taskSpec1 =
                TestPodFactory.getTaskSpec(
                        TASK_NAME_1, TestConstants.RESOURCE_SET_ID + 1, TestConstants.TASK_DNS_PREFIX);
        PodSpec podSpec = DefaultPodSpec.newBuilder("")
                .type(TestConstants.POD_TYPE)
                .count(1)
                .tasks(Arrays.asList(taskSpec0, taskSpec1))
                .build();
        PodInstance podInstance = new DefaultPodInstance(podSpec, 0);

        DeploymentStep step = new DeploymentStep(
                TEST_STEP_NAME,
                PodInstanceRequirement.newBuilder(podInstance, TaskUtils.getTaskNames(podInstance)).build(),
                mockStateStore);

        Assert.assertTrue(step.isEligible(Arrays.asList()));

        Collection<PodInstanceRequirement> dirtyAssets = Arrays.asList(step.getPodInstanceRequirement().get());
        Assert.assertFalse(step.isEligible(dirtyAssets));
    }

    @Test
    public void testPrepared() {
        TaskSpec taskSpec0 =
                TestPodFactory.getTaskSpec(
                        TASK_NAME_0, TestConstants.RESOURCE_SET_ID + 0, TestConstants.TASK_DNS_PREFIX);
        TaskSpec taskSpec1 =
                TestPodFactory.getTaskSpec(
                        TASK_NAME_1, TestConstants.RESOURCE_SET_ID + 1, TestConstants.TASK_DNS_PREFIX);
        PodSpec podSpec = DefaultPodSpec.newBuilder("")
                .type(TestConstants.POD_TYPE)
                .count(1)
                .tasks(Arrays.asList(taskSpec0, taskSpec1))
                .build();
        PodInstance podInstance = new DefaultPodInstance(podSpec, 0);

        DeploymentStep step = new DeploymentStep(
                TEST_STEP_NAME,
                PodInstanceRequirement.newBuilder(podInstance, TaskUtils.getTaskNames(podInstance)).build(),
                mockStateStore);

        Assert.assertTrue(step.isPending());

        step.updateOfferStatus(Collections.emptyList());
        Assert.assertTrue(step.isPrepared());
    }

    @Test
    public void testStepStatusCoherenceOnSuccess() {
        // A TaskStatus update of RUNNING should not cause a backwards motion in Step status.
        // One of multiple tasks going to its goal state (RUNNING) should mean the Step stays STARTING until, all
        // tasks are RUNNING.
        testStepStatusCoherence(Protos.TaskState.TASK_RUNNING, Status.STARTING);
    }

    @Test
    public void testStepStatusCoherenceOnFailure() {
        // A TaskStatus update of FAILED should cause a backwards motion in Step status.
        // One of multiple tasks failing should mean the Step as a whole goes back to PENDING
        testStepStatusCoherence(Protos.TaskState.TASK_FAILED, Status.PENDING);
    }

    private void testStepStatusCoherence(Protos.TaskState updateState, Status expectedStatus) {
        String taskName0 = TASK_NAME_0;
        TaskSpec taskSpec0 =
                TestPodFactory.getTaskSpec(
                        taskName0, TestConstants.RESOURCE_SET_ID + 0, TestConstants.TASK_DNS_PREFIX);

        String taskName1 = TASK_NAME_1;
        TaskSpec taskSpec1 =
                TestPodFactory.getTaskSpec(
                        taskName1, TestConstants.RESOURCE_SET_ID + 1, TestConstants.TASK_DNS_PREFIX);
        PodSpec podSpec = DefaultPodSpec.newBuilder("")
                .type(TestConstants.POD_TYPE)
                .count(1)
                .tasks(Arrays.asList(taskSpec0, taskSpec1))
                .build();
        PodInstance podInstance = new DefaultPodInstance(podSpec, 0);

        Protos.TaskID taskId0 = CommonIdUtils.toTaskId(TaskSpec.getInstanceName(podInstance, taskName0));
        Protos.TaskID taskId1 = CommonIdUtils.toTaskId(TaskSpec.getInstanceName(podInstance, taskName1));

        DeploymentStep step = new DeploymentStep(
                TEST_STEP_NAME,
                PodInstanceRequirement.newBuilder(podInstance, TaskUtils.getTaskNames(podInstance)).build(),
                mockStateStore);

        LaunchOfferRecommendation launchRec0 = new LaunchOfferRecommendation(
                OfferTestUtils.getEmptyOfferBuilder().build(),
                Protos.TaskInfo.newBuilder()
                        .setTaskId(taskId0)
                        .setName(taskName0)
                        .setSlaveId(TestConstants.AGENT_ID)
                        .build(),
                Protos.ExecutorInfo.newBuilder().setExecutorId(
                        Protos.ExecutorID.newBuilder().setValue("executor")).build(),
                true,
                true);

        LaunchOfferRecommendation launchRec1 = new LaunchOfferRecommendation(
                OfferTestUtils.getEmptyOfferBuilder().build(),
                Protos.TaskInfo.newBuilder()
                        .setTaskId(taskId1)
                        .setName(taskName1)
                        .setSlaveId(TestConstants.AGENT_ID)
                        .build(),
                Protos.ExecutorInfo.newBuilder().setExecutorId(
                        Protos.ExecutorID.newBuilder().setValue("executor")).build(),
                true,
                true);

        step.updateOfferStatus(Arrays.asList(launchRec0, launchRec1));
        Assert.assertEquals(Status.STARTING, step.getStatus());
        Assert.assertEquals(Status.STARTING.toString(), step.getDisplayStatus());

        step.update(Protos.TaskStatus.newBuilder()
                .setTaskId(taskId0)
                .setState(updateState)
                .build());
        Assert.assertEquals(expectedStatus, step.getStatus());
        Assert.assertEquals(expectedStatus.toString(), step.getDisplayStatus());
    }

    private void testStepTransition(
            Step step,
            Protos.TaskState updateState,
            Status startStatus,
            Status endStatus) {

        Assert.assertEquals(startStatus, step.getStatus());
        Assert.assertEquals(startStatus.toString(), step.getDisplayStatus());
        step.update(Protos.TaskStatus.newBuilder()
                .setTaskId(taskID)
                .setState(updateState)
                .build());

        Assert.assertEquals(endStatus, step.getStatus());
        Assert.assertEquals(endStatus.toString(), step.getDisplayStatus());
    }

    private DeploymentStep getPendingStep() {
        return new DeploymentStep(
                TEST_STEP_NAME,
                PodInstanceRequirement.newBuilder(mockPodInstance, TaskUtils.getTaskNames(mockPodInstance)).build(),
                mockStateStore);
    }

    private DeploymentStep getStartingStep() {
        DeploymentStep step = getPendingStep();
        LaunchOfferRecommendation launchRec = new LaunchOfferRecommendation(
                OfferTestUtils.getEmptyOfferBuilder().build(),
                Protos.TaskInfo.newBuilder()
                        .setTaskId(taskID)
                        .setName(taskName)
                        .setSlaveId(TestConstants.AGENT_ID)
                        .build(),
                Protos.ExecutorInfo.newBuilder().setExecutorId(
                        Protos.ExecutorID.newBuilder().setValue("executor")).build(),
                true,
                true);
        step.updateOfferStatus(Arrays.asList(launchRec));
        return step;
    }
}
