package org.apache.mesos.offer;

import static org.junit.Assert.*;

import org.apache.mesos.Protos.CommandInfo;
import org.apache.mesos.Protos.SlaveID;
import org.apache.mesos.Protos.TaskID;
import org.apache.mesos.Protos.TaskInfo;
import org.apache.mesos.protobuf.ExecutorInfoBuilder;
import org.apache.mesos.protobuf.ResourceBuilder;
import org.junit.Test;

public class TaskRequirementTest {

    private static final TaskInfo VALID_TASKINFO = TaskInfo.newBuilder()
            .setSlaveId(SlaveID.newBuilder().setValue("ignored"))
            .setTaskId(TaskID.newBuilder().setValue(ResourceTestUtils.testTaskId))
            .setName(ResourceTestUtils.testTaskName)
            .build();

    @Test
    public void testTaskIdChanges() throws Exception {
        assertNotEquals(ResourceTestUtils.testTaskId,
                new TaskRequirement(VALID_TASKINFO).getTaskInfo().getTaskId().getValue());
    }

    @Test
    public void testZeroResourcesValid() throws Exception {
        new TaskRequirement(VALID_TASKINFO);
    }

    @Test
    public void testTwoResourcesValid() throws Exception {
        new TaskRequirement(VALID_TASKINFO.toBuilder()
                .addResources(ResourceBuilder.cpus(1.0))
                .addResources(ResourceBuilder.disk(1234.))
                .build());
    }

    @Test
    public void testEmptyTaskIdValid() throws Exception {
        new TaskRequirement(VALID_TASKINFO.toBuilder()
                .setTaskId(TaskID.newBuilder().setValue(""))
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
                .setExecutor(new ExecutorInfoBuilder(
                        "ignored-id", "ignored-name", CommandInfo.newBuilder().build()).build())
                .build());
    }
}
