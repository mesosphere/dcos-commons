package org.apache.mesos.offer.constrain;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;

import org.apache.mesos.Protos.Offer;
import org.apache.mesos.Protos.SlaveID;
import org.apache.mesos.Protos.TaskInfo;
import org.apache.mesos.config.SerializationUtils;
import org.apache.mesos.offer.OfferRequirement;
import org.apache.mesos.offer.TaskUtils;
import org.apache.mesos.offer.constrain.TaskTypeGenerator.TaskTypeLabelConverter;
import org.apache.mesos.testutils.OfferRequirementTestUtils;
import org.apache.mesos.testutils.OfferTestUtils;
import org.apache.mesos.testutils.TaskTestUtils;

/**
 * Tests for {@link TaskTypeGenerator}.
 */
public class TaskTypeGeneratorTest {

    private static final OfferRequirement REQ = OfferRequirementTestUtils.getOfferRequirement();

    private static final Offer OFFER_1 = getOffer("agent1");
    private static final Offer OFFER_2 = getOffer("agent2");
    private static final Offer OFFER_3 = getOffer("agent3");

    private static final TaskInfo TASK_MATCH_1 = getTask("match", "matchtask-0__uuid", "agent1");
    private static final TaskInfo TASK_MATCH_3 = getTask("match", "matchtask-2__uuid", "agent3");
    private static final TaskInfo TASK_MISMATCH_1 = getTask("mismatch", "othertask-0__uuid", "agent1");
    private static final TaskInfo TASK_MISMATCH_2 = getTask("mismatch", "othertask-1__uuid", "agent2");
    private static final TaskInfo TASK_MISMATCH_3 = getTask("mismatch", "othertask-2__uuid", "agent3");

    @Test
    public void testLabelConverter() {
        TaskInfo task = getTask("hey", "hello-1234__uuid", "agent");
        assertEquals("hey", new TaskTypeLabelConverter().getTaskType(task));
    }

    @Test(expected=IllegalArgumentException.class)
    public void testLabelConverterNoLabels() {
        TaskInfo task = getTask("DELETE ME", "hello-1234__uuid", "agent").toBuilder()
                .clearLabels()
                .build();
        new TaskTypeLabelConverter().getTaskType(task);
    }

    @Test
    public void testColocate() {
        PlacementRule rule = TaskTypeGenerator.createColocate("match")
                .generate(Arrays.asList(
                        TASK_MISMATCH_1, TASK_MATCH_1,
                        TASK_MISMATCH_2,
                        TASK_MISMATCH_3, TASK_MATCH_3));
        assertEquals(rule.toString(), 2, rule.filter(OFFER_1, REQ).getResourcesCount());
        assertEquals(rule.toString(), 0, rule.filter(OFFER_2, REQ).getResourcesCount());
        assertEquals(rule.toString(), 2, rule.filter(OFFER_3, REQ).getResourcesCount());
    }

    @Test
    public void testAvoid() {
        PlacementRule rule = TaskTypeGenerator.createAvoid("match")
                .generate(Arrays.asList(
                        TASK_MISMATCH_1, TASK_MATCH_1,
                        TASK_MISMATCH_2,
                        TASK_MISMATCH_3, TASK_MATCH_3));
        assertEquals(rule.toString(), 0, rule.filter(OFFER_1, REQ).getResourcesCount());
        assertEquals(rule.toString(), 2, rule.filter(OFFER_2, REQ).getResourcesCount());
        assertEquals(rule.toString(), 0, rule.filter(OFFER_3, REQ).getResourcesCount());
    }

    @Test
    public void testColocateNotFound() {
        PlacementRule rule = TaskTypeGenerator.createColocate("match")
                .generate(Arrays.asList(TASK_MISMATCH_1, TASK_MISMATCH_2, TASK_MISMATCH_3));
        assertEquals(rule.toString(), 2, rule.filter(OFFER_1, REQ).getResourcesCount());
        assertEquals(rule.toString(), 2, rule.filter(OFFER_2, REQ).getResourcesCount());
        assertEquals(rule.toString(), 2, rule.filter(OFFER_3, REQ).getResourcesCount());
    }

    @Test
    public void testAvoidNotFound() {
        PlacementRule rule = TaskTypeGenerator.createAvoid("match")
                .generate(Arrays.asList(TASK_MISMATCH_1, TASK_MISMATCH_2, TASK_MISMATCH_3));
        assertEquals(rule.toString(), 2, rule.filter(OFFER_1, REQ).getResourcesCount());
        assertEquals(rule.toString(), 2, rule.filter(OFFER_2, REQ).getResourcesCount());
        assertEquals(rule.toString(), 2, rule.filter(OFFER_3, REQ).getResourcesCount());
    }

    @Test
    public void testSerializeDeserialize() throws IOException {
        PlacementRuleGenerator generator = TaskTypeGenerator.createAvoid("match");
        assertEquals(generator, SerializationUtils.fromJsonString(SerializationUtils.toJsonString(generator), PlacementRuleGenerator.class));

        generator = TaskTypeGenerator.createColocate("match");
        assertEquals(generator, SerializationUtils.fromJsonString(SerializationUtils.toJsonString(generator), PlacementRuleGenerator.class));
    }

    private static TaskInfo getTask(String type, String id, String agent) {
        TaskInfo.Builder taskBuilder = TaskTestUtils.getTaskInfo(Collections.emptyList()).toBuilder();
        taskBuilder.getTaskIdBuilder().setValue(id);
        taskBuilder.getSlaveIdBuilder().setValue(agent);
        taskBuilder = TaskUtils.setTaskType(taskBuilder, type);
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
