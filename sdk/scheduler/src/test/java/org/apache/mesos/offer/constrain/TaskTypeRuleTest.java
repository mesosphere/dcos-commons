package org.apache.mesos.offer.constrain;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;

import org.apache.mesos.Protos.Offer;
import org.apache.mesos.Protos.SlaveID;
import org.apache.mesos.Protos.TaskInfo;
import org.apache.mesos.config.SerializationUtils;
import org.apache.mesos.offer.InvalidRequirementException;
import org.apache.mesos.offer.OfferRequirement;
import org.apache.mesos.offer.TaskUtils;
import org.apache.mesos.offer.constrain.TaskTypeRule.TaskTypeLabelConverter;
import org.apache.mesos.testutils.OfferRequirementTestUtils;
import org.apache.mesos.testutils.OfferTestUtils;
import org.apache.mesos.testutils.TaskTestUtils;

/**
 * Tests for {@link TaskTypeRule}.
 */
public class TaskTypeRuleTest {

    private static final String MATCH_TYPE = "match";
    private static final String MISMATCH_TYPE = "mismatch";
    private static final String AGENT1 = "agent1";
    private static final String AGENT2 = "agent2";
    private static final String AGENT3 = "agent3";

    private static final Offer OFFER_1 = getOffer(AGENT1);
    private static final Offer OFFER_2 = getOffer(AGENT2);
    private static final Offer OFFER_3 = getOffer(AGENT3);

    private static final TaskInfo TASK_MATCH_1 = getTask(MATCH_TYPE, "matchtask-1__uuid", AGENT1);
    private static final TaskInfo TASK_MATCH_3 = getTask(MATCH_TYPE, "matchtask-3__uuid", AGENT3);
    private static final TaskInfo TASK_MISMATCH_1 = getTask(MISMATCH_TYPE, "othertask-1__uuid", AGENT1);
    private static final TaskInfo TASK_MISMATCH_2 = getTask(MISMATCH_TYPE, "othertask-2__uuid", AGENT2);
    private static final TaskInfo TASK_MISMATCH_3 = getTask(MISMATCH_TYPE, "othertask-3__uuid", AGENT3);
    private static final Collection<TaskInfo> TASKS = Arrays.asList(
            TASK_MISMATCH_1, TASK_MATCH_1,
            TASK_MISMATCH_2,
            TASK_MISMATCH_3, TASK_MATCH_3);
    private static final Collection<TaskInfo> MISMATCH_TASKS = Arrays.asList(
            TASK_MISMATCH_1, TASK_MISMATCH_2, TASK_MISMATCH_3);

    private static final OfferRequirement REQ = OfferRequirementTestUtils.getOfferRequirement();
    private static final OfferRequirement REQ_WITH_TASK_MATCH_1 = getOfferReq(TASK_MATCH_1);
    private static final OfferRequirement REQ_WITH_TASK_MATCH_3 = getOfferReq(TASK_MATCH_3);
    private static final OfferRequirement REQ_WITH_TASK_MISMATCH_1 = getOfferReq(TASK_MISMATCH_1);
    private static final OfferRequirement REQ_WITH_TASK_MISMATCH_2 = getOfferReq(TASK_MISMATCH_2);
    private static final OfferRequirement REQ_WITH_TASK_MISMATCH_3 = getOfferReq(TASK_MISMATCH_3);

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
        PlacementRule rule = TaskTypeRule.colocateWith(MATCH_TYPE);

