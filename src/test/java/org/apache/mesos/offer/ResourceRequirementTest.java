package org.apache.mesos.offer;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.apache.mesos.Protos.Resource;
import org.apache.mesos.protobuf.ResourceBuilder;

import org.junit.Assert;
import org.junit.Test;

public class ResourceRequirementTest {
  private final Log log = LogFactory.getLog(getClass());

  private static final String testRole = "test-role";
  private static final String testPrincipal = "test-principal";
  private static final String testResourceId = "test-resource-id";
  private static final String testContainerPath = "test-containter-path";
  private static final String testPersistenceId = "test-persistence-id";

  @Test
  public void testConstructor() {
    Resource res = ResourceBuilder.cpus(1.0);
    log.info("Resource: " + res);
    ResourceRequirement resReq = new ResourceRequirement(res);

    Assert.assertNotNull(resReq);
    Assert.assertEquals(res, resReq.getResource());
  }

  @Test
  public void testNotReservingResource() {
    Resource res = ResourceBuilder.cpus(1.0);
    log.info("Resource: " + res);
    ResourceRequirement resReq = new ResourceRequirement(res);

    Assert.assertNull(resReq.getResourceId());
    Assert.assertTrue(resReq.consumesUnreservedResource());
    Assert.assertFalse(resReq.expectsResource());
    Assert.assertFalse(resReq.reservesResource());
    Assert.assertFalse(resReq.needsVolume());
  }

  @Test
  public void testReservingResource() {
    Resource res = ResourceBuilder.reservedCpus(1.0, testRole, testPrincipal);
    log.info("Resource: " + res);
    ResourceRequirement resReq = new ResourceRequirement(res);

    Assert.assertFalse(resReq.consumesUnreservedResource());
    Assert.assertTrue(resReq.reservesResource());
    Assert.assertFalse(resReq.expectsResource());
    Assert.assertTrue(resReq.getResourceId().isEmpty());
    Assert.assertFalse(resReq.needsVolume());
  }

  @Test
  public void testExpectedResource() {
    Resource res = ResourceBuilder.reservedCpus(1.0, testRole, testPrincipal, testResourceId);
    log.info("Resource: " + res);
    ResourceRequirement resReq = new ResourceRequirement(res);

    Assert.assertFalse(resReq.consumesUnreservedResource());
    Assert.assertFalse(resReq.reservesResource());
    Assert.assertTrue(resReq.expectsResource());
    Assert.assertNotNull(resReq.getResourceId());
    Assert.assertFalse(resReq.needsVolume());
  }

  @Test
  public void testCreateVolume() {
    Resource res = ResourceBuilder.volume(1000.0, testRole, testPrincipal, testContainerPath);
    log.info("Resource: " + res);
    ResourceRequirement resReq = new ResourceRequirement(res);

    Assert.assertFalse(resReq.consumesUnreservedResource());
    Assert.assertFalse(resReq.expectsVolume());
    Assert.assertTrue(resReq.createsVolume());
    Assert.assertTrue(resReq.needsVolume());
  }

  @Test
  public void testExistingVolume() {
    Resource res = ResourceBuilder.volume(1000.0, testRole, testPrincipal, testContainerPath, testPersistenceId);
    log.info("Resource: " + res);
    ResourceRequirement resReq = new ResourceRequirement(res);

    Assert.assertFalse(resReq.consumesUnreservedResource());
    Assert.assertTrue(resReq.expectsVolume());
    Assert.assertFalse(resReq.createsVolume());
    Assert.assertTrue(resReq.needsVolume());
  }

  @Test
  public void testSimpleScalarNotAtomic() {
    ResourceRequirement resReq = new ResourceRequirement(ResourceBuilder.cpus(1.0));
    Assert.assertFalse(resReq.isAtomic());
  }

  @Test
  public void testMountVolumeIsAtomic() {
    Resource res = ResourceBuilder.mountVolume(1000.0, testRole, testPrincipal, testContainerPath);
    ResourceRequirement resReq = new ResourceRequirement(res);
    Assert.assertTrue(resReq.isAtomic());
  }
}
