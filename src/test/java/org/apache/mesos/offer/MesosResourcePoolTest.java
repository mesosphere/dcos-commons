package org.apache.mesos.offer;

import org.apache.mesos.Protos;
import org.apache.mesos.Protos.Offer;
import org.apache.mesos.Protos.Resource;
import org.apache.mesos.testutils.OfferTestUtils;
import org.apache.mesos.testutils.ResourceTestUtils;
import org.apache.mesos.testutils.TestConstants;
import org.junit.Assert;
import org.junit.Test;

import java.util.Arrays;
import java.util.Optional;


public class MesosResourcePoolTest {

    @Test
    public void testEmptyUnreservedAtomicPool() {
        Offer offer = OfferTestUtils.getOffer(ResourceTestUtils.getUnreservedCpu(1.0));
        MesosResourcePool pool = new MesosResourcePool(offer);

        Assert.assertEquals(0, pool.getUnreservedAtomicPool().size());
    }

    @Test
    public void testCreateSingleUnreservedAtomicPool() {
        Offer offer = OfferTestUtils.getOffer(ResourceTestUtils.getUnreservedMountVolume(1000));
        MesosResourcePool pool = new MesosResourcePool(offer);

        Assert.assertEquals(1, pool.getUnreservedAtomicPool().size());
        Assert.assertEquals(1, pool.getUnreservedAtomicPool().get("disk").size());
    }

    @Test
    public void testCreateSingleReservedAtomicPool() {
        Resource resource = ResourceTestUtils.getExpectedMountVolume(1000);
        Offer offer = OfferTestUtils.getOffer(resource);
        MesosResourcePool pool = new MesosResourcePool(offer);
        String resourceId = new MesosResource(resource).getResourceId();

        Assert.assertEquals(0, pool.getUnreservedAtomicPool().size());
        Assert.assertEquals(1, pool.getReservedPool().size());
        Assert.assertEquals(resource, pool.getReservedPool().get(resourceId).getResource());
    }

    @Test
    public void testMultipleUnreservedAtomicPool() {
        Resource resource = ResourceTestUtils.getUnreservedMountVolume(1000);
        Offer offer = OfferTestUtils.getOffer(Arrays.asList(resource, resource));
        MesosResourcePool pool = new MesosResourcePool(offer);

        Assert.assertEquals(1, pool.getUnreservedAtomicPool().size());
        Assert.assertEquals(2, pool.getUnreservedAtomicPool().get("disk").size());
    }

    @Test
    public void testConsumeUnreservedAtomicResource() {
        Resource offerResource = ResourceTestUtils.getUnreservedMountVolume(1000);
        Resource resource = ResourceTestUtils.getDesiredMountVolume(1000);
        ResourceRequirement resReq = new ResourceRequirement(resource);
        Offer offer = OfferTestUtils.getOffer(offerResource);
        MesosResourcePool pool = new MesosResourcePool(offer);

        Assert.assertEquals(1, pool.getUnreservedAtomicPool().size());
        MesosResource resourceToConsume = pool.consume(resReq).get();
        Assert.assertEquals(offerResource, resourceToConsume.getResource());
        Assert.assertEquals(0, pool.getUnreservedAtomicPool().size());
    }

    @Test
    public void testConsumeReservedMergedResource() {
        Resource resource = ResourceTestUtils.getExpectedCpu(1.0);
        ResourceRequirement resReq = new ResourceRequirement(resource);
        Offer offer = OfferTestUtils.getOffer(resource);
        MesosResourcePool pool = new MesosResourcePool(offer);

        Assert.assertEquals(1, pool.getReservedPool().size());
        MesosResource resourceToConsume = pool.consume(resReq).get();
        Assert.assertEquals(resource, resourceToConsume.getResource());
        Assert.assertEquals(0, pool.getReservedPool().size());
    }

    @Test
    public void testConsumeUnreservedMergedResource() {
        Resource resource = ResourceTestUtils.getUnreservedCpu(1.0);
        ResourceRequirement resReq = new ResourceRequirement(resource);
        Offer offer = OfferTestUtils.getOffer(resource);
        MesosResourcePool pool = new MesosResourcePool(offer);

        Assert.assertEquals(1, pool.getUnreservedMergedPool().size());
        Assert.assertEquals(resource.getScalar().getValue(),
                pool.getUnreservedMergedPool().get("cpus").getScalar().getValue(), 0.0);
        MesosResource resourceToConsume = pool.consume(resReq).get();
        Assert.assertEquals(resource, resourceToConsume.getResource());
        Assert.assertEquals(ValueUtils.getZero(Protos.Value.Type.SCALAR),
                pool.getUnreservedMergedPool().get("cpus"));
    }

