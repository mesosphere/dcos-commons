package com.mesosphere.sdk.offer.constrain;

import com.mesosphere.sdk.testutils.OfferRequirementTestUtils;
import com.mesosphere.sdk.testutils.OfferTestUtils;
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

/**
 * Tests for {@link NotRule}.
 */
public class NotRuleTest {

    private static final OfferRequirement REQ = OfferRequirementTestUtils.getOfferRequirement();

    @Test
    public void testNotAll() {
        Offer o = new NotRule(TestPlacementUtils.ALL)
                .filter(offerWith(TestPlacementUtils.RESOURCES), REQ, Collections.emptyList());
        assertTrue(o.getResourcesList().isEmpty());
    }

    @Test
    public void testNotNone() {
        Offer o = new NotRule(TestPlacementUtils.NONE)
                .filter(offerWith(TestPlacementUtils.RESOURCES), REQ, Collections.emptyList());
        assertEquals(offerWith(TestPlacementUtils.RESOURCES), o);
    }

    @Test
    public void testNotFirstRemoved() {
        Offer o = new NotRule(TestPlacementUtils.REMOVE_FIRST)
                .filter(offerWith(TestPlacementUtils.RESOURCES), REQ, Collections.emptyList());
        assertEquals(offerWith(TestPlacementUtils.RESOURCE_1), o);
    }

    @Test
    public void testNotLastRemoved() {
        Offer o = new NotRule(TestPlacementUtils.REMOVE_LAST)
                .filter(offerWith(TestPlacementUtils.RESOURCES), REQ, Collections.emptyList());
        assertEquals(offerWith(TestPlacementUtils.RESOURCE_4), o);
    }

    @Test
    public void testSerializeDeserialize() throws IOException {
        PlacementRule rule = new NotRule(TestPlacementUtils.REMOVE_LAST);
        assertEquals(rule, SerializationUtils.fromString(
                SerializationUtils.toJsonString(rule), PlacementRule.class, TestPlacementUtils.OBJECT_MAPPER));

        rule = new NotRule(HostnameRule.requireExact("foo", "bar"));
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
