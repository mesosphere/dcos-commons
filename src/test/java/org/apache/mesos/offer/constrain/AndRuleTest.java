package org.apache.mesos.offer.constrain;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;

import org.apache.mesos.Protos.Offer;
import org.apache.mesos.Protos.Resource;
import org.apache.mesos.testutils.OfferTestUtils;

/**
 * Tests for {@link AndRule}.
 */
public class AndRuleTest {

    @Test
    public void testEmpty() {
        Offer o = new AndRule()
                .filter(offerWith(TestPlacementUtils.RESOURCES));
        assertTrue(o.getResourcesList().isEmpty());
    }

    @Test
    public void testAllPass() {
        Offer o = new AndRule(TestPlacementUtils.ALL)
                .filter(offerWith(TestPlacementUtils.RESOURCES));
        assertEquals(offerWith(TestPlacementUtils.RESOURCES), o);

        o = new AndRule(TestPlacementUtils.ALL, TestPlacementUtils.ALL, TestPlacementUtils.ALL)
                .filter(offerWith(TestPlacementUtils.RESOURCES));
        assertEquals(offerWith(TestPlacementUtils.RESOURCES), o);
    }

    @Test
    public void testAllFail() {
        Offer o = new AndRule(TestPlacementUtils.NONE)
                .filter(offerWith(TestPlacementUtils.RESOURCES));
        assertTrue(o.getResourcesList().isEmpty());

        o = new AndRule(TestPlacementUtils.NONE, TestPlacementUtils.ALL)
                .filter(offerWith(TestPlacementUtils.RESOURCES));
        assertTrue(o.getResourcesList().isEmpty());

        o = new AndRule(TestPlacementUtils.ALL, TestPlacementUtils.NONE)
                .filter(offerWith(TestPlacementUtils.RESOURCES));
        assertTrue(o.getResourcesList().isEmpty());

        o = new AndRule(TestPlacementUtils.NONE, TestPlacementUtils.NONE)
                .filter(offerWith(TestPlacementUtils.RESOURCES));
        assertTrue(o.getResourcesList().isEmpty());

        o = new AndRule(TestPlacementUtils.NONE, TestPlacementUtils.ALL, TestPlacementUtils.NONE)
                .filter(offerWith(TestPlacementUtils.RESOURCES));
        assertTrue(o.getResourcesList().isEmpty());

        o = new AndRule(TestPlacementUtils.ALL, TestPlacementUtils.NONE, TestPlacementUtils.ALL)
                .filter(offerWith(TestPlacementUtils.RESOURCES));
        assertTrue(o.getResourcesList().isEmpty());

        o = new AndRule(TestPlacementUtils.NONE, TestPlacementUtils.NONE, TestPlacementUtils.NONE)
                .filter(offerWith(TestPlacementUtils.RESOURCES));
        assertTrue(o.getResourcesList().isEmpty());
    }

    @Test
    public void testRemoveFirstPermutations() {
        Offer o = new AndRule(TestPlacementUtils.REMOVE_FIRST)
                .filter(offerWith(TestPlacementUtils.RESOURCES));
        assertEquals(offerWith(TestPlacementUtils.RESOURCE_2, TestPlacementUtils.RESOURCE_3, TestPlacementUtils.RESOURCE_4), o);

        o = new AndRule(TestPlacementUtils.REMOVE_FIRST, TestPlacementUtils.ALL)
                .filter(offerWith(TestPlacementUtils.RESOURCES));
        assertEquals(offerWith(TestPlacementUtils.RESOURCE_2, TestPlacementUtils.RESOURCE_3, TestPlacementUtils.RESOURCE_4), o);

        o = new AndRule(TestPlacementUtils.ALL, TestPlacementUtils.REMOVE_FIRST)
                .filter(offerWith(TestPlacementUtils.RESOURCES));
        assertEquals(offerWith(TestPlacementUtils.RESOURCE_2, TestPlacementUtils.RESOURCE_3, TestPlacementUtils.RESOURCE_4), o);

        o = new AndRule(TestPlacementUtils.REMOVE_FIRST, TestPlacementUtils.REMOVE_FIRST)
                .filter(offerWith(TestPlacementUtils.RESOURCES));
        assertEquals(offerWith(TestPlacementUtils.RESOURCE_2, TestPlacementUtils.RESOURCE_3, TestPlacementUtils.RESOURCE_4), o);

        o = new AndRule(TestPlacementUtils.REMOVE_FIRST, TestPlacementUtils.ALL, TestPlacementUtils.REMOVE_FIRST)
                .filter(offerWith(TestPlacementUtils.RESOURCES));
        assertEquals(offerWith(TestPlacementUtils.RESOURCE_2, TestPlacementUtils.RESOURCE_3, TestPlacementUtils.RESOURCE_4), o);

        o = new AndRule(TestPlacementUtils.ALL, TestPlacementUtils.REMOVE_FIRST, TestPlacementUtils.ALL)
                .filter(offerWith(TestPlacementUtils.RESOURCES));
        assertEquals(offerWith(TestPlacementUtils.RESOURCE_2, TestPlacementUtils.RESOURCE_3, TestPlacementUtils.RESOURCE_4), o);

        o = new AndRule(TestPlacementUtils.REMOVE_FIRST, TestPlacementUtils.REMOVE_FIRST, TestPlacementUtils.REMOVE_FIRST)
                .filter(offerWith(TestPlacementUtils.RESOURCES));
        assertEquals(offerWith(TestPlacementUtils.RESOURCE_2, TestPlacementUtils.RESOURCE_3, TestPlacementUtils.RESOURCE_4), o);
    }

