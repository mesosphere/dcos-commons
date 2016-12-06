package com.mesosphere.sdk.offer.constrain;

import com.mesosphere.sdk.testutils.OfferRequirementTestUtils;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;

import org.apache.mesos.Protos.Offer;
import org.apache.mesos.Protos.Resource;
import com.mesosphere.sdk.config.SerializationUtils;
import com.mesosphere.sdk.offer.OfferRequirement;
import com.mesosphere.sdk.testutils.OfferTestUtils;

/**
 * Tests for {@link OrRule}.
 */
public class OrRuleTest {

    private static final OfferRequirement REQ = OfferRequirementTestUtils.getOfferRequirement();

    @Test
    public void testEmpty() {
        Offer o = new OrRule()
                .filter(offerWith(TestPlacementUtils.RESOURCES), REQ, Collections.emptyList());
        assertTrue(o.getResourcesList().isEmpty());
    }

    @Test
    public void testAllPass() {
        Offer o = new OrRule(TestPlacementUtils.ALL)
                .filter(offerWith(TestPlacementUtils.RESOURCES), REQ, Collections.emptyList());
        assertEquals(offerWith(TestPlacementUtils.RESOURCES), o);

        o = new OrRule(TestPlacementUtils.ALL, TestPlacementUtils.ALL, TestPlacementUtils.ALL)
                .filter(offerWith(TestPlacementUtils.RESOURCES), REQ, Collections.emptyList());
        assertEquals(offerWith(TestPlacementUtils.RESOURCES), o);
    }

    @Test
    public void testAllFail() {
        Offer o = new OrRule(TestPlacementUtils.NONE)
                .filter(offerWith(TestPlacementUtils.RESOURCES), REQ, Collections.emptyList());
        assertTrue(o.getResourcesList().isEmpty());

        o = new OrRule(TestPlacementUtils.NONE, TestPlacementUtils.ALL)
                .filter(offerWith(TestPlacementUtils.RESOURCES), REQ, Collections.emptyList());
        assertEquals(offerWith(TestPlacementUtils.RESOURCES), o);

        o = new OrRule(TestPlacementUtils.ALL, TestPlacementUtils.NONE)
                .filter(offerWith(TestPlacementUtils.RESOURCES), REQ, Collections.emptyList());
        assertEquals(offerWith(TestPlacementUtils.RESOURCES), o);

        o = new OrRule(TestPlacementUtils.NONE, TestPlacementUtils.NONE)
                .filter(offerWith(TestPlacementUtils.RESOURCES), REQ, Collections.emptyList());
        assertTrue(o.getResourcesList().isEmpty());

        o = new OrRule(TestPlacementUtils.NONE, TestPlacementUtils.ALL, TestPlacementUtils.NONE)
                .filter(offerWith(TestPlacementUtils.RESOURCES), REQ, Collections.emptyList());
        assertEquals(offerWith(TestPlacementUtils.RESOURCES), o);

        o = new OrRule(TestPlacementUtils.ALL, TestPlacementUtils.NONE, TestPlacementUtils.ALL)
                .filter(offerWith(TestPlacementUtils.RESOURCES), REQ, Collections.emptyList());
        assertEquals(offerWith(TestPlacementUtils.RESOURCES), o);

        o = new OrRule(TestPlacementUtils.NONE, TestPlacementUtils.NONE, TestPlacementUtils.NONE)
                .filter(offerWith(TestPlacementUtils.RESOURCES), REQ, Collections.emptyList());
        assertTrue(o.getResourcesList().isEmpty());
    }

