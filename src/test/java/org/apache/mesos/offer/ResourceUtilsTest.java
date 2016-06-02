package org.apache.mesos.offer;

import org.apache.mesos.Protos.Resource;
import org.apache.mesos.Protos.Resource.DiskInfo.Source;

import org.junit.Assert;
import org.junit.Test;

public class ResourceUtilsTest {

  @Test
  public void testCreateDesiredMountVolume() {
    Resource desiredMountVolume = ResourceUtils.getDesiredMountVolume(
        ResourceTestUtils.testRole,
        ResourceTestUtils.testPrincipal,
        1000,
        ResourceTestUtils.testContainerPath);
    Assert.assertNotNull(desiredMountVolume);
    Assert.assertTrue(desiredMountVolume.getDisk().hasPersistence());
    Assert.assertEquals("", desiredMountVolume.getDisk().getPersistence().getId());
    Assert.assertEquals("", new ResourceRequirement(desiredMountVolume).getResourceId());
    Assert.assertEquals(Source.Type.MOUNT, desiredMountVolume.getDisk().getSource().getType());
  }

  @Test
  public void testCreateDesiredRootVolume() {
    Resource desiredRootVolume = ResourceUtils.getDesiredRootVolume(
        ResourceTestUtils.testRole,
        ResourceTestUtils.testPrincipal,
        1000,
        ResourceTestUtils.testContainerPath);
    Assert.assertNotNull(desiredRootVolume);
    Assert.assertTrue(desiredRootVolume.getDisk().hasPersistence());
    Assert.assertEquals("", desiredRootVolume.getDisk().getPersistence().getId());
    Assert.assertEquals("", new ResourceRequirement(desiredRootVolume).getResourceId());
    Assert.assertFalse(desiredRootVolume.getDisk().hasSource());
  }
}
