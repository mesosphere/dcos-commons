package org.apache.mesos.scheduler.plan;

import org.apache.mesos.Protos;
import org.apache.mesos.offer.OfferRequirement;
import org.apache.mesos.offer.TaskUtils;
import org.apache.mesos.specification.PodInstance;
import org.apache.mesos.specification.PodSpec;
import org.apache.mesos.specification.TaskSpec;
import org.apache.mesos.testutils.TestConstants;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Arrays;
import java.util.Collections;
import java.util.Optional;

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
        when(taskSpec.getGoal()).thenReturn(TaskSpec.GoalState.RUNNING);
        when(podInstance.getPod()).thenReturn(podSpec);
        when(podInstance.getName()).thenReturn(TestConstants.POD_TYPE + "-" + 0);
    }

    @Test
    public void testCompleteTerminal() {
        DefaultStep step = new DefaultStep(
                TEST_STEP_NAME,
                Optional.of(mockOfferRequirement),
                Status.PENDING,
                podInstance,
                Collections.emptyList());

        Assert.assertTrue(step.isPending());
        String taskName = TaskSpec.getInstanceName(podInstance, taskSpec);
        Protos.TaskID taskID = TaskUtils.toTaskId(taskName);

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
