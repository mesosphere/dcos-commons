package com.mesosphere.sdk.offer;

import com.mesosphere.sdk.testutils.TestConstants;
import org.apache.commons.io.FileUtils;
import org.apache.mesos.Protos;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.contrib.java.lang.system.EnvironmentVariables;

import java.io.File;
import java.io.IOException;
import java.util.*;

/**
 * This class tests the CommonTaskUtils class.
 */
public class CommonTaskUtilsTest {
    @Rule
    public final EnvironmentVariables environmentVariables = new EnvironmentVariables();

    private static final String testTaskName = "test-task-name";
    private static final String testTaskId = "test-task-id";
    private static final String testAgentId = "test-agent-id";
    private static final UUID testTargetConfigurationId = UUID.randomUUID();

    @Test
    public void testValidToTaskName() throws Exception {
        Protos.TaskID validTaskId = getTaskId(testTaskName + "__id");
        Assert.assertEquals(testTaskName, CommonTaskUtils.toTaskName(validTaskId));
    }

    @Test(expected = TaskException.class)
    public void testInvalidToTaskName() throws Exception {
        CommonTaskUtils.toTaskName(getTaskId(testTaskName + "_id"));
    }

    @Test(expected = TaskException.class)
    public void testGetTargetConfigurationFailure() throws Exception {
        CommonTaskUtils.getTargetConfiguration(getTestTaskInfo());
    }

    @Test
    public void testSetTargetConfiguration() throws Exception {
        Protos.TaskInfo taskInfo = CommonTaskUtils.setTargetConfiguration(
                getTestTaskInfo().toBuilder(), testTargetConfigurationId).build();
        Assert.assertEquals(testTargetConfigurationId, CommonTaskUtils.getTargetConfiguration(taskInfo));
    }

    @Test
    public void testSetGetOfferAttributes() {
        Protos.Offer.Builder offerBuilder = Protos.Offer.newBuilder()
                .setId(TestConstants.OFFER_ID)
                .setFrameworkId(TestConstants.FRAMEWORK_ID)
                .setSlaveId(TestConstants.AGENT_ID)
                .setHostname(TestConstants.HOSTNAME);
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

        Assert.assertTrue(CommonTaskUtils.getOfferAttributeStrings(getTestTaskInfo()).isEmpty());

        Protos.TaskInfo.Builder tb = CommonTaskUtils.setOfferAttributes(
                getTestTaskInfo().toBuilder(), offerBuilder.build());
        List<String> expectedStrings = new ArrayList<>();
        expectedStrings.add("1:[5-6,10-12]");
        expectedStrings.add("2:123.457");
        expectedStrings.add("3:{foo,bar,baz}");
        expectedStrings.add("4:[7-8,10-12]");
        Assert.assertEquals(expectedStrings, CommonTaskUtils.getOfferAttributeStrings(tb.build()));

        tb = CommonTaskUtils.setOfferAttributes(
                getTestTaskInfo().toBuilder(), offerBuilder.clearAttributes().build());
        Assert.assertTrue(CommonTaskUtils.getOfferAttributeStrings(tb.build()).isEmpty());
    }

    @Test(expected = TaskException.class)
    public void testGetMissingTaskTypeFails() throws TaskException {
        CommonTaskUtils.getType(getTestTaskInfo());
    }

    @Test
    public void testTaskLostNeedsRecovery() {
        Protos.TaskStatus taskStatus = Protos.TaskStatus.newBuilder()
                .setTaskId(Protos.TaskID.newBuilder().setValue(UUID.randomUUID().toString()))
                .setState(Protos.TaskState.TASK_LOST)
                .build();
        Assert.assertTrue(CommonTaskUtils.isRecoveryNeeded(taskStatus));
    }

    @Test
    public void testSetGetTaskType() throws TaskException {
        Assert.assertEquals("foo", CommonTaskUtils.getType(CommonTaskUtils.setType(
                getTestTaskInfo().toBuilder(), "foo").build()));
        Assert.assertEquals("", CommonTaskUtils.getType(CommonTaskUtils.setType(
                getTestTaskInfo().toBuilder(), "").build()));
    }

    @Test
    public void testApplyEnvToMustache() throws IOException {
        environmentVariables.set("PORT_API", String.valueOf(TestConstants.PORT_API_VALUE));
        ClassLoader classLoader = getClass().getClassLoader();
        File file = new File(classLoader.getResource("valid-exhaustive.yml").getFile());
        String yaml = FileUtils.readFileToString(file);
        Assert.assertTrue(yaml.contains("api-port: {{PORT_API}}"));
        String renderedYaml = CommonTaskUtils.applyEnvToMustache(yaml, System.getenv());
        Assert.assertTrue(renderedYaml.contains(String.format("api-port: %d", TestConstants.PORT_API_VALUE)));
    }

    @Test
    public void testReadinessCheckTagging() throws TaskException {
        Protos.TaskInfo.Builder builder = Protos.TaskInfo.newBuilder()
                .setName(TestConstants.TASK_NAME)
                .setTaskId(TestConstants.TASK_ID)
                .setSlaveId(TestConstants.AGENT_ID);
        Protos.HealthCheck inReadinessCheck = Protos.HealthCheck.newBuilder()
                .setDelaySeconds(1.0)
                .build();
        CommonTaskUtils.setReadinessCheck(builder, inReadinessCheck);
        Protos.HealthCheck outReadinessCheck = CommonTaskUtils.getReadinessCheck(builder.build()).get();

        Assert.assertEquals(inReadinessCheck.getDelaySeconds(), outReadinessCheck.getDelaySeconds(), 0.0);
    }

    private static Protos.TaskID getTaskId(String value) {
        return Protos.TaskID.newBuilder().setValue(value).build();
    }

    private static Protos.TaskInfo getTestTaskInfo() {
        return Protos.TaskInfo.newBuilder()
                .setName(testTaskName)
                .setTaskId(Protos.TaskID.newBuilder().setValue(testTaskId))
                .setSlaveId(Protos.SlaveID.newBuilder().setValue(testAgentId))
                .build();
    }
}
