package com.mesosphere.sdk.offer.constrain;

import com.mesosphere.sdk.testutils.OfferRequirementTestUtils;
import com.mesosphere.sdk.testutils.OfferTestUtils;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

import java.io.IOException;
import java.util.Collections;

import org.apache.mesos.Protos.Attribute;
import org.apache.mesos.Protos.Offer;
import org.apache.mesos.Protos.Value;
import com.mesosphere.sdk.config.SerializationUtils;
import com.mesosphere.sdk.offer.OfferRequirement;
import com.mesosphere.sdk.offer.AttributeStringUtils;

/**
 * Tests for {@link AttributeRule}.
 */
public class AttributeRuleTest {

    private static final OfferRequirement REQ = OfferRequirementTestUtils.getOfferRequirement();

    private static final Attribute ATTR_TEXT;
    private static final Attribute ATTR_SCALAR;
    private static final Attribute ATTR_RANGES;
    private static final Attribute ATTR_SET;
    static {
        Attribute.Builder a = Attribute.newBuilder()
                .setType(Value.Type.TEXT)
                .setName("footext");
        a.getTextBuilder().setValue("bar");
        ATTR_TEXT = a.build();
        a = Attribute.newBuilder()
                .setType(Value.Type.SCALAR)
                .setName("fooscalar");
        a.getScalarBuilder().setValue(123.456);
        ATTR_SCALAR = a.build();
        a = Attribute.newBuilder()
                .setType(Value.Type.RANGES)
                .setName("fooranges");
        a.getRangesBuilder().addRangeBuilder().setBegin(234).setEnd(345);
        a.getRangesBuilder().addRangeBuilder().setBegin(456).setEnd(567);
        ATTR_RANGES = a.build();
        a = Attribute.newBuilder()
                .setType(Value.Type.SET)
                .setName("fooset");
        a.getSetBuilder().addItem("bar").addItem("baz");
        ATTR_SET = a.build();
    }
    private static final String ATTR_TEXT_REGEX = "footext:...";
    private static final String ATTR_SCALAR_REGEX = ".*:[0-9.]+";
    private static final String ATTR_RANGES_REGEX = ".*ranges:.+";
    private static final String ATTR_SET_REGEX = ".*:\\{bar,baz\\}";

    @Test
    public void testExactMatchesString() {
        Offer.Builder o = getOfferWithResources()
                .addAttributes(ATTR_TEXT);
        assertEquals(o.build(), AttributeRule.require(ExactMatcher.create(
                AttributeStringUtils.toString(ATTR_TEXT))).filter(o.build(), REQ, Collections.emptyList()));

        o = getOfferWithResources()
                .addAttributes(ATTR_SCALAR);
        assertEquals(o.build(), AttributeRule.require(ExactMatcher.create(
                AttributeStringUtils.toString(ATTR_SCALAR))).filter(o.build(), REQ, Collections.emptyList()));

        o = getOfferWithResources()
                .addAttributes(ATTR_RANGES);
        assertEquals(o.build(), AttributeRule.require(ExactMatcher.create(
                AttributeStringUtils.toString(ATTR_RANGES))).filter(o.build(), REQ, Collections.emptyList()));

        o = getOfferWithResources()
                .addAttributes(ATTR_SET);
        assertEquals(o.build(), AttributeRule.require(ExactMatcher.create(
                AttributeStringUtils.toString(ATTR_SET))).filter(o.build(), REQ, Collections.emptyList()));
    }

    @Test
    public void testAnyMatchesString() {
        Offer o = getOfferWithResources()
                .addAttributes(ATTR_TEXT)
                .addAttributes(ATTR_SCALAR)
                .addAttributes(ATTR_RANGES)
                .addAttributes(ATTR_SET)
                .build();
        assertEquals(o, AttributeRule.require(ExactMatcher.create(
                AttributeStringUtils.toString(ATTR_TEXT))).filter(o, REQ, Collections.emptyList()));
        assertEquals(o, AttributeRule.require(ExactMatcher.create(
                AttributeStringUtils.toString(ATTR_SCALAR))).filter(o, REQ, Collections.emptyList()));
        assertEquals(o, AttributeRule.require(ExactMatcher.create(
                AttributeStringUtils.toString(ATTR_RANGES))).filter(o, REQ, Collections.emptyList()));
        assertEquals(o, AttributeRule.require(ExactMatcher.create(
                AttributeStringUtils.toString(ATTR_SET))).filter(o, REQ, Collections.emptyList()));
    }

    @Test
    public void testMismatchesString() {
        Offer o = getOfferWithResources()
                .addAttributes(ATTR_SCALAR)
                .addAttributes(ATTR_SET)
                .build();
        assertEquals(0, AttributeRule.require(ExactMatcher.create(
                AttributeStringUtils.toString(ATTR_TEXT))).filter(o, REQ, Collections.emptyList()).getResourcesCount());
        assertEquals(o, AttributeRule.require(ExactMatcher.create(
                AttributeStringUtils.toString(ATTR_SCALAR))).filter(o, REQ, Collections.emptyList()));
        assertEquals(0, AttributeRule.require(ExactMatcher.create(
                AttributeStringUtils.toString(ATTR_RANGES))).filter(o, REQ, Collections.emptyList()).getResourcesCount());
        assertEquals(o, AttributeRule.require(ExactMatcher.create(
                AttributeStringUtils.toString(ATTR_SET))).filter(o, REQ, Collections.emptyList()));

        o = getOfferWithResources()
                .addAttributes(ATTR_RANGES)
                .addAttributes(ATTR_TEXT)
                .build();
        assertEquals(o, AttributeRule.require(ExactMatcher.create(
                AttributeStringUtils.toString(ATTR_TEXT))).filter(o, REQ, Collections.emptyList()));
        assertEquals(0, AttributeRule.require(ExactMatcher.create(
                AttributeStringUtils.toString(ATTR_SCALAR))).filter(o, REQ, Collections.emptyList()).getResourcesCount());
        assertEquals(o, AttributeRule.require(ExactMatcher.create(
                AttributeStringUtils.toString(ATTR_RANGES))).filter(o, REQ, Collections.emptyList()));
        assertEquals(0, AttributeRule.require(ExactMatcher.create(
                AttributeStringUtils.toString(ATTR_SET))).filter(o, REQ, Collections.emptyList()).getResourcesCount());
    }

