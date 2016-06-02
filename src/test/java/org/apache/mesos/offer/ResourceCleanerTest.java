package org.apache.mesos.offer;

import java.util.*;

import org.apache.mesos.Protos.Offer;
import org.apache.mesos.Protos.Offer.Operation;
import org.apache.mesos.Protos.Resource;

import org.apache.mesos.protobuf.OfferBuilder;
import org.apache.mesos.protobuf.ResourceBuilder;

import org.junit.Assert;
import org.junit.Test;

public class ResourceCleanerTest {

  private static final String testRole = "test-role";
  private static final String testPrincipal = "test-principal";
  private static final String testResourceId = "test-resource-id";
  private static final String testContainerPath = "test-container-path";
  private static final String testPersistenceId = "test-persistence-id";
  private static final String testOfferId = "test-offer-id";
  private static final String testFrameworkId = "test-framework-id";
  private static final String testSlaveId = "test-slave-id";
  private static final String testHostname = "test-hostname";

  @Test
  public void testConstructor() {
    ResourceCleaner cleaner = new ResourceCleaner(Collections.emptyList(), Collections.emptyList());
    Assert.assertNotNull(cleaner);
  }

  @Test
  public void testNoRecommendations() {
    ResourceCleaner cleaner = new ResourceCleaner(Collections.emptyList(), Collections.emptyList());
    List<OfferRecommendation> recommendations = cleaner.evaluate(Collections.emptyList());

    Assert.assertNotNull(recommendations);
    Assert.assertEquals(Collections.emptyList(), recommendations);
  }

  @Test
  public void testUnreserveRecommendation() {
    Resource unexpectedResource = ResourceBuilder.reservedCpus(1.0, testRole, testPrincipal, testResourceId);
    List<Offer> offers = getOffers(unexpectedResource);

    ResourceCleaner cleaner = new ResourceCleaner(Collections.emptyList(), Collections.emptyList());
    List<OfferRecommendation> recommendations = cleaner.evaluate(offers);

    Assert.assertEquals(1, recommendations.size());

    Operation op = recommendations.get(0).getOperation();
    Assert.assertEquals(Operation.Type.UNRESERVE, op.getType());
  }

  @Test
  public void testNoRecommendationsWhenResourceIdsUnknown() {
    Resource unexpectedResource = ResourceBuilder.reservedCpus(1.0, testRole, testPrincipal, testResourceId);
    List<Offer> offers = getOffers(unexpectedResource);

    ResourceCleaner cleaner = new ResourceCleaner(null, Collections.emptyList());
    List<OfferRecommendation> recommendations = cleaner.evaluate(offers);

    Assert.assertEquals(0, recommendations.size());
  }

  @Test
  public void testDestroyRecommendation() {
    Resource unexpectedResource = ResourceBuilder.volume(
        1000.0,
        testRole,
        testPrincipal,
        testContainerPath,
        testPersistenceId);

    List<Offer> offers = getOffers(unexpectedResource);
    ResourceCleaner cleaner = new ResourceCleaner(Collections.emptyList(), Collections.emptyList());
    List<OfferRecommendation> recommendations = cleaner.evaluate(offers);

    Assert.assertEquals(2, recommendations.size());

    Operation destroyOp = recommendations.get(0).getOperation();
    Assert.assertEquals(Operation.Type.DESTROY, destroyOp.getType());

    Operation unreserveOp = recommendations.get(1).getOperation();
    Assert.assertEquals(Operation.Type.UNRESERVE, unreserveOp.getType());
  }

  @Test
  public void testNoDestroyWhenPersistenceIdsUnknown() {
    Resource unexpectedResource = ResourceBuilder.volume(
        1000.0,
        testRole,
        testPrincipal,
        testContainerPath,
        testPersistenceId);

    List<Offer> offers = getOffers(unexpectedResource);
    ResourceCleaner cleaner = new ResourceCleaner(Collections.emptyList(), null);
    List<OfferRecommendation> recommendations = cleaner.evaluate(offers);

    Assert.assertEquals(1, recommendations.size());

    Operation unreserveOp = recommendations.get(0).getOperation();
    Assert.assertEquals(Operation.Type.UNRESERVE, unreserveOp.getType());
  }

  @Test
  public void testNoOperationsWhenIdsUnknown() {
    Resource unexpectedResource = ResourceBuilder.volume(
        1000.0,
        testRole,
        testPrincipal,
        testContainerPath,
        testPersistenceId);

    List<Offer> offers = getOffers(unexpectedResource);
    ResourceCleaner cleaner = new ResourceCleaner(null, null);
    List<OfferRecommendation> recommendations = cleaner.evaluate(offers);

    Assert.assertEquals(0, recommendations.size());
  }

  private List<Offer> getOffers(Resource resource) {
    return getOffers(Arrays.asList(resource));
  }

  private List<Offer> getOffers(List<Resource> resources) {
    OfferBuilder builder = new OfferBuilder(testOfferId, testFrameworkId, testSlaveId, testHostname);
    builder.addAllResources(resources);
    return Arrays.asList(builder.build());
  }
}
