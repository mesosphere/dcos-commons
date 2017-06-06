package com.mesosphere.sdk.offer.evaluate.placement;

import com.mesosphere.sdk.config.SerializationUtils;
import com.mesosphere.sdk.offer.CommonIdUtils;
import com.mesosphere.sdk.offer.taskdata.SchedulerLabelReader;
import com.mesosphere.sdk.offer.taskdata.SchedulerLabelWriter;
import com.mesosphere.sdk.scheduler.plan.DefaultPodInstance;
import com.mesosphere.sdk.scheduler.plan.PodInstanceRequirementTestUtils;
import com.mesosphere.sdk.specification.*;
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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Tests for {@link MaxPerAttributeRule}.
 */
public class MaxPerAttributeRuleTest {
    private static final String ATTR_PATTERN = "^footext:.*$";
    private static final StringMatcher ATTR_MATCHER = RegexMatcher.create(ATTR_PATTERN);

    private static final Offer OFFER_NO_ATTRS = getOfferWithResources();
    private static final Offer OFFER_ATTR_MATCH_1;
    private static final Offer OFFER_ATTR_MATCH_2;
    private static final Offer OFFER_ATTR_MISMATCH;
    static {
        Attribute.Builder a = Attribute.newBuilder()
                .setType(Value.Type.TEXT)
                .setName("footext");
        a.getTextBuilder().setValue("123");
        OFFER_ATTR_MATCH_1 = OFFER_NO_ATTRS.toBuilder().addAttributes(a.build()).build();

        a = Attribute.newBuilder()
                .setType(Value.Type.TEXT)
                .setName("footext");
        a.getTextBuilder().setValue("456");
        OFFER_ATTR_MATCH_2 = OFFER_NO_ATTRS.toBuilder().addAttributes(a.build()).build();

        a = Attribute.newBuilder()
                .setType(Value.Type.TEXT)
                .setName("other");
        a.getTextBuilder().setValue("123");
        OFFER_ATTR_MISMATCH = OFFER_NO_ATTRS.toBuilder().addAttributes(a.build()).build();
    }

    private static final TaskInfo TASK_NO_ATTRS = TaskTestUtils.getTaskInfo(Collections.emptyList());
    private static final TaskInfo TASK_ATTR_MATCH_1 = getTask("match-1__abc", OFFER_ATTR_MATCH_1);
    private static final TaskInfo TASK_ATTR_MATCH_2 = getTask("match-2__def", OFFER_ATTR_MATCH_2);
    private static final TaskInfo TASK_ATTR_MISMATCH = getTask("mismatch__ghi", OFFER_ATTR_MISMATCH);
    private static final Collection<TaskInfo> TASKS = Arrays.asList(
            TASK_NO_ATTRS, TASK_ATTR_MATCH_1, TASK_ATTR_MATCH_2, TASK_ATTR_MISMATCH);

    private static final PodInstance POD = PodInstanceRequirementTestUtils.getCpuRequirement(1.0).getPodInstance();
    private static final PodInstance POD_WITH_TASK_NO_ATTRS = getPodInstance(TASK_NO_ATTRS);
    private static final PodInstance POD_WITH_TASK_ATTR_MATCH_1 = getPodInstance(TASK_ATTR_MATCH_1);
    private static final PodInstance POD_WITH_TASK_ATTR_MATCH_2 = getPodInstance(TASK_ATTR_MATCH_2);
    private static final PodInstance POD_WITH_TASK_ATTR_MISMATCH = getPodInstance(TASK_ATTR_MISMATCH);

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