    @Test
    public void testRemoveFirstPermutations() {
        Offer o = new OrRule(TestPlacementUtils.REMOVE_FIRST)
                .filter(offerWith(TestPlacementUtils.RESOURCES), REQ, Collections.emptyList());
        assertEquals(offerWith(TestPlacementUtils.RESOURCE_2, TestPlacementUtils.RESOURCE_3, TestPlacementUtils.RESOURCE_4), o);

        o = new OrRule(TestPlacementUtils.REMOVE_FIRST, TestPlacementUtils.ALL)
                .filter(offerWith(TestPlacementUtils.RESOURCES), REQ, Collections.emptyList());
        assertEquals(offerWith(TestPlacementUtils.RESOURCE_1, TestPlacementUtils.RESOURCE_2, TestPlacementUtils.RESOURCE_3, TestPlacementUtils.RESOURCE_4), o);

        o = new OrRule(TestPlacementUtils.ALL, TestPlacementUtils.REMOVE_FIRST)
                .filter(offerWith(TestPlacementUtils.RESOURCES), REQ, Collections.emptyList());
        assertEquals(offerWith(TestPlacementUtils.RESOURCE_1, TestPlacementUtils.RESOURCE_2, TestPlacementUtils.RESOURCE_3, TestPlacementUtils.RESOURCE_4), o);

        o = new OrRule(TestPlacementUtils.REMOVE_FIRST, TestPlacementUtils.REMOVE_FIRST)
                .filter(offerWith(TestPlacementUtils.RESOURCES), REQ, Collections.emptyList());
        assertEquals(offerWith(TestPlacementUtils.RESOURCE_2, TestPlacementUtils.RESOURCE_3, TestPlacementUtils.RESOURCE_4), o);

        o = new OrRule(TestPlacementUtils.REMOVE_FIRST, TestPlacementUtils.ALL, TestPlacementUtils.REMOVE_FIRST)
                .filter(offerWith(TestPlacementUtils.RESOURCES), REQ, Collections.emptyList());
        assertEquals(offerWith(TestPlacementUtils.RESOURCE_1, TestPlacementUtils.RESOURCE_2, TestPlacementUtils.RESOURCE_3, TestPlacementUtils.RESOURCE_4), o);

        o = new OrRule(TestPlacementUtils.ALL, TestPlacementUtils.REMOVE_FIRST, TestPlacementUtils.ALL)
                .filter(offerWith(TestPlacementUtils.RESOURCES), REQ, Collections.emptyList());
        assertEquals(offerWith(TestPlacementUtils.RESOURCE_1, TestPlacementUtils.RESOURCE_2, TestPlacementUtils.RESOURCE_3, TestPlacementUtils.RESOURCE_4), o);

        o = new OrRule(TestPlacementUtils.REMOVE_FIRST, TestPlacementUtils.REMOVE_FIRST, TestPlacementUtils.REMOVE_FIRST)
                .filter(offerWith(TestPlacementUtils.RESOURCES), REQ, Collections.emptyList());
        assertEquals(offerWith(TestPlacementUtils.RESOURCE_2, TestPlacementUtils.RESOURCE_3, TestPlacementUtils.RESOURCE_4), o);
    }

    @Test
    public void testRemoveLastPermutations() {
        Offer o = new OrRule(TestPlacementUtils.REMOVE_LAST)
                .filter(offerWith(TestPlacementUtils.RESOURCES), REQ, Collections.emptyList());
        assertEquals(offerWith(TestPlacementUtils.RESOURCE_1, TestPlacementUtils.RESOURCE_2, TestPlacementUtils.RESOURCE_3), o);

        o = new OrRule(TestPlacementUtils.REMOVE_LAST, TestPlacementUtils.ALL)
                .filter(offerWith(TestPlacementUtils.RESOURCES), REQ, Collections.emptyList());
        assertEquals(offerWith(TestPlacementUtils.RESOURCE_1, TestPlacementUtils.RESOURCE_2, TestPlacementUtils.RESOURCE_3, TestPlacementUtils.RESOURCE_4), o);

        o = new OrRule(TestPlacementUtils.ALL, TestPlacementUtils.REMOVE_LAST)
                .filter(offerWith(TestPlacementUtils.RESOURCES), REQ, Collections.emptyList());
        assertEquals(offerWith(TestPlacementUtils.RESOURCE_1, TestPlacementUtils.RESOURCE_2, TestPlacementUtils.RESOURCE_3, TestPlacementUtils.RESOURCE_4), o);

        o = new OrRule(TestPlacementUtils.REMOVE_LAST, TestPlacementUtils.REMOVE_LAST)
                .filter(offerWith(TestPlacementUtils.RESOURCES), REQ, Collections.emptyList());
        assertEquals(offerWith(TestPlacementUtils.RESOURCE_1, TestPlacementUtils.RESOURCE_2, TestPlacementUtils.RESOURCE_3), o);

        o = new OrRule(TestPlacementUtils.REMOVE_LAST, TestPlacementUtils.ALL, TestPlacementUtils.REMOVE_LAST)
                .filter(offerWith(TestPlacementUtils.RESOURCES), REQ, Collections.emptyList());
        assertEquals(offerWith(TestPlacementUtils.RESOURCE_1, TestPlacementUtils.RESOURCE_2, TestPlacementUtils.RESOURCE_3, TestPlacementUtils.RESOURCE_4), o);

        o = new OrRule(TestPlacementUtils.ALL, TestPlacementUtils.REMOVE_LAST, TestPlacementUtils.ALL)
                .filter(offerWith(TestPlacementUtils.RESOURCES), REQ, Collections.emptyList());
        assertEquals(offerWith(TestPlacementUtils.RESOURCE_1, TestPlacementUtils.RESOURCE_2, TestPlacementUtils.RESOURCE_3, TestPlacementUtils.RESOURCE_4), o);

        o = new OrRule(TestPlacementUtils.REMOVE_LAST, TestPlacementUtils.REMOVE_LAST, TestPlacementUtils.REMOVE_LAST)
                .filter(offerWith(TestPlacementUtils.RESOURCES), REQ, Collections.emptyList());
        assertEquals(offerWith(TestPlacementUtils.RESOURCE_1, TestPlacementUtils.RESOURCE_2, TestPlacementUtils.RESOURCE_3), o);
    }

