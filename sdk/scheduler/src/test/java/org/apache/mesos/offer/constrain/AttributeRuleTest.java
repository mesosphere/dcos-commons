package org.apache.mesos.offer.constrain;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

import java.io.IOException;
import java.util.Collections;

import org.apache.mesos.Protos.Attribute;
import org.apache.mesos.Protos.Offer;
import org.apache.mesos.Protos.Value;
import org.apache.mesos.config.SerializationUtils;
import org.apache.mesos.offer.AttributeStringUtils;
import org.apache.mesos.offer.OfferRequirement;
import org.apache.mesos.testutils.OfferRequirementTestUtils;
import org.apache.mesos.testutils.OfferTestUtils;

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
        assertEquals(o.build(), new AttributeRule(AttributeSelector.createStringSelector(
                AttributeStringUtils.toString(ATTR_TEXT))).filter(o.build(), REQ));

        o = getOfferWithResources()
                .addAttributes(ATTR_SCALAR);
        assertEquals(o.build(), new AttributeRule(AttributeSelector.createStringSelector(
                AttributeStringUtils.toString(ATTR_SCALAR))).filter(o.build(), REQ));

        o = getOfferWithResources()
                .addAttributes(ATTR_RANGES);
        assertEquals(o.build(), new AttributeRule(AttributeSelector.createStringSelector(
                AttributeStringUtils.toString(ATTR_RANGES))).filter(o.build(), REQ));

        o = getOfferWithResources()
                .addAttributes(ATTR_SET);
        assertEquals(o.build(), new AttributeRule(AttributeSelector.createStringSelector(
                AttributeStringUtils.toString(ATTR_SET))).filter(o.build(), REQ));
    }

    @Test
    public void testAnyMatchesString() {
        Offer o = getOfferWithResources()
                .addAttributes(ATTR_TEXT)
                .addAttributes(ATTR_SCALAR)
                .addAttributes(ATTR_RANGES)
                .addAttributes(ATTR_SET)
                .build();
        assertEquals(o, new AttributeRule.Generator(AttributeSelector.createStringSelector(
                AttributeStringUtils.toString(ATTR_TEXT))).generate(Collections.emptyList()).filter(o, REQ));
        assertEquals(o, new AttributeRule(AttributeSelector.createStringSelector(
                AttributeStringUtils.toString(ATTR_SCALAR))).filter(o, REQ));
        assertEquals(o, new AttributeRule(AttributeSelector.createStringSelector(
                AttributeStringUtils.toString(ATTR_RANGES))).filter(o, REQ));
        assertEquals(o, new AttributeRule(AttributeSelector.createStringSelector(
                AttributeStringUtils.toString(ATTR_SET))).filter(o, REQ));
    }

    @Test
    public void testMismatchesString() {
        Offer o = getOfferWithResources()
                .addAttributes(ATTR_SCALAR)
                .addAttributes(ATTR_SET)
                .build();
        assertEquals(0, new AttributeRule(AttributeSelector.createStringSelector(
                AttributeStringUtils.toString(ATTR_TEXT))).filter(o, REQ).getResourcesCount());
        assertEquals(o, new AttributeRule(AttributeSelector.createStringSelector(
                AttributeStringUtils.toString(ATTR_SCALAR))).filter(o, REQ));
        assertEquals(0, new AttributeRule(AttributeSelector.createStringSelector(
                AttributeStringUtils.toString(ATTR_RANGES))).filter(o, REQ).getResourcesCount());
        assertEquals(o, new AttributeRule(AttributeSelector.createStringSelector(
                AttributeStringUtils.toString(ATTR_SET))).filter(o, REQ));

        o = getOfferWithResources()
                .addAttributes(ATTR_RANGES)
                .addAttributes(ATTR_TEXT)
                .build();
        assertEquals(o, new AttributeRule(AttributeSelector.createStringSelector(
                AttributeStringUtils.toString(ATTR_TEXT))).filter(o, REQ));
        assertEquals(0, new AttributeRule(AttributeSelector.createStringSelector(
                AttributeStringUtils.toString(ATTR_SCALAR))).filter(o, REQ).getResourcesCount());
        assertEquals(o, new AttributeRule(AttributeSelector.createStringSelector(
                AttributeStringUtils.toString(ATTR_RANGES))).filter(o, REQ));
        assertEquals(0, new AttributeRule(AttributeSelector.createStringSelector(
                AttributeStringUtils.toString(ATTR_SET))).filter(o, REQ).getResourcesCount());
    }

    @Test
    public void testExactMatchesRegex() {
        Offer.Builder o = getOfferWithResources()
                .addAttributes(ATTR_TEXT);
        assertEquals(o.build(), new AttributeRule(AttributeSelector.createRegexSelector(
                ATTR_TEXT_REGEX)).filter(o.build(), REQ));

        o = getOfferWithResources()
                .addAttributes(ATTR_SCALAR);
        assertEquals(o.build(), new AttributeRule(AttributeSelector.createRegexSelector(
                ATTR_SCALAR_REGEX)).filter(o.build(), REQ));

        o = getOfferWithResources()
                .addAttributes(ATTR_RANGES);
        assertEquals(o.build(), new AttributeRule(AttributeSelector.createRegexSelector(
                ATTR_RANGES_REGEX)).filter(o.build(), REQ));

        o = getOfferWithResources()
                .addAttributes(ATTR_SET);
        assertEquals(o.build(), new AttributeRule(AttributeSelector.createRegexSelector(
                ATTR_SET_REGEX)).filter(o.build(), REQ));
    }

    @Test
    public void testAnyMatchesRegex() {
        Offer o = getOfferWithResources()
                .addAttributes(ATTR_TEXT)
                .addAttributes(ATTR_SCALAR)
                .addAttributes(ATTR_RANGES)
                .addAttributes(ATTR_SET)
                .build();
        assertEquals(o, new AttributeRule(AttributeSelector.createRegexSelector(
                ATTR_TEXT_REGEX)).filter(o, REQ));
        assertEquals(o, new AttributeRule(AttributeSelector.createRegexSelector(
                ATTR_SCALAR_REGEX)).filter(o, REQ));
        assertEquals(o, new AttributeRule(AttributeSelector.createRegexSelector(
                ATTR_RANGES_REGEX)).filter(o, REQ));
        assertEquals(o, new AttributeRule(AttributeSelector.createRegexSelector(
                ATTR_SET_REGEX)).filter(o, REQ));
    }

    @Test
    public void testMismatchesRegex() {
        Offer o = getOfferWithResources()
                .addAttributes(ATTR_SCALAR)
                .addAttributes(ATTR_SET)
                .build();
        assertEquals(0, new AttributeRule(AttributeSelector.createRegexSelector(
                ATTR_TEXT_REGEX)).filter(o, REQ).getResourcesCount());
        assertEquals(o, new AttributeRule(AttributeSelector.createRegexSelector(
                ATTR_SCALAR_REGEX)).filter(o, REQ));
        assertEquals(0, new AttributeRule(AttributeSelector.createRegexSelector(
                ATTR_RANGES_REGEX)).filter(o, REQ).getResourcesCount());
        assertEquals(o, new AttributeRule(AttributeSelector.createRegexSelector(
                ATTR_SET_REGEX)).filter(o, REQ));

        o = getOfferWithResources()
                .addAttributes(ATTR_RANGES)
                .addAttributes(ATTR_TEXT)
                .build();
        assertEquals(o, new AttributeRule(AttributeSelector.createRegexSelector(
                ATTR_TEXT_REGEX)).filter(o, REQ));
        assertEquals(0, new AttributeRule(AttributeSelector.createRegexSelector(
                ATTR_SCALAR_REGEX)).filter(o, REQ).getResourcesCount());
        assertEquals(o, new AttributeRule(AttributeSelector.createRegexSelector(
                ATTR_RANGES_REGEX)).filter(o, REQ));
        assertEquals(0, new AttributeRule(AttributeSelector.createRegexSelector(
                ATTR_SET_REGEX)).filter(o, REQ).getResourcesCount());
    }

    @Test
    public void testSerializeDeserialize() throws IOException {
        PlacementRuleGenerator generator = new AttributeRule.Generator(
                AttributeSelector.createStringSelector(AttributeStringUtils.toString(ATTR_SCALAR)));
        assertEquals(generator, SerializationUtils.fromJsonString(SerializationUtils.toJsonString(generator), PlacementRuleGenerator.class));

        generator = new AttributeRule.Generator(
                AttributeSelector.createRegexSelector(ATTR_RANGES_REGEX));
        assertEquals(generator, SerializationUtils.fromJsonString(SerializationUtils.toJsonString(generator), PlacementRuleGenerator.class));
    }

    private static Offer.Builder getOfferWithResources() {
        Offer.Builder o = OfferTestUtils.getEmptyOfferBuilder();
        OfferTestUtils.addResource(o, "a");
        OfferTestUtils.addResource(o, "b");
        return o;
    }
}
