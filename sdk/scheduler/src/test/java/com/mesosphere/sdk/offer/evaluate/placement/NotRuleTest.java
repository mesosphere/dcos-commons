package com.mesosphere.sdk.offer.evaluate.placement;

import com.mesosphere.sdk.testutils.OfferRequirementTestUtils;
import com.mesosphere.sdk.testutils.OfferTestUtils;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
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
        assertFalse(new NotRule(TestPlacementUtils.PASS)
                .filter(offerWith(TestPlacementUtils.RESOURCES), REQ, Collections.emptyList()).isPassing());
    }

    @Test
    public void testNotNone() {
        assertTrue(new NotRule(TestPlacementUtils.FAIL)
                .filter(offerWith(TestPlacementUtils.RESOURCES), REQ, Collections.emptyList()).isPassing());
    }

    @Test
    public void testSerializeDeserialize() throws IOException {
        PlacementRule rule = new NotRule(TestPlacementUtils.PASS);
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
}
