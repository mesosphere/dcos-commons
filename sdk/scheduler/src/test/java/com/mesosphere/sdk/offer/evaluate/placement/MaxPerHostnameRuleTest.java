package com.mesosphere.sdk.offer.evaluate.placement;

import com.mesosphere.sdk.config.SerializationUtils;
import com.mesosphere.sdk.offer.CommonIdUtils;
import com.mesosphere.sdk.offer.taskdata.SchedulerLabelReader;
import com.mesosphere.sdk.offer.taskdata.SchedulerLabelWriter;
import com.mesosphere.sdk.scheduler.plan.DefaultPodInstance;
import com.mesosphere.sdk.scheduler.plan.PodInstanceRequirementTestUtils;
import com.mesosphere.sdk.specification.PodInstance;
import com.mesosphere.sdk.specification.PodSpec;
import com.mesosphere.sdk.specification.ResourceSet;
import com.mesosphere.sdk.testutils.OfferTestUtils;
import com.mesosphere.sdk.testutils.TaskTestUtils;
import org.apache.mesos.Protos.Attribute;
import org.apache.mesos.Protos.Offer;
import org.apache.mesos.Protos.TaskInfo;
import org.apache.mesos.Protos.Value;
import org.junit.Test;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;

import static org.junit.Assert.*;

/**
 * Tests for {@link MaxPerHostnameRule}.
 */
public class MaxPerHostnameRuleTest {
    private static final String HOSTNAME_1 = "www.hostname.1";
    private static final String HOSTNAME_2 = "www.hostname.2";
    private static final String HOSTNAME_3 = "www.hostname.3";

    private static final Offer OFFER_NO_HOSTNAME = getOfferWithResources();
    private static final Offer OFFER_HOSTNAME_1;
    private static final Offer OFFER_HOSTNAME_2;
    private static final Offer OFFER_HOSTNAME_3;
    static {
        Attribute.Builder a = Attribute.newBuilder()
                .setType(Value.Type.TEXT)
                .setName("footext");
        a.getTextBuilder().setValue("123");
        OFFER_HOSTNAME_1 = OFFER_NO_HOSTNAME.toBuilder().setHostname(HOSTNAME_1).build();

        a = Attribute.newBuilder()
                .setType(Value.Type.TEXT)
                .setName("footext");
        a.getTextBuilder().setValue("456");
        OFFER_HOSTNAME_2 = OFFER_NO_HOSTNAME.toBuilder().setHostname(HOSTNAME_2).build();

        a = Attribute.newBuilder()
                .setType(Value.Type.TEXT)
                .setName("other");
        a.getTextBuilder().setValue("123");
        OFFER_HOSTNAME_3 = OFFER_NO_HOSTNAME.toBuilder().setHostname(HOSTNAME_3).build();
    }

    private static final TaskInfo TASK_NO_HOSTNAME = TaskTestUtils.getTaskInfo(Collections.emptyList());
    private static final TaskInfo TASK_HOSTNAME_1 = getTask("match-1__abc", OFFER_HOSTNAME_1);
    private static final TaskInfo TASK_HOSTNAME_2 = getTask("match-2__def", OFFER_HOSTNAME_2);
    private static final TaskInfo TASK_HOSTNAME_3 = getTask("mismatch-3__ghi", OFFER_HOSTNAME_3);
    private static final Collection<TaskInfo> TASKS = Arrays.asList(
            TASK_NO_HOSTNAME, TASK_HOSTNAME_1, TASK_HOSTNAME_2, TASK_HOSTNAME_3);

    private static final PodInstance REQ = PodInstanceRequirementTestUtils.getCpuRequirement(1.0).getPodInstance();
    private static final PodInstance REQ_WITH_NO_HOSTNAME = getPodInstance(TASK_NO_HOSTNAME);
    private static final PodInstance REQ_WITH_HOSTNAME_1 = getPodInstance(TASK_HOSTNAME_1);
    private static final PodInstance REQ_WITH_HOSTNAME_2 = getPodInstance(TASK_HOSTNAME_2);

