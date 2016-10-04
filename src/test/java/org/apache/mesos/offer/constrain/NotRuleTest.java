package org.apache.mesos.offer.constrain;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;

import org.apache.mesos.Protos.Offer;
import org.apache.mesos.Protos.Resource;
import org.apache.mesos.Protos.Value;
import org.apache.mesos.testutils.OfferTestUtils;

/**
 * Tests for {@link NotRule}.
 */
public class NotRuleTest {

    private static final Resource RESOURCE_1;
    private static final Resource RESOURCE_2;
    private static final Resource RESOURCE_3;
    private static final Resource RESOURCE_4;

    private static final Collection<Resource> RESOURCES;

    static {
        Resource.Builder b = Resource.newBuilder().setName("1").setType(Value.Type.RANGES);
        b.getRangesBuilder().addRangeBuilder().setBegin(5).setEnd(6);
        b.getRangesBuilder().addRangeBuilder().setBegin(10).setEnd(12);
        RESOURCE_1 = b.build();

        b = Resource.newBuilder().setName("2").setType(Value.Type.SCALAR);
        b.getScalarBuilder().setValue(123.456);
        RESOURCE_2 = b.build();

        b = Resource.newBuilder().setName("3").setType(Value.Type.SET);
        b.getSetBuilder().addItem("foo").addItem("bar").addItem("baz");
        RESOURCE_3 = b.build();

        b = Resource.newBuilder().setName("4").setType(Value.Type.RANGES);
        b.getRangesBuilder().addRangeBuilder().setBegin(7).setEnd(8);
        b.getRangesBuilder().addRangeBuilder().setBegin(10).setEnd(12);
        RESOURCE_4 = b.build();

        RESOURCES = Arrays.asList(RESOURCE_1, RESOURCE_2, RESOURCE_3, RESOURCE_4);
    }

    private static final PlacementRule ALL = new PlacementRule() {
        @Override
        public Offer filter(Offer offer) {
            return offer;
        }
    };
    private static final PlacementRule NONE = new PlacementRule() {
        @Override
        public Offer filter(Offer offer) {
            return offer.toBuilder().clearResources().build();
        }
    };
    private static final PlacementRule REMOVE_FIRST = new PlacementRule() {
        @Override
        public Offer filter(Offer offer) {
            if (offer.getResourcesCount() == 0) {
                return offer;
            }
            return offer.toBuilder().removeResources(0).build();
        }
    };
    private static final PlacementRule REMOVE_LAST = new PlacementRule() {
        @Override
        public Offer filter(Offer offer) {
            int count = offer.getResourcesCount();
            if (count == 0) {
                return offer;
            }
            return offer.toBuilder().removeResources(count - 1).build();
        }
    };

    @Test
    public void testNotAll() {
        Offer o = new NotRule(ALL).filter(offerWith(RESOURCES));
        assertTrue(o.getResourcesList().isEmpty());
    }

    @Test
    public void testNotNone() {
        Offer o = new NotRule(NONE).filter(offerWith(RESOURCES));
        assertEquals(offerWith(RESOURCES), o);
    }

    @Test
    public void testNotFirstRemoved() {
        Offer o = new NotRule(REMOVE_FIRST).filter(offerWith(RESOURCES));
        assertEquals(offerWith(RESOURCE_1), o);
    }

    @Test
    public void testNotLastRemoved() {
        Offer o = new NotRule(REMOVE_LAST).filter(offerWith(RESOURCES));
        assertEquals(offerWith(RESOURCE_4), o);
    }

    @Test
    public void testGenerator() {
        Offer o = new NotRule.Generator(new PassthroughGenerator(REMOVE_LAST))
                .generate(Collections.emptyList())
                .filter(offerWith(RESOURCES));
        assertEquals(offerWith(RESOURCE_4), o);
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
