package com.mesosphere.sdk.offer.taskdata;

import com.mesosphere.sdk.offer.TaskException;
import com.mesosphere.sdk.testutils.OfferRequirementTestUtils;
import com.mesosphere.sdk.testutils.TestConstants;
import org.apache.mesos.Protos;
import org.junit.Assert;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Tests for {@link SchedulerLabelReader} and {@link SchedulerLabelWriter}.
 */
public class SchedulerLabelReaderWriterTest {
    private static final String testTaskName = "test-task-name";
    private static final String testTaskId = "test-task-id";
    private static final String testAgentId = "test-agent-id";
    private static final UUID testTargetConfigurationId = UUID.randomUUID();

    @Test(expected = TaskException.class)
    public void testGetTargetConfigurationFailure() throws Exception {
        new SchedulerLabelReader(getTestTaskInfo()).getTargetConfiguration();
    }

    @Test
    public void testSetTargetConfiguration() throws Exception {
        Protos.TaskInfo.Builder taskBuilder = getTestTaskInfo().toBuilder();
        taskBuilder.setLabels(new SchedulerLabelWriter(taskBuilder)
                .setTargetConfiguration(testTargetConfigurationId)
                .toProto());
        Assert.assertEquals(testTargetConfigurationId, new SchedulerLabelReader(taskBuilder).getTargetConfiguration());
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

        Assert.assertTrue(new SchedulerLabelReader(getTestTaskInfo()).getOfferAttributeStrings().isEmpty());

        Protos.TaskInfo.Builder tb = getTestTaskInfo().toBuilder();
        tb.setLabels(new SchedulerLabelWriter(tb).setOfferAttributes(offerBuilder.build()).toProto());
        List<String> expectedStrings = new ArrayList<>();
        expectedStrings.add("1:[5-6,10-12]");
        expectedStrings.add("2:123.457");
        expectedStrings.add("3:{foo,bar,baz}");
        expectedStrings.add("4:[7-8,10-12]");
        Assert.assertEquals(expectedStrings, new SchedulerLabelReader(tb).getOfferAttributeStrings());

        tb.setLabels(new SchedulerLabelWriter(tb).setOfferAttributes(offerBuilder.clearAttributes().build()).toProto());
        Assert.assertTrue(new SchedulerLabelReader(tb.build()).getOfferAttributeStrings().isEmpty());
    }

    @Test(expected = TaskException.class)
    public void testGetMissingTaskTypeFails() throws TaskException {
        new SchedulerLabelReader(getTestTaskInfo()).getType();
    }

    @Test
    public void testSetGetTaskType() throws TaskException {
        Protos.TaskInfo.Builder builder = getTestTaskInfo().toBuilder();
        builder.setLabels(new SchedulerLabelWriter(builder).setType("foo").toProto());
        Assert.assertEquals("foo", new SchedulerLabelReader(builder).getType());

        builder = getTestTaskInfo().toBuilder();
        builder.setLabels(new SchedulerLabelWriter(builder).setType("").toProto());
        Assert.assertEquals("", new SchedulerLabelReader(builder).getType());
    }

    @Test
    public void testReadinessCheckTagging() throws TaskException {
        Protos.HealthCheck inReadinessCheck = Protos.HealthCheck.newBuilder()
                .setDelaySeconds(1.0)
                .build();
        Protos.TaskInfo.Builder builder = getTestTaskInfo().toBuilder();
        builder.setLabels(new SchedulerLabelWriter(builder)
                .setReadinessCheck(inReadinessCheck)
                .toProto());
        Protos.HealthCheck outReadinessCheck = OfferRequirementTestUtils.getReadinessCheck(builder.build()).get();

        Assert.assertEquals(inReadinessCheck.getDelaySeconds(), outReadinessCheck.getDelaySeconds(), 0.0);
    }

    private static Protos.TaskInfo getTestTaskInfo() {
        return Protos.TaskInfo.newBuilder()
                .setName(testTaskName)
                .setTaskId(Protos.TaskID.newBuilder().setValue(testTaskId))
                .setSlaveId(Protos.SlaveID.newBuilder().setValue(testAgentId))
                .build();
    }
}
