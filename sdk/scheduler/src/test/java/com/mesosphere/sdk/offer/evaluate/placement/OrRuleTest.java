package com.mesosphere.sdk.offer.evaluate.placement;

import com.mesosphere.sdk.testutils.OfferRequirementTestUtils;
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
import com.mesosphere.sdk.testutils.OfferTestUtils;

/**
 * Tests for {@link OrRule}.
 */
public class OrRuleTest {

    private static final OfferRequirement REQ = OfferRequirementTestUtils.getOfferRequirement();

    @Test
    public void testEmpty() {
        assertFalse(new OrRule()
                .filter(offerWith(TestPlacementUtils.RESOURCES), REQ, Collections.emptyList()).isPassing());
    }

    @Test
    public void testAllPass() {
        assertTrue(new OrRule(TestPlacementUtils.PASS)
                .filter(offerWith(TestPlacementUtils.RESOURCES), REQ, Collections.emptyList()).isPassing());

        assertTrue(new OrRule(TestPlacementUtils.PASS, TestPlacementUtils.PASS, TestPlacementUtils.PASS)
                .filter(offerWith(TestPlacementUtils.RESOURCES), REQ, Collections.emptyList()).isPassing());
    }

    @Test
    public void testAllFail() {
        assertFalse(new OrRule(TestPlacementUtils.FAIL)
                .filter(offerWith(TestPlacementUtils.RESOURCES), REQ, Collections.emptyList()).isPassing());

        assertTrue(new OrRule(TestPlacementUtils.FAIL, TestPlacementUtils.PASS)
                .filter(offerWith(TestPlacementUtils.RESOURCES), REQ, Collections.emptyList()).isPassing());

        assertTrue(new OrRule(TestPlacementUtils.PASS, TestPlacementUtils.FAIL)
                .filter(offerWith(TestPlacementUtils.RESOURCES), REQ, Collections.emptyList()).isPassing());

        assertFalse(new OrRule(TestPlacementUtils.FAIL, TestPlacementUtils.FAIL)
                .filter(offerWith(TestPlacementUtils.RESOURCES), REQ, Collections.emptyList()).isPassing());

        assertTrue(new OrRule(TestPlacementUtils.FAIL, TestPlacementUtils.PASS, TestPlacementUtils.FAIL)
                .filter(offerWith(TestPlacementUtils.RESOURCES), REQ, Collections.emptyList()).isPassing());

        assertTrue(new OrRule(TestPlacementUtils.PASS, TestPlacementUtils.FAIL, TestPlacementUtils.PASS)
                .filter(offerWith(TestPlacementUtils.RESOURCES), REQ, Collections.emptyList()).isPassing());

        assertFalse(new OrRule(TestPlacementUtils.FAIL, TestPlacementUtils.FAIL, TestPlacementUtils.FAIL)
                .filter(offerWith(TestPlacementUtils.RESOURCES), REQ, Collections.emptyList()).isPassing());
    }

    @Test
    public void testSerializeDeserialize() throws IOException {
        PlacementRule rule = new OrRule(TestPlacementUtils.FAIL, TestPlacementUtils.PASS, TestPlacementUtils.PASS);
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
