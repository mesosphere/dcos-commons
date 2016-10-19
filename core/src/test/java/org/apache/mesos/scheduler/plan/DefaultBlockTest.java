package org.apache.mesos.scheduler.plan;

import org.apache.mesos.Protos;
import org.apache.mesos.offer.OfferRequirement;
import org.apache.mesos.testutils.TestConstants;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.testng.Assert;

import java.util.Arrays;

/**
 * This class tests the DefaultBlock class.
 */
public class DefaultBlockTest {
    private static final String TEST_BLOCK_NAME = "test-block";

    @Mock
    private OfferRequirement mockOfferRequirement;

    @Before
    public void beforeEach() {
        MockitoAnnotations.initMocks(this);
    }

    @Test
    public void testCompleteTerminal() {
        DefaultBlock block = new DefaultBlock(TEST_BLOCK_NAME, mockOfferRequirement, Status.PENDING);

        Assert.assertTrue(block.isPending());

        Protos.Offer.Operation operation = Protos.Offer.Operation.newBuilder()
                .setType(Protos.Offer.Operation.Type.LAUNCH)
                .setLaunch(Protos.Offer.Operation.Launch.newBuilder()
                        .addTaskInfos(Protos.TaskInfo.newBuilder()
                                .setTaskId(TestConstants.TASK_ID)
                                .setName(TestConstants.TASK_NAME)
                                .setSlaveId(TestConstants.AGENT_ID)))
                .build();
        block.updateOfferStatus(Arrays.asList(operation));

        Assert.assertTrue(block.isInProgress());

        block.update(Protos.TaskStatus.newBuilder()
                .setTaskId(TestConstants.TASK_ID)
                .setState(Protos.TaskState.TASK_RUNNING)
                .build());

        Assert.assertTrue(block.isComplete());

        block.update(Protos.TaskStatus.newBuilder()
                .setTaskId(TestConstants.TASK_ID)
                .setState(Protos.TaskState.TASK_FAILED)
                .build());

        Assert.assertTrue(block.isComplete());
    }
}
