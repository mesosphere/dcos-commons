package org.apache.mesos.offer;

import org.apache.mesos.Protos;
import org.junit.Assert;
import org.junit.Test;

import java.util.UUID;

/**
 * This class tests the TaskUtils class.
 */
public class TaskUtilsTest {
    private static final String testTaskName = "test-task-name";
    private static final String testTaskId = "test-task-id";
    private static final String testAgentId = "test-agent-id";
    private static final UUID testTargetConfigurationId = UUID.randomUUID();

    @Test
    public void testValidToTaskName() throws Exception {
        Protos.TaskID validTaskId = getTaskId(testTaskName + "__id");
        Assert.assertEquals(testTaskName, TaskUtils.toTaskName(validTaskId));
    }

    @Test(expected=TaskException.class)
    public void testInvalidToTaskName() throws Exception {
        TaskUtils.toTaskName(getTaskId(testTaskName + "_id"));
    }

    @Test(expected=TaskException.class)
    public void testGetTargetConfigurationFailure() throws Exception {
        TaskUtils.getTargetConfiguration(getTestTaskInfo());
    }

    @Test
    public void testGetTargetConfigurationSuccess() throws Exception {
        Assert.assertEquals(
                testTargetConfigurationId,
                TaskUtils.getTargetConfiguration(getTestTaskInfoWithTargetConfiguration()));
    }

    @Test
    public void testSetTargetConfiguration() throws Exception {
        Protos.TaskInfo taskInfo = TaskUtils.setTargetConfiguration(getTestTaskInfo(), testTargetConfigurationId);
        Assert.assertEquals(testTargetConfigurationId, TaskUtils.getTargetConfiguration(taskInfo));
    }

    private Protos.TaskID getTaskId(String value) {
        return Protos.TaskID.newBuilder().setValue(value).build();
    }

    private Protos.TaskInfo getTestTaskInfoWithTargetConfiguration() {
        return TaskUtils.setTargetConfiguration(getTestTaskInfo(), testTargetConfigurationId);
    }

    private Protos.TaskInfo getTestTaskInfo() {
        return Protos.TaskInfo.newBuilder()
                .setName(testTaskName)
                .setTaskId(Protos.TaskID.newBuilder().setValue(testTaskId))
                .setSlaveId(Protos.SlaveID.newBuilder().setValue(testAgentId))
                .build();
    }
}
