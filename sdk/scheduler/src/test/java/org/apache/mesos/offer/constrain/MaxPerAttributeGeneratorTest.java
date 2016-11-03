package org.apache.mesos.offer.constrain;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;

import org.apache.mesos.Protos.Attribute;
import org.apache.mesos.Protos.Offer;
import org.apache.mesos.Protos.TaskInfo;
import org.apache.mesos.Protos.Value;
import org.apache.mesos.config.SerializationUtils;
import org.apache.mesos.offer.OfferRequirement;
import org.apache.mesos.offer.TaskUtils;
import org.apache.mesos.testutils.OfferRequirementTestUtils;
import org.apache.mesos.testutils.OfferTestUtils;
import org.apache.mesos.testutils.TaskTestUtils;

/**
 * Tests for {@link MaxPerAttributeGenerator}.
 */
public class MaxPerAttributeGeneratorTest {

    private static final String ATTR_PATTERN = "^footext:.*$";
    private static final AttributeSelector ATTR_SELECTOR = AttributeSelector.createRegexSelector(ATTR_PATTERN);

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

    private static final OfferRequirement REQ = OfferRequirementTestUtils.getOfferRequirement();
    private static final OfferRequirement REQ_WITH_TASK_NO_ATTRS = getOfferReq(TASK_NO_ATTRS);
    private static final OfferRequirement REQ_WITH_TASK_EMPTY_ATTRS = getOfferReq(TASK_EMPTY_ATTRS);
    private static final OfferRequirement REQ_WITH_TASK_ATTR_MATCH_1 = getOfferReq(TASK_ATTR_MATCH_1);
    private static final OfferRequirement REQ_WITH_TASK_ATTR_MATCH_2 = getOfferReq(TASK_ATTR_MATCH_2);
    private static final OfferRequirement REQ_WITH_TASK_ATTR_MISMATCH = getOfferReq(TASK_ATTR_MISMATCH);

    @Test
    public void testLimitZero() {
        PlacementRuleGenerator generator = new MaxPerAttributeGenerator(0, ATTR_SELECTOR);

        PlacementRule rule = generator.generate(Arrays.asList(
                TASK_NO_ATTRS, TASK_EMPTY_ATTRS, TASK_ATTR_MATCH_1, TASK_ATTR_MATCH_2, TASK_ATTR_MISMATCH));

        assertEquals(rule.toString(), 2, rule.filter(OFFER_NO_ATTRS, REQ).getResourcesCount());
        assertEquals(rule.toString(), 0, rule.filter(OFFER_ATTR_MATCH_1, REQ).getResourcesCount());
        assertEquals(rule.toString(), 0, rule.filter(OFFER_ATTR_MATCH_2, REQ).getResourcesCount());
        assertEquals(rule.toString(), 2, rule.filter(OFFER_ATTR_MISMATCH, REQ).getResourcesCount());

        rule = generator.generate(Arrays.asList());

        assertEquals(rule.toString(), 2, rule.filter(OFFER_NO_ATTRS, REQ).getResourcesCount());
        assertEquals(rule.toString(), 2, rule.filter(OFFER_ATTR_MATCH_1, REQ).getResourcesCount());
        assertEquals(rule.toString(), 2, rule.filter(OFFER_ATTR_MATCH_2, REQ).getResourcesCount());
        assertEquals(rule.toString(), 2, rule.filter(OFFER_ATTR_MISMATCH, REQ).getResourcesCount());
    }

