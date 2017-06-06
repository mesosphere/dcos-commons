package com.mesosphere.sdk.offer.evaluate.placement;

import com.mesosphere.sdk.config.SerializationUtils;
import com.mesosphere.sdk.offer.CommonIdUtils;
import com.mesosphere.sdk.offer.InvalidRequirementException;
import com.mesosphere.sdk.offer.taskdata.SchedulerLabelReader;
import com.mesosphere.sdk.offer.taskdata.SchedulerLabelWriter;
import com.mesosphere.sdk.scheduler.plan.DefaultPodInstance;
import com.mesosphere.sdk.scheduler.plan.PodInstanceRequirementTestUtils;
import com.mesosphere.sdk.specification.PodInstance;
import com.mesosphere.sdk.specification.PodSpec;
import com.mesosphere.sdk.specification.ResourceSet;
import com.mesosphere.sdk.testutils.OfferTestUtils;
import com.mesosphere.sdk.testutils.TaskTestUtils;
import org.apache.mesos.Protos.Offer;
import org.apache.mesos.Protos.SlaveID;
import org.apache.mesos.Protos.TaskInfo;
import org.junit.Test;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;

import static org.junit.Assert.*;

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

    private static final PodInstance POD = PodInstanceRequirementTestUtils.getCpuRequirement(1.0).getPodInstance();
    private static final PodInstance POD_WITH_TASK_MATCH_1 = getPodInstance(TASK_MATCH_1);
    private static final PodInstance POD_WITH_TASK_MATCH_3 = getPodInstance(TASK_MATCH_3);
    private static final PodInstance POD_WITH_TASK_MISMATCH_1 = getPodInstance(TASK_MISMATCH_1);
    private static final PodInstance POD_WITH_TASK_MISMATCH_2 = getPodInstance(TASK_MISMATCH_2);
    private static final PodInstance POD_WITH_TASK_MISMATCH_3 = getPodInstance(TASK_MISMATCH_3);

    private static PodInstance getPodInstance(TaskInfo taskInfo) {
        try {
            SchedulerLabelReader labels = new SchedulerLabelReader(taskInfo);
            ResourceSet resourceSet = PodInstanceRequirementTestUtils.getCpuResourceSet(1.0);
            PodSpec podSpec = PodInstanceRequirementTestUtils.getRequirement(
                    resourceSet,
                    labels.getType(),
                    labels.getIndex())
                    .getPodInstance()
                    .getPod();
            return new DefaultPodInstance(podSpec, labels.getIndex());
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static TaskInfo getTask(String type, String id, String agent) {
        TaskInfo.Builder taskBuilder = TaskTestUtils.getTaskInfo(Collections.emptyList()).toBuilder();
        taskBuilder.getTaskIdBuilder().setValue(id);
        try {
            taskBuilder.setName(CommonIdUtils.toTaskName(taskBuilder.getTaskId()));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
        taskBuilder.getSlaveIdBuilder().setValue(agent);
        taskBuilder.setLabels(new SchedulerLabelWriter(taskBuilder).setType(type).toProto());
        return taskBuilder.build();
    }

    private static Offer getOffer(String agent) {
        Offer.Builder o = OfferTestUtils.getEmptyOfferBuilder()
                .setSlaveId(SlaveID.newBuilder().setValue(agent));
        OfferTestUtils.addResource(o, "a");
        OfferTestUtils.addResource(o, "b");
        return o.build();
    }

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

        assertTrue(rule.filter(OFFER_1, POD, TASKS).isPassing());
        assertFalse(rule.filter(OFFER_2, POD, TASKS).isPassing());
        assertTrue(rule.filter(OFFER_3, POD, TASKS).isPassing());
    }

    @Test
    public void testColocateWithSamePresent() throws InvalidRequirementException {
        PlacementRule rule = TaskTypeRule.colocateWith(MATCH_TYPE);

        assertFalse(rule.filter(OFFER_1, POD_WITH_TASK_MATCH_1, TASKS).isPassing());
        assertFalse(rule.filter(OFFER_2, POD_WITH_TASK_MATCH_1, TASKS).isPassing());
        assertTrue(rule.filter(OFFER_3, POD_WITH_TASK_MATCH_1, TASKS).isPassing());

        assertTrue(rule.filter(OFFER_1, POD_WITH_TASK_MATCH_3, TASKS).isPassing());
        assertFalse(rule.filter(OFFER_2, POD_WITH_TASK_MATCH_3, TASKS).isPassing());
        assertFalse(rule.filter(OFFER_3, POD_WITH_TASK_MATCH_3, TASKS).isPassing());

        assertTrue(rule.filter(OFFER_1, POD_WITH_TASK_MISMATCH_1, TASKS).isPassing());
        assertFalse(rule.filter(OFFER_2, POD_WITH_TASK_MISMATCH_1, TASKS).isPassing());
        assertTrue(rule.filter(OFFER_3, POD_WITH_TASK_MISMATCH_1, TASKS).isPassing());

        assertTrue(rule.filter(OFFER_1, POD_WITH_TASK_MISMATCH_2, TASKS).isPassing());
        assertFalse(rule.filter(OFFER_2, POD_WITH_TASK_MISMATCH_2, TASKS).isPassing());
        assertTrue(rule.filter(OFFER_3, POD_WITH_TASK_MISMATCH_2, TASKS).isPassing());

        assertTrue(rule.filter(OFFER_1, POD_WITH_TASK_MISMATCH_3, TASKS).isPassing());
        assertFalse(rule.filter(OFFER_2, POD_WITH_TASK_MISMATCH_3, TASKS).isPassing());
        assertTrue(rule.filter(OFFER_3, POD_WITH_TASK_MISMATCH_3, TASKS).isPassing());
    }

    @Test
    public void testAvoid() {
        PlacementRule rule = TaskTypeRule.avoid(MATCH_TYPE);

        assertFalse(rule.filter(OFFER_1, POD, TASKS).isPassing());
        assertTrue(rule.filter(OFFER_2, POD, TASKS).isPassing());
        assertFalse(rule.filter(OFFER_3, POD, TASKS).isPassing());
    }

    @Test
    public void testAvoidWithSamePresent() throws InvalidRequirementException {
        PlacementRule rule = TaskTypeRule.avoid(MATCH_TYPE);

        assertTrue(rule.filter(OFFER_1, POD_WITH_TASK_MATCH_1, TASKS).isPassing());
        assertTrue(rule.filter(OFFER_2, POD_WITH_TASK_MATCH_1, TASKS).isPassing());
        assertFalse(rule.filter(OFFER_3, POD_WITH_TASK_MATCH_1, TASKS).isPassing());

        assertFalse(rule.filter(OFFER_1, POD_WITH_TASK_MATCH_3, TASKS).isPassing());
        assertTrue(rule.filter(OFFER_2, POD_WITH_TASK_MATCH_3, TASKS).isPassing());
        assertTrue(rule.filter(OFFER_3, POD_WITH_TASK_MATCH_3, TASKS).isPassing());

        assertFalse(rule.filter(OFFER_1, POD_WITH_TASK_MISMATCH_1, TASKS).isPassing());
        assertTrue(rule.filter(OFFER_2, POD_WITH_TASK_MISMATCH_1, TASKS).isPassing());
        assertFalse(rule.filter(OFFER_3, POD_WITH_TASK_MISMATCH_1, TASKS).isPassing());

        assertFalse(rule.filter(OFFER_1, POD_WITH_TASK_MISMATCH_2, TASKS).isPassing());
        assertTrue(rule.filter(OFFER_2, POD_WITH_TASK_MISMATCH_2, TASKS).isPassing());
        assertFalse(rule.filter(OFFER_3, POD_WITH_TASK_MISMATCH_2, TASKS).isPassing());

        assertFalse(rule.filter(OFFER_1, POD_WITH_TASK_MISMATCH_3, TASKS).isPassing());
        assertTrue(rule.filter(OFFER_2, POD_WITH_TASK_MISMATCH_3, TASKS).isPassing());
        assertFalse(rule.filter(OFFER_3, POD_WITH_TASK_MISMATCH_3, TASKS).isPassing());
    }

    @Test
    public void testColocateNotFound() {
        PlacementRule rule = TaskTypeRule.colocateWith(MATCH_TYPE);

        assertTrue(rule.filter(OFFER_1, POD, MISMATCH_TASKS).isPassing());
        assertTrue(rule.filter(OFFER_2, POD, MISMATCH_TASKS).isPassing());
        assertTrue(rule.filter(OFFER_3, POD, MISMATCH_TASKS).isPassing());
    }

    @Test
    public void testAvoidNotFound() {
        PlacementRule rule = TaskTypeRule.avoid(MATCH_TYPE);

        assertTrue(rule.filter(OFFER_1, POD, MISMATCH_TASKS).isPassing());
        assertTrue(rule.filter(OFFER_2, POD, MISMATCH_TASKS).isPassing());
        assertTrue(rule.filter(OFFER_3, POD, MISMATCH_TASKS).isPassing());
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

    @Test
    public void testDeserializeNoOptionalConverter() throws IOException {
        String str = "{ '@type': 'TaskTypeRule', 'type': 'foo', 'behavior': 'AVOID' }".replace('\'', '"');
        SerializationUtils.fromString(str,
                PlacementRule.class, TestPlacementUtils.OBJECT_MAPPER);
    }

    @Test
    public void testDeserializeAllParams() throws IOException {
        String str = "{ '@type': 'TaskTypeRule', 'type': 'foo', 'converter': { '@type': 'TaskTypeLabelConverter' }, 'behavior': 'AVOID' }".replace('\'', '"');
        SerializationUtils.fromString(str,
                PlacementRule.class, TestPlacementUtils.OBJECT_MAPPER);
    }
}
