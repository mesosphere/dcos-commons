package com.mesosphere.sdk.offer;

import com.mesosphere.sdk.testutils.DefaultCapabilitiesTestSuite;
import com.mesosphere.sdk.testutils.OfferTestUtils;
import com.mesosphere.sdk.testutils.ResourceTestUtils;
import org.apache.mesos.Protos;
import org.apache.mesos.Protos.Offer;
import org.apache.mesos.Protos.Resource;
import org.junit.Assert;
import org.junit.Test;

import java.util.Arrays;
import java.util.Optional;

public class MesosResourcePoolTest extends DefaultCapabilitiesTestSuite {

    @Test
    public void testEmptyUnreservedAtomicPool() {
        Offer offer = OfferTestUtils.getCompleteOffer(ResourceTestUtils.getUnreservedCpu(1.0));
        MesosResourcePool pool = new MesosResourcePool(offer, Optional.of(Constants.ANY_ROLE));

        Assert.assertEquals(0, pool.getUnreservedAtomicPool().size());
    }

    @Test
    public void testCreateSingleUnreservedAtomicPool() {
        Offer offer = OfferTestUtils.getCompleteOffer(ResourceTestUtils.getUnreservedMountVolume(1000));
        MesosResourcePool pool = new MesosResourcePool(offer, Optional.of(Constants.ANY_ROLE));

        Assert.assertEquals(1, pool.getUnreservedAtomicPool().size());
        Assert.assertEquals(1, pool.getUnreservedAtomicPool().get("disk").size());
    }

    @Test
    public void testCreateSingleReservedAtomicPool() {
        Resource resource = ResourceTestUtils.getExpectedMountVolume(1000);
        Offer offer = OfferTestUtils.getCompleteOffer(resource);
        MesosResourcePool pool = new MesosResourcePool(offer, Optional.of(Constants.ANY_ROLE));
        String resourceId = new MesosResource(resource).getResourceId().get();

        Assert.assertEquals(0, pool.getUnreservedAtomicPool().size());
        Assert.assertEquals(1, pool.getDynamicallyReservedPoolByResourceId().size());
        Assert.assertEquals(resource, pool.getDynamicallyReservedPoolByResourceId().get(resourceId).getResource());
    }

    @Test
    public void testMultipleUnreservedAtomicPool() {
        Resource resource = ResourceTestUtils.getUnreservedMountVolume(1000);
        Offer offer = OfferTestUtils.getCompleteOffer(Arrays.asList(resource, resource));
        MesosResourcePool pool = new MesosResourcePool(offer, Optional.of(Constants.ANY_ROLE));

        Assert.assertEquals(1, pool.getUnreservedAtomicPool().size());
        Assert.assertEquals(2, pool.getUnreservedAtomicPool().get("disk").size());
    }

    @Test
    public void testConsumeUnreservedAtomicResource() {
        Resource offerResource = ResourceTestUtils.getUnreservedMountVolume(1000);
        Protos.Value resourceValue = ValueUtils.getValue(offerResource);
        Offer offer = OfferTestUtils.getCompleteOffer(offerResource);
        MesosResourcePool pool = new MesosResourcePool(offer, Optional.of(Constants.ANY_ROLE));

        Assert.assertEquals(1, pool.getUnreservedAtomicPool().size());
        MesosResource resourceToConsume = pool.consumeAtomic(offerResource.getName(), resourceValue).get();
        Assert.assertEquals(offerResource, resourceToConsume.getResource());
        Assert.assertEquals(0, pool.getUnreservedAtomicPool().size());
    }

    @Test
    public void testConsumeReservedMergedResource() {
        Resource resource = ResourceTestUtils.getExpectedCpu(1.0);
        Protos.Value resourceValue = ValueUtils.getValue(resource);
        String resourceId = ResourceTestUtils.getResourceId(resource);
        Offer offer = OfferTestUtils.getCompleteOffer(resource);
        MesosResourcePool pool = new MesosResourcePool(offer, Optional.of(Constants.ANY_ROLE));

        Assert.assertEquals(1, pool.getDynamicallyReservedPoolByResourceId().size());
        MesosResource resourceToConsume = pool.consumeReserved(resource.getName(), resourceValue, resourceId).get();
        Assert.assertEquals(resource, resourceToConsume.getResource());
        Assert.assertEquals(0, pool.getDynamicallyReservedPoolByResourceId().size());
    }

    @Test
    public void testConsumeUnreservedMergedResource() {
        Resource resource = ResourceTestUtils.getUnreservedCpu(1.0);
        Protos.Value resourceValue = ValueUtils.getValue(resource);
        Offer offer = OfferTestUtils.getOffer(resource);
        MesosResourcePool pool = new MesosResourcePool(offer, Optional.of(Constants.ANY_ROLE));

        Assert.assertEquals(1, pool.getUnreservedMergedPool().size());
        Assert.assertEquals(resource.getScalar().getValue(),
                pool.getUnreservedMergedPool().get("cpus").getScalar().getValue(), 0.0);
        MesosResource resourceToConsume = pool.consumeReservableMerged(resource.getName(), resourceValue, Constants.ANY_ROLE).get();
        Assert.assertEquals(resource, resourceToConsume.getResource());
        Assert.assertEquals(ValueUtils.getZero(Protos.Value.Type.SCALAR),
                pool.getUnreservedMergedPool().get("cpus"));
    }

    @Test
    public void testConsumeInsufficientUnreservedMergedResource() {
        Resource desiredUnreservedResource = ResourceTestUtils.getUnreservedCpu(2.0);
        Protos.Value resourceValue = ValueUtils.getValue(desiredUnreservedResource);
        Resource offeredUnreservedResource = ResourceTestUtils.getUnreservedScalar("cpus", 1.0);
        Offer offer = OfferTestUtils.getCompleteOffer(offeredUnreservedResource);
        MesosResourcePool pool = new MesosResourcePool(offer, Optional.of(Constants.ANY_ROLE));

        Assert.assertFalse(
                pool.consumeReservableMerged(desiredUnreservedResource.getName(), resourceValue, Constants.ANY_ROLE)
                        .isPresent());
    }
}