    @Test
    public void testConsumeInsufficientUnreservedMergedResource() {
        Resource desiredUnreservedResource = ResourceTestUtils.getUnreservedCpu(2.0);
        ResourceRequirement resReq = new ResourceRequirement(desiredUnreservedResource);
        Resource offeredUnreservedResource = ResourceUtils.getUnreservedScalar("cpus", 1.0);
        Offer offer = OfferTestUtils.getOffer(offeredUnreservedResource);
        MesosResourcePool pool = new MesosResourcePool(offer);

        Assert.assertFalse(pool.consume(resReq).isPresent());
    }
    @Test
    public void testConsumeDynamicPort()  {
        Protos.Value.Range range = Protos.Value.Range.newBuilder().setBegin(0).setEnd(0).build();
        Protos.Value.Range range2 = Protos.Value.Range.newBuilder().setBegin(1003).setEnd(1003).build();
        Protos.Value.Range range3 = Protos.Value.Range.newBuilder().setBegin(2003).setEnd(2003).build();

        Resource desiredPort = ResourceUtils.getDesiredRanges(TestConstants.ROLE, TestConstants.PRINCIPAL,
                        "ports", Arrays.asList(range));

        Resource desiredPort2 = ResourceUtils.getDesiredRanges(TestConstants.ROLE, TestConstants.PRINCIPAL,
                "ports", Arrays.asList(range2));

        Resource desiredPort3 = ResourceUtils.getDesiredRanges(TestConstants.ROLE, TestConstants.PRINCIPAL,
                "ports", Arrays.asList(range3));

        ResourceRequirement dynamicPortRequirement1 = new ResourceRequirement(desiredPort);
        dynamicPortRequirement1.setEnvName(TestConstants.PORT_NAME);

        ResourceRequirement dynamicPortRequirement2 = new ResourceRequirement(desiredPort2);
        ResourceRequirement dynamicPortRequirement3 = new ResourceRequirement(desiredPort3);

        Resource offeredPorts = ResourceTestUtils.getUnreservedPorts(1000, 1005);
        Offer offer = OfferTestUtils.getOffer(offeredPorts);
        MesosResourcePool pool = new MesosResourcePool(offer);
        MesosResource mesosResource = pool.consume(dynamicPortRequirement1).get();

        // mesosResource is created new and value is set - does not hold the info from ResourceRequirement

        Assert.assertEquals(1, mesosResource.getResource().getRanges().getRangeCount());
        range = mesosResource.getResource().getRanges().getRange(0);
        Assert.assertEquals(1000, range.getBegin());
        Assert.assertEquals(1000, range.getEnd());

        Resource offeredCPU = ResourceTestUtils.getUnreservedCpu(10);
        offer = OfferTestUtils.getOffer(offeredCPU);
        pool = new MesosResourcePool(offer);
        Optional<MesosResource> mesosResource1 = pool.consume(dynamicPortRequirement1);
        Assert.assertEquals(false, mesosResource1.isPresent());

        offer = OfferTestUtils.getOffer(offeredPorts);
        pool = new MesosResourcePool(offer);
        mesosResource = pool.consume(dynamicPortRequirement2).get();
        Assert.assertEquals(1, mesosResource.getResource().getRanges().getRangeCount());
        range = mesosResource.getResource().getRanges().getRange(0);
        Assert.assertEquals(1003, range.getBegin());
        Assert.assertEquals(1003, range.getEnd());

        offer = OfferTestUtils.getOffer(offeredPorts);
        pool = new MesosResourcePool(offer);
        Optional<MesosResource> mesosResource3 = pool.consume(dynamicPortRequirement3);
        Assert.assertEquals(false, mesosResource1.isPresent());

    }
       /* @Test
        public void testConsumeDynamicPort() throws DynamicPortRequirement.DynamicPortException {
            Resource desiredDynamicPort = DynamicPortRequirement.getDesiredDynamicPort(
                    TestConstants.PORT_NAME,
                    TestConstants.ROLE,
                    TestConstants.PRINCIPAL);

            DynamicPortRequirement dynamicPortRequirement = new DynamicPortRequirement(desiredDynamicPort);
            Resource offeredPorts = ResourceTestUtils.getUnreservedPorts(10000, 10005);
            Offer offer = OfferTestUtils.getOffer(offeredPorts);
            MesosResourcePool pool = new MesosResourcePool(offer);

            MesosResource mesosResource = pool.consume(dynamicPortRequirement).get();
            Assert.assertEquals(1, mesosResource.getResource().getRanges().getRangeCount());
            Protos.Value.Range range = mesosResource.getResource().getRanges().getRange(0);
            Assert.assertEquals(10000, range.getBegin());
            Assert.assertEquals(10000, range.getEnd());
        }
    */
    @Test
    public void testReleaseAtomicResource() {
        Resource offerResource = ResourceTestUtils.getUnreservedMountVolume(1000);
        Resource releaseResource = ResourceTestUtils.getExpectedMountVolume(1000);
        Offer offer = OfferTestUtils.getOffer(offerResource);
        MesosResourcePool pool = new MesosResourcePool(offer);

        Assert.assertEquals(1, pool.getUnreservedAtomicPool().get("disk").size());
        pool.release(new MesosResource(releaseResource));
        Assert.assertEquals(2, pool.getUnreservedAtomicPool().get("disk").size());
    }

    @Test
    public void testReleaseMergedResource() {
        Resource resource = ResourceTestUtils.getUnreservedCpu(1.0);
        Offer offer = OfferTestUtils.getOffer(resource);
        MesosResourcePool pool = new MesosResourcePool(offer);

        Assert.assertEquals(1, pool.getUnreservedMergedPool().get("cpus").getScalar().getValue(), 0.0);
        pool.release(new MesosResource(resource));
        Assert.assertEquals(2, pool.getUnreservedMergedPool().get("cpus").getScalar().getValue(), 0.0);
    }
}