    @Test
    public void testLimitZeroWithSamePresent() {
        PlacementRuleGenerator generator = new MaxPerAttributeGenerator(0, ATTR_SELECTOR);

        PlacementRule rule = generator.generate(Arrays.asList(
                TASK_NO_ATTRS, TASK_EMPTY_ATTRS, TASK_ATTR_MATCH_1, TASK_ATTR_MATCH_2, TASK_ATTR_MISMATCH));

        assertEquals(rule.toString(), 2, rule.filter(OFFER_NO_ATTRS, REQ_WITH_TASK_NO_ATTRS).getResourcesCount());
        assertEquals(rule.toString(), 0, rule.filter(OFFER_ATTR_MATCH_1, REQ_WITH_TASK_NO_ATTRS).getResourcesCount());
        assertEquals(rule.toString(), 0, rule.filter(OFFER_ATTR_MATCH_2, REQ_WITH_TASK_NO_ATTRS).getResourcesCount());
        assertEquals(rule.toString(), 2, rule.filter(OFFER_ATTR_MISMATCH, REQ_WITH_TASK_NO_ATTRS).getResourcesCount());

        assertEquals(rule.toString(), 2, rule.filter(OFFER_NO_ATTRS, REQ_WITH_TASK_EMPTY_ATTRS).getResourcesCount());
        assertEquals(rule.toString(), 0, rule.filter(OFFER_ATTR_MATCH_1, REQ_WITH_TASK_EMPTY_ATTRS).getResourcesCount());
        assertEquals(rule.toString(), 0, rule.filter(OFFER_ATTR_MATCH_2, REQ_WITH_TASK_EMPTY_ATTRS).getResourcesCount());
        assertEquals(rule.toString(), 2, rule.filter(OFFER_ATTR_MISMATCH, REQ_WITH_TASK_EMPTY_ATTRS).getResourcesCount());

        assertEquals(rule.toString(), 2, rule.filter(OFFER_NO_ATTRS, REQ_WITH_TASK_ATTR_MATCH_1).getResourcesCount());
        assertEquals(rule.toString(), 2, rule.filter(OFFER_ATTR_MATCH_1, REQ_WITH_TASK_ATTR_MATCH_1).getResourcesCount());
        assertEquals(rule.toString(), 0, rule.filter(OFFER_ATTR_MATCH_2, REQ_WITH_TASK_ATTR_MATCH_1).getResourcesCount());
        assertEquals(rule.toString(), 2, rule.filter(OFFER_ATTR_MISMATCH, REQ_WITH_TASK_ATTR_MATCH_1).getResourcesCount());

        assertEquals(rule.toString(), 2, rule.filter(OFFER_NO_ATTRS, REQ_WITH_TASK_ATTR_MATCH_2).getResourcesCount());
        assertEquals(rule.toString(), 0, rule.filter(OFFER_ATTR_MATCH_1, REQ_WITH_TASK_ATTR_MATCH_2).getResourcesCount());
        assertEquals(rule.toString(), 2, rule.filter(OFFER_ATTR_MATCH_2, REQ_WITH_TASK_ATTR_MATCH_2).getResourcesCount());
        assertEquals(rule.toString(), 2, rule.filter(OFFER_ATTR_MISMATCH, REQ_WITH_TASK_ATTR_MATCH_2).getResourcesCount());

        assertEquals(rule.toString(), 2, rule.filter(OFFER_NO_ATTRS, REQ_WITH_TASK_ATTR_MISMATCH).getResourcesCount());
        assertEquals(rule.toString(), 0, rule.filter(OFFER_ATTR_MATCH_1, REQ_WITH_TASK_ATTR_MISMATCH).getResourcesCount());
        assertEquals(rule.toString(), 0, rule.filter(OFFER_ATTR_MATCH_2, REQ_WITH_TASK_ATTR_MISMATCH).getResourcesCount());
        assertEquals(rule.toString(), 2, rule.filter(OFFER_ATTR_MISMATCH, REQ_WITH_TASK_ATTR_MISMATCH).getResourcesCount());

        rule = generator.generate(Arrays.asList());

        assertEquals(rule.toString(), 2, rule.filter(OFFER_NO_ATTRS, REQ_WITH_TASK_NO_ATTRS).getResourcesCount());
        assertEquals(rule.toString(), 2, rule.filter(OFFER_ATTR_MATCH_1, REQ_WITH_TASK_NO_ATTRS).getResourcesCount());
        assertEquals(rule.toString(), 2, rule.filter(OFFER_ATTR_MATCH_2, REQ_WITH_TASK_NO_ATTRS).getResourcesCount());
        assertEquals(rule.toString(), 2, rule.filter(OFFER_ATTR_MISMATCH, REQ_WITH_TASK_NO_ATTRS).getResourcesCount());

        assertEquals(rule.toString(), 2, rule.filter(OFFER_NO_ATTRS, REQ_WITH_TASK_EMPTY_ATTRS).getResourcesCount());
        assertEquals(rule.toString(), 2, rule.filter(OFFER_ATTR_MATCH_1, REQ_WITH_TASK_EMPTY_ATTRS).getResourcesCount());
        assertEquals(rule.toString(), 2, rule.filter(OFFER_ATTR_MATCH_2, REQ_WITH_TASK_EMPTY_ATTRS).getResourcesCount());
        assertEquals(rule.toString(), 2, rule.filter(OFFER_ATTR_MISMATCH, REQ_WITH_TASK_EMPTY_ATTRS).getResourcesCount());

        assertEquals(rule.toString(), 2, rule.filter(OFFER_NO_ATTRS, REQ_WITH_TASK_ATTR_MATCH_1).getResourcesCount());
        assertEquals(rule.toString(), 2, rule.filter(OFFER_ATTR_MATCH_1, REQ_WITH_TASK_ATTR_MATCH_1).getResourcesCount());
        assertEquals(rule.toString(), 2, rule.filter(OFFER_ATTR_MATCH_2, REQ_WITH_TASK_ATTR_MATCH_1).getResourcesCount());
        assertEquals(rule.toString(), 2, rule.filter(OFFER_ATTR_MISMATCH, REQ_WITH_TASK_ATTR_MATCH_1).getResourcesCount());

        assertEquals(rule.toString(), 2, rule.filter(OFFER_NO_ATTRS, REQ_WITH_TASK_ATTR_MATCH_2).getResourcesCount());
        assertEquals(rule.toString(), 2, rule.filter(OFFER_ATTR_MATCH_1, REQ_WITH_TASK_ATTR_MATCH_2).getResourcesCount());
        assertEquals(rule.toString(), 2, rule.filter(OFFER_ATTR_MATCH_2, REQ_WITH_TASK_ATTR_MATCH_2).getResourcesCount());
        assertEquals(rule.toString(), 2, rule.filter(OFFER_ATTR_MISMATCH, REQ_WITH_TASK_ATTR_MATCH_2).getResourcesCount());

        assertEquals(rule.toString(), 2, rule.filter(OFFER_NO_ATTRS, REQ_WITH_TASK_ATTR_MISMATCH).getResourcesCount());
        assertEquals(rule.toString(), 2, rule.filter(OFFER_ATTR_MATCH_1, REQ_WITH_TASK_ATTR_MISMATCH).getResourcesCount());
        assertEquals(rule.toString(), 2, rule.filter(OFFER_ATTR_MATCH_2, REQ_WITH_TASK_ATTR_MISMATCH).getResourcesCount());
        assertEquals(rule.toString(), 2, rule.filter(OFFER_ATTR_MISMATCH, REQ_WITH_TASK_ATTR_MISMATCH).getResourcesCount());
    }

