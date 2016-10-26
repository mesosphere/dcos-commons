package org.apache.mesos.offer;

import org.apache.mesos.Protos.TaskID;
import org.apache.mesos.Protos.TaskInfo;
import org.apache.mesos.testutils.ResourceTestUtils;
import org.apache.mesos.testutils.TaskTestUtils;
import org.apache.mesos.testutils.TestConstants;
import org.junit.Test;

import java.util.Collections;

import static org.junit.Assert.assertNotEquals;

public class TaskRequirementTest {

    private static final TaskInfo VALID_TASKINFO = TaskTestUtils.getTaskInfo(Collections.emptyList());

    @Test
    public void testTaskIdChanges() throws Exception {
        assertNotEquals(TestConstants.TASK_ID,
                new TaskRequirement(VALID_TASKINFO).getTaskInfo().getTaskId().getValue());
    }

    @Test
    public void testZeroResourcesValid() throws Exception {
        new TaskRequirement(VALID_TASKINFO);
    }

    @Test
    public void testTwoResourcesValid() throws Exception {
        new TaskRequirement(VALID_TASKINFO.toBuilder()
                .addResources(ResourceTestUtils.getUnreservedCpu(1.0))
                .addResources(ResourceTestUtils.getDesiredRootVolume(1000))
                .build());
    }

    @Test
    public void testEmptyTaskIdValid() throws Exception {
        new TaskRequirement(VALID_TASKINFO.toBuilder()
                .setTaskId(TaskUtils.emptyTaskId())
                .build());
    }

    @Test(expected = InvalidRequirementException.class)
    public void testBadTaskIdFails() throws Exception {
        new TaskRequirement(VALID_TASKINFO.toBuilder()
                .setTaskId(TaskID.newBuilder().setValue("foo"))
                .build());
    }

    @Test(expected = InvalidRequirementException.class)
    public void testEmptyNameFails() throws Exception {
        new TaskRequirement(VALID_TASKINFO.toBuilder().setName("").build());
    }

    @Test(expected = InvalidRequirementException.class)
    public void testMismatchNameFails() throws Exception {
        new TaskRequirement(VALID_TASKINFO.toBuilder().setName("asdf").build());
    }

    @Test(expected = InvalidRequirementException.class)
    public void testExecutorInfoPresentFails() throws Exception {
        new TaskRequirement(VALID_TASKINFO.toBuilder()
                .setExecutor(TaskTestUtils.getExecutorInfo(Collections.emptyList()))
                .build());
    }
}
