package com.mesosphere.sdk.offer.constrain;

import com.mesosphere.sdk.testutils.OfferRequirementTestUtils;
import com.mesosphere.sdk.testutils.OfferTestUtils;
import org.apache.mesos.Protos.Attribute;
import org.apache.mesos.Protos.Offer;
import org.apache.mesos.Protos.TaskInfo;
import org.apache.mesos.Protos.Value;
import com.mesosphere.sdk.config.SerializationUtils;
import com.mesosphere.sdk.offer.OfferRequirement;
import com.mesosphere.sdk.offer.CommonTaskUtils;
import com.mesosphere.sdk.testutils.TaskTestUtils;
import org.junit.Test;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;

import static org.junit.Assert.assertEquals;

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
    private static final TaskInfo TASK_EMPTY_ATTRS = getTask("empty__000", OFFER_NO_ATTRS);
    private static final TaskInfo TASK_ATTR_MATCH_1 = getTask("match-1__abc", OFFER_ATTR_MATCH_1);
    private static final TaskInfo TASK_ATTR_MATCH_2 = getTask("match-2__def", OFFER_ATTR_MATCH_2);
    private static final TaskInfo TASK_ATTR_MISMATCH = getTask("mismatch__ghi", OFFER_ATTR_MISMATCH);
    private static final Collection<TaskInfo> TASKS = Arrays.asList(
            TASK_NO_ATTRS, TASK_EMPTY_ATTRS, TASK_ATTR_MATCH_1, TASK_ATTR_MATCH_2, TASK_ATTR_MISMATCH);

    private static final OfferRequirement REQ = OfferRequirementTestUtils.getOfferRequirement();
    private static final OfferRequirement REQ_WITH_TASK_NO_ATTRS = getOfferReq(TASK_NO_ATTRS);
    private static final OfferRequirement REQ_WITH_TASK_EMPTY_ATTRS = getOfferReq(TASK_EMPTY_ATTRS);
    private static final OfferRequirement REQ_WITH_TASK_ATTR_MATCH_1 = getOfferReq(TASK_ATTR_MATCH_1);
    private static final OfferRequirement REQ_WITH_TASK_ATTR_MATCH_2 = getOfferReq(TASK_ATTR_MATCH_2);
    private static final OfferRequirement REQ_WITH_TASK_ATTR_MISMATCH = getOfferReq(TASK_ATTR_MISMATCH);

    @Test
    public void testLimitZero() {
        PlacementRule rule = new MaxPerAttributeRule(0, ATTR_MATCHER);

        assertEquals(rule.toString(), 2, rule.filter(OFFER_NO_ATTRS, REQ, TASKS).getResourcesCount());
        assertEquals(rule.toString(), 0, rule.filter(OFFER_ATTR_MATCH_1, REQ, TASKS).getResourcesCount());
        assertEquals(rule.toString(), 0, rule.filter(OFFER_ATTR_MATCH_2, REQ, TASKS).getResourcesCount());
        assertEquals(rule.toString(), 2, rule.filter(OFFER_ATTR_MISMATCH, REQ, TASKS).getResourcesCount());

        assertEquals(rule.toString(), 2, rule.filter(OFFER_NO_ATTRS, REQ, Collections.emptyList()).getResourcesCount());
        assertEquals(rule.toString(), 2, rule.filter(OFFER_ATTR_MATCH_1, REQ, Collections.emptyList()).getResourcesCount());
        assertEquals(rule.toString(), 2, rule.filter(OFFER_ATTR_MATCH_2, REQ, Collections.emptyList()).getResourcesCount());
        assertEquals(rule.toString(), 2, rule.filter(OFFER_ATTR_MISMATCH, REQ, Collections.emptyList()).getResourcesCount());
    }

    @Test
    public void testLimitZeroWithSamePresent() {
        PlacementRule rule = new MaxPerAttributeRule(0, ATTR_MATCHER);

        assertEquals(rule.toString(), 2, rule.filter(OFFER_NO_ATTRS, REQ_WITH_TASK_NO_ATTRS, TASKS).getResourcesCount());
        assertEquals(rule.toString(), 0, rule.filter(OFFER_ATTR_MATCH_1, REQ_WITH_TASK_NO_ATTRS, TASKS).getResourcesCount());
        assertEquals(rule.toString(), 0, rule.filter(OFFER_ATTR_MATCH_2, REQ_WITH_TASK_NO_ATTRS, TASKS).getResourcesCount());
        assertEquals(rule.toString(), 2, rule.filter(OFFER_ATTR_MISMATCH, REQ_WITH_TASK_NO_ATTRS, TASKS).getResourcesCount());

        assertEquals(rule.toString(), 2, rule.filter(OFFER_NO_ATTRS, REQ_WITH_TASK_EMPTY_ATTRS, TASKS).getResourcesCount());
        assertEquals(rule.toString(), 0, rule.filter(OFFER_ATTR_MATCH_1, REQ_WITH_TASK_EMPTY_ATTRS, TASKS).getResourcesCount());
        assertEquals(rule.toString(), 0, rule.filter(OFFER_ATTR_MATCH_2, REQ_WITH_TASK_EMPTY_ATTRS, TASKS).getResourcesCount());
        assertEquals(rule.toString(), 2, rule.filter(OFFER_ATTR_MISMATCH, REQ_WITH_TASK_EMPTY_ATTRS, TASKS).getResourcesCount());

        assertEquals(rule.toString(), 2, rule.filter(OFFER_NO_ATTRS, REQ_WITH_TASK_ATTR_MATCH_1, TASKS).getResourcesCount());
        assertEquals(rule.toString(), 2, rule.filter(OFFER_ATTR_MATCH_1, REQ_WITH_TASK_ATTR_MATCH_1, TASKS).getResourcesCount());
        assertEquals(rule.toString(), 0, rule.filter(OFFER_ATTR_MATCH_2, REQ_WITH_TASK_ATTR_MATCH_1, TASKS).getResourcesCount());
        assertEquals(rule.toString(), 2, rule.filter(OFFER_ATTR_MISMATCH, REQ_WITH_TASK_ATTR_MATCH_1, TASKS).getResourcesCount());

        assertEquals(rule.toString(), 2, rule.filter(OFFER_NO_ATTRS, REQ_WITH_TASK_ATTR_MATCH_2, TASKS).getResourcesCount());
        assertEquals(rule.toString(), 0, rule.filter(OFFER_ATTR_MATCH_1, REQ_WITH_TASK_ATTR_MATCH_2, TASKS).getResourcesCount());
        assertEquals(rule.toString(), 2, rule.filter(OFFER_ATTR_MATCH_2, REQ_WITH_TASK_ATTR_MATCH_2, TASKS).getResourcesCount());
        assertEquals(rule.toString(), 2, rule.filter(OFFER_ATTR_MISMATCH, REQ_WITH_TASK_ATTR_MATCH_2, TASKS).getResourcesCount());

        assertEquals(rule.toString(), 2, rule.filter(OFFER_NO_ATTRS, REQ_WITH_TASK_ATTR_MISMATCH, TASKS).getResourcesCount());
        assertEquals(rule.toString(), 0, rule.filter(OFFER_ATTR_MATCH_1, REQ_WITH_TASK_ATTR_MISMATCH, TASKS).getResourcesCount());
        assertEquals(rule.toString(), 0, rule.filter(OFFER_ATTR_MATCH_2, REQ_WITH_TASK_ATTR_MISMATCH, TASKS).getResourcesCount());
        assertEquals(rule.toString(), 2, rule.filter(OFFER_ATTR_MISMATCH, REQ_WITH_TASK_ATTR_MISMATCH, TASKS).getResourcesCount());

        assertEquals(rule.toString(), 2, rule.filter(OFFER_NO_ATTRS, REQ_WITH_TASK_NO_ATTRS, Collections.emptyList()).getResourcesCount());
        assertEquals(rule.toString(), 2, rule.filter(OFFER_ATTR_MATCH_1, REQ_WITH_TASK_NO_ATTRS, Collections.emptyList()).getResourcesCount());
        assertEquals(rule.toString(), 2, rule.filter(OFFER_ATTR_MATCH_2, REQ_WITH_TASK_NO_ATTRS, Collections.emptyList()).getResourcesCount());
        assertEquals(rule.toString(), 2, rule.filter(OFFER_ATTR_MISMATCH, REQ_WITH_TASK_NO_ATTRS, Collections.emptyList()).getResourcesCount());

        assertEquals(rule.toString(), 2, rule.filter(OFFER_NO_ATTRS, REQ_WITH_TASK_EMPTY_ATTRS, Collections.emptyList()).getResourcesCount());
        assertEquals(rule.toString(), 2, rule.filter(OFFER_ATTR_MATCH_1, REQ_WITH_TASK_EMPTY_ATTRS, Collections.emptyList()).getResourcesCount());
        assertEquals(rule.toString(), 2, rule.filter(OFFER_ATTR_MATCH_2, REQ_WITH_TASK_EMPTY_ATTRS, Collections.emptyList()).getResourcesCount());
        assertEquals(rule.toString(), 2, rule.filter(OFFER_ATTR_MISMATCH, REQ_WITH_TASK_EMPTY_ATTRS, Collections.emptyList()).getResourcesCount());

        assertEquals(rule.toString(), 2, rule.filter(OFFER_NO_ATTRS, REQ_WITH_TASK_ATTR_MATCH_1, Collections.emptyList()).getResourcesCount());
        assertEquals(rule.toString(), 2, rule.filter(OFFER_ATTR_MATCH_1, REQ_WITH_TASK_ATTR_MATCH_1, Collections.emptyList()).getResourcesCount());
        assertEquals(rule.toString(), 2, rule.filter(OFFER_ATTR_MATCH_2, REQ_WITH_TASK_ATTR_MATCH_1, Collections.emptyList()).getResourcesCount());
        assertEquals(rule.toString(), 2, rule.filter(OFFER_ATTR_MISMATCH, REQ_WITH_TASK_ATTR_MATCH_1, Collections.emptyList()).getResourcesCount());

        assertEquals(rule.toString(), 2, rule.filter(OFFER_NO_ATTRS, REQ_WITH_TASK_ATTR_MATCH_2, Collections.emptyList()).getResourcesCount());
        assertEquals(rule.toString(), 2, rule.filter(OFFER_ATTR_MATCH_1, REQ_WITH_TASK_ATTR_MATCH_2, Collections.emptyList()).getResourcesCount());
        assertEquals(rule.toString(), 2, rule.filter(OFFER_ATTR_MATCH_2, REQ_WITH_TASK_ATTR_MATCH_2, Collections.emptyList()).getResourcesCount());
        assertEquals(rule.toString(), 2, rule.filter(OFFER_ATTR_MISMATCH, REQ_WITH_TASK_ATTR_MATCH_2, Collections.emptyList()).getResourcesCount());

        assertEquals(rule.toString(), 2, rule.filter(OFFER_NO_ATTRS, REQ_WITH_TASK_ATTR_MISMATCH, Collections.emptyList()).getResourcesCount());
        assertEquals(rule.toString(), 2, rule.filter(OFFER_ATTR_MATCH_1, REQ_WITH_TASK_ATTR_MISMATCH, Collections.emptyList()).getResourcesCount());
        assertEquals(rule.toString(), 2, rule.filter(OFFER_ATTR_MATCH_2, REQ_WITH_TASK_ATTR_MISMATCH, Collections.emptyList()).getResourcesCount());
        assertEquals(rule.toString(), 2, rule.filter(OFFER_ATTR_MISMATCH, REQ_WITH_TASK_ATTR_MISMATCH, Collections.emptyList()).getResourcesCount());
    }

    @Test
    public void testLimitOne() {
        PlacementRule rule = new MaxPerAttributeRule(1, ATTR_MATCHER);

        assertEquals(rule.toString(), 2, rule.filter(OFFER_NO_ATTRS, REQ, TASKS).getResourcesCount());
        assertEquals(rule.toString(), 0, rule.filter(OFFER_ATTR_MATCH_1, REQ, TASKS).getResourcesCount());
        assertEquals(rule.toString(), 0, rule.filter(OFFER_ATTR_MATCH_2, REQ, TASKS).getResourcesCount());
        assertEquals(rule.toString(), 2, rule.filter(OFFER_ATTR_MISMATCH, REQ, TASKS).getResourcesCount());

        assertEquals(rule.toString(), 2, rule.filter(OFFER_NO_ATTRS, REQ, Collections.emptyList()).getResourcesCount());
        assertEquals(rule.toString(), 2, rule.filter(OFFER_ATTR_MATCH_1, REQ, Collections.emptyList()).getResourcesCount());
        assertEquals(rule.toString(), 2, rule.filter(OFFER_ATTR_MATCH_2, REQ, Collections.emptyList()).getResourcesCount());
        assertEquals(rule.toString(), 2, rule.filter(OFFER_ATTR_MISMATCH, REQ, Collections.emptyList()).getResourcesCount());
    }

    @Test
    public void testLimitOneWithSamePresent() {
        PlacementRule rule = new MaxPerAttributeRule(1, ATTR_MATCHER);

        assertEquals(rule.toString(), 2, rule.filter(OFFER_NO_ATTRS, REQ_WITH_TASK_NO_ATTRS, TASKS).getResourcesCount());
        assertEquals(rule.toString(), 0, rule.filter(OFFER_ATTR_MATCH_1, REQ_WITH_TASK_NO_ATTRS, TASKS).getResourcesCount());
        assertEquals(rule.toString(), 0, rule.filter(OFFER_ATTR_MATCH_2, REQ_WITH_TASK_NO_ATTRS, TASKS).getResourcesCount());
        assertEquals(rule.toString(), 2, rule.filter(OFFER_ATTR_MISMATCH, REQ_WITH_TASK_NO_ATTRS, TASKS).getResourcesCount());

        assertEquals(rule.toString(), 2, rule.filter(OFFER_NO_ATTRS, REQ_WITH_TASK_EMPTY_ATTRS, TASKS).getResourcesCount());
        assertEquals(rule.toString(), 0, rule.filter(OFFER_ATTR_MATCH_1, REQ_WITH_TASK_EMPTY_ATTRS, TASKS).getResourcesCount());
        assertEquals(rule.toString(), 0, rule.filter(OFFER_ATTR_MATCH_2, REQ_WITH_TASK_EMPTY_ATTRS, TASKS).getResourcesCount());
        assertEquals(rule.toString(), 2, rule.filter(OFFER_ATTR_MISMATCH, REQ_WITH_TASK_EMPTY_ATTRS, TASKS).getResourcesCount());

        assertEquals(rule.toString(), 2, rule.filter(OFFER_NO_ATTRS, REQ_WITH_TASK_ATTR_MATCH_1, TASKS).getResourcesCount());
        assertEquals(rule.toString(), 2, rule.filter(OFFER_ATTR_MATCH_1, REQ_WITH_TASK_ATTR_MATCH_1, TASKS).getResourcesCount());
        assertEquals(rule.toString(), 0, rule.filter(OFFER_ATTR_MATCH_2, REQ_WITH_TASK_ATTR_MATCH_1, TASKS).getResourcesCount());
        assertEquals(rule.toString(), 2, rule.filter(OFFER_ATTR_MISMATCH, REQ_WITH_TASK_ATTR_MATCH_1, TASKS).getResourcesCount());

        assertEquals(rule.toString(), 2, rule.filter(OFFER_NO_ATTRS, REQ_WITH_TASK_ATTR_MATCH_2, TASKS).getResourcesCount());
        assertEquals(rule.toString(), 0, rule.filter(OFFER_ATTR_MATCH_1, REQ_WITH_TASK_ATTR_MATCH_2, TASKS).getResourcesCount());
        assertEquals(rule.toString(), 2, rule.filter(OFFER_ATTR_MATCH_2, REQ_WITH_TASK_ATTR_MATCH_2, TASKS).getResourcesCount());
        assertEquals(rule.toString(), 2, rule.filter(OFFER_ATTR_MISMATCH, REQ_WITH_TASK_ATTR_MATCH_2, TASKS).getResourcesCount());

        assertEquals(rule.toString(), 2, rule.filter(OFFER_NO_ATTRS, REQ_WITH_TASK_ATTR_MISMATCH, TASKS).getResourcesCount());
        assertEquals(rule.toString(), 0, rule.filter(OFFER_ATTR_MATCH_1, REQ_WITH_TASK_ATTR_MISMATCH, TASKS).getResourcesCount());
        assertEquals(rule.toString(), 0, rule.filter(OFFER_ATTR_MATCH_2, REQ_WITH_TASK_ATTR_MISMATCH, TASKS).getResourcesCount());
        assertEquals(rule.toString(), 2, rule.filter(OFFER_ATTR_MISMATCH, REQ_WITH_TASK_ATTR_MISMATCH, TASKS).getResourcesCount());

        assertEquals(rule.toString(), 2, rule.filter(OFFER_NO_ATTRS, REQ_WITH_TASK_NO_ATTRS, Collections.emptyList()).getResourcesCount());
        assertEquals(rule.toString(), 2, rule.filter(OFFER_ATTR_MATCH_1, REQ_WITH_TASK_NO_ATTRS, Collections.emptyList()).getResourcesCount());
        assertEquals(rule.toString(), 2, rule.filter(OFFER_ATTR_MATCH_2, REQ_WITH_TASK_NO_ATTRS, Collections.emptyList()).getResourcesCount());
        assertEquals(rule.toString(), 2, rule.filter(OFFER_ATTR_MISMATCH, REQ_WITH_TASK_NO_ATTRS, Collections.emptyList()).getResourcesCount());

        assertEquals(rule.toString(), 2, rule.filter(OFFER_NO_ATTRS, REQ_WITH_TASK_EMPTY_ATTRS, Collections.emptyList()).getResourcesCount());
        assertEquals(rule.toString(), 2, rule.filter(OFFER_ATTR_MATCH_1, REQ_WITH_TASK_EMPTY_ATTRS, Collections.emptyList()).getResourcesCount());
        assertEquals(rule.toString(), 2, rule.filter(OFFER_ATTR_MATCH_2, REQ_WITH_TASK_EMPTY_ATTRS, Collections.emptyList()).getResourcesCount());
        assertEquals(rule.toString(), 2, rule.filter(OFFER_ATTR_MISMATCH, REQ_WITH_TASK_EMPTY_ATTRS, Collections.emptyList()).getResourcesCount());

        assertEquals(rule.toString(), 2, rule.filter(OFFER_NO_ATTRS, REQ_WITH_TASK_ATTR_MATCH_1, Collections.emptyList()).getResourcesCount());
        assertEquals(rule.toString(), 2, rule.filter(OFFER_ATTR_MATCH_1, REQ_WITH_TASK_ATTR_MATCH_1, Collections.emptyList()).getResourcesCount());
        assertEquals(rule.toString(), 2, rule.filter(OFFER_ATTR_MATCH_2, REQ_WITH_TASK_ATTR_MATCH_1, Collections.emptyList()).getResourcesCount());
        assertEquals(rule.toString(), 2, rule.filter(OFFER_ATTR_MISMATCH, REQ_WITH_TASK_ATTR_MATCH_1, Collections.emptyList()).getResourcesCount());

        assertEquals(rule.toString(), 2, rule.filter(OFFER_NO_ATTRS, REQ_WITH_TASK_ATTR_MATCH_2, Collections.emptyList()).getResourcesCount());
        assertEquals(rule.toString(), 2, rule.filter(OFFER_ATTR_MATCH_1, REQ_WITH_TASK_ATTR_MATCH_2, Collections.emptyList()).getResourcesCount());
        assertEquals(rule.toString(), 2, rule.filter(OFFER_ATTR_MATCH_2, REQ_WITH_TASK_ATTR_MATCH_2, Collections.emptyList()).getResourcesCount());
        assertEquals(rule.toString(), 2, rule.filter(OFFER_ATTR_MISMATCH, REQ_WITH_TASK_ATTR_MATCH_2, Collections.emptyList()).getResourcesCount());

        assertEquals(rule.toString(), 2, rule.filter(OFFER_NO_ATTRS, REQ_WITH_TASK_ATTR_MISMATCH, Collections.emptyList()).getResourcesCount());
        assertEquals(rule.toString(), 2, rule.filter(OFFER_ATTR_MATCH_1, REQ_WITH_TASK_ATTR_MISMATCH, Collections.emptyList()).getResourcesCount());
        assertEquals(rule.toString(), 2, rule.filter(OFFER_ATTR_MATCH_2, REQ_WITH_TASK_ATTR_MISMATCH, Collections.emptyList()).getResourcesCount());
        assertEquals(rule.toString(), 2, rule.filter(OFFER_ATTR_MISMATCH, REQ_WITH_TASK_ATTR_MISMATCH, Collections.emptyList()).getResourcesCount());
    }

    @Test
    public void testLimitOneWithTaskFilter() {
        PlacementRule rule = new MaxPerAttributeRule(1, ATTR_MATCHER, RegexMatcher.create("match-.*"));

        assertEquals(rule.toString(), 2, rule.filter(OFFER_NO_ATTRS, REQ, TASKS).getResourcesCount());
        assertEquals(rule.toString(), 0, rule.filter(OFFER_ATTR_MATCH_1, REQ, TASKS).getResourcesCount());
        assertEquals(rule.toString(), 0, rule.filter(OFFER_ATTR_MATCH_2, REQ, TASKS).getResourcesCount());
        assertEquals(rule.toString(), 2, rule.filter(OFFER_ATTR_MISMATCH, REQ, TASKS).getResourcesCount());

        rule = new MaxPerAttributeRule(1, ATTR_MATCHER, ExactMatcher.create("match-1"));

        assertEquals(rule.toString(), 2, rule.filter(OFFER_NO_ATTRS, REQ, TASKS).getResourcesCount());
        assertEquals(rule.toString(), 0, rule.filter(OFFER_ATTR_MATCH_1, REQ, TASKS).getResourcesCount());
        assertEquals(rule.toString(), 2, rule.filter(OFFER_ATTR_MATCH_2, REQ, TASKS).getResourcesCount());
        assertEquals(rule.toString(), 2, rule.filter(OFFER_ATTR_MISMATCH, REQ, TASKS).getResourcesCount());

        rule = new MaxPerAttributeRule(1, ATTR_MATCHER, ExactMatcher.create("match-2"));

        assertEquals(rule.toString(), 2, rule.filter(OFFER_NO_ATTRS, REQ, TASKS).getResourcesCount());
        assertEquals(rule.toString(), 2, rule.filter(OFFER_ATTR_MATCH_1, REQ, TASKS).getResourcesCount());
        assertEquals(rule.toString(), 0, rule.filter(OFFER_ATTR_MATCH_2, REQ, TASKS).getResourcesCount());
        assertEquals(rule.toString(), 2, rule.filter(OFFER_ATTR_MISMATCH, REQ, TASKS).getResourcesCount());

        rule = new MaxPerAttributeRule(1, ATTR_MATCHER, ExactMatcher.create("mismatch"));

        assertEquals(rule.toString(), 2, rule.filter(OFFER_NO_ATTRS, REQ, TASKS).getResourcesCount());
        assertEquals(rule.toString(), 2, rule.filter(OFFER_ATTR_MATCH_1, REQ, TASKS).getResourcesCount());
        assertEquals(rule.toString(), 2, rule.filter(OFFER_ATTR_MATCH_2, REQ, TASKS).getResourcesCount());
        assertEquals(rule.toString(), 2, rule.filter(OFFER_ATTR_MISMATCH, REQ, TASKS).getResourcesCount());
    }

    @Test
    public void testLimitTwo() {
        PlacementRule rule = new MaxPerAttributeRule(2, ATTR_MATCHER);

        assertEquals(rule.toString(), 2, rule.filter(OFFER_NO_ATTRS, REQ, TASKS).getResourcesCount());
        assertEquals(rule.toString(), 2, rule.filter(OFFER_ATTR_MATCH_1, REQ, TASKS).getResourcesCount());
        assertEquals(rule.toString(), 2, rule.filter(OFFER_ATTR_MATCH_2, REQ, TASKS).getResourcesCount());
        assertEquals(rule.toString(), 2, rule.filter(OFFER_ATTR_MISMATCH, REQ, TASKS).getResourcesCount());

        assertEquals(rule.toString(), 2, rule.filter(OFFER_NO_ATTRS, REQ, Collections.emptyList()).getResourcesCount());
        assertEquals(rule.toString(), 2, rule.filter(OFFER_ATTR_MATCH_1, REQ, Collections.emptyList()).getResourcesCount());
        assertEquals(rule.toString(), 2, rule.filter(OFFER_ATTR_MATCH_2, REQ, Collections.emptyList()).getResourcesCount());
        assertEquals(rule.toString(), 2, rule.filter(OFFER_ATTR_MISMATCH, REQ, Collections.emptyList()).getResourcesCount());
    }

    @Test
    public void testLimitTwoWithSamePresent() {
        PlacementRule rule = new MaxPerAttributeRule(2, ATTR_MATCHER);

        assertEquals(rule.toString(), 2, rule.filter(OFFER_NO_ATTRS, REQ_WITH_TASK_NO_ATTRS, TASKS).getResourcesCount());
        assertEquals(rule.toString(), 2, rule.filter(OFFER_ATTR_MATCH_1, REQ_WITH_TASK_NO_ATTRS, TASKS).getResourcesCount());
        assertEquals(rule.toString(), 2, rule.filter(OFFER_ATTR_MATCH_2, REQ_WITH_TASK_NO_ATTRS, TASKS).getResourcesCount());
        assertEquals(rule.toString(), 2, rule.filter(OFFER_ATTR_MISMATCH, REQ_WITH_TASK_NO_ATTRS, TASKS).getResourcesCount());

        assertEquals(rule.toString(), 2, rule.filter(OFFER_NO_ATTRS, REQ_WITH_TASK_EMPTY_ATTRS, TASKS).getResourcesCount());
        assertEquals(rule.toString(), 2, rule.filter(OFFER_ATTR_MATCH_1, REQ_WITH_TASK_EMPTY_ATTRS, TASKS).getResourcesCount());
        assertEquals(rule.toString(), 2, rule.filter(OFFER_ATTR_MATCH_2, REQ_WITH_TASK_EMPTY_ATTRS, TASKS).getResourcesCount());
        assertEquals(rule.toString(), 2, rule.filter(OFFER_ATTR_MISMATCH, REQ_WITH_TASK_EMPTY_ATTRS, TASKS).getResourcesCount());

        assertEquals(rule.toString(), 2, rule.filter(OFFER_NO_ATTRS, REQ_WITH_TASK_ATTR_MATCH_1, TASKS).getResourcesCount());
        assertEquals(rule.toString(), 2, rule.filter(OFFER_ATTR_MATCH_1, REQ_WITH_TASK_ATTR_MATCH_1, TASKS).getResourcesCount());
        assertEquals(rule.toString(), 2, rule.filter(OFFER_ATTR_MATCH_2, REQ_WITH_TASK_ATTR_MATCH_1, TASKS).getResourcesCount());
        assertEquals(rule.toString(), 2, rule.filter(OFFER_ATTR_MISMATCH, REQ_WITH_TASK_ATTR_MATCH_1, TASKS).getResourcesCount());

        assertEquals(rule.toString(), 2, rule.filter(OFFER_NO_ATTRS, REQ_WITH_TASK_ATTR_MATCH_2, TASKS).getResourcesCount());
        assertEquals(rule.toString(), 2, rule.filter(OFFER_ATTR_MATCH_1, REQ_WITH_TASK_ATTR_MATCH_2, TASKS).getResourcesCount());
        assertEquals(rule.toString(), 2, rule.filter(OFFER_ATTR_MATCH_2, REQ_WITH_TASK_ATTR_MATCH_2, TASKS).getResourcesCount());
        assertEquals(rule.toString(), 2, rule.filter(OFFER_ATTR_MISMATCH, REQ_WITH_TASK_ATTR_MATCH_2, TASKS).getResourcesCount());

        assertEquals(rule.toString(), 2, rule.filter(OFFER_NO_ATTRS, REQ_WITH_TASK_ATTR_MISMATCH, TASKS).getResourcesCount());
        assertEquals(rule.toString(), 2, rule.filter(OFFER_ATTR_MATCH_1, REQ_WITH_TASK_ATTR_MISMATCH, TASKS).getResourcesCount());
        assertEquals(rule.toString(), 2, rule.filter(OFFER_ATTR_MATCH_2, REQ_WITH_TASK_ATTR_MISMATCH, TASKS).getResourcesCount());
        assertEquals(rule.toString(), 2, rule.filter(OFFER_ATTR_MISMATCH, REQ_WITH_TASK_ATTR_MISMATCH, TASKS).getResourcesCount());

        assertEquals(rule.toString(), 2, rule.filter(OFFER_NO_ATTRS, REQ_WITH_TASK_NO_ATTRS, Collections.emptyList()).getResourcesCount());
        assertEquals(rule.toString(), 2, rule.filter(OFFER_ATTR_MATCH_1, REQ_WITH_TASK_NO_ATTRS, Collections.emptyList()).getResourcesCount());
        assertEquals(rule.toString(), 2, rule.filter(OFFER_ATTR_MATCH_2, REQ_WITH_TASK_NO_ATTRS, Collections.emptyList()).getResourcesCount());
        assertEquals(rule.toString(), 2, rule.filter(OFFER_ATTR_MISMATCH, REQ_WITH_TASK_NO_ATTRS, Collections.emptyList()).getResourcesCount());

        assertEquals(rule.toString(), 2, rule.filter(OFFER_NO_ATTRS, REQ_WITH_TASK_EMPTY_ATTRS, Collections.emptyList()).getResourcesCount());
        assertEquals(rule.toString(), 2, rule.filter(OFFER_ATTR_MATCH_1, REQ_WITH_TASK_EMPTY_ATTRS, Collections.emptyList()).getResourcesCount());
        assertEquals(rule.toString(), 2, rule.filter(OFFER_ATTR_MATCH_2, REQ_WITH_TASK_EMPTY_ATTRS, Collections.emptyList()).getResourcesCount());
        assertEquals(rule.toString(), 2, rule.filter(OFFER_ATTR_MISMATCH, REQ_WITH_TASK_EMPTY_ATTRS, Collections.emptyList()).getResourcesCount());

        assertEquals(rule.toString(), 2, rule.filter(OFFER_NO_ATTRS, REQ_WITH_TASK_ATTR_MATCH_1, Collections.emptyList()).getResourcesCount());
        assertEquals(rule.toString(), 2, rule.filter(OFFER_ATTR_MATCH_1, REQ_WITH_TASK_ATTR_MATCH_1, Collections.emptyList()).getResourcesCount());
        assertEquals(rule.toString(), 2, rule.filter(OFFER_ATTR_MATCH_2, REQ_WITH_TASK_ATTR_MATCH_1, Collections.emptyList()).getResourcesCount());
        assertEquals(rule.toString(), 2, rule.filter(OFFER_ATTR_MISMATCH, REQ_WITH_TASK_ATTR_MATCH_1, Collections.emptyList()).getResourcesCount());

        assertEquals(rule.toString(), 2, rule.filter(OFFER_NO_ATTRS, REQ_WITH_TASK_ATTR_MATCH_2, Collections.emptyList()).getResourcesCount());
        assertEquals(rule.toString(), 2, rule.filter(OFFER_ATTR_MATCH_1, REQ_WITH_TASK_ATTR_MATCH_2, Collections.emptyList()).getResourcesCount());
        assertEquals(rule.toString(), 2, rule.filter(OFFER_ATTR_MATCH_2, REQ_WITH_TASK_ATTR_MATCH_2, Collections.emptyList()).getResourcesCount());
        assertEquals(rule.toString(), 2, rule.filter(OFFER_ATTR_MISMATCH, REQ_WITH_TASK_ATTR_MATCH_2, Collections.emptyList()).getResourcesCount());

        assertEquals(rule.toString(), 2, rule.filter(OFFER_NO_ATTRS, REQ_WITH_TASK_ATTR_MISMATCH, Collections.emptyList()).getResourcesCount());
        assertEquals(rule.toString(), 2, rule.filter(OFFER_ATTR_MATCH_1, REQ_WITH_TASK_ATTR_MISMATCH, Collections.emptyList()).getResourcesCount());
        assertEquals(rule.toString(), 2, rule.filter(OFFER_ATTR_MATCH_2, REQ_WITH_TASK_ATTR_MISMATCH, Collections.emptyList()).getResourcesCount());
        assertEquals(rule.toString(), 2, rule.filter(OFFER_ATTR_MISMATCH, REQ_WITH_TASK_ATTR_MISMATCH, Collections.emptyList()).getResourcesCount());
    }

    @Test
    public void testLimitTwoDuplicates() {
        PlacementRule rule = new MaxPerAttributeRule(2, ATTR_MATCHER);

        Collection<TaskInfo> tasks = Arrays.asList(
                TASK_NO_ATTRS, TASK_EMPTY_ATTRS,
                TASK_ATTR_MATCH_1, TASK_ATTR_MATCH_1,
                TASK_ATTR_MATCH_2,
                TASK_ATTR_MISMATCH);

        assertEquals(rule.toString(), 2, rule.filter(OFFER_NO_ATTRS, REQ, tasks).getResourcesCount());
        assertEquals(rule.toString(), 0, rule.filter(OFFER_ATTR_MATCH_1, REQ, tasks).getResourcesCount());
        assertEquals(rule.toString(), 2, rule.filter(OFFER_ATTR_MATCH_2, REQ, tasks).getResourcesCount());
        assertEquals(rule.toString(), 2, rule.filter(OFFER_ATTR_MISMATCH, REQ, tasks).getResourcesCount());

        tasks = Arrays.asList(
                TASK_NO_ATTRS, TASK_EMPTY_ATTRS, TASK_NO_ATTRS,
                TASK_ATTR_MATCH_1,
                TASK_ATTR_MATCH_2, TASK_ATTR_MATCH_2,
                TASK_ATTR_MISMATCH);

        assertEquals(rule.toString(), 2, rule.filter(OFFER_NO_ATTRS, REQ, tasks).getResourcesCount());
        assertEquals(rule.toString(), 2, rule.filter(OFFER_ATTR_MATCH_1, REQ, tasks).getResourcesCount());
        assertEquals(rule.toString(), 0, rule.filter(OFFER_ATTR_MATCH_2, REQ, tasks).getResourcesCount());
        assertEquals(rule.toString(), 2, rule.filter(OFFER_ATTR_MISMATCH, REQ, tasks).getResourcesCount());

        tasks = Arrays.asList(
                TASK_EMPTY_ATTRS, TASK_NO_ATTRS,
                TASK_ATTR_MATCH_1, TASK_ATTR_MATCH_1,
                TASK_ATTR_MATCH_2, TASK_ATTR_MATCH_2,
                TASK_ATTR_MISMATCH, TASK_ATTR_MISMATCH, TASK_ATTR_MISMATCH);

        assertEquals(rule.toString(), 2, rule.filter(OFFER_NO_ATTRS, REQ, tasks).getResourcesCount());
        assertEquals(rule.toString(), 0, rule.filter(OFFER_ATTR_MATCH_1, REQ, tasks).getResourcesCount());
        assertEquals(rule.toString(), 0, rule.filter(OFFER_ATTR_MATCH_2, REQ, tasks).getResourcesCount());
        assertEquals(rule.toString(), 2, rule.filter(OFFER_ATTR_MISMATCH, REQ, tasks).getResourcesCount());
    }

    @Test
    public void testLimitTwoDuplicatesWithSamePresent() {
        PlacementRule rule = new MaxPerAttributeRule(2, ATTR_MATCHER);

        Collection<TaskInfo> tasks = Arrays.asList(
                TASK_NO_ATTRS, TASK_EMPTY_ATTRS,
                TASK_ATTR_MATCH_1, TASK_ATTR_MATCH_1,
                TASK_ATTR_MATCH_2,
                TASK_ATTR_MISMATCH);

        assertEquals(rule.toString(), 2, rule.filter(OFFER_NO_ATTRS, REQ_WITH_TASK_NO_ATTRS, tasks).getResourcesCount());
        assertEquals(rule.toString(), 0, rule.filter(OFFER_ATTR_MATCH_1, REQ_WITH_TASK_NO_ATTRS, tasks).getResourcesCount());
        assertEquals(rule.toString(), 2, rule.filter(OFFER_ATTR_MATCH_2, REQ_WITH_TASK_NO_ATTRS, tasks).getResourcesCount());
        assertEquals(rule.toString(), 2, rule.filter(OFFER_ATTR_MISMATCH, REQ_WITH_TASK_NO_ATTRS, tasks).getResourcesCount());

        assertEquals(rule.toString(), 2, rule.filter(OFFER_NO_ATTRS, REQ_WITH_TASK_EMPTY_ATTRS, tasks).getResourcesCount());
        assertEquals(rule.toString(), 0, rule.filter(OFFER_ATTR_MATCH_1, REQ_WITH_TASK_EMPTY_ATTRS, tasks).getResourcesCount());
        assertEquals(rule.toString(), 2, rule.filter(OFFER_ATTR_MATCH_2, REQ_WITH_TASK_EMPTY_ATTRS, tasks).getResourcesCount());
        assertEquals(rule.toString(), 2, rule.filter(OFFER_ATTR_MISMATCH, REQ_WITH_TASK_EMPTY_ATTRS, tasks).getResourcesCount());

        assertEquals(rule.toString(), 2, rule.filter(OFFER_NO_ATTRS, REQ_WITH_TASK_ATTR_MATCH_1, tasks).getResourcesCount());
        assertEquals(rule.toString(), 2, rule.filter(OFFER_ATTR_MATCH_1, REQ_WITH_TASK_ATTR_MATCH_1, tasks).getResourcesCount());
        assertEquals(rule.toString(), 2, rule.filter(OFFER_ATTR_MATCH_2, REQ_WITH_TASK_ATTR_MATCH_1, tasks).getResourcesCount());
        assertEquals(rule.toString(), 2, rule.filter(OFFER_ATTR_MISMATCH, REQ_WITH_TASK_ATTR_MATCH_1, tasks).getResourcesCount());

        assertEquals(rule.toString(), 2, rule.filter(OFFER_NO_ATTRS, REQ_WITH_TASK_ATTR_MATCH_2, tasks).getResourcesCount());
        assertEquals(rule.toString(), 0, rule.filter(OFFER_ATTR_MATCH_1, REQ_WITH_TASK_ATTR_MATCH_2, tasks).getResourcesCount());
        assertEquals(rule.toString(), 2, rule.filter(OFFER_ATTR_MATCH_2, REQ_WITH_TASK_ATTR_MATCH_2, tasks).getResourcesCount());
        assertEquals(rule.toString(), 2, rule.filter(OFFER_ATTR_MISMATCH, REQ_WITH_TASK_ATTR_MATCH_2, tasks).getResourcesCount());

        assertEquals(rule.toString(), 2, rule.filter(OFFER_NO_ATTRS, REQ_WITH_TASK_ATTR_MISMATCH, tasks).getResourcesCount());
        assertEquals(rule.toString(), 0, rule.filter(OFFER_ATTR_MATCH_1, REQ_WITH_TASK_ATTR_MISMATCH, tasks).getResourcesCount());
        assertEquals(rule.toString(), 2, rule.filter(OFFER_ATTR_MATCH_2, REQ_WITH_TASK_ATTR_MISMATCH, tasks).getResourcesCount());
        assertEquals(rule.toString(), 2, rule.filter(OFFER_ATTR_MISMATCH, REQ_WITH_TASK_ATTR_MISMATCH, tasks).getResourcesCount());

        tasks = Arrays.asList(
                TASK_NO_ATTRS, TASK_EMPTY_ATTRS, TASK_NO_ATTRS,
                TASK_ATTR_MATCH_1,
                TASK_ATTR_MATCH_2, TASK_ATTR_MATCH_2,
                TASK_ATTR_MISMATCH);

        assertEquals(rule.toString(), 2, rule.filter(OFFER_NO_ATTRS, REQ_WITH_TASK_NO_ATTRS, tasks).getResourcesCount());
        assertEquals(rule.toString(), 2, rule.filter(OFFER_ATTR_MATCH_1, REQ_WITH_TASK_NO_ATTRS, tasks).getResourcesCount());
        assertEquals(rule.toString(), 0, rule.filter(OFFER_ATTR_MATCH_2, REQ_WITH_TASK_NO_ATTRS, tasks).getResourcesCount());
        assertEquals(rule.toString(), 2, rule.filter(OFFER_ATTR_MISMATCH, REQ_WITH_TASK_NO_ATTRS, tasks).getResourcesCount());

        assertEquals(rule.toString(), 2, rule.filter(OFFER_NO_ATTRS, REQ_WITH_TASK_EMPTY_ATTRS, tasks).getResourcesCount());
        assertEquals(rule.toString(), 2, rule.filter(OFFER_ATTR_MATCH_1, REQ_WITH_TASK_EMPTY_ATTRS, tasks).getResourcesCount());
        assertEquals(rule.toString(), 0, rule.filter(OFFER_ATTR_MATCH_2, REQ_WITH_TASK_EMPTY_ATTRS, tasks).getResourcesCount());
        assertEquals(rule.toString(), 2, rule.filter(OFFER_ATTR_MISMATCH, REQ_WITH_TASK_EMPTY_ATTRS, tasks).getResourcesCount());

        assertEquals(rule.toString(), 2, rule.filter(OFFER_NO_ATTRS, REQ_WITH_TASK_ATTR_MATCH_1, tasks).getResourcesCount());
        assertEquals(rule.toString(), 2, rule.filter(OFFER_ATTR_MATCH_1, REQ_WITH_TASK_ATTR_MATCH_1, tasks).getResourcesCount());
        assertEquals(rule.toString(), 0, rule.filter(OFFER_ATTR_MATCH_2, REQ_WITH_TASK_ATTR_MATCH_1, tasks).getResourcesCount());
        assertEquals(rule.toString(), 2, rule.filter(OFFER_ATTR_MISMATCH, REQ_WITH_TASK_ATTR_MATCH_1, tasks).getResourcesCount());

        assertEquals(rule.toString(), 2, rule.filter(OFFER_NO_ATTRS, REQ_WITH_TASK_ATTR_MATCH_2, tasks).getResourcesCount());
        assertEquals(rule.toString(), 2, rule.filter(OFFER_ATTR_MATCH_1, REQ_WITH_TASK_ATTR_MATCH_2, tasks).getResourcesCount());
        assertEquals(rule.toString(), 2, rule.filter(OFFER_ATTR_MATCH_2, REQ_WITH_TASK_ATTR_MATCH_2, tasks).getResourcesCount());
        assertEquals(rule.toString(), 2, rule.filter(OFFER_ATTR_MISMATCH, REQ_WITH_TASK_ATTR_MATCH_2, tasks).getResourcesCount());

        assertEquals(rule.toString(), 2, rule.filter(OFFER_NO_ATTRS, REQ_WITH_TASK_ATTR_MISMATCH, tasks).getResourcesCount());
        assertEquals(rule.toString(), 2, rule.filter(OFFER_ATTR_MATCH_1, REQ_WITH_TASK_ATTR_MISMATCH, tasks).getResourcesCount());
        assertEquals(rule.toString(), 0, rule.filter(OFFER_ATTR_MATCH_2, REQ_WITH_TASK_ATTR_MISMATCH, tasks).getResourcesCount());
        assertEquals(rule.toString(), 2, rule.filter(OFFER_ATTR_MISMATCH, REQ_WITH_TASK_ATTR_MISMATCH, tasks).getResourcesCount());

        tasks = Arrays.asList(
                TASK_EMPTY_ATTRS, TASK_NO_ATTRS,
                TASK_ATTR_MATCH_1, TASK_ATTR_MATCH_1,
                TASK_ATTR_MATCH_2, TASK_ATTR_MATCH_2,
                TASK_ATTR_MISMATCH, TASK_ATTR_MISMATCH, TASK_ATTR_MISMATCH);

        assertEquals(rule.toString(), 2, rule.filter(OFFER_NO_ATTRS, REQ_WITH_TASK_NO_ATTRS, tasks).getResourcesCount());
        assertEquals(rule.toString(), 0, rule.filter(OFFER_ATTR_MATCH_1, REQ_WITH_TASK_NO_ATTRS, tasks).getResourcesCount());
        assertEquals(rule.toString(), 0, rule.filter(OFFER_ATTR_MATCH_2, REQ_WITH_TASK_NO_ATTRS, tasks).getResourcesCount());
        assertEquals(rule.toString(), 2, rule.filter(OFFER_ATTR_MISMATCH, REQ_WITH_TASK_NO_ATTRS, tasks).getResourcesCount());

        assertEquals(rule.toString(), 2, rule.filter(OFFER_NO_ATTRS, REQ_WITH_TASK_EMPTY_ATTRS, tasks).getResourcesCount());
        assertEquals(rule.toString(), 0, rule.filter(OFFER_ATTR_MATCH_1, REQ_WITH_TASK_EMPTY_ATTRS, tasks).getResourcesCount());
        assertEquals(rule.toString(), 0, rule.filter(OFFER_ATTR_MATCH_2, REQ_WITH_TASK_EMPTY_ATTRS, tasks).getResourcesCount());
        assertEquals(rule.toString(), 2, rule.filter(OFFER_ATTR_MISMATCH, REQ_WITH_TASK_EMPTY_ATTRS, tasks).getResourcesCount());

        assertEquals(rule.toString(), 2, rule.filter(OFFER_NO_ATTRS, REQ_WITH_TASK_ATTR_MATCH_1, tasks).getResourcesCount());
        assertEquals(rule.toString(), 2, rule.filter(OFFER_ATTR_MATCH_1, REQ_WITH_TASK_ATTR_MATCH_1, tasks).getResourcesCount());
        assertEquals(rule.toString(), 0, rule.filter(OFFER_ATTR_MATCH_2, REQ_WITH_TASK_ATTR_MATCH_1, tasks).getResourcesCount());
        assertEquals(rule.toString(), 2, rule.filter(OFFER_ATTR_MISMATCH, REQ_WITH_TASK_ATTR_MATCH_1, tasks).getResourcesCount());

        assertEquals(rule.toString(), 2, rule.filter(OFFER_NO_ATTRS, REQ_WITH_TASK_ATTR_MATCH_2, tasks).getResourcesCount());
        assertEquals(rule.toString(), 0, rule.filter(OFFER_ATTR_MATCH_1, REQ_WITH_TASK_ATTR_MATCH_2, tasks).getResourcesCount());
        assertEquals(rule.toString(), 2, rule.filter(OFFER_ATTR_MATCH_2, REQ_WITH_TASK_ATTR_MATCH_2, tasks).getResourcesCount());
        assertEquals(rule.toString(), 2, rule.filter(OFFER_ATTR_MISMATCH, REQ_WITH_TASK_ATTR_MATCH_2, tasks).getResourcesCount());

        assertEquals(rule.toString(), 2, rule.filter(OFFER_NO_ATTRS, REQ_WITH_TASK_ATTR_MISMATCH, tasks).getResourcesCount());
        assertEquals(rule.toString(), 0, rule.filter(OFFER_ATTR_MATCH_1, REQ_WITH_TASK_ATTR_MISMATCH, tasks).getResourcesCount());
        assertEquals(rule.toString(), 0, rule.filter(OFFER_ATTR_MATCH_2, REQ_WITH_TASK_ATTR_MISMATCH, tasks).getResourcesCount());
        assertEquals(rule.toString(), 2, rule.filter(OFFER_ATTR_MISMATCH, REQ_WITH_TASK_ATTR_MISMATCH, tasks).getResourcesCount());
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

    private static TaskInfo getTask(String id, Offer offer) {
        TaskInfo.Builder taskBuilder = TaskTestUtils.getTaskInfo(Collections.emptyList()).toBuilder();
        taskBuilder.getTaskIdBuilder().setValue(id);
        try {
            taskBuilder.setName(CommonTaskUtils.toTaskName(taskBuilder.getTaskId()));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
        return CommonTaskUtils.setOfferAttributes(taskBuilder, offer).build();
    }

    private static OfferRequirement getOfferReq(TaskInfo taskInfo) {
        try {
            return OfferRequirement.create(
                    CommonTaskUtils.getType(taskInfo),
                    CommonTaskUtils.getIndex(taskInfo),
                    Arrays.asList(taskInfo));
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
}