    @Test
    public void testLimitOne() {
        PlacementRuleGenerator generator = new MaxPerAttributeGenerator(1, ATTR_SELECTOR);

        PlacementRule rule = generator.generate(Arrays.asList(
                TASK_NO_ATTRS, TASK_EMPTY_ATTRS, TASK_ATTR_MATCH_1, TASK_ATTR_MATCH_2, TASK_ATTR_MISMATCH));

        assertEquals(rule.toString(), 2, rule.filter(OFFER_NO_ATTRS, REQ).getResourcesCount());
        assertEquals(rule.toString(), 0, rule.filter(OFFER_ATTR_MATCH_1, REQ).getResourcesCount());
        assertEquals(rule.toString(), 0, rule.filter(OFFER_ATTR_MATCH_2, REQ).getResourcesCount());
        assertEquals(rule.toString(), 2, rule.filter(OFFER_ATTR_MISMATCH, REQ).getResourcesCount());

        rule = generator.generate(Arrays.asList());

        assertEquals(rule.toString(), 2, rule.filter(OFFER_NO_ATTRS, REQ).getResourcesCount());
        assertEquals(rule.toString(), 2, rule.filter(OFFER_ATTR_MATCH_1, REQ).getResourcesCount());
        assertEquals(rule.toString(), 2, rule.filter(OFFER_ATTR_MATCH_2, REQ).getResourcesCount());
        assertEquals(rule.toString(), 2, rule.filter(OFFER_ATTR_MISMATCH, REQ).getResourcesCount());
    }

    @Test
    public void testLimitOneWithSamePresent() {
        PlacementRuleGenerator generator = new MaxPerAttributeGenerator(1, ATTR_SELECTOR);

        PlacementRule rule = generator.generate(Arrays.asList(
                TASK_NO_ATTRS, TASK_EMPTY_ATTRS, TASK_ATTR_MATCH_1, TASK_ATTR_MATCH_2, TASK_ATTR_MISMATCH));

        assertEquals(rule.toString(), 2, rule.filter(OFFER_NO_ATTRS, REQ_WITH_TASK_NO_ATTRS).getResourcesCount());
        assertEquals(rule.toString(), 0, rule.filter(OFFER_ATTR_MATCH_1, REQ_WITH_TASK_NO_ATTRS).getResourcesCount());
        assertEquals(rule.toString(), 0, rule.filter(OFFER_ATTR_MATCH_2, REQ_WITH_TASK_NO_ATTRS).getResourcesCount());
        assertEquals(rule.toString(), 2, rule.filter(OFFER_ATTR_MISMATCH, REQ_WITH_TASK_NO_ATTRS).getResourcesCount());

        assertEquals(rule.toString(), 2, rule.filter(OFFER_NO_ATTRS, REQ_WITH_TASK_EMPTY_ATTRS).getResourcesCount());
        assertEquals(rule.toString(), 0, rule.filter(OFFER_ATTR_MATCH_1, REQ_WITH_TASK_EMPTY_ATTRS).getResourcesCount());
        assertEquals(rule.toString(), 0, rule.filter(OFFER_ATTR_MATCH_2, REQ_WITH_TASK_EMPTY_ATTRS).getResourcesCount());
        assertEquals(rule.toString(), 2, rule.filter(OFFER_ATTR_MISMATCH, REQ_WITH_TASK_EMPTY_ATTRS).getResourcesCount());

        assertEquals(rule.toString(), 2, rule.filter(OFFER_NO_ATTRS, REQ_WITH_TASK_ATTR_MATCH_1).getResourcesCount());
        assertEquals(rule.toString(), 2, rule.filter(OFFER_ATTR_MATCH_1, REQ_WITH_TASK_ATTR_MATCH_1).getResourcesCount());
        assertEquals(rule.toString(), 0, rule.filter(OFFER_ATTR_MATCH_2, REQ_WITH_TASK_ATTR_MATCH_1).getResourcesCount());
        assertEquals(rule.toString(), 2, rule.filter(OFFER_ATTR_MISMATCH, REQ_WITH_TASK_ATTR_MATCH_1).getResourcesCount());

        assertEquals(rule.toString(), 2, rule.filter(OFFER_NO_ATTRS, REQ_WITH_TASK_ATTR_MATCH_2).getResourcesCount());
        assertEquals(rule.toString(), 0, rule.filter(OFFER_ATTR_MATCH_1, REQ_WITH_TASK_ATTR_MATCH_2).getResourcesCount());
        assertEquals(rule.toString(), 2, rule.filter(OFFER_ATTR_MATCH_2, REQ_WITH_TASK_ATTR_MATCH_2).getResourcesCount());
        assertEquals(rule.toString(), 2, rule.filter(OFFER_ATTR_MISMATCH, REQ_WITH_TASK_ATTR_MATCH_2).getResourcesCount());

        assertEquals(rule.toString(), 2, rule.filter(OFFER_NO_ATTRS, REQ_WITH_TASK_ATTR_MISMATCH).getResourcesCount());
        assertEquals(rule.toString(), 0, rule.filter(OFFER_ATTR_MATCH_1, REQ_WITH_TASK_ATTR_MISMATCH).getResourcesCount());
        assertEquals(rule.toString(), 0, rule.filter(OFFER_ATTR_MATCH_2, REQ_WITH_TASK_ATTR_MISMATCH).getResourcesCount());
        assertEquals(rule.toString(), 2, rule.filter(OFFER_ATTR_MISMATCH, REQ_WITH_TASK_ATTR_MISMATCH).getResourcesCount());

        rule = generator.generate(Arrays.asList());

        assertEquals(rule.toString(), 2, rule.filter(OFFER_NO_ATTRS, REQ_WITH_TASK_NO_ATTRS).getResourcesCount());
        assertEquals(rule.toString(), 2, rule.filter(OFFER_ATTR_MATCH_1, REQ_WITH_TASK_NO_ATTRS).getResourcesCount());
        assertEquals(rule.toString(), 2, rule.filter(OFFER_ATTR_MATCH_2, REQ_WITH_TASK_NO_ATTRS).getResourcesCount());
        assertEquals(rule.toString(), 2, rule.filter(OFFER_ATTR_MISMATCH, REQ_WITH_TASK_NO_ATTRS).getResourcesCount());

        assertEquals(rule.toString(), 2, rule.filter(OFFER_NO_ATTRS, REQ_WITH_TASK_EMPTY_ATTRS).getResourcesCount());
        assertEquals(rule.toString(), 2, rule.filter(OFFER_ATTR_MATCH_1, REQ_WITH_TASK_EMPTY_ATTRS).getResourcesCount());
        assertEquals(rule.toString(), 2, rule.filter(OFFER_ATTR_MATCH_2, REQ_WITH_TASK_EMPTY_ATTRS).getResourcesCount());
        assertEquals(rule.toString(), 2, rule.filter(OFFER_ATTR_MISMATCH, REQ_WITH_TASK_EMPTY_ATTRS).getResourcesCount());

        assertEquals(rule.toString(), 2, rule.filter(OFFER_NO_ATTRS, REQ_WITH_TASK_ATTR_MATCH_1).getResourcesCount());
        assertEquals(rule.toString(), 2, rule.filter(OFFER_ATTR_MATCH_1, REQ_WITH_TASK_ATTR_MATCH_1).getResourcesCount());
        assertEquals(rule.toString(), 2, rule.filter(OFFER_ATTR_MATCH_2, REQ_WITH_TASK_ATTR_MATCH_1).getResourcesCount());
        assertEquals(rule.toString(), 2, rule.filter(OFFER_ATTR_MISMATCH, REQ_WITH_TASK_ATTR_MATCH_1).getResourcesCount());

        assertEquals(rule.toString(), 2, rule.filter(OFFER_NO_ATTRS, REQ_WITH_TASK_ATTR_MATCH_2).getResourcesCount());
        assertEquals(rule.toString(), 2, rule.filter(OFFER_ATTR_MATCH_1, REQ_WITH_TASK_ATTR_MATCH_2).getResourcesCount());
        assertEquals(rule.toString(), 2, rule.filter(OFFER_ATTR_MATCH_2, REQ_WITH_TASK_ATTR_MATCH_2).getResourcesCount());
        assertEquals(rule.toString(), 2, rule.filter(OFFER_ATTR_MISMATCH, REQ_WITH_TASK_ATTR_MATCH_2).getResourcesCount());

        assertEquals(rule.toString(), 2, rule.filter(OFFER_NO_ATTRS, REQ_WITH_TASK_ATTR_MISMATCH).getResourcesCount());
        assertEquals(rule.toString(), 2, rule.filter(OFFER_ATTR_MATCH_1, REQ_WITH_TASK_ATTR_MISMATCH).getResourcesCount());
        assertEquals(rule.toString(), 2, rule.filter(OFFER_ATTR_MATCH_2, REQ_WITH_TASK_ATTR_MISMATCH).getResourcesCount());
        assertEquals(rule.toString(), 2, rule.filter(OFFER_ATTR_MISMATCH, REQ_WITH_TASK_ATTR_MISMATCH).getResourcesCount());
    }

