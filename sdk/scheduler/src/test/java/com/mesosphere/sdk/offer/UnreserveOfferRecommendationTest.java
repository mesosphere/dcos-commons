package com.mesosphere.sdk.offer;

import com.mesosphere.sdk.testutils.DefaultCapabilitiesTestSuite;
import com.mesosphere.sdk.testutils.OfferTestUtils;
import com.mesosphere.sdk.testutils.ResourceTestUtils;
import org.apache.mesos.Protos;
import org.junit.Assert;
import org.junit.Test;

/**
 * This class tests the {@link UnreserveOfferRecommendation} class.
 */
public class UnreserveOfferRecommendationTest extends DefaultCapabilitiesTestSuite {

    @Test
    public void testUnreserveRootDisk() {
        Protos.Resource resource = ResourceTestUtils.getReservedRootVolume(1);
        Protos.Offer offer = OfferTestUtils.getOffer(resource);

        UnreserveOfferRecommendation unreserveOfferRecommendation = new UnreserveOfferRecommendation(offer, resource);
        Protos.Offer.Operation operation = unreserveOfferRecommendation.getOperation();
        Assert.assertEquals(1, operation.getUnreserve().getResourcesCount());

        Protos.Resource opResource = operation.getUnreserve().getResources(0);

        Assert.assertFalse(opResource.hasDisk());
        Assert.assertFalse(opResource.hasRevocable());

        resource = resource.toBuilder().clearDisk().clearRevocable().build();
        Assert.assertEquals(resource, opResource);
    }

    @Test
    public void testUnreserveMountDisk() {
        Protos.Resource resource = ResourceTestUtils.getReservedMountVolume(1);
        Protos.Offer offer = OfferTestUtils.getOffer(resource);

        UnreserveOfferRecommendation unreserveOfferRecommendation = new UnreserveOfferRecommendation(offer, resource);
        Protos.Offer.Operation operation = unreserveOfferRecommendation.getOperation();
        Assert.assertEquals(1, operation.getUnreserve().getResourcesCount());

        Protos.Resource opResource = operation.getUnreserve().getResources(0);

        Assert.assertTrue(opResource.hasDisk());
        Assert.assertTrue(opResource.getDisk().hasSource());
        Assert.assertFalse(opResource.hasRevocable());

        resource = resource.toBuilder().setDisk(
                Protos.Resource.DiskInfo.newBuilder()
                        .setSource(resource.getDisk().getSource()))
                .build();
        Assert.assertEquals(resource, opResource);
    }
}
