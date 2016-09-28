package org.apache.mesos.offer.constrain;

import org.junit.Test;

import com.google.protobuf.ByteString;

import static org.junit.Assert.assertEquals;

import java.util.Arrays;
import java.util.Collections;

import org.apache.mesos.Protos;
import org.apache.mesos.Protos.CommandInfo;
import org.apache.mesos.Protos.Offer;
import org.apache.mesos.Protos.SlaveID;
import org.apache.mesos.Protos.TaskInfo;
import org.apache.mesos.executor.DcosTaskConstants;
import org.apache.mesos.offer.constrain.TaskTypeGenerator.TaskEnvMapConverter;
import org.apache.mesos.testutils.OfferTestUtils;
import org.apache.mesos.testutils.TaskTestUtils;

/**
 * Tests for {@link TaskTypeGenerator}.
 */
public class TaskTypeRuleGeneratorTest {

    private static final Offer OFFER_1 = getOffer("agent1");
    private static final Offer OFFER_2 = getOffer("agent2");
    private static final Offer OFFER_3 = getOffer("agent3");

    private static final TaskInfo TASK_MATCH_1 = getTask("match", "matchtask-0__uuid", "agent1");
    private static final TaskInfo TASK_MATCH_3 = getTask("match", "matchtask-2__uuid", "agent3");
    private static final TaskInfo TASK_MISMATCH_1 = getTask("mismatch", "othertask-0__uuid", "agent1");
    private static final TaskInfo TASK_MISMATCH_2 = getTask("mismatch", "othertask-1__uuid", "agent2");
    private static final TaskInfo TASK_MISMATCH_3 = getTask("mismatch", "othertask-2__uuid", "agent3");

    @Test
    public void testEnvConverter() {
        TaskInfo task = getTask("hey", "hello-1234__uuid", "agent");
        assertEquals("hey", new TaskEnvMapConverter().getTaskType(task));
    }

    @Test(expected=IllegalArgumentException.class)
    public void testEnvConverterNoData() {
        TaskInfo task = getTask("hey", "hello-1234__uuid", "agent").toBuilder()
                .clearData()
                .build();
        new TaskEnvMapConverter().getTaskType(task);
    }

    @Test(expected=IllegalArgumentException.class)
    public void testEnvConverterBadData() {
        ByteString corrupt = ByteString.copyFromUtf8("Hello");
        TaskInfo task = getTask("hey", "hello-1234__uuid", "agent").toBuilder()
                .setData(corrupt)
                .build();
        new TaskEnvMapConverter().getTaskType(task);
    }

    @Test(expected=IllegalArgumentException.class)
    public void testEnvConverterMissingEnvvar() {
        CommandInfo.Builder cb = CommandInfo.newBuilder();
        cb.getEnvironmentBuilder().addVariablesBuilder()
                .setName("hi")
                .setValue("hey");
        TaskInfo task = getTask("DELETEME", "hello-1234__uuid", "agent").toBuilder()
                .setData(cb.build().toByteString())
                .build();
        new TaskEnvMapConverter().getTaskType(task);
    }

    @Test
    public void testColocate() {
        PlacementRule rule = TaskTypeGenerator.createColocate("match", new TaskEnvMapConverter())
                .generate(Arrays.asList(
                        TASK_MISMATCH_1, TASK_MATCH_1,
                        TASK_MISMATCH_2,
                        TASK_MISMATCH_3, TASK_MATCH_3));
        assertEquals(rule.toString(), 2, rule.filter(OFFER_1).getResourcesCount());
        assertEquals(rule.toString(), 0, rule.filter(OFFER_2).getResourcesCount());
        assertEquals(rule.toString(), 2, rule.filter(OFFER_3).getResourcesCount());
    }

    @Test
    public void testAvoid() {
        PlacementRule rule = TaskTypeGenerator.createAvoid("match", new TaskEnvMapConverter())
                .generate(Arrays.asList(
                        TASK_MISMATCH_1, TASK_MATCH_1,
                        TASK_MISMATCH_2,
                        TASK_MISMATCH_3, TASK_MATCH_3));
        assertEquals(rule.toString(), 0, rule.filter(OFFER_1).getResourcesCount());
        assertEquals(rule.toString(), 2, rule.filter(OFFER_2).getResourcesCount());
        assertEquals(rule.toString(), 0, rule.filter(OFFER_3).getResourcesCount());
    }

    @Test
    public void testColocateNotFound() {
        PlacementRule rule = TaskTypeGenerator.createColocate("match", new TaskEnvMapConverter())
                .generate(Arrays.asList(TASK_MISMATCH_1, TASK_MISMATCH_2, TASK_MISMATCH_3));
        assertEquals(rule.toString(), 2, rule.filter(OFFER_1).getResourcesCount());
        assertEquals(rule.toString(), 2, rule.filter(OFFER_2).getResourcesCount());
        assertEquals(rule.toString(), 2, rule.filter(OFFER_3).getResourcesCount());
    }

    @Test
    public void testAvoidNotFound() {
        PlacementRule rule = TaskTypeGenerator.createAvoid("match", new TaskEnvMapConverter())
                .generate(Arrays.asList(TASK_MISMATCH_1, TASK_MISMATCH_2, TASK_MISMATCH_3));
        assertEquals(rule.toString(), 2, rule.filter(OFFER_1).getResourcesCount());
        assertEquals(rule.toString(), 2, rule.filter(OFFER_2).getResourcesCount());
        assertEquals(rule.toString(), 2, rule.filter(OFFER_3).getResourcesCount());
    }

    private static TaskInfo getTask(String type, String id, String agent) {
        Protos.CommandInfo.Builder cmdInfoBuilder = Protos.CommandInfo.newBuilder();
        cmdInfoBuilder.getEnvironmentBuilder().addVariablesBuilder()
                .setName(DcosTaskConstants.TASK_TYPE)
                .setValue(type);
        TaskInfo.Builder taskBuilder = TaskTestUtils.getTaskInfo(Collections.emptyList()).toBuilder()
                .setData(cmdInfoBuilder.build().toByteString());
        taskBuilder.getTaskIdBuilder().setValue(id);
        taskBuilder.getSlaveIdBuilder().setValue(agent);
        return taskBuilder.build();
    }

    private static Offer getOffer(String agent) {
        Offer.Builder o = OfferTestUtils.getEmptyOfferBuilder()
                .setSlaveId(SlaveID.newBuilder().setValue(agent));
        OfferTestUtils.addResource(o, "a");
        OfferTestUtils.addResource(o, "b");
        return o.build();
    }
}
