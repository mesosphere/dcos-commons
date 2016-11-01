package org.apache.mesos.offer.constrain;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;

import org.apache.mesos.Protos.Offer;
import org.apache.mesos.Protos.Resource;
import org.apache.mesos.config.SerializationUtils;
import org.apache.mesos.offer.OfferRequirement;
import org.apache.mesos.testutils.OfferRequirementTestUtils;
import org.apache.mesos.testutils.OfferTestUtils;

/**
 * Tests for {@link NotRule}.
 */
public class NotRuleTest {

    private static final OfferRequirement REQ = OfferRequirementTestUtils.getOfferRequirement();

    @Test
    public void testNotAll() {
        Offer o = new NotRule(TestPlacementUtils.ALL)
                .filter(offerWith(TestPlacementUtils.RESOURCES), REQ);
        assertTrue(o.getResourcesList().isEmpty());
    }

    @Test
    public void testNotNone() {
        Offer o = new NotRule(TestPlacementUtils.NONE)
                .filter(offerWith(TestPlacementUtils.RESOURCES), REQ);
        assertEquals(offerWith(TestPlacementUtils.RESOURCES), o);
    }

    @Test
    public void testNotFirstRemoved() {
        Offer o = new NotRule(TestPlacementUtils.REMOVE_FIRST)
                .filter(offerWith(TestPlacementUtils.RESOURCES), REQ);
        assertEquals(offerWith(TestPlacementUtils.RESOURCE_1), o);
    }

    @Test
    public void testNotLastRemoved() {
        Offer o = new NotRule(TestPlacementUtils.REMOVE_LAST)
                .filter(offerWith(TestPlacementUtils.RESOURCES), REQ);
        assertEquals(offerWith(TestPlacementUtils.RESOURCE_4), o);
    }

    @Test
    public void testGenerator() {
        Offer o = new NotRule.Generator(new PassthroughGenerator(TestPlacementUtils.REMOVE_LAST))
                .generate(Collections.emptyList())
                .filter(offerWith(TestPlacementUtils.RESOURCES), REQ);
        assertEquals(offerWith(TestPlacementUtils.RESOURCE_4), o);
    }

    @Test
    public void testSerializeDeserialize() throws IOException {
        PlacementRuleGenerator generator = new NotRule.Generator(new PassthroughGenerator(TestPlacementUtils.REMOVE_LAST));
        assertEquals(generator, SerializationUtils.fromJsonString(SerializationUtils.toJsonString(generator), PlacementRuleGenerator.class));

        generator = new NotRule.Generator(new HostnameRule.RequireHostnamesGenerator("foo", "bar"));
        assertEquals(generator, SerializationUtils.fromJsonString(SerializationUtils.toJsonString(generator), PlacementRuleGenerator.class));
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
