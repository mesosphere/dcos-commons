package org.apache.mesos.scheduler.plan;

import org.apache.mesos.Protos;
import org.apache.mesos.offer.OfferRequirement;
import org.apache.mesos.testutils.TestConstants;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Arrays;
import java.util.Collections;
import java.util.Optional;

/**
 * This class tests the DefaultStep class.
 */
public class DefaultStepTest {
    private static final String TEST_STEP_NAME = "test-step";

    @Mock
    private OfferRequirement mockOfferRequirement;

    @Before
    public void beforeEach() {
        MockitoAnnotations.initMocks(this);
    }

    @Test
    public void testCompleteTerminal() {
        DefaultStep step = new DefaultStep(
                TEST_STEP_NAME,
                Optional.of(mockOfferRequirement),
                Status.PENDING,
                Collections.emptyList());

        Assert.assertTrue(step.isPending());

        Protos.Offer.Operation operation = Protos.Offer.Operation.newBuilder()
                .setType(Protos.Offer.Operation.Type.LAUNCH)
                .setLaunch(Protos.Offer.Operation.Launch.newBuilder()
                        .addTaskInfos(Protos.TaskInfo.newBuilder()
                                .setTaskId(TestConstants.TASK_ID)
                                .setName(TestConstants.TASK_NAME)
                                .setSlaveId(TestConstants.AGENT_ID)))
                .build();
        step.updateOfferStatus(Arrays.asList(operation));

        Assert.assertTrue(step.isInProgress());

        step.update(Protos.TaskStatus.newBuilder()
                .setTaskId(TestConstants.TASK_ID)
                .setState(Protos.TaskState.TASK_RUNNING)
                .build());

        Assert.assertTrue(step.isComplete());

        step.update(Protos.TaskStatus.newBuilder()
                .setTaskId(TestConstants.TASK_ID)
                .setState(Protos.TaskState.TASK_FAILED)
                .build());

        Assert.assertTrue(step.isComplete());
    }
}
