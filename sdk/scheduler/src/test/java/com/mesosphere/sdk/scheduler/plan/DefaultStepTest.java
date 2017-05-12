package com.mesosphere.sdk.scheduler.plan;

import com.mesosphere.sdk.offer.CommonIdUtils;
import com.mesosphere.sdk.offer.LaunchOfferRecommendation;
import com.mesosphere.sdk.offer.OfferRequirement;
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
public class DefaultStepTest {
    private static final String TEST_STEP_NAME = "test-step";

    @Mock private OfferRequirement mockOfferRequirement;
    @Mock private PodSpec podSpec;
    @Mock private PodInstance podInstance;
    @Mock private TaskSpec taskSpec;

    @Before
    public void beforeEach() {
        MockitoAnnotations.initMocks(this);
        when(podSpec.getTasks()).thenReturn(Arrays.asList(taskSpec));
        when(podSpec.getType()).thenReturn(TestConstants.POD_TYPE);
        when(taskSpec.getName()).thenReturn(TestConstants.TASK_NAME);
        when(taskSpec.getGoal()).thenReturn(GoalState.RUNNING);
        when(podInstance.getPod()).thenReturn(podSpec);
        when(podInstance.getName()).thenReturn(TestConstants.POD_TYPE + "-" + 0);
    }

    @Test
    public void testCompleteTerminal() {
        DeploymentStep step = new DeploymentStep(
                TEST_STEP_NAME,
                Status.PENDING,
                PodInstanceRequirement.newBuilder(podInstance, TaskUtils.getTaskNames(podInstance)).build(),
                Collections.emptyList());

        Assert.assertTrue(step.isPending());
        String taskName = TaskSpec.getInstanceName(podInstance, taskSpec);
        Protos.TaskID taskID = CommonIdUtils.toTaskId(taskName);

        LaunchOfferRecommendation launchRec = new LaunchOfferRecommendation(
                OfferTestUtils.getEmptyOfferBuilder().build(),
                Protos.TaskInfo.newBuilder()
                    .setTaskId(taskID)
                    .setName(taskName)
                    .setSlaveId(TestConstants.AGENT_ID)
                    .build());
        step.updateOfferStatus(Arrays.asList(launchRec));

        Assert.assertTrue(step.isStarting());

        step.update(Protos.TaskStatus.newBuilder()
                .setTaskId(taskID)
                .setState(Protos.TaskState.TASK_RUNNING)
                .build());

        Assert.assertTrue(step.isComplete());

        step.update(Protos.TaskStatus.newBuilder()
                .setTaskId(taskID)
                .setState(Protos.TaskState.TASK_FAILED)
                .build());

        Assert.assertTrue(step.isComplete());
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
}
