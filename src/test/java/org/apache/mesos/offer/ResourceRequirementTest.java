package org.apache.mesos.offer;

import org.apache.mesos.Protos.Resource;
import org.apache.mesos.testutils.ResourceTestUtils;
import org.junit.Assert;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ResourceRequirementTest {
    private static final Logger logger = LoggerFactory.getLogger(ResourceRequirementTest.class);

    @Test
    public void testConstructor() {
        Resource res = ResourceTestUtils.getUnreservedCpu(1.0);
        logger.info("Resource: {}", res);
        ResourceRequirement resReq = new ResourceRequirement(res);

        Assert.assertNotNull(resReq);
        Assert.assertEquals(res, resReq.getResource());
    }

    @Test
    public void testNotReservingResource() {
        Resource res = ResourceTestUtils.getUnreservedCpu(1.0);
        logger.info("Resource: {}", res);
        ResourceRequirement resReq = new ResourceRequirement(res);

        Assert.assertNull(resReq.getResourceId());
        Assert.assertTrue(resReq.consumesUnreservedResource());
        Assert.assertFalse(resReq.expectsResource());
        Assert.assertFalse(resReq.reservesResource());
        Assert.assertFalse(resReq.needsVolume());
    }

    @Test
    public void testReservingResource() {
        Resource res = ResourceTestUtils.getDesiredCpu(1.0);
        logger.info("Resource: {}", res);
        ResourceRequirement resReq = new ResourceRequirement(res);

        Assert.assertFalse(resReq.consumesUnreservedResource());
        Assert.assertTrue(resReq.reservesResource());
        Assert.assertFalse(resReq.expectsResource());
        Assert.assertTrue(resReq.getResourceId().isEmpty());
        Assert.assertFalse(resReq.needsVolume());
    }

    @Test
    public void testExpectedResource() {
        Resource res = ResourceTestUtils.getExpectedCpu(1.0);
        logger.info("Resource: {}", res);
        ResourceRequirement resReq = new ResourceRequirement(res);

        Assert.assertFalse(resReq.consumesUnreservedResource());
        Assert.assertFalse(resReq.reservesResource());
        Assert.assertTrue(resReq.expectsResource());
        Assert.assertNotNull(resReq.getResourceId());
        Assert.assertFalse(resReq.needsVolume());
    }

    @Test
    public void testCreateVolume() {
        Resource res = ResourceTestUtils.getDesiredRootVolume(1000);
        logger.info("Resource: {}", res);
        ResourceRequirement resReq = new ResourceRequirement(res);

        Assert.assertFalse(resReq.consumesUnreservedResource());
        Assert.assertFalse(resReq.expectsVolume());
        Assert.assertTrue(resReq.createsVolume());
        Assert.assertTrue(resReq.needsVolume());
    }

    @Test
    public void testExistingVolume() {
        Resource res = ResourceTestUtils.getExpectedRootVolume(1000);
        logger.info("Resource: {}", res);
        ResourceRequirement resReq = new ResourceRequirement(res);

        Assert.assertFalse(resReq.consumesUnreservedResource());
        Assert.assertTrue(resReq.expectsVolume());
        Assert.assertFalse(resReq.createsVolume());
        Assert.assertTrue(resReq.needsVolume());
    }

    @Test
    public void testSimpleScalarNotAtomic() {
        ResourceRequirement resReq = new ResourceRequirement(ResourceTestUtils.getUnreservedCpu(1.0));
        Assert.assertFalse(resReq.isAtomic());
    }

    @Test
    public void testMountVolumeIsAtomic() {
        Resource res = ResourceTestUtils.getDesiredMountVolume(1000);
        ResourceRequirement resReq = new ResourceRequirement(res);
        Assert.assertTrue(resReq.isAtomic());
    }
}