    @Test
    public void testLimitTwo() {
        PlacementRuleGenerator generator = new MaxPerAttributeGenerator(2, ATTR_SELECTOR);

        PlacementRule rule = generator.generate(Arrays.asList(
                TASK_NO_ATTRS, TASK_EMPTY_ATTRS, TASK_ATTR_MATCH_1, TASK_ATTR_MATCH_2, TASK_ATTR_MISMATCH));

        assertEquals(rule.toString(), 2, rule.filter(OFFER_NO_ATTRS, REQ).getResourcesCount());
        assertEquals(rule.toString(), 2, rule.filter(OFFER_ATTR_MATCH_1, REQ).getResourcesCount());
        assertEquals(rule.toString(), 2, rule.filter(OFFER_ATTR_MATCH_2, REQ).getResourcesCount());
        assertEquals(rule.toString(), 2, rule.filter(OFFER_ATTR_MISMATCH, REQ).getResourcesCount());

        rule = generator.generate(Arrays.asList());

        assertEquals(rule.toString(), 2, rule.filter(OFFER_NO_ATTRS, REQ).getResourcesCount());
        assertEquals(rule.toString(), 2, rule.filter(OFFER_ATTR_MATCH_1, REQ).getResourcesCount());
        assertEquals(rule.toString(), 2, rule.filter(OFFER_ATTR_MATCH_2, REQ).getResourcesCount());
        assertEquals(rule.toString(), 2, rule.filter(OFFER_ATTR_MISMATCH, REQ).getResourcesCount());
    }

