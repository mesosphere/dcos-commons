package org.apache.mesos.offer;

import org.apache.mesos.Protos;
import org.apache.mesos.specification.*;
import org.apache.mesos.testutils.OfferTestUtils;
import org.apache.mesos.testutils.TestConstants;
import org.junit.Assert;
import org.junit.Test;

import com.google.protobuf.InvalidProtocolBufferException;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
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
        TestTaskSpecification newTaskSpecification = new TestTaskSpecification(TestTaskSetFactory.getTaskSpecification())
                .addResource(new DefaultResourceSpecification(
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
        TestTaskSpecification oldTaskSpecification = new TestTaskSpecification(TestTaskSetFactory.getTaskSpecification())
                .addResource(new DefaultResourceSpecification(
                        "bar",
                        Protos.Value.newBuilder()
                                .setType(Protos.Value.Type.SCALAR)
                                .setScalar(Protos.Value.Scalar.newBuilder().setValue(1.0).build())
                                .build(),
                        TestConstants.ROLE,
                        TestConstants.PRINCIPAL));

        TestTaskSpecification newTaskSpecification = new TestTaskSpecification(TestTaskSetFactory.getTaskSpecification())
                .addResource(new DefaultResourceSpecification(
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
    public void testAreNotDifferentTaskSpecificationsResourcesMatch() {
        TestTaskSpecification oldTaskSpecification = new TestTaskSpecification(TestTaskSetFactory.getTaskSpecification())
                .addResource(new DefaultResourceSpecification(
                        "bar",
                        Protos.Value.newBuilder()
                                .setType(Protos.Value.Type.SCALAR)
                                .setScalar(Protos.Value.Scalar.newBuilder().setValue(1.0).build())
                                .build(),
                        TestConstants.ROLE,
                        TestConstants.PRINCIPAL));

        TestTaskSpecification newTaskSpecification = new TestTaskSpecification(TestTaskSetFactory.getTaskSpecification())
                .addResource(new DefaultResourceSpecification(
                        "bar",
                        Protos.Value.newBuilder()
                                .setType(Protos.Value.Type.SCALAR)
                                .setScalar(Protos.Value.Scalar.newBuilder().setValue(1.0).build())
                                .build(),
                        TestConstants.ROLE,
                        TestConstants.PRINCIPAL));

        Assert.assertFalse(TaskUtils.areDifferent(oldTaskSpecification, newTaskSpecification));
    }

    @Test(expected=IllegalArgumentException.class)
    public void testAreDifferentTaskSpecificationsConfigsSamePathFailsValidation() {
        TaskSpecification oldTaskSpecification = TestTaskSetFactory.getTaskSpecification();
        TestTaskSpecification newTaskSpecification = new TestTaskSpecification(TestTaskSetFactory.getTaskSpecification())
                .addConfigFile(new DefaultConfigFileSpecification(
                        "../relative/path/to/config",
                        "this is a config template"))
                .addConfigFile(new DefaultConfigFileSpecification(
                        "../relative/path/to/config",
                        "two configs with same path should fail validation"));
        TaskUtils.areDifferent(oldTaskSpecification, newTaskSpecification);
    }

    @Test
    public void testAreDifferentTaskSpecificationsConfigsLength() {
        TaskSpecification oldTaskSpecification = TestTaskSetFactory.getTaskSpecification();
        TestTaskSpecification newTaskSpecification = new TestTaskSpecification(TestTaskSetFactory.getTaskSpecification())
                .addConfigFile(new DefaultConfigFileSpecification(
                        "../relative/path/to/config",
                        "this is a config template"));

        Assert.assertTrue(TaskUtils.areDifferent(oldTaskSpecification, newTaskSpecification));
    }

    @Test
    public void testAreDifferentTaskSpecificationsNoConfigOverlap() {
        TestTaskSpecification oldTaskSpecification = new TestTaskSpecification(TestTaskSetFactory.getTaskSpecification())
                .addConfigFile(new DefaultConfigFileSpecification(
                        "../relative/path/to/config",
                        "this is a config template"))
                .addConfigFile(new DefaultConfigFileSpecification(
                        "../relative/path/to/config2",
                        "this is a second config template"));

        TestTaskSpecification newTaskSpecification = new TestTaskSpecification(TestTaskSetFactory.getTaskSpecification())
                .addConfigFile(new DefaultConfigFileSpecification(
                        "../different/path/to/config",
                        "different path to a different template"))
                .addConfigFile(new DefaultConfigFileSpecification(
                        "../relative/path/to/config2",
                        "this is a second config template"));

        Assert.assertTrue(TaskUtils.areDifferent(oldTaskSpecification, newTaskSpecification));
    }

    @Test
    public void testAreNotDifferentTaskSpecificationsConfigMatch() {
        TestTaskSpecification oldTaskSpecification = new TestTaskSpecification(TestTaskSetFactory.getTaskSpecification())
                .addConfigFile(new DefaultConfigFileSpecification(
                        "../relative/path/to/config",
                        "this is a config template"))
                .addConfigFile(new DefaultConfigFileSpecification(
                        "../relative/path/to/config2",
                        "this is a second config template"));

        TestTaskSpecification newTaskSpecification = new TestTaskSpecification(TestTaskSetFactory.getTaskSpecification())
                .addConfigFile(new DefaultConfigFileSpecification(
                        "../relative/path/to/config",
                        "this is a config template"))
                .addConfigFile(new DefaultConfigFileSpecification(
                        "../relative/path/to/config2",
                        "this is a second config template"));

        Assert.assertFalse(TaskUtils.areDifferent(oldTaskSpecification, newTaskSpecification));
    }

    @Test
    public void testSetGetConfigTemplates() throws InvalidProtocolBufferException {
        Protos.TaskInfo.Builder taskBuilder = getTestTaskInfo().toBuilder();
        Collection<ConfigFileSpecification> configs = Arrays.asList(
                new DefaultConfigFileSpecification("../relative/path/to/config", "this is a config template"),
                new DefaultConfigFileSpecification("../relative/path/to/config2", "this is a second config template"));
        TaskUtils.setConfigFiles(taskBuilder, configs);
        Assert.assertEquals(configs, TaskUtils.getConfigFiles(taskBuilder.build()));
    }

    @Test(expected=IllegalStateException.class)
    public void testSetTemplatesTooBig() throws InvalidProtocolBufferException {
        Protos.TaskInfo.Builder taskBuilder = getTestTaskInfo().toBuilder();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 256 * 1024; ++i) {
            sb.append('a');
        }
        Collection<ConfigFileSpecification> configs = Arrays.asList(
                new DefaultConfigFileSpecification("../relative/path/to/config", sb.toString()),
                new DefaultConfigFileSpecification("../relative/path/to/config2", sb.toString()),
                new DefaultConfigFileSpecification("../relative/path/to/config3", "a"));
        TaskUtils.setConfigFiles(taskBuilder, configs);
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

    @Test(expected=TaskException.class)
    public void testGetMissingTaskTypeFails() throws TaskException {
        TaskUtils.getTaskType(getTestTaskInfo());
    }

    @Test
    public void testSetGetTaskType() throws TaskException {
        Assert.assertEquals("foo", TaskUtils.getTaskType(TaskUtils.setTaskType(
                getTestTaskInfo().toBuilder(), "foo").build()));
        Assert.assertEquals("", TaskUtils.getTaskType(TaskUtils.setTaskType(
                getTestTaskInfo().toBuilder(), "").build()));

    }

    private static Protos.TaskID getTaskId(String value) {
        return Protos.TaskID.newBuilder().setValue(value).build();
    }

    private static Protos.TaskInfo getTestTaskInfoWithTargetConfiguration() {
        return TaskUtils.setTargetConfiguration(getTestTaskInfo().toBuilder(), testTargetConfigurationId).build();
    }

    private static Protos.TaskInfo getTestTaskInfo() {
        return Protos.TaskInfo.newBuilder()
                .setName(testTaskName)
                .setTaskId(Protos.TaskID.newBuilder().setValue(testTaskId))
                .setSlaveId(Protos.SlaveID.newBuilder().setValue(testAgentId))
                .build();
    }
}
