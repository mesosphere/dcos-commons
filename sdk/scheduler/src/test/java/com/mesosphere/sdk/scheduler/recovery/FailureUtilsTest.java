package com.mesosphere.sdk.scheduler.recovery;

import com.mesosphere.sdk.testutils.TestConstants;
import org.apache.mesos.Protos;
import org.junit.Assert;
import org.junit.Test;

/**
 * This class tests the FailureUtils class.
 */
public class FailureUtilsTest {
    @Test
    public void testFailureMarking() {
        Protos.TaskInfo taskInfo = Protos.TaskInfo.newBuilder()
                .setName(TestConstants.TASK_NAME)
                .setTaskId(TestConstants.TASK_ID)
                .setSlaveId(TestConstants.AGENT_ID)
                .build();
        Assert.assertFalse(FailureUtils.isLabeledAsFailed(taskInfo));

        taskInfo = FailureUtils.clearFailed(taskInfo);
        Assert.assertFalse(FailureUtils.isLabeledAsFailed(taskInfo));

        taskInfo = FailureUtils.markFailed(taskInfo);
        Assert.assertTrue(FailureUtils.isLabeledAsFailed(taskInfo));

        taskInfo = FailureUtils.markFailed(taskInfo);
        Assert.assertTrue(FailureUtils.isLabeledAsFailed(taskInfo));

        taskInfo = FailureUtils.clearFailed(taskInfo);
        Assert.assertFalse(FailureUtils.isLabeledAsFailed(taskInfo));
    }
}