    @Test
    public void testLimitTwoWithSamePresent() {
        PlacementRuleGenerator generator = new MaxPerAttributeGenerator(2, ATTR_SELECTOR);

        PlacementRule rule = generator.generate(Arrays.asList(
                TASK_NO_ATTRS, TASK_EMPTY_ATTRS, TASK_ATTR_MATCH_1, TASK_ATTR_MATCH_2, TASK_ATTR_MISMATCH));

        assertEquals(rule.toString(), 2, rule.filter(OFFER_NO_ATTRS, REQ_WITH_TASK_NO_ATTRS).getResourcesCount());
        assertEquals(rule.toString(), 2, rule.filter(OFFER_ATTR_MATCH_1, REQ_WITH_TASK_NO_ATTRS).getResourcesCount());
        assertEquals(rule.toString(), 2, rule.filter(OFFER_ATTR_MATCH_2, REQ_WITH_TASK_NO_ATTRS).getResourcesCount());
        assertEquals(rule.toString(), 2, rule.filter(OFFER_ATTR_MISMATCH, REQ_WITH_TASK_NO_ATTRS).getResourcesCount());

        assertEquals(rule.toString(), 2, rule.filter(OFFER_NO_ATTRS, REQ_WITH_TASK_EMPTY_ATTRS).getResourcesCount());
        assertEquals(rule.toString(), 2, rule.filter(OFFER_ATTR_MATCH_1, REQ_WITH_TASK_EMPTY_ATTRS).getResourcesCount());
        assertEquals(rule.toString(), 2, rule.filter(OFFER_ATTR_MATCH_2, REQ_WITH_TASK_EMPTY_ATTRS).getResourcesCount());
        assertEquals(rule.toString(), 2, rule.filter(OFFER_ATTR_MISMATCH, REQ_WITH_TASK_EMPTY_ATTRS).getResourcesCount());

        assertEquals(rule.toString(), 2, rule.filter(OFFER_NO_ATTRS, REQ_WITH_TASK_ATTR_MATCH_1).getResourcesCount());
        assertEquals(rule.toString(), 2, rule.filter(OFFER_ATTR_MATCH_1, REQ_WITH_TASK_ATTR_MATCH_1).getResourcesCount());
        assertEquals(rule.toString(), 2, rule.filter(OFFER_ATTR_MATCH_2, REQ_WITH_TASK_ATTR_MATCH_1).getResourcesCount());
        assertEquals(rule.toString(), 2, rule.filter(OFFER_ATTR_MISMATCH, REQ_WITH_TASK_ATTR_MATCH_1).getResourcesCount());

        assertEquals(rule.toString(), 2, rule.filter(OFFER_NO_ATTRS, REQ_WITH_TASK_ATTR_MATCH_2).getResourcesCount());
        assertEquals(rule.toString(), 2, rule.filter(OFFER_ATTR_MATCH_1, REQ_WITH_TASK_ATTR_MATCH_2).getResourcesCount());
        assertEquals(rule.toString(), 2, rule.filter(OFFER_ATTR_MATCH_2, REQ_WITH_TASK_ATTR_MATCH_2).getResourcesCount());
        assertEquals(rule.toString(), 2, rule.filter(OFFER_ATTR_MISMATCH, REQ_WITH_TASK_ATTR_MATCH_2).getResourcesCount());

        assertEquals(rule.toString(), 2, rule.filter(OFFER_NO_ATTRS, REQ_WITH_TASK_ATTR_MISMATCH).getResourcesCount());
        assertEquals(rule.toString(), 2, rule.filter(OFFER_ATTR_MATCH_1, REQ_WITH_TASK_ATTR_MISMATCH).getResourcesCount());
        assertEquals(rule.toString(), 2, rule.filter(OFFER_ATTR_MATCH_2, REQ_WITH_TASK_ATTR_MISMATCH).getResourcesCount());
        assertEquals(rule.toString(), 2, rule.filter(OFFER_ATTR_MISMATCH, REQ_WITH_TASK_ATTR_MISMATCH).getResourcesCount());

        rule = generator.generate(Arrays.asList());

        assertEquals(rule.toString(), 2, rule.filter(OFFER_NO_ATTRS, REQ_WITH_TASK_NO_ATTRS).getResourcesCount());
        assertEquals(rule.toString(), 2, rule.filter(OFFER_ATTR_MATCH_1, REQ_WITH_TASK_NO_ATTRS).getResourcesCount());
        assertEquals(rule.toString(), 2, rule.filter(OFFER_ATTR_MATCH_2, REQ_WITH_TASK_NO_ATTRS).getResourcesCount());
        assertEquals(rule.toString(), 2, rule.filter(OFFER_ATTR_MISMATCH, REQ_WITH_TASK_NO_ATTRS).getResourcesCount());

        assertEquals(rule.toString(), 2, rule.filter(OFFER_NO_ATTRS, REQ_WITH_TASK_EMPTY_ATTRS).getResourcesCount());
        assertEquals(rule.toString(), 2, rule.filter(OFFER_ATTR_MATCH_1, REQ_WITH_TASK_EMPTY_ATTRS).getResourcesCount());
        assertEquals(rule.toString(), 2, rule.filter(OFFER_ATTR_MATCH_2, REQ_WITH_TASK_EMPTY_ATTRS).getResourcesCount());
        assertEquals(rule.toString(), 2, rule.filter(OFFER_ATTR_MISMATCH, REQ_WITH_TASK_EMPTY_ATTRS).getResourcesCount());

        assertEquals(rule.toString(), 2, rule.filter(OFFER_NO_ATTRS, REQ_WITH_TASK_ATTR_MATCH_1).getResourcesCount());
        assertEquals(rule.toString(), 2, rule.filter(OFFER_ATTR_MATCH_1, REQ_WITH_TASK_ATTR_MATCH_1).getResourcesCount());
        assertEquals(rule.toString(), 2, rule.filter(OFFER_ATTR_MATCH_2, REQ_WITH_TASK_ATTR_MATCH_1).getResourcesCount());
        assertEquals(rule.toString(), 2, rule.filter(OFFER_ATTR_MISMATCH, REQ_WITH_TASK_ATTR_MATCH_1).getResourcesCount());

        assertEquals(rule.toString(), 2, rule.filter(OFFER_NO_ATTRS, REQ_WITH_TASK_ATTR_MATCH_2).getResourcesCount());
        assertEquals(rule.toString(), 2, rule.filter(OFFER_ATTR_MATCH_1, REQ_WITH_TASK_ATTR_MATCH_2).getResourcesCount());
        assertEquals(rule.toString(), 2, rule.filter(OFFER_ATTR_MATCH_2, REQ_WITH_TASK_ATTR_MATCH_2).getResourcesCount());
        assertEquals(rule.toString(), 2, rule.filter(OFFER_ATTR_MISMATCH, REQ_WITH_TASK_ATTR_MATCH_2).getResourcesCount());

        assertEquals(rule.toString(), 2, rule.filter(OFFER_NO_ATTRS, REQ_WITH_TASK_ATTR_MISMATCH).getResourcesCount());
        assertEquals(rule.toString(), 2, rule.filter(OFFER_ATTR_MATCH_1, REQ_WITH_TASK_ATTR_MISMATCH).getResourcesCount());
        assertEquals(rule.toString(), 2, rule.filter(OFFER_ATTR_MATCH_2, REQ_WITH_TASK_ATTR_MISMATCH).getResourcesCount());
        assertEquals(rule.toString(), 2, rule.filter(OFFER_ATTR_MISMATCH, REQ_WITH_TASK_ATTR_MISMATCH).getResourcesCount());
    }

