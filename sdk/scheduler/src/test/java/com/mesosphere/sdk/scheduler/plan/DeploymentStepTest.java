package com.mesosphere.sdk.scheduler.plan;

import com.mesosphere.sdk.offer.CommonIdUtils;
import com.mesosphere.sdk.offer.LaunchOfferRecommendation;
import com.mesosphere.sdk.offer.TaskUtils;
import com.mesosphere.sdk.specification.*;
import com.mesosphere.sdk.testutils.OfferTestUtils;
import com.mesosphere.sdk.testutils.TestConstants;
import org.apache.mesos.Protos;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;

import static org.mockito.Mockito.when;

/**
 * This class tests the DefaultStep class.
 */
public class DeploymentStepTest {
    private static final String TEST_STEP_NAME = "test-step";

    @Mock private PodSpec podSpec;
    @Mock private PodInstance podInstance;
    @Mock private TaskSpec taskSpec;
    private String taskName;
    private Protos.TaskID taskID;

    @Before
    public void beforeEach() {
        MockitoAnnotations.initMocks(this);
        when(podSpec.getTasks()).thenReturn(Arrays.asList(taskSpec));
        when(podSpec.getType()).thenReturn(TestConstants.POD_TYPE);
        when(taskSpec.getName()).thenReturn(TestConstants.TASK_NAME);
        when(taskSpec.getGoal()).thenReturn(GoalState.RUNNING);
        when(podInstance.getPod()).thenReturn(podSpec);
        when(podInstance.getName()).thenReturn(TestConstants.POD_TYPE + "-" + 0);
        taskName = TaskSpec.getInstanceName(podInstance, taskSpec);
        taskID = CommonIdUtils.toTaskId(taskName);
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
                        TestConstants.TASK_NAME + 0, TestConstants.RESOURCE_SET_ID + 0, TestConstants.TASK_DNS_PREFIX);
        TaskSpec taskSpec1 =
                TestPodFactory.getTaskSpec(
                        TestConstants.TASK_NAME + 1, TestConstants.RESOURCE_SET_ID + 1, TestConstants.TASK_DNS_PREFIX);
        PodSpec podSpec = DefaultPodSpec.newBuilder("")
                .type(TestConstants.POD_TYPE)
                .count(1)
                .tasks(Arrays.asList(taskSpec0, taskSpec1))
                .build();
        PodInstance podInstance = new DefaultPodInstance(podSpec, 0);

        DeploymentStep step = new DeploymentStep(
                TEST_STEP_NAME,
                Status.PENDING,
                PodInstanceRequirement.newBuilder(podInstance, TaskUtils.getTaskNames(podInstance)).build(),
                Collections.emptyList());

        Assert.assertTrue(step.isEligible(Arrays.asList()));

        Collection<PodInstanceRequirement> dirtyAssets = Arrays.asList(step.getAsset().get());
        Assert.assertFalse(step.isEligible(dirtyAssets));
    }

    private void testStepTransition(
            Step step,
            Protos.TaskState updateState,
            Status startStatus,
            Status endStatus) {

        Assert.assertEquals(startStatus, step.getStatus());
        step.update(Protos.TaskStatus.newBuilder()
                .setTaskId(taskID)
                .setState(updateState)
                .build());

        Assert.assertEquals(endStatus, step.getStatus());
    }

    private Step getPendingStep() {
        return new DeploymentStep(
                TEST_STEP_NAME,
                Status.PENDING,
                PodInstanceRequirement.newBuilder(podInstance, TaskUtils.getTaskNames(podInstance)).build(),
                Collections.emptyList());
    }

    private Step getStartingStep() {
        Step step = getPendingStep();
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
