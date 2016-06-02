package org.apache.mesos.offer;

import java.util.*;

import org.apache.mesos.Protos.Offer;
import org.apache.mesos.Protos.Resource;

import org.apache.mesos.protobuf.OfferBuilder;
import org.apache.mesos.protobuf.ResourceBuilder;

import org.junit.Assert;
import org.junit.Test;

public class MesosResourcePoolTest {

  private static final String testOfferId = "test-offer-id";
  private static final String testFrameworkId = "test-framework-id";
  private static final String testSlaveId = "test-slave-id";
  private static final String testHostname = "test-hostname";
  private static final String testMountRoot = "test-mount-root";
  private static final String testRole = "test-role";
  private static final String testPrincipal = "test-principal";
  private static final String testContainerPath = "test-containter-path";
  private static final String testResourceId = "test-resource-id";

  @Test
  public void testEmptyUnreservedAtomicPool() {
    Offer offer = getOffer(ResourceBuilder.cpus(1.0));
    MesosResourcePool pool = new MesosResourcePool(offer);

    Assert.assertEquals(0, pool.getUnreservedAtomicPool().size());
  }

  @Test
  public void testCreateSingleUnreservedAtomicPool() {
    Offer offer = getOffer(ResourceBuilder.mountVolume(1000.0, testMountRoot));
    MesosResourcePool pool = new MesosResourcePool(offer);

    Assert.assertEquals(1, pool.getUnreservedAtomicPool().size());
    Assert.assertEquals(1, pool.getUnreservedAtomicPool().get("disk").size());
  }

  @Test
  public void testCreateSingleReservedAtomicPool() {
    Resource resource = ResourceBuilder.mountVolume(1000.0, testRole, testPrincipal, testContainerPath);
    Offer offer = getOffer(resource);
    MesosResourcePool pool = new MesosResourcePool(offer);
    String resourceId = new MesosResource(resource).getResourceId();

    Assert.assertEquals(0, pool.getUnreservedAtomicPool().size());
    Assert.assertEquals(1, pool.getReservedPool().size());
    Assert.assertEquals(resource, pool.getReservedPool().get(resourceId).getResource());
  }

  @Test
  public void testMultipleUnreservedAtomicPool() {
    Resource resource = ResourceBuilder.mountVolume(1000.0, testMountRoot);
    Offer offer = getOffer(Arrays.asList(resource, resource));
    MesosResourcePool pool = new MesosResourcePool(offer);

    Assert.assertEquals(1, pool.getUnreservedAtomicPool().size());
    Assert.assertEquals(2, pool.getUnreservedAtomicPool().get("disk").size());
  }

  @Test
  public void testConsumeUnreservedAtomicResource() {
    Resource offerResource = ResourceBuilder.mountVolume(1000.0, testMountRoot);
    Resource resource = ResourceBuilder.mountVolume(1000.0, testRole, testPrincipal, testContainerPath);
    ResourceRequirement resReq = new ResourceRequirement(resource);
    Offer offer = getOffer(offerResource);
    MesosResourcePool pool = new MesosResourcePool(offer);

    MesosResource resourceToConsume = pool.consume(resReq);
    Assert.assertEquals(offerResource, resourceToConsume.getResource());
  }

  @Test
  public void testConsumeReservedMergedResource() {
    Resource resource = ResourceBuilder.reservedCpus(1.0, testRole, testPrincipal, testResourceId);
    ResourceRequirement resReq = new ResourceRequirement(resource);
    Offer offer = getOffer(resource);
    MesosResourcePool pool = new MesosResourcePool(offer);

    Assert.assertEquals(1, pool.getReservedPool().size());
    MesosResource resourceToConsume = pool.consume(resReq);
    Assert.assertEquals(resource, resourceToConsume.getResource());
  }

  @Test
  public void testConsumeUnreservedMergedResource() {
    Resource resource = ResourceBuilder.cpus(1.0);
    ResourceRequirement resReq = new ResourceRequirement(resource);
    Offer offer = getOffer(resource);
    MesosResourcePool pool = new MesosResourcePool(offer);

    Assert.assertEquals(1, pool.getUnreservedMergedPool().size());
    MesosResource resourceToConsume = pool.consume(resReq);
    Assert.assertEquals(resource, resourceToConsume.getResource());
  }

  @Test
  public void testReleaseAtomicResource() {
    Resource offerResource = ResourceBuilder.mountVolume(1000.0, testMountRoot);
    Resource releaseResource = ResourceBuilder.mountVolume(1000.0, testRole, testPrincipal, testContainerPath);
    Offer offer = getOffer(offerResource);
    MesosResourcePool pool = new MesosResourcePool(offer);

    Assert.assertEquals(1, pool.getUnreservedAtomicPool().get("disk").size());
    pool.release(new MesosResource(releaseResource));
    Assert.assertEquals(2, pool.getUnreservedAtomicPool().get("disk").size());
  }

  @Test
  public void testReleaseMergedResource() {
    Resource resource = ResourceBuilder.cpus(1.0);
    Offer offer = getOffer(resource);
    MesosResourcePool pool = new MesosResourcePool(offer);

    Assert.assertEquals(1, pool.getUnreservedMergedPool().get("cpus").getScalar().getValue(), 0.0);
    pool.release(new MesosResource(resource));
    Assert.assertEquals(2, pool.getUnreservedMergedPool().get("cpus").getScalar().getValue(), 0.0);
  }

  private Offer getOffer(List<Resource> resources) {
    OfferBuilder builder = new OfferBuilder(testOfferId, testFrameworkId, testSlaveId, testHostname);
    return builder.addAllResources(resources).build();
  }

  private Offer getOffer(Resource resource) {
    return getOffer(Arrays.asList(resource));
  }
}