    @Test
    public void testExactMatchesRegex() {
        Offer.Builder o = getOfferWithResources()
                .addAttributes(ATTR_TEXT);
        assertEquals(o.build(), AttributeRule.require(RegexMatcher.create(
                ATTR_TEXT_REGEX)).filter(o.build(), REQ, Collections.emptyList()));

        o = getOfferWithResources()
                .addAttributes(ATTR_SCALAR);
        assertEquals(o.build(), AttributeRule.require(RegexMatcher.create(
                ATTR_SCALAR_REGEX)).filter(o.build(), REQ, Collections.emptyList()));

        o = getOfferWithResources()
                .addAttributes(ATTR_RANGES);
        assertEquals(o.build(), AttributeRule.require(RegexMatcher.create(
                ATTR_RANGES_REGEX)).filter(o.build(), REQ, Collections.emptyList()));

        o = getOfferWithResources()
                .addAttributes(ATTR_SET);
        assertEquals(o.build(), AttributeRule.require(RegexMatcher.create(
                ATTR_SET_REGEX)).filter(o.build(), REQ, Collections.emptyList()));
    }

    @Test
    public void testAnyMatchesRegex() {
        Offer o = getOfferWithResources()
                .addAttributes(ATTR_TEXT)
                .addAttributes(ATTR_SCALAR)
                .addAttributes(ATTR_RANGES)
                .addAttributes(ATTR_SET)
                .build();
        assertEquals(o, AttributeRule.require(RegexMatcher.create(
                ATTR_TEXT_REGEX)).filter(o, REQ, Collections.emptyList()));
        assertEquals(o, AttributeRule.require(RegexMatcher.create(
                ATTR_SCALAR_REGEX)).filter(o, REQ, Collections.emptyList()));
        assertEquals(o, AttributeRule.require(RegexMatcher.create(
                ATTR_RANGES_REGEX)).filter(o, REQ, Collections.emptyList()));
        assertEquals(o, AttributeRule.require(RegexMatcher.create(
                ATTR_SET_REGEX)).filter(o, REQ, Collections.emptyList()));
    }

    @Test
    public void testMismatchesRegex() {
        Offer o = getOfferWithResources()
                .addAttributes(ATTR_SCALAR)
                .addAttributes(ATTR_SET)
                .build();
        assertEquals(0, AttributeRule.require(RegexMatcher.create(
                ATTR_TEXT_REGEX)).filter(o, REQ, Collections.emptyList()).getResourcesCount());
        assertEquals(o, AttributeRule.require(RegexMatcher.create(
                ATTR_SCALAR_REGEX)).filter(o, REQ, Collections.emptyList()));
        assertEquals(0, AttributeRule.require(RegexMatcher.create(
                ATTR_RANGES_REGEX)).filter(o, REQ, Collections.emptyList()).getResourcesCount());
        assertEquals(o, AttributeRule.require(RegexMatcher.create(
                ATTR_SET_REGEX)).filter(o, REQ, Collections.emptyList()));

        o = getOfferWithResources()
                .addAttributes(ATTR_RANGES)
                .addAttributes(ATTR_TEXT)
                .build();
        assertEquals(o, AttributeRule.require(RegexMatcher.create(
                ATTR_TEXT_REGEX)).filter(o, REQ, Collections.emptyList()));
        assertEquals(0, AttributeRule.require(RegexMatcher.create(
                ATTR_SCALAR_REGEX)).filter(o, REQ, Collections.emptyList()).getResourcesCount());
        assertEquals(o, AttributeRule.require(RegexMatcher.create(
                ATTR_RANGES_REGEX)).filter(o, REQ, Collections.emptyList()));
        assertEquals(0, AttributeRule.require(RegexMatcher.create(
                ATTR_SET_REGEX)).filter(o, REQ, Collections.emptyList()).getResourcesCount());
    }

    @Test
    public void testSerializeDeserialize() throws IOException {
        PlacementRule rule = AttributeRule.require(
                ExactMatcher.create(AttributeStringUtils.toString(ATTR_SCALAR)));
        assertEquals(rule, SerializationUtils.fromString(
                SerializationUtils.toJsonString(rule), PlacementRule.class, TestPlacementUtils.OBJECT_MAPPER));

        rule = AttributeRule.require(
                RegexMatcher.create(ATTR_RANGES_REGEX));
        assertEquals(rule, SerializationUtils.fromString(
                SerializationUtils.toJsonString(rule), PlacementRule.class, TestPlacementUtils.OBJECT_MAPPER));
    }

    private static Offer.Builder getOfferWithResources() {
        Offer.Builder o = OfferTestUtils.getEmptyOfferBuilder();
        OfferTestUtils.addResource(o, "a");
        OfferTestUtils.addResource(o, "b");
        return o;
    }
}