    @Test
    public void testRemoveFirstOrRemoveLastPermutations() {
        Offer o = new OrRule(TestPlacementUtils.REMOVE_LAST, TestPlacementUtils.REMOVE_FIRST, TestPlacementUtils.ALL)
                .filter(offerWith(TestPlacementUtils.RESOURCES), REQ, Collections.emptyList());
        assertEquals(offerWith(TestPlacementUtils.RESOURCE_1, TestPlacementUtils.RESOURCE_2, TestPlacementUtils.RESOURCE_3, TestPlacementUtils.RESOURCE_4), o);

        o = new OrRule(TestPlacementUtils.REMOVE_FIRST, TestPlacementUtils.ALL, TestPlacementUtils.REMOVE_LAST)
                .filter(offerWith(TestPlacementUtils.RESOURCES), REQ, Collections.emptyList());
        assertEquals(offerWith(TestPlacementUtils.RESOURCE_1, TestPlacementUtils.RESOURCE_2, TestPlacementUtils.RESOURCE_3, TestPlacementUtils.RESOURCE_4), o);

        o = new OrRule(TestPlacementUtils.REMOVE_LAST, TestPlacementUtils.REMOVE_FIRST)
                .filter(offerWith(TestPlacementUtils.RESOURCES), REQ, Collections.emptyList());
        assertEquals(offerWith(TestPlacementUtils.RESOURCE_1, TestPlacementUtils.RESOURCE_2, TestPlacementUtils.RESOURCE_3, TestPlacementUtils.RESOURCE_4), o);

        o = new OrRule(TestPlacementUtils.REMOVE_FIRST, TestPlacementUtils.REMOVE_LAST, TestPlacementUtils.REMOVE_FIRST, TestPlacementUtils.REMOVE_LAST)
                .filter(offerWith(TestPlacementUtils.RESOURCES), REQ, Collections.emptyList());
        assertEquals(offerWith(TestPlacementUtils.RESOURCE_1, TestPlacementUtils.RESOURCE_2, TestPlacementUtils.RESOURCE_3, TestPlacementUtils.RESOURCE_4), o);
    }

    @Test
    public void testSerializeDeserialize() throws IOException {
        PlacementRule rule = new OrRule(
                TestPlacementUtils.REMOVE_LAST,
                TestPlacementUtils.REMOVE_FIRST,
                TestPlacementUtils.ALL);
        assertEquals(rule, SerializationUtils.fromString(
                SerializationUtils.toJsonString(rule), PlacementRule.class, TestPlacementUtils.OBJECT_MAPPER));
    }

    private static Offer offerWith(Collection<Resource> resources) {
        Offer.Builder o = OfferTestUtils.getEmptyOfferBuilder();
        for (Resource r : resources) {
            o.addResources(r);
        }
        return o.build();
    }

    private static Offer offerWith(Resource... resources) {
        return offerWith(Arrays.asList(resources));
    }
}
