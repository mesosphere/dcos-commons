package com.mesosphere.sdk.scheduler.plan;

import com.mesosphere.sdk.offer.CommonTaskUtils;
import com.mesosphere.sdk.offer.OfferRequirement;
import com.mesosphere.sdk.offer.TaskUtils;
import com.mesosphere.sdk.specification.GoalState;
import com.mesosphere.sdk.specification.PodInstance;
import com.mesosphere.sdk.specification.PodSpec;
import com.mesosphere.sdk.specification.TaskSpec;
import com.mesosphere.sdk.testutils.TestConstants;
import org.apache.mesos.Protos;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Arrays;
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
        DefaultStep step = new DefaultStep(
                TEST_STEP_NAME,
                Status.PENDING,
                podInstance,
                TaskUtils.getTaskNames(podInstance),
                Collections.emptyList());

        Assert.assertTrue(step.isPending());
        String taskName = TaskSpec.getInstanceName(podInstance, taskSpec);
        Protos.TaskID taskID = CommonTaskUtils.toTaskId(taskName);

        Protos.Offer.Operation operation = Protos.Offer.Operation.newBuilder()
                .setType(Protos.Offer.Operation.Type.LAUNCH)
                .setLaunch(Protos.Offer.Operation.Launch.newBuilder()
                        .addTaskInfos(Protos.TaskInfo.newBuilder()
                                .setTaskId(taskID)
                                .setName(taskName)
                                .setSlaveId(TestConstants.AGENT_ID)))
                .build();
        step.updateOfferStatus(Arrays.asList(operation));

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
}