    @Test
    public void testLimitTwoDuplicates() {
        PlacementRuleGenerator generator = new MaxPerAttributeGenerator(2, ATTR_SELECTOR);

        PlacementRule rule = generator.generate(Arrays.asList(
                TASK_NO_ATTRS, TASK_EMPTY_ATTRS,
                TASK_ATTR_MATCH_1, TASK_ATTR_MATCH_1,
                TASK_ATTR_MATCH_2,
                TASK_ATTR_MISMATCH));

        assertEquals(rule.toString(), 2, rule.filter(OFFER_NO_ATTRS, REQ).getResourcesCount());
        assertEquals(rule.toString(), 0, rule.filter(OFFER_ATTR_MATCH_1, REQ).getResourcesCount());
        assertEquals(rule.toString(), 2, rule.filter(OFFER_ATTR_MATCH_2, REQ).getResourcesCount());
        assertEquals(rule.toString(), 2, rule.filter(OFFER_ATTR_MISMATCH, REQ).getResourcesCount());

        rule = generator.generate(Arrays.asList(
                TASK_NO_ATTRS, TASK_EMPTY_ATTRS, TASK_NO_ATTRS,
                TASK_ATTR_MATCH_1,
                TASK_ATTR_MATCH_2, TASK_ATTR_MATCH_2,
                TASK_ATTR_MISMATCH));

        assertEquals(rule.toString(), 2, rule.filter(OFFER_NO_ATTRS, REQ).getResourcesCount());
        assertEquals(rule.toString(), 2, rule.filter(OFFER_ATTR_MATCH_1, REQ).getResourcesCount());
        assertEquals(rule.toString(), 0, rule.filter(OFFER_ATTR_MATCH_2, REQ).getResourcesCount());
        assertEquals(rule.toString(), 2, rule.filter(OFFER_ATTR_MISMATCH, REQ).getResourcesCount());

        rule = generator.generate(Arrays.asList(
                TASK_EMPTY_ATTRS, TASK_NO_ATTRS,
                TASK_ATTR_MATCH_1, TASK_ATTR_MATCH_1,
                TASK_ATTR_MATCH_2, TASK_ATTR_MATCH_2,
                TASK_ATTR_MISMATCH, TASK_ATTR_MISMATCH, TASK_ATTR_MISMATCH));

        assertEquals(rule.toString(), 2, rule.filter(OFFER_NO_ATTRS, REQ).getResourcesCount());
        assertEquals(rule.toString(), 0, rule.filter(OFFER_ATTR_MATCH_1, REQ).getResourcesCount());
        assertEquals(rule.toString(), 0, rule.filter(OFFER_ATTR_MATCH_2, REQ).getResourcesCount());
        assertEquals(rule.toString(), 2, rule.filter(OFFER_ATTR_MISMATCH, REQ).getResourcesCount());
    }

