package com.mesosphere.sdk.scheduler.uninstall;

import com.mesosphere.sdk.offer.OfferRecommendation;
import com.mesosphere.sdk.offer.ResourceCleaner;
import com.mesosphere.sdk.testutils.OfferTestUtils;
import com.mesosphere.sdk.testutils.ResourceTestUtils;
import com.mesosphere.sdk.testutils.TestConstants;
import org.apache.mesos.Protos.Offer;
import org.apache.mesos.Protos.Offer.Operation;
import org.apache.mesos.Protos.Resource;
import org.apache.mesos.Protos.Value;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;

public class UninstallResourceCleanerTest {

    private static final String RESERVED_RESOURCE_1_ID = "reserved-resource-id";
    private static final String RESERVED_RESOURCE_2_ID = "reserved-volume-id";
    private static final String RESERVED_RESOURCE_3_ID = "reserved-cpu-id";
    private static final String RESERVED_RESOURCE_4_ID = "reserved-volume-id2";


    private static final Resource RESERVED_RESOURCE_1 = ResourceTestUtils.getExpectedRanges(
            "ports",
            Collections.singletonList(Value.Range.newBuilder().setBegin(123).setEnd(234).build()),
            RESERVED_RESOURCE_1_ID,
            TestConstants.ROLE,
            TestConstants.PRINCIPAL);

    private static final Resource RESERVED_RESOURCE_2 = ResourceTestUtils.getExpectedRootVolume(
            999.0,
            RESERVED_RESOURCE_2_ID,
            TestConstants.CONTAINER_PATH,
            TestConstants.ROLE,
            TestConstants.PRINCIPAL,
            RESERVED_RESOURCE_2_ID);

    private static final Resource RESERVED_RESOURCE_3 = ResourceTestUtils.getExpectedScalar(
            "cpus",
            1.0,
            RESERVED_RESOURCE_3_ID,
            TestConstants.ROLE,
            TestConstants.PRINCIPAL);

    private static final Resource RESERVED_RESOURCE_4 = ResourceTestUtils.getExpectedRootVolume(
            999.0,
            RESERVED_RESOURCE_4_ID,
            TestConstants.CONTAINER_PATH,
            TestConstants.ROLE,
            TestConstants.PRINCIPAL,
            RESERVED_RESOURCE_4_ID);

    private static final Resource UNRESERVED_RESOURCE_1 = ResourceTestUtils.getUnreservedRootVolume(1000.0);


    private static final Resource UNRESERVED_RESOURCE_2 = ResourceTestUtils.getUnreservedScalar("cpus", 1.0);

    private ResourceCleaner uninstallResourceCleaner = new UninstallResourceCleaner();

    @Test
    public void testReservedOffer() {
        Offer offer = OfferTestUtils.getOffer(Arrays.asList(RESERVED_RESOURCE_1, RESERVED_RESOURCE_2,
                RESERVED_RESOURCE_3, RESERVED_RESOURCE_4));

        List<OfferRecommendation> recommendations = uninstallResourceCleaner.evaluate(Collections.singletonList(offer));
        // all destroy operations, followed by all unreserve operations

        assertEquals("Got: " + recommendations, 6, recommendations.size());

        OfferRecommendation rec = recommendations.get(0);
        assertEquals(Operation.Type.DESTROY, rec.getOperation().getType());

        rec = recommendations.get(1);
        assertEquals(Operation.Type.DESTROY, rec.getOperation().getType());

        rec = recommendations.get(2);
        assertEquals(Operation.Type.UNRESERVE, rec.getOperation().getType());

        rec = recommendations.get(3);
        assertEquals(Operation.Type.UNRESERVE, rec.getOperation().getType());

        rec = recommendations.get(4);
        assertEquals(Operation.Type.UNRESERVE, rec.getOperation().getType());

        rec = recommendations.get(5);
        assertEquals(Operation.Type.UNRESERVE, rec.getOperation().getType());
    }

    @Test
    public void testUnreservedOffer() {
        Offer offer = OfferTestUtils.getOffer(Arrays.asList(UNRESERVED_RESOURCE_1, UNRESERVED_RESOURCE_2));

        List<OfferRecommendation> recommendations = uninstallResourceCleaner.evaluate(Collections.singletonList(offer));

        assertEquals("Got: " + recommendations, 0, recommendations.size());
    }


    @Test
    public void testMixedOffer() {
        Offer offer = OfferTestUtils.getOffer(Arrays.asList(RESERVED_RESOURCE_1, UNRESERVED_RESOURCE_2));

        List<OfferRecommendation> recommendations = uninstallResourceCleaner.evaluate(Collections.singletonList(offer));
        assertEquals("Got: " + recommendations, 1, recommendations.size());

        OfferRecommendation rec = recommendations.get(0);
        assertEquals(Operation.Type.UNRESERVE, rec.getOperation().getType());
    }
}
