package org.apache.mesos.offer;

import org.apache.mesos.Protos;
import org.apache.mesos.specification.*;
import org.apache.mesos.testutils.OfferTestUtils;
import org.apache.mesos.testutils.TestConstants;
import org.junit.Assert;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
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
        TaskSpecification oldTaskSpecification = TestTaskSpecificationFactory.getTaskSpecification();
        TaskSpecification newTaskSpecification = TestTaskSpecificationFactory.getTaskSpecification();
        Assert.assertFalse(TaskUtils.areDifferent(oldTaskSpecification, newTaskSpecification));
    }

    @Test
    public void testAreDifferentTaskSpecificationsName() {
        TaskSpecification oldTaskSpecification = TestTaskSpecificationFactory.getTaskSpecification();
        TaskSpecification newTaskSpecification =
                TestTaskSpecificationFactory.getTaskSpecification(
                        "new" + TestTaskSpecificationFactory.NAME,
                        TestTaskSpecificationFactory.CMD.getValue(),
                        TestTaskSpecificationFactory.CPU,
                        TestTaskSpecificationFactory.MEM,
                        TestTaskSpecificationFactory.DISK);

        Assert.assertTrue(TaskUtils.areDifferent(oldTaskSpecification, newTaskSpecification));
    }

    @Test
    public void testAreDifferentTaskSpecificationsCmd() {
        TaskSpecification oldTaskSpecification = TestTaskSpecificationFactory.getTaskSpecification();
        TaskSpecification newTaskSpecification =
                TestTaskSpecificationFactory.getTaskSpecification(
                        TestTaskSpecificationFactory.NAME,
                        TestTaskSpecificationFactory.CMD.getValue() + " && echo foo",
                        TestTaskSpecificationFactory.CPU,
                        TestTaskSpecificationFactory.MEM,
                        TestTaskSpecificationFactory.DISK);

        Assert.assertTrue(TaskUtils.areDifferent(oldTaskSpecification, newTaskSpecification));
    }

    @Test
    public void testAreDifferentTaskSpecificationsResourcesLength() {
        TaskSpecification oldTaskSpecification = TestTaskSpecificationFactory.getTaskSpecification();
        TaskSpecification tmpTaskSpecification =
                TestTaskSpecificationFactory.getTaskSpecification(
                        TestTaskSpecificationFactory.NAME,
                        TestTaskSpecificationFactory.CMD.getValue(),
                        TestTaskSpecificationFactory.CPU,
                        TestTaskSpecificationFactory.MEM,
                        TestTaskSpecificationFactory.DISK);
        TestTaskSpecification newTaskSpecification = new TestTaskSpecification(tmpTaskSpecification);
        newTaskSpecification.addResource(new DefaultResourceSpecification(
                "foo",
                Protos.Value.newBuilder()
                        .setType(Protos.Value.Type.SCALAR)
                        .setScalar(Protos.Value.Scalar.newBuilder().setValue(1.0).build())
                .build(),
                TestConstants.role,
                TestConstants.principal));

        Assert.assertTrue(TaskUtils.areDifferent(oldTaskSpecification, newTaskSpecification));
    }

    @Test
    public void testAreDifferentTaskSpecificationsNoResourceOverlap() {
        TaskSpecification tmpOldTaskSpecification = TestTaskSpecificationFactory.getTaskSpecification();
        TestTaskSpecification oldTaskSpecification = new TestTaskSpecification(tmpOldTaskSpecification);
        oldTaskSpecification.addResource(new DefaultResourceSpecification(
                "bar",
                Protos.Value.newBuilder()
                        .setType(Protos.Value.Type.SCALAR)
                        .setScalar(Protos.Value.Scalar.newBuilder().setValue(1.0).build())
                        .build(),
                TestConstants.role,
                TestConstants.principal));

        TaskSpecification tmpNewTaskSpecification =
                TestTaskSpecificationFactory.getTaskSpecification(
                        TestTaskSpecificationFactory.NAME,
                        TestTaskSpecificationFactory.CMD.getValue(),
                        TestTaskSpecificationFactory.CPU,
                        TestTaskSpecificationFactory.MEM,
                        TestTaskSpecificationFactory.DISK);
        TestTaskSpecification newTaskSpecification = new TestTaskSpecification(tmpNewTaskSpecification);
        newTaskSpecification.addResource(new DefaultResourceSpecification(
                "foo",
                Protos.Value.newBuilder()
                        .setType(Protos.Value.Type.SCALAR)
                        .setScalar(Protos.Value.Scalar.newBuilder().setValue(1.0).build())
                        .build(),
                TestConstants.role,
                TestConstants.principal));

        Assert.assertTrue(TaskUtils.areDifferent(oldTaskSpecification, newTaskSpecification));
    }

    @Test
    public void testSetGetOfferAttributes() {
        Protos.Offer.Builder offerBuilder = OfferTestUtils.getEmptyOfferBuilder();
        Protos.Attribute.Builder attrBuilder =
                offerBuilder.addAttributesBuilder().setName("1").setType(Protos.Value.Type.RANGES);
        attrBuilder.getRangesBuilder().addRangeBuilder().setBegin(5).setEnd(6);
        attrBuilder.getRangesBuilder().addRangeBuilder().setBegin(10).setEnd(12);
        attrBuilder = offerBuilder.addAttributesBuilder().setName("2").setType(Protos.Value.Type.SCALAR);
        attrBuilder.getScalarBuilder().setValue(123.4567);
        attrBuilder = offerBuilder.addAttributesBuilder().setName("3").setType(Protos.Value.Type.SET);
        attrBuilder.getSetBuilder().addItem("foo").addItem("bar").addItem("baz");
        attrBuilder = offerBuilder.addAttributesBuilder().setName("4").setType(Protos.Value.Type.RANGES);
        attrBuilder.getRangesBuilder().addRangeBuilder().setBegin(7).setEnd(8);
        attrBuilder.getRangesBuilder().addRangeBuilder().setBegin(10).setEnd(12);

        Assert.assertTrue(TaskUtils.getOfferAttributeStrings(getTestTaskInfo()).isEmpty());

        Protos.TaskInfo.Builder tb = TaskUtils.setOfferAttributes(
                getTestTaskInfo().toBuilder(), offerBuilder.build());
        List<String> expectedStrings = new ArrayList<>();
        expectedStrings.add("1:[5-6,10-12]");
        expectedStrings.add("2:123.457");
        expectedStrings.add("3:{foo,bar,baz}");
        expectedStrings.add("4:[7-8,10-12]");
        Assert.assertEquals(expectedStrings, TaskUtils.getOfferAttributeStrings(tb.build()));

        tb = TaskUtils.setOfferAttributes(
                getTestTaskInfo().toBuilder(), offerBuilder.clearAttributes().build());
        Assert.assertTrue(TaskUtils.getOfferAttributeStrings(tb.build()).isEmpty());
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