    @Test
    public void testLimitTwoDuplicatesWithSamePresent() {
        PlacementRuleGenerator generator = new MaxPerAttributeGenerator(2, ATTR_SELECTOR);

        PlacementRule rule = generator.generate(Arrays.asList(
                TASK_NO_ATTRS, TASK_EMPTY_ATTRS,
                TASK_ATTR_MATCH_1, TASK_ATTR_MATCH_1,
                TASK_ATTR_MATCH_2,
                TASK_ATTR_MISMATCH));

        assertEquals(rule.toString(), 2, rule.filter(OFFER_NO_ATTRS, REQ_WITH_TASK_NO_ATTRS).getResourcesCount());
        assertEquals(rule.toString(), 0, rule.filter(OFFER_ATTR_MATCH_1, REQ_WITH_TASK_NO_ATTRS).getResourcesCount());
        assertEquals(rule.toString(), 2, rule.filter(OFFER_ATTR_MATCH_2, REQ_WITH_TASK_NO_ATTRS).getResourcesCount());
        assertEquals(rule.toString(), 2, rule.filter(OFFER_ATTR_MISMATCH, REQ_WITH_TASK_NO_ATTRS).getResourcesCount());

        assertEquals(rule.toString(), 2, rule.filter(OFFER_NO_ATTRS, REQ_WITH_TASK_EMPTY_ATTRS).getResourcesCount());
        assertEquals(rule.toString(), 0, rule.filter(OFFER_ATTR_MATCH_1, REQ_WITH_TASK_EMPTY_ATTRS).getResourcesCount());
        assertEquals(rule.toString(), 2, rule.filter(OFFER_ATTR_MATCH_2, REQ_WITH_TASK_EMPTY_ATTRS).getResourcesCount());
        assertEquals(rule.toString(), 2, rule.filter(OFFER_ATTR_MISMATCH, REQ_WITH_TASK_EMPTY_ATTRS).getResourcesCount());

        assertEquals(rule.toString(), 2, rule.filter(OFFER_NO_ATTRS, REQ_WITH_TASK_ATTR_MATCH_1).getResourcesCount());
        assertEquals(rule.toString(), 2, rule.filter(OFFER_ATTR_MATCH_1, REQ_WITH_TASK_ATTR_MATCH_1).getResourcesCount());
        assertEquals(rule.toString(), 2, rule.filter(OFFER_ATTR_MATCH_2, REQ_WITH_TASK_ATTR_MATCH_1).getResourcesCount());
        assertEquals(rule.toString(), 2, rule.filter(OFFER_ATTR_MISMATCH, REQ_WITH_TASK_ATTR_MATCH_1).getResourcesCount());

        assertEquals(rule.toString(), 2, rule.filter(OFFER_NO_ATTRS, REQ_WITH_TASK_ATTR_MATCH_2).getResourcesCount());
        assertEquals(rule.toString(), 0, rule.filter(OFFER_ATTR_MATCH_1, REQ_WITH_TASK_ATTR_MATCH_2).getResourcesCount());
        assertEquals(rule.toString(), 2, rule.filter(OFFER_ATTR_MATCH_2, REQ_WITH_TASK_ATTR_MATCH_2).getResourcesCount());
        assertEquals(rule.toString(), 2, rule.filter(OFFER_ATTR_MISMATCH, REQ_WITH_TASK_ATTR_MATCH_2).getResourcesCount());

        assertEquals(rule.toString(), 2, rule.filter(OFFER_NO_ATTRS, REQ_WITH_TASK_ATTR_MISMATCH).getResourcesCount());
        assertEquals(rule.toString(), 0, rule.filter(OFFER_ATTR_MATCH_1, REQ_WITH_TASK_ATTR_MISMATCH).getResourcesCount());
        assertEquals(rule.toString(), 2, rule.filter(OFFER_ATTR_MATCH_2, REQ_WITH_TASK_ATTR_MISMATCH).getResourcesCount());
        assertEquals(rule.toString(), 2, rule.filter(OFFER_ATTR_MISMATCH, REQ_WITH_TASK_ATTR_MISMATCH).getResourcesCount());

        rule = generator.generate(Arrays.asList(
                TASK_NO_ATTRS, TASK_EMPTY_ATTRS, TASK_NO_ATTRS,
                TASK_ATTR_MATCH_1,
                TASK_ATTR_MATCH_2, TASK_ATTR_MATCH_2,
                TASK_ATTR_MISMATCH));

        assertEquals(rule.toString(), 2, rule.filter(OFFER_NO_ATTRS, REQ_WITH_TASK_NO_ATTRS).getResourcesCount());
        assertEquals(rule.toString(), 2, rule.filter(OFFER_ATTR_MATCH_1, REQ_WITH_TASK_NO_ATTRS).getResourcesCount());
        assertEquals(rule.toString(), 0, rule.filter(OFFER_ATTR_MATCH_2, REQ_WITH_TASK_NO_ATTRS).getResourcesCount());
        assertEquals(rule.toString(), 2, rule.filter(OFFER_ATTR_MISMATCH, REQ_WITH_TASK_NO_ATTRS).getResourcesCount());

        assertEquals(rule.toString(), 2, rule.filter(OFFER_NO_ATTRS, REQ_WITH_TASK_EMPTY_ATTRS).getResourcesCount());
        assertEquals(rule.toString(), 2, rule.filter(OFFER_ATTR_MATCH_1, REQ_WITH_TASK_EMPTY_ATTRS).getResourcesCount());
        assertEquals(rule.toString(), 0, rule.filter(OFFER_ATTR_MATCH_2, REQ_WITH_TASK_EMPTY_ATTRS).getResourcesCount());
        assertEquals(rule.toString(), 2, rule.filter(OFFER_ATTR_MISMATCH, REQ_WITH_TASK_EMPTY_ATTRS).getResourcesCount());

        assertEquals(rule.toString(), 2, rule.filter(OFFER_NO_ATTRS, REQ_WITH_TASK_ATTR_MATCH_1).getResourcesCount());
        assertEquals(rule.toString(), 2, rule.filter(OFFER_ATTR_MATCH_1, REQ_WITH_TASK_ATTR_MATCH_1).getResourcesCount());
        assertEquals(rule.toString(), 0, rule.filter(OFFER_ATTR_MATCH_2, REQ_WITH_TASK_ATTR_MATCH_1).getResourcesCount());
        assertEquals(rule.toString(), 2, rule.filter(OFFER_ATTR_MISMATCH, REQ_WITH_TASK_ATTR_MATCH_1).getResourcesCount());

        assertEquals(rule.toString(), 2, rule.filter(OFFER_NO_ATTRS, REQ_WITH_TASK_ATTR_MATCH_2).getResourcesCount());
        assertEquals(rule.toString(), 2, rule.filter(OFFER_ATTR_MATCH_1, REQ_WITH_TASK_ATTR_MATCH_2).getResourcesCount());
        assertEquals(rule.toString(), 2, rule.filter(OFFER_ATTR_MATCH_2, REQ_WITH_TASK_ATTR_MATCH_2).getResourcesCount());
        assertEquals(rule.toString(), 2, rule.filter(OFFER_ATTR_MISMATCH, REQ_WITH_TASK_ATTR_MATCH_2).getResourcesCount());

        assertEquals(rule.toString(), 2, rule.filter(OFFER_NO_ATTRS, REQ_WITH_TASK_ATTR_MISMATCH).getResourcesCount());
        assertEquals(rule.toString(), 2, rule.filter(OFFER_ATTR_MATCH_1, REQ_WITH_TASK_ATTR_MISMATCH).getResourcesCount());
        assertEquals(rule.toString(), 0, rule.filter(OFFER_ATTR_MATCH_2, REQ_WITH_TASK_ATTR_MISMATCH).getResourcesCount());
        assertEquals(rule.toString(), 2, rule.filter(OFFER_ATTR_MISMATCH, REQ_WITH_TASK_ATTR_MISMATCH).getResourcesCount());

        rule = generator.generate(Arrays.asList(
                TASK_EMPTY_ATTRS, TASK_NO_ATTRS,
                TASK_ATTR_MATCH_1, TASK_ATTR_MATCH_1,
                TASK_ATTR_MATCH_2, TASK_ATTR_MATCH_2,
                TASK_ATTR_MISMATCH, TASK_ATTR_MISMATCH, TASK_ATTR_MISMATCH));

        assertEquals(rule.toString(), 2, rule.filter(OFFER_NO_ATTRS, REQ_WITH_TASK_NO_ATTRS).getResourcesCount());
        assertEquals(rule.toString(), 0, rule.filter(OFFER_ATTR_MATCH_1, REQ_WITH_TASK_NO_ATTRS).getResourcesCount());
        assertEquals(rule.toString(), 0, rule.filter(OFFER_ATTR_MATCH_2, REQ_WITH_TASK_NO_ATTRS).getResourcesCount());
        assertEquals(rule.toString(), 2, rule.filter(OFFER_ATTR_MISMATCH, REQ_WITH_TASK_NO_ATTRS).getResourcesCount());

        assertEquals(rule.toString(), 2, rule.filter(OFFER_NO_ATTRS, REQ_WITH_TASK_EMPTY_ATTRS).getResourcesCount());
        assertEquals(rule.toString(), 0, rule.filter(OFFER_ATTR_MATCH_1, REQ_WITH_TASK_EMPTY_ATTRS).getResourcesCount());
        assertEquals(rule.toString(), 0, rule.filter(OFFER_ATTR_MATCH_2, REQ_WITH_TASK_EMPTY_ATTRS).getResourcesCount());
        assertEquals(rule.toString(), 2, rule.filter(OFFER_ATTR_MISMATCH, REQ_WITH_TASK_EMPTY_ATTRS).getResourcesCount());

        assertEquals(rule.toString(), 2, rule.filter(OFFER_NO_ATTRS, REQ_WITH_TASK_ATTR_MATCH_1).getResourcesCount());
        assertEquals(rule.toString(), 2, rule.filter(OFFER_ATTR_MATCH_1, REQ_WITH_TASK_ATTR_MATCH_1).getResourcesCount());
        assertEquals(rule.toString(), 0, rule.filter(OFFER_ATTR_MATCH_2, REQ_WITH_TASK_ATTR_MATCH_1).getResourcesCount());
        assertEquals(rule.toString(), 2, rule.filter(OFFER_ATTR_MISMATCH, REQ_WITH_TASK_ATTR_MATCH_1).getResourcesCount());

        assertEquals(rule.toString(), 2, rule.filter(OFFER_NO_ATTRS, REQ_WITH_TASK_ATTR_MATCH_2).getResourcesCount());
        assertEquals(rule.toString(), 0, rule.filter(OFFER_ATTR_MATCH_1, REQ_WITH_TASK_ATTR_MATCH_2).getResourcesCount());
        assertEquals(rule.toString(), 2, rule.filter(OFFER_ATTR_MATCH_2, REQ_WITH_TASK_ATTR_MATCH_2).getResourcesCount());
        assertEquals(rule.toString(), 2, rule.filter(OFFER_ATTR_MISMATCH, REQ_WITH_TASK_ATTR_MATCH_2).getResourcesCount());

        assertEquals(rule.toString(), 2, rule.filter(OFFER_NO_ATTRS, REQ_WITH_TASK_ATTR_MISMATCH).getResourcesCount());
        assertEquals(rule.toString(), 0, rule.filter(OFFER_ATTR_MATCH_1, REQ_WITH_TASK_ATTR_MISMATCH).getResourcesCount());
        assertEquals(rule.toString(), 0, rule.filter(OFFER_ATTR_MATCH_2, REQ_WITH_TASK_ATTR_MISMATCH).getResourcesCount());
        assertEquals(rule.toString(), 2, rule.filter(OFFER_ATTR_MISMATCH, REQ_WITH_TASK_ATTR_MISMATCH).getResourcesCount());
    }

    @Test
    public void testSerializeDeserialize() throws IOException {
        PlacementRuleGenerator generator = new MaxPerAttributeGenerator(2, ATTR_SELECTOR);
        assertEquals(generator, SerializationUtils.fromJsonString(SerializationUtils.toJsonString(generator), PlacementRuleGenerator.class));

        generator = new MaxPerAttributeGenerator(0, ATTR_SELECTOR);
        assertEquals(generator, SerializationUtils.fromJsonString(SerializationUtils.toJsonString(generator), PlacementRuleGenerator.class));
    }

    private static TaskInfo getTask(String id, Offer offer) {
        TaskInfo.Builder taskBuilder = TaskTestUtils.getTaskInfo(Collections.emptyList()).toBuilder();
        taskBuilder.getTaskIdBuilder().setValue(id);
        try {
            taskBuilder.setName(TaskUtils.toTaskName(taskBuilder.getTaskId()));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
        return TaskUtils.setOfferAttributes(taskBuilder, offer).build();
    }

    private static OfferRequirement getOfferReq(TaskInfo taskInfo) {
        try {
            return new OfferRequirement(TaskUtils.getTaskType(taskInfo), Arrays.asList(taskInfo));
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