    private static TaskInfo getTask(String id, Offer offer) {
        TaskInfo.Builder taskBuilder = TaskTestUtils.getTaskInfo(Collections.emptyList()).toBuilder();
        taskBuilder.getTaskIdBuilder().setValue(id);
        try {
            taskBuilder.setName(CommonIdUtils.toTaskName(taskBuilder.getTaskId()));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
        taskBuilder.setLabels(new SchedulerLabelWriter(taskBuilder).setOfferAttributes(offer).toProto());
        return taskBuilder.build();
    }

    private static Offer getOfferWithResources() {
        Offer.Builder o = OfferTestUtils.getEmptyOfferBuilder();
        OfferTestUtils.addResource(o, "a");
        OfferTestUtils.addResource(o, "b");
        return o.build();
    }

    @Test
    public void testLimitZero() {
        PlacementRule rule = new MaxPerAttributeRule(0, ATTR_MATCHER);

        assertTrue(rule.filter(OFFER_NO_ATTRS, POD, TASKS).isPassing());
        assertFalse(rule.filter(OFFER_ATTR_MATCH_1, POD, TASKS).isPassing());
        assertFalse(rule.filter(OFFER_ATTR_MATCH_2, POD, TASKS).isPassing());
        assertTrue(rule.filter(OFFER_ATTR_MISMATCH, POD, TASKS).isPassing());

        assertTrue(rule.filter(OFFER_NO_ATTRS, POD, Collections.emptyList()).isPassing());
        assertTrue(rule.filter(OFFER_ATTR_MATCH_1, POD, Collections.emptyList()).isPassing());
        assertTrue(rule.filter(OFFER_ATTR_MATCH_2, POD, Collections.emptyList()).isPassing());
        assertTrue(rule.filter(OFFER_ATTR_MISMATCH, POD, Collections.emptyList()).isPassing());
    }

    @Test
    public void testLimitZeroWithSamePresent() {
        PlacementRule rule = new MaxPerAttributeRule(0, ATTR_MATCHER);

        assertTrue(rule.filter(OFFER_NO_ATTRS, POD_WITH_TASK_NO_ATTRS, TASKS).isPassing());
        assertFalse(rule.filter(OFFER_ATTR_MATCH_1, POD_WITH_TASK_NO_ATTRS, TASKS).isPassing());
        assertFalse(rule.filter(OFFER_ATTR_MATCH_2, POD_WITH_TASK_NO_ATTRS, TASKS).isPassing());
        assertTrue(rule.filter(OFFER_ATTR_MISMATCH, POD_WITH_TASK_NO_ATTRS, TASKS).isPassing());

        assertTrue(rule.filter(OFFER_NO_ATTRS, POD_WITH_TASK_ATTR_MATCH_1, TASKS).isPassing());
        assertTrue(rule.filter(OFFER_ATTR_MATCH_1, POD_WITH_TASK_ATTR_MATCH_1, TASKS).isPassing());
        assertFalse(rule.filter(OFFER_ATTR_MATCH_2, POD_WITH_TASK_ATTR_MATCH_1, TASKS).isPassing());
        assertTrue(rule.filter(OFFER_ATTR_MISMATCH, POD_WITH_TASK_ATTR_MATCH_1, TASKS).isPassing());

        assertTrue(rule.filter(OFFER_NO_ATTRS, POD_WITH_TASK_ATTR_MATCH_2, TASKS).isPassing());
        assertFalse(rule.filter(OFFER_ATTR_MATCH_1, POD_WITH_TASK_ATTR_MATCH_2, TASKS).isPassing());
        assertTrue(rule.filter(OFFER_ATTR_MATCH_2, POD_WITH_TASK_ATTR_MATCH_2, TASKS).isPassing());
        assertTrue(rule.filter(OFFER_ATTR_MISMATCH, POD_WITH_TASK_ATTR_MATCH_2, TASKS).isPassing());

        assertTrue(rule.filter(OFFER_NO_ATTRS, POD_WITH_TASK_ATTR_MISMATCH, TASKS).isPassing());
        assertFalse(rule.filter(OFFER_ATTR_MATCH_1, POD_WITH_TASK_ATTR_MISMATCH, TASKS).isPassing());
        assertFalse(rule.filter(OFFER_ATTR_MATCH_2, POD_WITH_TASK_ATTR_MISMATCH, TASKS).isPassing());
        assertTrue(rule.filter(OFFER_ATTR_MISMATCH, POD_WITH_TASK_ATTR_MISMATCH, TASKS).isPassing());

        assertTrue(rule.filter(OFFER_NO_ATTRS, POD_WITH_TASK_NO_ATTRS, Collections.emptyList()).isPassing());
        assertTrue(rule.filter(OFFER_ATTR_MATCH_1, POD_WITH_TASK_NO_ATTRS, Collections.emptyList()).isPassing());
        assertTrue(rule.filter(OFFER_ATTR_MATCH_2, POD_WITH_TASK_NO_ATTRS, Collections.emptyList()).isPassing());
        assertTrue(rule.filter(OFFER_ATTR_MISMATCH, POD_WITH_TASK_NO_ATTRS, Collections.emptyList()).isPassing());

        assertTrue(rule.filter(OFFER_NO_ATTRS, POD_WITH_TASK_ATTR_MATCH_1, Collections.emptyList()).isPassing());
        assertTrue(rule.filter(OFFER_ATTR_MATCH_1, POD_WITH_TASK_ATTR_MATCH_1, Collections.emptyList()).isPassing());
        assertTrue(rule.filter(OFFER_ATTR_MATCH_2, POD_WITH_TASK_ATTR_MATCH_1, Collections.emptyList()).isPassing());
        assertTrue(rule.filter(OFFER_ATTR_MISMATCH, POD_WITH_TASK_ATTR_MATCH_1, Collections.emptyList()).isPassing());

        assertTrue(rule.filter(OFFER_NO_ATTRS, POD_WITH_TASK_ATTR_MATCH_2, Collections.emptyList()).isPassing());
        assertTrue(rule.filter(OFFER_ATTR_MATCH_1, POD_WITH_TASK_ATTR_MATCH_2, Collections.emptyList()).isPassing());
        assertTrue(rule.filter(OFFER_ATTR_MATCH_2, POD_WITH_TASK_ATTR_MATCH_2, Collections.emptyList()).isPassing());
        assertTrue(rule.filter(OFFER_ATTR_MISMATCH, POD_WITH_TASK_ATTR_MATCH_2, Collections.emptyList()).isPassing());

        assertTrue(rule.filter(OFFER_NO_ATTRS, POD_WITH_TASK_ATTR_MISMATCH, Collections.emptyList()).isPassing());
        assertTrue(rule.filter(OFFER_ATTR_MATCH_1, POD_WITH_TASK_ATTR_MISMATCH, Collections.emptyList()).isPassing());
        assertTrue(rule.filter(OFFER_ATTR_MATCH_2, POD_WITH_TASK_ATTR_MISMATCH, Collections.emptyList()).isPassing());
        assertTrue(rule.filter(OFFER_ATTR_MISMATCH, POD_WITH_TASK_ATTR_MISMATCH, Collections.emptyList()).isPassing());
    }

    @Test
    public void testLimitOne() {
        PlacementRule rule = new MaxPerAttributeRule(1, ATTR_MATCHER);

        assertTrue(rule.filter(OFFER_NO_ATTRS, POD, TASKS).isPassing());
        assertFalse(rule.filter(OFFER_ATTR_MATCH_1, POD, TASKS).isPassing());
        assertFalse(rule.filter(OFFER_ATTR_MATCH_2, POD, TASKS).isPassing());
        assertTrue(rule.filter(OFFER_ATTR_MISMATCH, POD, TASKS).isPassing());

        assertTrue(rule.filter(OFFER_NO_ATTRS, POD, Collections.emptyList()).isPassing());
        assertTrue(rule.filter(OFFER_ATTR_MATCH_1, POD, Collections.emptyList()).isPassing());
        assertTrue(rule.filter(OFFER_ATTR_MATCH_2, POD, Collections.emptyList()).isPassing());
        assertTrue(rule.filter(OFFER_ATTR_MISMATCH, POD, Collections.emptyList()).isPassing());
    }

    @Test
    public void testLimitOneWithSamePresent() {
        PlacementRule rule = new MaxPerAttributeRule(1, ATTR_MATCHER);

        assertTrue(rule.filter(OFFER_NO_ATTRS, POD_WITH_TASK_NO_ATTRS, TASKS).isPassing());
        assertFalse(rule.filter(OFFER_ATTR_MATCH_1, POD_WITH_TASK_NO_ATTRS, TASKS).isPassing());
        assertFalse(rule.filter(OFFER_ATTR_MATCH_2, POD_WITH_TASK_NO_ATTRS, TASKS).isPassing());
        assertTrue(rule.filter(OFFER_ATTR_MISMATCH, POD_WITH_TASK_NO_ATTRS, TASKS).isPassing());

        assertTrue(rule.filter(OFFER_NO_ATTRS, POD_WITH_TASK_ATTR_MATCH_1, TASKS).isPassing());
        assertTrue(rule.filter(OFFER_ATTR_MATCH_1, POD_WITH_TASK_ATTR_MATCH_1, TASKS).isPassing());
        assertFalse(rule.filter(OFFER_ATTR_MATCH_2, POD_WITH_TASK_ATTR_MATCH_1, TASKS).isPassing());
        assertTrue(rule.filter(OFFER_ATTR_MISMATCH, POD_WITH_TASK_ATTR_MATCH_1, TASKS).isPassing());

        assertTrue(rule.filter(OFFER_NO_ATTRS, POD_WITH_TASK_ATTR_MATCH_2, TASKS).isPassing());
        assertFalse(rule.filter(OFFER_ATTR_MATCH_1, POD_WITH_TASK_ATTR_MATCH_2, TASKS).isPassing());
        assertTrue(rule.filter(OFFER_ATTR_MATCH_2, POD_WITH_TASK_ATTR_MATCH_2, TASKS).isPassing());
        assertTrue(rule.filter(OFFER_ATTR_MISMATCH, POD_WITH_TASK_ATTR_MATCH_2, TASKS).isPassing());

        assertTrue(rule.filter(OFFER_NO_ATTRS, POD_WITH_TASK_ATTR_MISMATCH, TASKS).isPassing());
        assertFalse(rule.filter(OFFER_ATTR_MATCH_1, POD_WITH_TASK_ATTR_MISMATCH, TASKS).isPassing());
        assertFalse(rule.filter(OFFER_ATTR_MATCH_2, POD_WITH_TASK_ATTR_MISMATCH, TASKS).isPassing());
        assertTrue(rule.filter(OFFER_ATTR_MISMATCH, POD_WITH_TASK_ATTR_MISMATCH, TASKS).isPassing());

        assertTrue(rule.filter(OFFER_NO_ATTRS, POD_WITH_TASK_NO_ATTRS, Collections.emptyList()).isPassing());
        assertTrue(rule.filter(OFFER_ATTR_MATCH_1, POD_WITH_TASK_NO_ATTRS, Collections.emptyList()).isPassing());
        assertTrue(rule.filter(OFFER_ATTR_MATCH_2, POD_WITH_TASK_NO_ATTRS, Collections.emptyList()).isPassing());
        assertTrue(rule.filter(OFFER_ATTR_MISMATCH, POD_WITH_TASK_NO_ATTRS, Collections.emptyList()).isPassing());

        assertTrue(rule.filter(OFFER_NO_ATTRS, POD_WITH_TASK_ATTR_MATCH_1, Collections.emptyList()).isPassing());
        assertTrue(rule.filter(OFFER_ATTR_MATCH_1, POD_WITH_TASK_ATTR_MATCH_1, Collections.emptyList()).isPassing());
        assertTrue(rule.filter(OFFER_ATTR_MATCH_2, POD_WITH_TASK_ATTR_MATCH_1, Collections.emptyList()).isPassing());
        assertTrue(rule.filter(OFFER_ATTR_MISMATCH, POD_WITH_TASK_ATTR_MATCH_1, Collections.emptyList()).isPassing());

        assertTrue(rule.filter(OFFER_NO_ATTRS, POD_WITH_TASK_ATTR_MATCH_2, Collections.emptyList()).isPassing());
        assertTrue(rule.filter(OFFER_ATTR_MATCH_1, POD_WITH_TASK_ATTR_MATCH_2, Collections.emptyList()).isPassing());
        assertTrue(rule.filter(OFFER_ATTR_MATCH_2, POD_WITH_TASK_ATTR_MATCH_2, Collections.emptyList()).isPassing());
        assertTrue(rule.filter(OFFER_ATTR_MISMATCH, POD_WITH_TASK_ATTR_MATCH_2, Collections.emptyList()).isPassing());

        assertTrue(rule.filter(OFFER_NO_ATTRS, POD_WITH_TASK_ATTR_MISMATCH, Collections.emptyList()).isPassing());
        assertTrue(rule.filter(OFFER_ATTR_MATCH_1, POD_WITH_TASK_ATTR_MISMATCH, Collections.emptyList()).isPassing());
        assertTrue(rule.filter(OFFER_ATTR_MATCH_2, POD_WITH_TASK_ATTR_MISMATCH, Collections.emptyList()).isPassing());
        assertTrue(rule.filter(OFFER_ATTR_MISMATCH, POD_WITH_TASK_ATTR_MISMATCH, Collections.emptyList()).isPassing());
    }

    @Test
    public void testLimitOneWithTaskFilter() {
        PlacementRule rule = new MaxPerAttributeRule(1, ATTR_MATCHER, RegexMatcher.create("match-.*"));

        assertTrue(rule.filter(OFFER_NO_ATTRS, POD, TASKS).isPassing());
        assertFalse(rule.filter(OFFER_ATTR_MATCH_1, POD, TASKS).isPassing());
        assertFalse(rule.filter(OFFER_ATTR_MATCH_2, POD, TASKS).isPassing());
        assertTrue(rule.filter(OFFER_ATTR_MISMATCH, POD, TASKS).isPassing());

        rule = new MaxPerAttributeRule(1, ATTR_MATCHER, ExactMatcher.create("match-1"));

        assertTrue(rule.filter(OFFER_NO_ATTRS, POD, TASKS).isPassing());
        assertFalse(rule.filter(OFFER_ATTR_MATCH_1, POD, TASKS).isPassing());
        assertTrue(rule.filter(OFFER_ATTR_MATCH_2, POD, TASKS).isPassing());
        assertTrue(rule.filter(OFFER_ATTR_MISMATCH, POD, TASKS).isPassing());

        rule = new MaxPerAttributeRule(1, ATTR_MATCHER, ExactMatcher.create("match-2"));

        assertTrue(rule.filter(OFFER_NO_ATTRS, POD, TASKS).isPassing());
        assertTrue(rule.filter(OFFER_ATTR_MATCH_1, POD, TASKS).isPassing());
        assertFalse(rule.filter(OFFER_ATTR_MATCH_2, POD, TASKS).isPassing());
        assertTrue(rule.filter(OFFER_ATTR_MISMATCH, POD, TASKS).isPassing());

        rule = new MaxPerAttributeRule(1, ATTR_MATCHER, ExactMatcher.create("mismatch"));

        assertTrue(rule.filter(OFFER_NO_ATTRS, POD, TASKS).isPassing());
        assertTrue(rule.filter(OFFER_ATTR_MATCH_1, POD, TASKS).isPassing());
        assertTrue(rule.filter(OFFER_ATTR_MATCH_2, POD, TASKS).isPassing());
        assertTrue(rule.filter(OFFER_ATTR_MISMATCH, POD, TASKS).isPassing());
    }

    @Test
    public void testLimitTwo() {
        PlacementRule rule = new MaxPerAttributeRule(2, ATTR_MATCHER);

        assertTrue(rule.filter(OFFER_NO_ATTRS, POD, TASKS).isPassing());
        assertTrue(rule.filter(OFFER_ATTR_MATCH_1, POD, TASKS).isPassing());
        assertTrue(rule.filter(OFFER_ATTR_MATCH_2, POD, TASKS).isPassing());
        assertTrue(rule.filter(OFFER_ATTR_MISMATCH, POD, TASKS).isPassing());

        assertTrue(rule.filter(OFFER_NO_ATTRS, POD, Collections.emptyList()).isPassing());
        assertTrue(rule.filter(OFFER_ATTR_MATCH_1, POD, Collections.emptyList()).isPassing());
        assertTrue(rule.filter(OFFER_ATTR_MATCH_2, POD, Collections.emptyList()).isPassing());
        assertTrue(rule.filter(OFFER_ATTR_MISMATCH, POD, Collections.emptyList()).isPassing());
    }

    @Test
    public void testLimitTwoWithSamePresent() {
        PlacementRule rule = new MaxPerAttributeRule(2, ATTR_MATCHER);

        assertTrue(rule.filter(OFFER_NO_ATTRS, POD_WITH_TASK_NO_ATTRS, TASKS).isPassing());
        assertTrue(rule.filter(OFFER_ATTR_MATCH_1, POD_WITH_TASK_NO_ATTRS, TASKS).isPassing());
        assertTrue(rule.filter(OFFER_ATTR_MATCH_2, POD_WITH_TASK_NO_ATTRS, TASKS).isPassing());
        assertTrue(rule.filter(OFFER_ATTR_MISMATCH, POD_WITH_TASK_NO_ATTRS, TASKS).isPassing());

        assertTrue(rule.filter(OFFER_NO_ATTRS, POD_WITH_TASK_ATTR_MATCH_1, TASKS).isPassing());
        assertTrue(rule.filter(OFFER_ATTR_MATCH_1, POD_WITH_TASK_ATTR_MATCH_1, TASKS).isPassing());
        assertTrue(rule.filter(OFFER_ATTR_MATCH_2, POD_WITH_TASK_ATTR_MATCH_1, TASKS).isPassing());
        assertTrue(rule.filter(OFFER_ATTR_MISMATCH, POD_WITH_TASK_ATTR_MATCH_1, TASKS).isPassing());

        assertTrue(rule.filter(OFFER_NO_ATTRS, POD_WITH_TASK_ATTR_MATCH_2, TASKS).isPassing());
        assertTrue(rule.filter(OFFER_ATTR_MATCH_1, POD_WITH_TASK_ATTR_MATCH_2, TASKS).isPassing());
        assertTrue(rule.filter(OFFER_ATTR_MATCH_2, POD_WITH_TASK_ATTR_MATCH_2, TASKS).isPassing());
        assertTrue(rule.filter(OFFER_ATTR_MISMATCH, POD_WITH_TASK_ATTR_MATCH_2, TASKS).isPassing());

        assertTrue(rule.filter(OFFER_NO_ATTRS, POD_WITH_TASK_ATTR_MISMATCH, TASKS).isPassing());
        assertTrue(rule.filter(OFFER_ATTR_MATCH_1, POD_WITH_TASK_ATTR_MISMATCH, TASKS).isPassing());
        assertTrue(rule.filter(OFFER_ATTR_MATCH_2, POD_WITH_TASK_ATTR_MISMATCH, TASKS).isPassing());
        assertTrue(rule.filter(OFFER_ATTR_MISMATCH, POD_WITH_TASK_ATTR_MISMATCH, TASKS).isPassing());

        assertTrue(rule.filter(OFFER_NO_ATTRS, POD_WITH_TASK_NO_ATTRS, Collections.emptyList()).isPassing());
        assertTrue(rule.filter(OFFER_ATTR_MATCH_1, POD_WITH_TASK_NO_ATTRS, Collections.emptyList()).isPassing());
        assertTrue(rule.filter(OFFER_ATTR_MATCH_2, POD_WITH_TASK_NO_ATTRS, Collections.emptyList()).isPassing());
        assertTrue(rule.filter(OFFER_ATTR_MISMATCH, POD_WITH_TASK_NO_ATTRS, Collections.emptyList()).isPassing());

        assertTrue(rule.filter(OFFER_NO_ATTRS, POD_WITH_TASK_ATTR_MATCH_1, Collections.emptyList()).isPassing());
        assertTrue(rule.filter(OFFER_ATTR_MATCH_1, POD_WITH_TASK_ATTR_MATCH_1, Collections.emptyList()).isPassing());
        assertTrue(rule.filter(OFFER_ATTR_MATCH_2, POD_WITH_TASK_ATTR_MATCH_1, Collections.emptyList()).isPassing());
        assertTrue(rule.filter(OFFER_ATTR_MISMATCH, POD_WITH_TASK_ATTR_MATCH_1, Collections.emptyList()).isPassing());

        assertTrue(rule.filter(OFFER_NO_ATTRS, POD_WITH_TASK_ATTR_MATCH_2, Collections.emptyList()).isPassing());
        assertTrue(rule.filter(OFFER_ATTR_MATCH_1, POD_WITH_TASK_ATTR_MATCH_2, Collections.emptyList()).isPassing());
        assertTrue(rule.filter(OFFER_ATTR_MATCH_2, POD_WITH_TASK_ATTR_MATCH_2, Collections.emptyList()).isPassing());
        assertTrue(rule.filter(OFFER_ATTR_MISMATCH, POD_WITH_TASK_ATTR_MATCH_2, Collections.emptyList()).isPassing());

        assertTrue(rule.filter(OFFER_NO_ATTRS, POD_WITH_TASK_ATTR_MISMATCH, Collections.emptyList()).isPassing());
        assertTrue(rule.filter(OFFER_ATTR_MATCH_1, POD_WITH_TASK_ATTR_MISMATCH, Collections.emptyList()).isPassing());
        assertTrue(rule.filter(OFFER_ATTR_MATCH_2, POD_WITH_TASK_ATTR_MISMATCH, Collections.emptyList()).isPassing());
        assertTrue(rule.filter(OFFER_ATTR_MISMATCH, POD_WITH_TASK_ATTR_MISMATCH, Collections.emptyList()).isPassing());
    }

    @Test
    public void testLimitTwoDuplicates() {
        PlacementRule rule = new MaxPerAttributeRule(2, ATTR_MATCHER);

        Collection<TaskInfo> tasks = Arrays.asList(
                TASK_NO_ATTRS,
                TASK_ATTR_MATCH_1, TASK_ATTR_MATCH_1,
                TASK_ATTR_MATCH_2,
                TASK_ATTR_MISMATCH);

        assertTrue(rule.filter(OFFER_NO_ATTRS, POD, tasks).isPassing());
        assertFalse(rule.filter(OFFER_ATTR_MATCH_1, POD, tasks).isPassing());
        assertTrue(rule.filter(OFFER_ATTR_MATCH_2, POD, tasks).isPassing());
        assertTrue(rule.filter(OFFER_ATTR_MISMATCH, POD, tasks).isPassing());

        tasks = Arrays.asList(
                TASK_NO_ATTRS, TASK_NO_ATTRS,
                TASK_ATTR_MATCH_1,
                TASK_ATTR_MATCH_2, TASK_ATTR_MATCH_2,
                TASK_ATTR_MISMATCH);

        assertTrue(rule.filter(OFFER_NO_ATTRS, POD, tasks).isPassing());
        assertTrue(rule.filter(OFFER_ATTR_MATCH_1, POD, tasks).isPassing());
        assertFalse(rule.filter(OFFER_ATTR_MATCH_2, POD, tasks).isPassing());
        assertTrue(rule.filter(OFFER_ATTR_MISMATCH, POD, tasks).isPassing());

        tasks = Arrays.asList(
                TASK_NO_ATTRS,
                TASK_ATTR_MATCH_1, TASK_ATTR_MATCH_1,
                TASK_ATTR_MATCH_2, TASK_ATTR_MATCH_2,
                TASK_ATTR_MISMATCH, TASK_ATTR_MISMATCH, TASK_ATTR_MISMATCH);

        assertTrue(rule.filter(OFFER_NO_ATTRS, POD, tasks).isPassing());
        assertFalse(rule.filter(OFFER_ATTR_MATCH_1, POD, tasks).isPassing());
        assertFalse(rule.filter(OFFER_ATTR_MATCH_2, POD, tasks).isPassing());
        assertTrue(rule.filter(OFFER_ATTR_MISMATCH, POD, tasks).isPassing());
    }

    @Test
    public void testLimitTwoDuplicatesWithSamePresent() {
        PlacementRule rule = new MaxPerAttributeRule(2, ATTR_MATCHER);

        Collection<TaskInfo> tasks = Arrays.asList(
                TASK_NO_ATTRS,
                TASK_ATTR_MATCH_1, TASK_ATTR_MATCH_1,
                TASK_ATTR_MATCH_2,
                TASK_ATTR_MISMATCH);

        assertTrue(rule.filter(OFFER_NO_ATTRS, POD_WITH_TASK_NO_ATTRS, tasks).isPassing());
        assertFalse(rule.filter(OFFER_ATTR_MATCH_1, POD_WITH_TASK_NO_ATTRS, tasks).isPassing());
        assertTrue(rule.filter(OFFER_ATTR_MATCH_2, POD_WITH_TASK_NO_ATTRS, tasks).isPassing());
        assertTrue(rule.filter(OFFER_ATTR_MISMATCH, POD_WITH_TASK_NO_ATTRS, tasks).isPassing());

        assertTrue(rule.filter(OFFER_NO_ATTRS, POD_WITH_TASK_ATTR_MATCH_1, tasks).isPassing());
        assertTrue(rule.filter(OFFER_ATTR_MATCH_1, POD_WITH_TASK_ATTR_MATCH_1, tasks).isPassing());
        assertTrue(rule.filter(OFFER_ATTR_MATCH_2, POD_WITH_TASK_ATTR_MATCH_1, tasks).isPassing());
        assertTrue(rule.filter(OFFER_ATTR_MISMATCH, POD_WITH_TASK_ATTR_MATCH_1, tasks).isPassing());

        assertTrue(rule.filter(OFFER_NO_ATTRS, POD_WITH_TASK_ATTR_MATCH_2, tasks).isPassing());
        assertFalse(rule.filter(OFFER_ATTR_MATCH_1, POD_WITH_TASK_ATTR_MATCH_2, tasks).isPassing());
        assertTrue(rule.filter(OFFER_ATTR_MATCH_2, POD_WITH_TASK_ATTR_MATCH_2, tasks).isPassing());
        assertTrue(rule.filter(OFFER_ATTR_MISMATCH, POD_WITH_TASK_ATTR_MATCH_2, tasks).isPassing());

        assertTrue(rule.filter(OFFER_NO_ATTRS, POD_WITH_TASK_ATTR_MISMATCH, tasks).isPassing());
        assertFalse(rule.filter(OFFER_ATTR_MATCH_1, POD_WITH_TASK_ATTR_MISMATCH, tasks).isPassing());
        assertTrue(rule.filter(OFFER_ATTR_MATCH_2, POD_WITH_TASK_ATTR_MISMATCH, tasks).isPassing());
        assertTrue(rule.filter(OFFER_ATTR_MISMATCH, POD_WITH_TASK_ATTR_MISMATCH, tasks).isPassing());

        tasks = Arrays.asList(
                TASK_NO_ATTRS, TASK_NO_ATTRS,
                TASK_ATTR_MATCH_1,
                TASK_ATTR_MATCH_2, TASK_ATTR_MATCH_2,
                TASK_ATTR_MISMATCH);

        assertTrue(rule.filter(OFFER_NO_ATTRS, POD_WITH_TASK_NO_ATTRS, tasks).isPassing());
        assertTrue(rule.filter(OFFER_ATTR_MATCH_1, POD_WITH_TASK_NO_ATTRS, tasks).isPassing());
        assertFalse(rule.filter(OFFER_ATTR_MATCH_2, POD_WITH_TASK_NO_ATTRS, tasks).isPassing());
        assertTrue(rule.filter(OFFER_ATTR_MISMATCH, POD_WITH_TASK_NO_ATTRS, tasks).isPassing());

        assertTrue(rule.filter(OFFER_NO_ATTRS, POD_WITH_TASK_ATTR_MATCH_1, tasks).isPassing());
        assertTrue(rule.filter(OFFER_ATTR_MATCH_1, POD_WITH_TASK_ATTR_MATCH_1, tasks).isPassing());
        assertFalse(rule.filter(OFFER_ATTR_MATCH_2, POD_WITH_TASK_ATTR_MATCH_1, tasks).isPassing());
        assertTrue(rule.filter(OFFER_ATTR_MISMATCH, POD_WITH_TASK_ATTR_MATCH_1, tasks).isPassing());

        assertTrue(rule.filter(OFFER_NO_ATTRS, POD_WITH_TASK_ATTR_MATCH_2, tasks).isPassing());
        assertTrue(rule.filter(OFFER_ATTR_MATCH_1, POD_WITH_TASK_ATTR_MATCH_2, tasks).isPassing());
        assertTrue(rule.filter(OFFER_ATTR_MATCH_2, POD_WITH_TASK_ATTR_MATCH_2, tasks).isPassing());
        assertTrue(rule.filter(OFFER_ATTR_MISMATCH, POD_WITH_TASK_ATTR_MATCH_2, tasks).isPassing());

        assertTrue(rule.filter(OFFER_NO_ATTRS, POD_WITH_TASK_ATTR_MISMATCH, tasks).isPassing());
        assertTrue(rule.filter(OFFER_ATTR_MATCH_1, POD_WITH_TASK_ATTR_MISMATCH, tasks).isPassing());
        assertFalse(rule.filter(OFFER_ATTR_MATCH_2, POD_WITH_TASK_ATTR_MISMATCH, tasks).isPassing());
        assertTrue(rule.filter(OFFER_ATTR_MISMATCH, POD_WITH_TASK_ATTR_MISMATCH, tasks).isPassing());

        tasks = Arrays.asList(
                TASK_NO_ATTRS,
                TASK_ATTR_MATCH_1, TASK_ATTR_MATCH_1,
                TASK_ATTR_MATCH_2, TASK_ATTR_MATCH_2,
                TASK_ATTR_MISMATCH, TASK_ATTR_MISMATCH, TASK_ATTR_MISMATCH);

        assertTrue(rule.filter(OFFER_NO_ATTRS, POD_WITH_TASK_NO_ATTRS, tasks).isPassing());
        assertFalse(rule.filter(OFFER_ATTR_MATCH_1, POD_WITH_TASK_NO_ATTRS, tasks).isPassing());
        assertFalse(rule.filter(OFFER_ATTR_MATCH_2, POD_WITH_TASK_NO_ATTRS, tasks).isPassing());
        assertTrue(rule.filter(OFFER_ATTR_MISMATCH, POD_WITH_TASK_NO_ATTRS, tasks).isPassing());

        assertTrue(rule.filter(OFFER_NO_ATTRS, POD_WITH_TASK_ATTR_MATCH_1, tasks).isPassing());
        assertTrue(rule.filter(OFFER_ATTR_MATCH_1, POD_WITH_TASK_ATTR_MATCH_1, tasks).isPassing());
        assertFalse(rule.filter(OFFER_ATTR_MATCH_2, POD_WITH_TASK_ATTR_MATCH_1, tasks).isPassing());
        assertTrue(rule.filter(OFFER_ATTR_MISMATCH, POD_WITH_TASK_ATTR_MATCH_1, tasks).isPassing());

        assertTrue(rule.filter(OFFER_NO_ATTRS, POD_WITH_TASK_ATTR_MATCH_2, tasks).isPassing());
        assertFalse(rule.filter(OFFER_ATTR_MATCH_1, POD_WITH_TASK_ATTR_MATCH_2, tasks).isPassing());
        assertTrue(rule.filter(OFFER_ATTR_MATCH_2, POD_WITH_TASK_ATTR_MATCH_2, tasks).isPassing());
        assertTrue(rule.filter(OFFER_ATTR_MISMATCH, POD_WITH_TASK_ATTR_MATCH_2, tasks).isPassing());

        assertTrue(rule.filter(OFFER_NO_ATTRS, POD_WITH_TASK_ATTR_MISMATCH, tasks).isPassing());
        assertFalse(rule.filter(OFFER_ATTR_MATCH_1, POD_WITH_TASK_ATTR_MISMATCH, tasks).isPassing());
        assertFalse(rule.filter(OFFER_ATTR_MATCH_2, POD_WITH_TASK_ATTR_MISMATCH, tasks).isPassing());
        assertTrue(rule.filter(OFFER_ATTR_MISMATCH, POD_WITH_TASK_ATTR_MISMATCH, tasks).isPassing());
    }

    @Test
    public void testSerializeDeserialize() throws IOException {
        PlacementRule rule = new MaxPerAttributeRule(2, ATTR_MATCHER);
        assertEquals(rule, SerializationUtils.fromString(
                SerializationUtils.toJsonString(rule), PlacementRule.class, TestPlacementUtils.OBJECT_MAPPER));

        rule = new MaxPerAttributeRule(0, ATTR_MATCHER, RegexMatcher.create("hello"));
        assertEquals(rule, SerializationUtils.fromString(
                SerializationUtils.toJsonString(rule), PlacementRule.class, TestPlacementUtils.OBJECT_MAPPER));
    }

    @Test
    public void testDeserializeNoOptionalTaskFilter() throws IOException {
        String str = "{ '@type': 'MaxPerAttributeRule', 'max': 2, 'matcher': { '@type': 'AnyMatcher' } }".replace('\'', '"');
        SerializationUtils.fromString(str,
                PlacementRule.class, TestPlacementUtils.OBJECT_MAPPER);
    }

    @Test
    public void testDeserializeAllParams() throws IOException {
        String str = "{ '@type': 'MaxPerAttributeRule', 'max': 2, 'matcher': { '@type': 'AnyMatcher' }, 'task-filter': { '@type': 'ExactMatcher', 'string': 'foo' } }".replace('\'', '"');
        SerializationUtils.fromString(str,
                PlacementRule.class, TestPlacementUtils.OBJECT_MAPPER);
    }

}