    @Test
    public void testRemoveLastPermutations() {
        Offer o = new AndRule(TestPlacementUtils.REMOVE_LAST)
                .filter(offerWith(TestPlacementUtils.RESOURCES));
        assertEquals(offerWith(TestPlacementUtils.RESOURCE_1, TestPlacementUtils.RESOURCE_2, TestPlacementUtils.RESOURCE_3), o);

        o = new AndRule(TestPlacementUtils.REMOVE_LAST, TestPlacementUtils.ALL)
                .filter(offerWith(TestPlacementUtils.RESOURCES));
        assertEquals(offerWith(TestPlacementUtils.RESOURCE_1, TestPlacementUtils.RESOURCE_2, TestPlacementUtils.RESOURCE_3), o);

        o = new AndRule(TestPlacementUtils.ALL, TestPlacementUtils.REMOVE_LAST)
                .filter(offerWith(TestPlacementUtils.RESOURCES));
        assertEquals(offerWith(TestPlacementUtils.RESOURCE_1, TestPlacementUtils.RESOURCE_2, TestPlacementUtils.RESOURCE_3), o);

        o = new AndRule(TestPlacementUtils.REMOVE_LAST, TestPlacementUtils.REMOVE_LAST)
                .filter(offerWith(TestPlacementUtils.RESOURCES));
        assertEquals(offerWith(TestPlacementUtils.RESOURCE_1, TestPlacementUtils.RESOURCE_2, TestPlacementUtils.RESOURCE_3), o);

        o = new AndRule(TestPlacementUtils.REMOVE_LAST, TestPlacementUtils.ALL, TestPlacementUtils.REMOVE_LAST)
                .filter(offerWith(TestPlacementUtils.RESOURCES));
        assertEquals(offerWith(TestPlacementUtils.RESOURCE_1, TestPlacementUtils.RESOURCE_2, TestPlacementUtils.RESOURCE_3), o);

        o = new AndRule(TestPlacementUtils.ALL, TestPlacementUtils.REMOVE_LAST, TestPlacementUtils.ALL)
                .filter(offerWith(TestPlacementUtils.RESOURCES));
        assertEquals(offerWith(TestPlacementUtils.RESOURCE_1, TestPlacementUtils.RESOURCE_2, TestPlacementUtils.RESOURCE_3), o);

        o = new AndRule(TestPlacementUtils.REMOVE_LAST, TestPlacementUtils.REMOVE_LAST, TestPlacementUtils.REMOVE_LAST)
                .filter(offerWith(TestPlacementUtils.RESOURCES));
        assertEquals(offerWith(TestPlacementUtils.RESOURCE_1, TestPlacementUtils.RESOURCE_2, TestPlacementUtils.RESOURCE_3), o);
    }

    @Test
    public void testRemoveFirstAndRemoveLastPermutations() {
        Offer o = new AndRule(TestPlacementUtils.REMOVE_LAST, TestPlacementUtils.REMOVE_FIRST, TestPlacementUtils.ALL)
                .filter(offerWith(TestPlacementUtils.RESOURCES));
        assertEquals(offerWith(TestPlacementUtils.RESOURCE_2, TestPlacementUtils.RESOURCE_3), o);

        o = new AndRule(TestPlacementUtils.REMOVE_FIRST, TestPlacementUtils.ALL, TestPlacementUtils.REMOVE_LAST)
                .filter(offerWith(TestPlacementUtils.RESOURCES));
        assertEquals(offerWith(TestPlacementUtils.RESOURCE_2, TestPlacementUtils.RESOURCE_3), o);

        o = new AndRule(TestPlacementUtils.REMOVE_LAST, TestPlacementUtils.REMOVE_FIRST)
                .filter(offerWith(TestPlacementUtils.RESOURCES));
        assertEquals(offerWith(TestPlacementUtils.RESOURCE_2, TestPlacementUtils.RESOURCE_3), o);

        o = new AndRule(TestPlacementUtils.REMOVE_FIRST, TestPlacementUtils.REMOVE_LAST, TestPlacementUtils.REMOVE_FIRST, TestPlacementUtils.REMOVE_LAST)
                .filter(offerWith(TestPlacementUtils.RESOURCES));
        assertEquals(offerWith(TestPlacementUtils.RESOURCE_2, TestPlacementUtils.RESOURCE_3), o);
    }

    @Test
    public void testGenerator() {
        PlacementRule r = new AndRule.Generator(
                new PassthroughGenerator(TestPlacementUtils.REMOVE_LAST),
                new PassthroughGenerator(TestPlacementUtils.REMOVE_FIRST),
                new PassthroughGenerator(TestPlacementUtils.ALL))
                .generate(Collections.emptyList());
        Offer o = r.filter(offerWith(TestPlacementUtils.RESOURCES));
        assertEquals(r.toString(), offerWith(TestPlacementUtils.RESOURCE_2, TestPlacementUtils.RESOURCE_3), o);
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
