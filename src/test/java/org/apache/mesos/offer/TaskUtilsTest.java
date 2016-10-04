package org.apache.mesos.offer;

import org.apache.mesos.Protos;
import org.apache.mesos.specification.*;
import org.apache.mesos.testutils.TestConstants;
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

    @Test
    public void testAreNotDifferentTaskSpecifications() {
        TaskSpecification oldTaskSpecification = TestTaskSetFactory.getTaskSpecification();
        TaskSpecification newTaskSpecification = TestTaskSetFactory.getTaskSpecification();
        Assert.assertFalse(TaskUtils.areDifferent(oldTaskSpecification, newTaskSpecification));
    }

    @Test
    public void testAreDifferentTaskSpecificationsName() {
        TaskSpecification oldTaskSpecification = TestTaskSetFactory.getTaskSpecification();
        TaskSpecification newTaskSpecification =
                TestTaskSetFactory.getTaskSpecification(
                        "new" + TestTaskSetFactory.NAME,
                        TestTaskSetFactory.CMD.getValue(),
                        TestTaskSetFactory.CPU,
                        TestTaskSetFactory.MEM,
                        TestTaskSetFactory.DISK);

        Assert.assertTrue(TaskUtils.areDifferent(oldTaskSpecification, newTaskSpecification));
    }

    @Test
    public void testAreDifferentTaskSpecificationsCmd() {
        TaskSpecification oldTaskSpecification = TestTaskSetFactory.getTaskSpecification();
        TaskSpecification newTaskSpecification =
                TestTaskSetFactory.getTaskSpecification(
                        TestTaskSetFactory.NAME,
                        TestTaskSetFactory.CMD.getValue() + " && echo foo",
                        TestTaskSetFactory.CPU,
                        TestTaskSetFactory.MEM,
                        TestTaskSetFactory.DISK);

        Assert.assertTrue(TaskUtils.areDifferent(oldTaskSpecification, newTaskSpecification));
    }

    @Test
    public void testAreDifferentTaskSpecificationsResourcesLength() {
        TaskSpecification oldTaskSpecification = TestTaskSetFactory.getTaskSpecification();
        TaskSpecification tmpTaskSpecification =
                TestTaskSetFactory.getTaskSpecification(
                        TestTaskSetFactory.NAME,
                        TestTaskSetFactory.CMD.getValue(),
                        TestTaskSetFactory.CPU,
                        TestTaskSetFactory.MEM,
                        TestTaskSetFactory.DISK);
        TestTaskSpecification newTaskSpecification = new TestTaskSpecification(tmpTaskSpecification);
        newTaskSpecification.addResource(new DefaultResourceSpecification(
                "foo",
                Protos.Value.newBuilder()
                        .setType(Protos.Value.Type.SCALAR)
                        .setScalar(Protos.Value.Scalar.newBuilder().setValue(1.0).build())
                .build(),
                TestConstants.ROLE,
                TestConstants.PRINCIPAL));

        Assert.assertTrue(TaskUtils.areDifferent(oldTaskSpecification, newTaskSpecification));
    }

    @Test
    public void testAreDifferentTaskSpecificationsNoResourceOverlap() {
        TaskSpecification tmpOldTaskSpecification = TestTaskSetFactory.getTaskSpecification();
        TestTaskSpecification oldTaskSpecification = new TestTaskSpecification(tmpOldTaskSpecification);
        oldTaskSpecification.addResource(new DefaultResourceSpecification(
                "bar",
                Protos.Value.newBuilder()
                        .setType(Protos.Value.Type.SCALAR)
                        .setScalar(Protos.Value.Scalar.newBuilder().setValue(1.0).build())
                        .build(),
                TestConstants.ROLE,
                TestConstants.PRINCIPAL));

        TaskSpecification tmpNewTaskSpecification =
                TestTaskSetFactory.getTaskSpecification(
                        TestTaskSetFactory.NAME,
                        TestTaskSetFactory.CMD.getValue(),
                        TestTaskSetFactory.CPU,
                        TestTaskSetFactory.MEM,
                        TestTaskSetFactory.DISK);
        TestTaskSpecification newTaskSpecification = new TestTaskSpecification(tmpNewTaskSpecification);
        newTaskSpecification.addResource(new DefaultResourceSpecification(
                "foo",
                Protos.Value.newBuilder()
                        .setType(Protos.Value.Type.SCALAR)
                        .setScalar(Protos.Value.Scalar.newBuilder().setValue(1.0).build())
                        .build(),
                TestConstants.ROLE,
                TestConstants.PRINCIPAL));

        Assert.assertTrue(TaskUtils.areDifferent(oldTaskSpecification, newTaskSpecification));
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