        assertEquals(rule.toString(), 2, rule.filter(OFFER_1, REQ, TASKS).getResourcesCount());
        assertEquals(rule.toString(), 0, rule.filter(OFFER_2, REQ, TASKS).getResourcesCount());
        assertEquals(rule.toString(), 2, rule.filter(OFFER_3, REQ, TASKS).getResourcesCount());
    }

    @Test
    public void testColocateWithSamePresent() throws InvalidRequirementException {
        PlacementRule rule = TaskTypeRule.colocateWith(MATCH_TYPE);

        assertEquals(rule.toString(), 0, rule.filter(OFFER_1, REQ_WITH_TASK_MATCH_1, TASKS).getResourcesCount());
        assertEquals(rule.toString(), 0, rule.filter(OFFER_2, REQ_WITH_TASK_MATCH_1, TASKS).getResourcesCount());
        assertEquals(rule.toString(), 2, rule.filter(OFFER_3, REQ_WITH_TASK_MATCH_1, TASKS).getResourcesCount());

        assertEquals(rule.toString(), 2, rule.filter(OFFER_1, REQ_WITH_TASK_MATCH_3, TASKS).getResourcesCount());
        assertEquals(rule.toString(), 0, rule.filter(OFFER_2, REQ_WITH_TASK_MATCH_3, TASKS).getResourcesCount());
        assertEquals(rule.toString(), 0, rule.filter(OFFER_3, REQ_WITH_TASK_MATCH_3, TASKS).getResourcesCount());

        assertEquals(rule.toString(), 2, rule.filter(OFFER_1, REQ_WITH_TASK_MISMATCH_1, TASKS).getResourcesCount());
        assertEquals(rule.toString(), 0, rule.filter(OFFER_2, REQ_WITH_TASK_MISMATCH_1, TASKS).getResourcesCount());
        assertEquals(rule.toString(), 2, rule.filter(OFFER_3, REQ_WITH_TASK_MISMATCH_1, TASKS).getResourcesCount());

        assertEquals(rule.toString(), 2, rule.filter(OFFER_1, REQ_WITH_TASK_MISMATCH_2, TASKS).getResourcesCount());
        assertEquals(rule.toString(), 0, rule.filter(OFFER_2, REQ_WITH_TASK_MISMATCH_2, TASKS).getResourcesCount());
        assertEquals(rule.toString(), 2, rule.filter(OFFER_3, REQ_WITH_TASK_MISMATCH_2, TASKS).getResourcesCount());

        assertEquals(rule.toString(), 2, rule.filter(OFFER_1, REQ_WITH_TASK_MISMATCH_3, TASKS).getResourcesCount());
        assertEquals(rule.toString(), 0, rule.filter(OFFER_2, REQ_WITH_TASK_MISMATCH_3, TASKS).getResourcesCount());
        assertEquals(rule.toString(), 2, rule.filter(OFFER_3, REQ_WITH_TASK_MISMATCH_3, TASKS).getResourcesCount());
    }

    @Test
    public void testAvoid() {
        PlacementRule rule = TaskTypeRule.avoid(MATCH_TYPE);

        assertEquals(rule.toString(), 0, rule.filter(OFFER_1, REQ, TASKS).getResourcesCount());
        assertEquals(rule.toString(), 2, rule.filter(OFFER_2, REQ, TASKS).getResourcesCount());
        assertEquals(rule.toString(), 0, rule.filter(OFFER_3, REQ, TASKS).getResourcesCount());
    }

    @Test
    public void testAvoidWithSamePresent() throws InvalidRequirementException {
        PlacementRule rule = TaskTypeRule.avoid(MATCH_TYPE);

        assertEquals(rule.toString(), 2, rule.filter(OFFER_1, REQ_WITH_TASK_MATCH_1, TASKS).getResourcesCount());
        assertEquals(rule.toString(), 2, rule.filter(OFFER_2, REQ_WITH_TASK_MATCH_1, TASKS).getResourcesCount());
        assertEquals(rule.toString(), 0, rule.filter(OFFER_3, REQ_WITH_TASK_MATCH_1, TASKS).getResourcesCount());

        assertEquals(rule.toString(), 0, rule.filter(OFFER_1, REQ_WITH_TASK_MATCH_3, TASKS).getResourcesCount());
        assertEquals(rule.toString(), 2, rule.filter(OFFER_2, REQ_WITH_TASK_MATCH_3, TASKS).getResourcesCount());
        assertEquals(rule.toString(), 2, rule.filter(OFFER_3, REQ_WITH_TASK_MATCH_3, TASKS).getResourcesCount());

        assertEquals(rule.toString(), 0, rule.filter(OFFER_1, REQ_WITH_TASK_MISMATCH_1, TASKS).getResourcesCount());
        assertEquals(rule.toString(), 2, rule.filter(OFFER_2, REQ_WITH_TASK_MISMATCH_1, TASKS).getResourcesCount());
        assertEquals(rule.toString(), 0, rule.filter(OFFER_3, REQ_WITH_TASK_MISMATCH_1, TASKS).getResourcesCount());

        assertEquals(rule.toString(), 0, rule.filter(OFFER_1, REQ_WITH_TASK_MISMATCH_2, TASKS).getResourcesCount());
        assertEquals(rule.toString(), 2, rule.filter(OFFER_2, REQ_WITH_TASK_MISMATCH_2, TASKS).getResourcesCount());
        assertEquals(rule.toString(), 0, rule.filter(OFFER_3, REQ_WITH_TASK_MISMATCH_2, TASKS).getResourcesCount());

        assertEquals(rule.toString(), 0, rule.filter(OFFER_1, REQ_WITH_TASK_MISMATCH_3, TASKS).getResourcesCount());
        assertEquals(rule.toString(), 2, rule.filter(OFFER_2, REQ_WITH_TASK_MISMATCH_3, TASKS).getResourcesCount());
        assertEquals(rule.toString(), 0, rule.filter(OFFER_3, REQ_WITH_TASK_MISMATCH_3, TASKS).getResourcesCount());
    }

    @Test
    public void testColocateNotFound() {
        PlacementRule rule = TaskTypeRule.colocateWith(MATCH_TYPE);

        assertEquals(rule.toString(), 2, rule.filter(OFFER_1, REQ, MISMATCH_TASKS).getResourcesCount());
        assertEquals(rule.toString(), 2, rule.filter(OFFER_2, REQ, MISMATCH_TASKS).getResourcesCount());
        assertEquals(rule.toString(), 2, rule.filter(OFFER_3, REQ, MISMATCH_TASKS).getResourcesCount());
    }

    @Test
    public void testAvoidNotFound() {
        PlacementRule rule = TaskTypeRule.avoid(MATCH_TYPE);

        assertEquals(rule.toString(), 2, rule.filter(OFFER_1, REQ, MISMATCH_TASKS).getResourcesCount());
        assertEquals(rule.toString(), 2, rule.filter(OFFER_2, REQ, MISMATCH_TASKS).getResourcesCount());
        assertEquals(rule.toString(), 2, rule.filter(OFFER_3, REQ, MISMATCH_TASKS).getResourcesCount());
    }

    @Test
    public void testSerializeDeserialize() throws IOException {
        PlacementRule rule = TaskTypeRule.avoid(MATCH_TYPE);
        assertEquals(rule, SerializationUtils.fromString(
                SerializationUtils.toJsonString(rule), PlacementRule.class, TestPlacementUtils.OBJECT_MAPPER));

        rule = TaskTypeRule.colocateWith(MATCH_TYPE);
        assertEquals(rule, SerializationUtils.fromString(
                SerializationUtils.toJsonString(rule), PlacementRule.class, TestPlacementUtils.OBJECT_MAPPER));
    }

    private static TaskInfo getTask(String type, String id, String agent) {
        TaskInfo.Builder taskBuilder = TaskTestUtils.getTaskInfo(Collections.emptyList()).toBuilder();
        taskBuilder.getTaskIdBuilder().setValue(id);
        try {
            taskBuilder.setName(TaskUtils.toTaskName(taskBuilder.getTaskId()));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
        taskBuilder.getSlaveIdBuilder().setValue(agent);
        taskBuilder = TaskUtils.setType(taskBuilder, type);
        return taskBuilder.build();
    }

    private static OfferRequirement getOfferReq(TaskInfo taskInfo) {
        try {
            return OfferRequirement.create(TaskUtils.getType(taskInfo), Arrays.asList(taskInfo));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static Offer getOffer(String agent) {
        Offer.Builder o = OfferTestUtils.getEmptyOfferBuilder()
                .setSlaveId(SlaveID.newBuilder().setValue(agent));
        OfferTestUtils.addResource(o, "a");
        OfferTestUtils.addResource(o, "b");
        return o.build();
    }
}