    private static TaskInfo getTask(String id, Offer offer) {
        TaskInfo.Builder taskBuilder = TaskTestUtils.getTaskInfo(Collections.emptyList()).toBuilder();
        taskBuilder.getTaskIdBuilder().setValue(id);
        try {
            taskBuilder.setName(CommonIdUtils.toTaskName(taskBuilder.getTaskId()));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
        taskBuilder.setLabels(new SchedulerLabelWriter(taskBuilder).setHostname(offer).toProto());
        return taskBuilder.build();
    }

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

    private static Offer getOfferWithResources() {
        Offer.Builder o = OfferTestUtils.getEmptyOfferBuilder();
        OfferTestUtils.addResource(o, "a");
        OfferTestUtils.addResource(o, "b");
        return o.build();
    }

    @Test
    public void testLimitZero() {
        PlacementRule rule = new MaxPerHostnameRule(0);

        assertTrue(rule.filter(OFFER_NO_HOSTNAME, REQ, TASKS).isPassing());
        assertFalse(rule.filter(OFFER_HOSTNAME_1, REQ, TASKS).isPassing());
        assertFalse(rule.filter(OFFER_HOSTNAME_2, REQ, TASKS).isPassing());
        assertFalse(rule.filter(OFFER_HOSTNAME_3, REQ, TASKS).isPassing());

        assertTrue(rule.filter(OFFER_NO_HOSTNAME, REQ, Collections.emptyList()).isPassing());
        assertTrue(rule.filter(OFFER_HOSTNAME_1, REQ, Collections.emptyList()).isPassing());
        assertTrue(rule.filter(OFFER_HOSTNAME_2, REQ, Collections.emptyList()).isPassing());
        assertTrue(rule.filter(OFFER_HOSTNAME_3, REQ, Collections.emptyList()).isPassing());
    }

    @Test
    public void testLimitZeroWithSamePresent() {
        PlacementRule rule = new MaxPerHostnameRule(0);

        assertTrue(rule.filter(OFFER_NO_HOSTNAME, REQ_WITH_NO_HOSTNAME, TASKS).isPassing());
        assertFalse(rule.filter(OFFER_HOSTNAME_1, REQ_WITH_NO_HOSTNAME, TASKS).isPassing());
        assertFalse(rule.filter(OFFER_HOSTNAME_2, REQ_WITH_NO_HOSTNAME, TASKS).isPassing());
        assertFalse(rule.filter(OFFER_HOSTNAME_3, REQ_WITH_NO_HOSTNAME, TASKS).isPassing());

        assertTrue(rule.filter(OFFER_NO_HOSTNAME, REQ_WITH_HOSTNAME_1, TASKS).isPassing());
        assertTrue(rule.filter(OFFER_HOSTNAME_1, REQ_WITH_HOSTNAME_1, TASKS).isPassing());
        assertFalse(rule.filter(OFFER_HOSTNAME_2, REQ_WITH_HOSTNAME_1, TASKS).isPassing());
        assertFalse(rule.filter(OFFER_HOSTNAME_3, REQ_WITH_HOSTNAME_1, TASKS).isPassing());

        assertTrue(rule.filter(OFFER_NO_HOSTNAME, REQ_WITH_NO_HOSTNAME, Collections.emptyList()).isPassing());
        assertTrue(rule.filter(OFFER_HOSTNAME_1, REQ_WITH_NO_HOSTNAME, Collections.emptyList()).isPassing());
        assertTrue(rule.filter(OFFER_HOSTNAME_2, REQ_WITH_NO_HOSTNAME, Collections.emptyList()).isPassing());
        assertTrue(rule.filter(OFFER_HOSTNAME_3, REQ_WITH_NO_HOSTNAME, Collections.emptyList()).isPassing());

        assertTrue(rule.filter(OFFER_NO_HOSTNAME, REQ_WITH_HOSTNAME_1, Collections.emptyList()).isPassing());
        assertTrue(rule.filter(OFFER_HOSTNAME_1, REQ_WITH_HOSTNAME_1, Collections.emptyList()).isPassing());
        assertTrue(rule.filter(OFFER_HOSTNAME_2, REQ_WITH_HOSTNAME_1, Collections.emptyList()).isPassing());
        assertTrue(rule.filter(OFFER_HOSTNAME_3, REQ_WITH_HOSTNAME_1, Collections.emptyList()).isPassing());
    }

    @Test
    public void testLimitOne() {
        PlacementRule rule = new MaxPerHostnameRule(1);

        assertTrue(rule.filter(OFFER_NO_HOSTNAME, REQ, TASKS).isPassing());
        assertFalse(rule.filter(OFFER_HOSTNAME_1, REQ, TASKS).isPassing());
        assertFalse(rule.filter(OFFER_HOSTNAME_2, REQ, TASKS).isPassing());
        assertFalse(rule.filter(OFFER_HOSTNAME_3, REQ, TASKS).isPassing());

        assertTrue(rule.filter(OFFER_NO_HOSTNAME, REQ, Collections.emptyList()).isPassing());
        assertTrue(rule.filter(OFFER_HOSTNAME_1, REQ, Collections.emptyList()).isPassing());
        assertTrue(rule.filter(OFFER_HOSTNAME_2, REQ, Collections.emptyList()).isPassing());
        assertTrue(rule.filter(OFFER_HOSTNAME_3, REQ, Collections.emptyList()).isPassing());
    }

    @Test
    public void testLimitOneWithSamePresent() {
        PlacementRule rule = new MaxPerHostnameRule(1);

        assertTrue(rule.filter(OFFER_NO_HOSTNAME, REQ_WITH_NO_HOSTNAME, TASKS).isPassing());
        assertFalse(rule.filter(OFFER_HOSTNAME_1, REQ_WITH_NO_HOSTNAME, TASKS).isPassing());
        assertFalse(rule.filter(OFFER_HOSTNAME_2, REQ_WITH_NO_HOSTNAME, TASKS).isPassing());
        assertFalse(rule.filter(OFFER_HOSTNAME_3, REQ_WITH_NO_HOSTNAME, TASKS).isPassing());

        assertTrue(rule.filter(OFFER_NO_HOSTNAME, REQ_WITH_HOSTNAME_1, TASKS).isPassing());
        assertTrue(rule.filter(OFFER_HOSTNAME_1, REQ_WITH_HOSTNAME_1, TASKS).isPassing());
        assertFalse(rule.filter(OFFER_HOSTNAME_2, REQ_WITH_HOSTNAME_1, TASKS).isPassing());
        assertFalse(rule.filter(OFFER_HOSTNAME_3, REQ_WITH_HOSTNAME_1, TASKS).isPassing());

        assertTrue(rule.filter(OFFER_NO_HOSTNAME, REQ_WITH_NO_HOSTNAME, Collections.emptyList()).isPassing());
        assertTrue(rule.filter(OFFER_HOSTNAME_1, REQ_WITH_NO_HOSTNAME, Collections.emptyList()).isPassing());
        assertTrue(rule.filter(OFFER_HOSTNAME_2, REQ_WITH_NO_HOSTNAME, Collections.emptyList()).isPassing());
        assertTrue(rule.filter(OFFER_HOSTNAME_3, REQ_WITH_NO_HOSTNAME, Collections.emptyList()).isPassing());

        assertTrue(rule.filter(OFFER_NO_HOSTNAME, REQ_WITH_HOSTNAME_1, Collections.emptyList()).isPassing());
        assertTrue(rule.filter(OFFER_HOSTNAME_1, REQ_WITH_HOSTNAME_1, Collections.emptyList()).isPassing());
        assertTrue(rule.filter(OFFER_HOSTNAME_2, REQ_WITH_HOSTNAME_1, Collections.emptyList()).isPassing());
        assertTrue(rule.filter(OFFER_HOSTNAME_3, REQ_WITH_HOSTNAME_1, Collections.emptyList()).isPassing());
    }

    @Test
    public void testLimitOneWithTaskFilter() {
        PlacementRule rule = new MaxPerHostnameRule(1, RegexMatcher.create("match-.*"));

        assertTrue(rule.filter(OFFER_NO_HOSTNAME, REQ, TASKS).isPassing());
        assertFalse(rule.filter(OFFER_HOSTNAME_1, REQ, TASKS).isPassing());
        assertFalse(rule.filter(OFFER_HOSTNAME_2, REQ, TASKS).isPassing());
        assertTrue(rule.filter(OFFER_HOSTNAME_3, REQ, TASKS).isPassing());

        rule = new MaxPerHostnameRule(1, ExactMatcher.create("match-1"));

        assertTrue(rule.filter(OFFER_NO_HOSTNAME, REQ, TASKS).isPassing());
        assertFalse(rule.filter(OFFER_HOSTNAME_1, REQ, TASKS).isPassing());
        assertTrue(rule.filter(OFFER_HOSTNAME_2, REQ, TASKS).isPassing());
        assertTrue(rule.filter(OFFER_HOSTNAME_3, REQ, TASKS).isPassing());

        rule = new MaxPerHostnameRule(1, ExactMatcher.create("match-2"));

        assertTrue(rule.filter(OFFER_NO_HOSTNAME, REQ, TASKS).isPassing());
        assertTrue(rule.filter(OFFER_HOSTNAME_1, REQ, TASKS).isPassing());
        assertFalse(rule.filter(OFFER_HOSTNAME_2, REQ, TASKS).isPassing());
        assertTrue(rule.filter(OFFER_HOSTNAME_3, REQ, TASKS).isPassing());

        rule = new MaxPerHostnameRule(1, ExactMatcher.create("mismatch"));

        assertTrue(rule.filter(OFFER_NO_HOSTNAME, REQ, TASKS).isPassing());
        assertTrue(rule.filter(OFFER_HOSTNAME_1, REQ, TASKS).isPassing());
        assertTrue(rule.filter(OFFER_HOSTNAME_2, REQ, TASKS).isPassing());
        assertTrue(rule.filter(OFFER_HOSTNAME_3, REQ, TASKS).isPassing());
    }

    @Test
    public void testLimitTwo() {
        PlacementRule rule = new MaxPerHostnameRule(2);

        assertTrue(rule.filter(OFFER_NO_HOSTNAME, REQ, TASKS).isPassing());
        assertTrue(rule.filter(OFFER_HOSTNAME_1, REQ, TASKS).isPassing());
        assertTrue(rule.filter(OFFER_HOSTNAME_2, REQ, TASKS).isPassing());
        assertTrue(rule.filter(OFFER_HOSTNAME_3, REQ, TASKS).isPassing());

        assertTrue(rule.filter(OFFER_NO_HOSTNAME, REQ, Collections.emptyList()).isPassing());
        assertTrue(rule.filter(OFFER_HOSTNAME_1, REQ, Collections.emptyList()).isPassing());
        assertTrue(rule.filter(OFFER_HOSTNAME_2, REQ, Collections.emptyList()).isPassing());
        assertTrue(rule.filter(OFFER_HOSTNAME_3, REQ, Collections.emptyList()).isPassing());
    }

    @Test
    public void testLimitTwoWithSamePresent() {
        PlacementRule rule = new MaxPerHostnameRule(2);

        assertTrue(rule.filter(OFFER_NO_HOSTNAME, REQ_WITH_NO_HOSTNAME, TASKS).isPassing());
        assertTrue(rule.filter(OFFER_HOSTNAME_1, REQ_WITH_NO_HOSTNAME, TASKS).isPassing());
        assertTrue(rule.filter(OFFER_HOSTNAME_2, REQ_WITH_NO_HOSTNAME, TASKS).isPassing());
        assertTrue(rule.filter(OFFER_HOSTNAME_3, REQ_WITH_NO_HOSTNAME, TASKS).isPassing());

        assertTrue(rule.filter(OFFER_NO_HOSTNAME, REQ_WITH_HOSTNAME_1, TASKS).isPassing());
        assertTrue(rule.filter(OFFER_HOSTNAME_1, REQ_WITH_HOSTNAME_1, TASKS).isPassing());
        assertTrue(rule.filter(OFFER_HOSTNAME_2, REQ_WITH_HOSTNAME_1, TASKS).isPassing());
        assertTrue(rule.filter(OFFER_HOSTNAME_3, REQ_WITH_HOSTNAME_1, TASKS).isPassing());

        assertTrue(rule.filter(OFFER_NO_HOSTNAME, REQ_WITH_NO_HOSTNAME, Collections.emptyList()).isPassing());
        assertTrue(rule.filter(OFFER_HOSTNAME_1, REQ_WITH_NO_HOSTNAME, Collections.emptyList()).isPassing());
        assertTrue(rule.filter(OFFER_HOSTNAME_2, REQ_WITH_NO_HOSTNAME, Collections.emptyList()).isPassing());
        assertTrue(rule.filter(OFFER_HOSTNAME_3, REQ_WITH_NO_HOSTNAME, Collections.emptyList()).isPassing());

        assertTrue(rule.filter(OFFER_NO_HOSTNAME, REQ_WITH_HOSTNAME_1, Collections.emptyList()).isPassing());
        assertTrue(rule.filter(OFFER_HOSTNAME_1, REQ_WITH_HOSTNAME_1, Collections.emptyList()).isPassing());
        assertTrue(rule.filter(OFFER_HOSTNAME_2, REQ_WITH_HOSTNAME_1, Collections.emptyList()).isPassing());
        assertTrue(rule.filter(OFFER_HOSTNAME_3, REQ_WITH_HOSTNAME_1, Collections.emptyList()).isPassing());
    }

    @Test
    public void testLimitTwoDuplicates() {
        PlacementRule rule = new MaxPerHostnameRule(2);

        Collection<TaskInfo> tasks = Arrays.asList(
                TASK_NO_HOSTNAME,
                TASK_HOSTNAME_1, TASK_HOSTNAME_1,
                TASK_HOSTNAME_2,
                TASK_HOSTNAME_3);

        assertTrue(rule.filter(OFFER_NO_HOSTNAME, REQ, tasks).isPassing());
        assertFalse(rule.filter(OFFER_HOSTNAME_1, REQ, tasks).isPassing());
        assertTrue(rule.filter(OFFER_HOSTNAME_2, REQ, tasks).isPassing());
        assertTrue(rule.filter(OFFER_HOSTNAME_3, REQ, tasks).isPassing());

        tasks = Arrays.asList(
                TASK_NO_HOSTNAME, TASK_NO_HOSTNAME,
                TASK_HOSTNAME_1,
                TASK_HOSTNAME_2, TASK_HOSTNAME_2,
                TASK_HOSTNAME_3);

        assertTrue(rule.filter(OFFER_NO_HOSTNAME, REQ, tasks).isPassing());
        assertTrue(rule.filter(OFFER_HOSTNAME_1, REQ, tasks).isPassing());
        assertFalse(rule.filter(OFFER_HOSTNAME_2, REQ, tasks).isPassing());
        assertTrue(rule.filter(OFFER_HOSTNAME_3, REQ, tasks).isPassing());

        tasks = Arrays.asList(
                TASK_NO_HOSTNAME,
                TASK_HOSTNAME_1, TASK_HOSTNAME_1,
                TASK_HOSTNAME_2, TASK_HOSTNAME_2,
                TASK_HOSTNAME_3, TASK_HOSTNAME_3, TASK_HOSTNAME_3);

        assertTrue(rule.filter(OFFER_NO_HOSTNAME, REQ, tasks).isPassing());
        assertFalse(rule.filter(OFFER_HOSTNAME_1, REQ, tasks).isPassing());
        assertFalse(rule.filter(OFFER_HOSTNAME_2, REQ, tasks).isPassing());
        assertFalse(rule.filter(OFFER_HOSTNAME_3, REQ, tasks).isPassing());
    }

    @Test
    public void testLimitTwoDuplicatesWithSamePresent() {
        PlacementRule rule = new MaxPerHostnameRule(2);

        Collection<TaskInfo> tasks = Arrays.asList(
                TASK_NO_HOSTNAME,
                TASK_HOSTNAME_1, TASK_HOSTNAME_1,
                TASK_HOSTNAME_2,
                TASK_HOSTNAME_3);

        assertTrue(rule.filter(OFFER_NO_HOSTNAME, REQ_WITH_NO_HOSTNAME, tasks).isPassing());
        assertFalse(rule.filter(OFFER_HOSTNAME_1, REQ_WITH_NO_HOSTNAME, tasks).isPassing());
        assertTrue(rule.filter(OFFER_HOSTNAME_2, REQ_WITH_NO_HOSTNAME, tasks).isPassing());
        assertTrue(rule.filter(OFFER_HOSTNAME_3, REQ_WITH_NO_HOSTNAME, tasks).isPassing());

        assertTrue(rule.filter(OFFER_NO_HOSTNAME, REQ_WITH_HOSTNAME_1, tasks).isPassing());
        assertTrue(rule.filter(OFFER_HOSTNAME_1, REQ_WITH_HOSTNAME_1, tasks).isPassing());
        assertTrue(rule.filter(OFFER_HOSTNAME_2, REQ_WITH_HOSTNAME_1, tasks).isPassing());
        assertTrue(rule.filter(OFFER_HOSTNAME_3, REQ_WITH_HOSTNAME_1, tasks).isPassing());

        tasks = Arrays.asList(
                TASK_NO_HOSTNAME, TASK_NO_HOSTNAME,
                TASK_HOSTNAME_1,
                TASK_HOSTNAME_2, TASK_HOSTNAME_2,
                TASK_HOSTNAME_3);

        assertTrue(rule.filter(OFFER_NO_HOSTNAME, REQ_WITH_NO_HOSTNAME, tasks).isPassing());
        assertTrue(rule.filter(OFFER_HOSTNAME_1, REQ_WITH_NO_HOSTNAME, tasks).isPassing());
        assertFalse(rule.filter(OFFER_HOSTNAME_2, REQ_WITH_NO_HOSTNAME, tasks).isPassing());
        assertTrue(rule.filter(OFFER_HOSTNAME_3, REQ_WITH_NO_HOSTNAME, tasks).isPassing());

        assertTrue(rule.filter(OFFER_NO_HOSTNAME, REQ_WITH_HOSTNAME_1, tasks).isPassing());
        assertTrue(rule.filter(OFFER_HOSTNAME_1, REQ_WITH_HOSTNAME_1, tasks).isPassing());
        assertFalse(rule.filter(OFFER_HOSTNAME_2, REQ_WITH_HOSTNAME_1, tasks).isPassing());
        assertTrue(rule.filter(OFFER_HOSTNAME_3, REQ_WITH_HOSTNAME_1, tasks).isPassing());

        assertTrue(rule.filter(OFFER_NO_HOSTNAME, REQ_WITH_HOSTNAME_2, tasks).isPassing());
        assertTrue(rule.filter(OFFER_HOSTNAME_1, REQ_WITH_HOSTNAME_2, tasks).isPassing());
        assertTrue(rule.filter(OFFER_HOSTNAME_2, REQ_WITH_HOSTNAME_2, tasks).isPassing());
        assertTrue(rule.filter(OFFER_HOSTNAME_3, REQ_WITH_HOSTNAME_2, tasks).isPassing());

        tasks = Arrays.asList(
                TASK_NO_HOSTNAME,
                TASK_HOSTNAME_1, TASK_HOSTNAME_1,
                TASK_HOSTNAME_2, TASK_HOSTNAME_2, TASK_HOSTNAME_2,
                TASK_HOSTNAME_3, TASK_HOSTNAME_3);

        assertTrue(rule.filter(OFFER_NO_HOSTNAME, REQ_WITH_NO_HOSTNAME, tasks).isPassing());
        assertFalse(rule.filter(OFFER_HOSTNAME_1, REQ_WITH_NO_HOSTNAME, tasks).isPassing());
        assertFalse(rule.filter(OFFER_HOSTNAME_2, REQ_WITH_NO_HOSTNAME, tasks).isPassing());
        assertFalse(rule.filter(OFFER_HOSTNAME_3, REQ_WITH_NO_HOSTNAME, tasks).isPassing());

        assertTrue(rule.filter(OFFER_NO_HOSTNAME, REQ_WITH_HOSTNAME_1, tasks).isPassing());
        assertTrue(rule.filter(OFFER_HOSTNAME_1, REQ_WITH_HOSTNAME_1, tasks).isPassing());
        assertFalse(rule.filter(OFFER_HOSTNAME_2, REQ_WITH_HOSTNAME_1, tasks).isPassing());
        assertFalse(rule.filter(OFFER_HOSTNAME_3, REQ_WITH_HOSTNAME_1, tasks).isPassing());

        assertTrue(rule.filter(OFFER_NO_HOSTNAME, REQ_WITH_HOSTNAME_2, tasks).isPassing());
        assertFalse(rule.filter(OFFER_HOSTNAME_1, REQ_WITH_HOSTNAME_2, tasks).isPassing());
        assertTrue(rule.filter(OFFER_HOSTNAME_2, REQ_WITH_HOSTNAME_2, tasks).isPassing());
        assertFalse(rule.filter(OFFER_HOSTNAME_3, REQ_WITH_HOSTNAME_2, tasks).isPassing());
    }

    @Test
    public void testSerializeDeserialize() throws IOException {
        PlacementRule rule = new MaxPerHostnameRule(2);
        assertEquals(rule, SerializationUtils.fromString(
                SerializationUtils.toJsonString(rule), PlacementRule.class, TestPlacementUtils.OBJECT_MAPPER));

        rule = new MaxPerHostnameRule(0, ExactMatcher.create("hi"));
        assertEquals(rule, SerializationUtils.fromString(
                SerializationUtils.toJsonString(rule), PlacementRule.class, TestPlacementUtils.OBJECT_MAPPER));
    }

    @Test
    public void testDeserializeNoOptionalTaskFilter() throws IOException {
        String str = "{ '@type': 'MaxPerHostnameRule', 'max': 2 }".replace('\'', '"');
        SerializationUtils.fromString(str,
                PlacementRule.class, TestPlacementUtils.OBJECT_MAPPER);
    }

    @Test
    public void testDeserializeAllParams() throws IOException {
        String str = "{ '@type': 'MaxPerHostnameRule', 'max': 2, 'task-filter': { '@type': 'ExactMatcher', 'string': 'foo' } }".replace('\'', '"');
        SerializationUtils.fromString(str,
                PlacementRule.class, TestPlacementUtils.OBJECT_MAPPER);
    }
}
