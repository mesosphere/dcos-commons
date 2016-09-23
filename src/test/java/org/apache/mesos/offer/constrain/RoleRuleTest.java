package org.apache.mesos.offer.constrain;

import org.apache.mesos.Protos.Offer;
import org.apache.mesos.testutils.OfferTestUtils;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

import java.util.ArrayList;

/**
 * Tests for {@link RoleRule}.
 */
public class RoleRuleTest {

    private static final String ROLE = "some_role";
    private static final String DEFAULT_ROLE = "*";

    @Test
    public void testAllMatch() {
        Offer.Builder o = OfferTestUtils.getEmptyOfferBuilder();
        OfferTestUtils.addResource(o, "a", ROLE);
        OfferTestUtils.addResource(o, "b", ROLE);
        OfferTestUtils.addResource(o, "c", ROLE);
        Offer offer = new RoleRule(ROLE).filter(o.build());
        assertEquals(3, offer.getResourcesCount());
        assertEquals("a", offer.getResources(0).getName());
        assertEquals("b", offer.getResources(1).getName());
        assertEquals("c", offer.getResources(2).getName());
    }

    @Test
    public void testSomeMatch() {
        Offer.Builder o = OfferTestUtils.getEmptyOfferBuilder();
        OfferTestUtils.addResource(o, "a", ROLE);
        OfferTestUtils.addResource(o, "b", ROLE.toUpperCase());
        OfferTestUtils.addResource(o, "c", DEFAULT_ROLE);
        OfferTestUtils.addResource(o, "d", ROLE);
        OfferTestUtils.addResource(o, "e", ROLE.substring(1));
        OfferTestUtils.addResource(o, "f");
        Offer offer = new RoleRule(ROLE).filter(o.build());
        assertEquals(2, offer.getResourcesCount());
        assertEquals("a", offer.getResources(0).getName());
        assertEquals("d", offer.getResources(1).getName());
    }

    @Test
    public void testGeneratorSomeMatch() {
        Offer.Builder o = OfferTestUtils.getEmptyOfferBuilder();
        OfferTestUtils.addResource(o, "a", ROLE);
        OfferTestUtils.addResource(o, "b", ROLE.toUpperCase());
        OfferTestUtils.addResource(o, "c", DEFAULT_ROLE);
        OfferTestUtils.addResource(o, "d", ROLE);
        OfferTestUtils.addResource(o, "e", ROLE.substring(1));
        OfferTestUtils.addResource(o, "f");
        Offer offer = new RoleRule.Generator(ROLE).generate(new ArrayList<>()).filter(o.build());
        assertEquals(2, offer.getResourcesCount());
        assertEquals("a", offer.getResources(0).getName());
        assertEquals("d", offer.getResources(1).getName());
    }

    @Test
    public void testAsteriskRole() {
        Offer.Builder o = OfferTestUtils.getEmptyOfferBuilder();
        OfferTestUtils.addResource(o, "a", ROLE);
        OfferTestUtils.addResource(o, "b", ROLE.toUpperCase());
        OfferTestUtils.addResource(o, "c", DEFAULT_ROLE);
        OfferTestUtils.addResource(o, "d", ROLE);
        OfferTestUtils.addResource(o, "e", ROLE.substring(1));
        OfferTestUtils.addResource(o, "f");
        Offer offer = new RoleRule(DEFAULT_ROLE).filter(o.build());
        assertEquals(2, offer.getResourcesCount());
        assertEquals("c", offer.getResources(0).getName());
        assertEquals("f", offer.getResources(1).getName());
    }

    @Test
    public void testNoneMatch() {
        Offer.Builder o = OfferTestUtils.getEmptyOfferBuilder();
        OfferTestUtils.addResource(o, "a", ROLE.toUpperCase());
        OfferTestUtils.addResource(o, "b", DEFAULT_ROLE);
        OfferTestUtils.addResource(o, "c", ROLE.substring(1));
        OfferTestUtils.addResource(o, "d");
        Offer offer = new RoleRule(ROLE).filter(o.build());
        assertEquals(0, offer.getResourcesCount());
    }

    @Test
    public void testEmptyOffer() {
        Offer offer = new RoleRule(ROLE).filter(OfferTestUtils.getEmptyOfferBuilder().build());
        assertEquals(0, offer.getResourcesCount());
    }
}
