package org.apache.mesos.offer;

import java.util.Arrays;
import java.util.List;

import org.apache.mesos.offer.TaskRequirement.InvalidTaskRequirementException;
import org.apache.mesos.protobuf.ExecutorInfoBuilder;
import org.apache.mesos.protobuf.OfferBuilder;
import org.apache.mesos.protobuf.ResourceBuilder;
import org.apache.mesos.protobuf.TaskInfoBuilder;

import org.apache.mesos.Protos.CommandInfo;
import org.apache.mesos.Protos.ExecutorInfo;
import org.apache.mesos.Protos.Offer;
import org.apache.mesos.Protos.Offer.Operation;
import org.apache.mesos.Protos.Resource;
import org.apache.mesos.Protos.TaskInfo;

import org.junit.Assert;
import org.junit.Test;

public class OfferEvaluatorTest {

  @Test
  public void testConstructor() throws InvalidTaskRequirementException {
    Resource resource = ResourceBuilder.cpus(1.0);
    OfferEvaluator offerEvaluator = getOfferEvaluator(resource);
    Assert.assertNotNull(offerEvaluator);
  }

  @Test
  public void testReserveTaskExecutorInsufficient() throws InvalidTaskRequirementException {
    Resource desiredTaskCpu = ResourceUtils.getDesiredScalar(ResourceTestUtils.testRole, ResourceTestUtils.testPrincipal, "cpus", 1.0);
    Resource desiredExecutorCpu = desiredTaskCpu;
    Resource insufficientOfferedResource = ResourceTestUtils.getOfferedUnreservedScalar("cpus", 1.0);

    OfferRequirement offerReq = new OfferRequirement(
            Arrays.asList(getTaskInfo(desiredTaskCpu)),
            getExecutorInfo(desiredExecutorCpu),
            null,
            null);

    OfferEvaluator offerEvaluator = new OfferEvaluator(offerReq);
    List<Offer> offers = getOffers(insufficientOfferedResource);
    List<OfferRecommendation> recommendations = offerEvaluator.evaluate(offers);
    Assert.assertEquals(0, recommendations.size());
  }

  @Test
  public void testReserveCreateLaunchMountVolume() throws InvalidTaskRequirementException {
    Resource desiredResource = ResourceUtils.getDesiredMountVolume(
        ResourceTestUtils.testRole,
        ResourceTestUtils.testPrincipal,
        1000,
        ResourceTestUtils.testContainerPath);
    Resource offeredResource = ResourceTestUtils.getOfferedUnreservedMountVolume(2000);
    OfferEvaluator offerEvaluator = getOfferEvaluator(desiredResource);
    List<Offer> offers = getOffers(offeredResource);

    List<OfferRecommendation> recommendations = offerEvaluator.evaluate(offers);
    Assert.assertEquals(3, recommendations.size());

    // Validate RESERVE Operation
    Operation reserveOperation = recommendations.get(0).getOperation();
    Resource reserveResource =
      reserveOperation
      .getReserve()
      .getResourcesList()
      .get(0);

    Assert.assertEquals(Operation.Type.RESERVE, reserveOperation.getType());
    Assert.assertEquals(2000, reserveResource.getScalar().getValue(), 0.0);
    Assert.assertEquals(ResourceTestUtils.testRole, reserveResource.getRole());
    Assert.assertEquals(ResourceTestUtils.testMountRoot, reserveResource.getDisk().getSource().getMount().getRoot());
    Assert.assertEquals(ResourceTestUtils.testPrincipal, reserveResource.getReservation().getPrincipal());
    Assert.assertEquals(ResourceRequirement.RESOURCE_ID_KEY, reserveResource.getReservation().getLabels().getLabelsList().get(0).getKey());
    Assert.assertEquals(36, reserveResource.getReservation().getLabels().getLabelsList().get(0).getValue().length());

    // Validate CREATE Operation
    String resourceId = reserveResource.getReservation().getLabels().getLabelsList().get(0).getValue();
    Operation createOperation = recommendations.get(1).getOperation();
    Resource createResource =
      createOperation
      .getCreate()
      .getVolumesList()
      .get(0);

    Assert.assertEquals(resourceId, createResource.getReservation().getLabels().getLabelsList().get(0).getValue());
    Assert.assertEquals(36, createResource.getDisk().getPersistence().getId().length());
    Assert.assertEquals(ResourceTestUtils.testMountRoot, createResource.getDisk().getSource().getMount().getRoot());
    Assert.assertEquals(ResourceTestUtils.testPrincipal, createResource.getDisk().getPersistence().getPrincipal());
    Assert.assertTrue(createResource.getDisk().hasVolume());

    // Validate LAUNCH Operation
    String persistenceId = createResource.getDisk().getPersistence().getId();
    Operation launchOperation = recommendations.get(2).getOperation();
    Resource launchResource =
      launchOperation
      .getLaunch()
      .getTaskInfosList()
      .get(0)
      .getResourcesList()
      .get(0);

    Assert.assertEquals(Operation.Type.LAUNCH, launchOperation.getType());
    Assert.assertEquals(resourceId, launchResource.getReservation().getLabels().getLabelsList().get(0).getValue());
    Assert.assertEquals(persistenceId, launchResource.getDisk().getPersistence().getId());
    Assert.assertEquals(ResourceTestUtils.testMountRoot, launchResource.getDisk().getSource().getMount().getRoot());
    Assert.assertEquals(ResourceTestUtils.testPrincipal, launchResource.getDisk().getPersistence().getPrincipal());
  }

  @Test
  public void testFailToCreateVolumeWithWrongResource() throws InvalidTaskRequirementException {
    Resource desiredResource = ResourceUtils.getDesiredRootVolume(ResourceTestUtils.testRole, ResourceTestUtils.testPrincipal, 1000, ResourceTestUtils.testContainerPath);
    Resource wrongOfferedResource = ResourceTestUtils.getOfferedUnreservedMountVolume(2000);
    OfferEvaluator offerEvaluator = getOfferEvaluator(desiredResource);
    List<Offer> offers = getOffers(wrongOfferedResource);

    List<OfferRecommendation> recommendations = offerEvaluator.evaluate(offers);
    Assert.assertEquals(0, recommendations.size());
  }

  @Test
  public void testReserveCreateLaunchRootVolume() throws InvalidTaskRequirementException {
    Resource desiredResource = ResourceUtils.getDesiredRootVolume(ResourceTestUtils.testRole, ResourceTestUtils.testPrincipal, 1500, ResourceTestUtils.testContainerPath);
    Resource offeredResource = ResourceTestUtils.getOfferedUnreservedRootVolume(2000);
    OfferEvaluator offerEvaluator = getOfferEvaluator(desiredResource);
    List<Offer> offers = getOffers(offeredResource);

    List<OfferRecommendation> recommendations = offerEvaluator.evaluate(offers);
    Assert.assertEquals(3, recommendations.size());

    // Validate RESERVE Operation
    Operation reserveOperation = recommendations.get(0).getOperation();
    Resource reserveResource =
      reserveOperation
      .getReserve()
      .getResourcesList()
      .get(0);

    Assert.assertEquals(Operation.Type.RESERVE, reserveOperation.getType());
    Assert.assertEquals(1500, reserveResource.getScalar().getValue(), 0.0);
    Assert.assertEquals(ResourceTestUtils.testRole, reserveResource.getRole());
    Assert.assertEquals(ResourceTestUtils.testPrincipal, reserveResource.getReservation().getPrincipal());
    Assert.assertEquals(ResourceRequirement.RESOURCE_ID_KEY, reserveResource.getReservation().getLabels().getLabelsList().get(0).getKey());
    Assert.assertEquals(36, reserveResource.getReservation().getLabels().getLabelsList().get(0).getValue().length());

    // Validate CREATE Operation
    String resourceId = reserveResource.getReservation().getLabels().getLabelsList().get(0).getValue();
    Operation createOperation = recommendations.get(1).getOperation();
    Resource createResource =
      createOperation
      .getCreate()
      .getVolumesList()
      .get(0);

    Assert.assertEquals(resourceId, createResource.getReservation().getLabels().getLabelsList().get(0).getValue());
    Assert.assertEquals(36, createResource.getDisk().getPersistence().getId().length());
    Assert.assertEquals(ResourceTestUtils.testPrincipal, createResource.getDisk().getPersistence().getPrincipal());
    Assert.assertTrue(createResource.getDisk().hasVolume());

    // Validate LAUNCH Operation
    String persistenceId = createResource.getDisk().getPersistence().getId();
    Operation launchOperation = recommendations.get(2).getOperation();
    Resource launchResource =
      launchOperation
      .getLaunch()
      .getTaskInfosList()
      .get(0)
      .getResourcesList()
      .get(0);

    Assert.assertEquals(Operation.Type.LAUNCH, launchOperation.getType());
    Assert.assertEquals(resourceId, launchResource.getReservation().getLabels().getLabelsList().get(0).getValue());
    Assert.assertEquals(persistenceId, launchResource.getDisk().getPersistence().getId());
    Assert.assertEquals(ResourceTestUtils.testPrincipal, launchResource.getDisk().getPersistence().getPrincipal());
  }

  @Test
  public void testFailCreateRootVolume() throws InvalidTaskRequirementException {
    Resource desiredResource = ResourceUtils.getDesiredRootVolume(ResourceTestUtils.testRole, ResourceTestUtils.testPrincipal, 2000, ResourceTestUtils.testContainerPath);
    Resource offeredResource = ResourceTestUtils.getOfferedUnreservedRootVolume(1000);
    OfferEvaluator offerEvaluator = getOfferEvaluator(desiredResource);
    List<Offer> offers = getOffers(offeredResource);

    List<OfferRecommendation> recommendations = offerEvaluator.evaluate(offers);
    Assert.assertEquals(0, recommendations.size());
  }

  @Test
  public void testExpectedMountVolume() throws InvalidTaskRequirementException {
    Resource expectedResource = ResourceTestUtils.getExpectedMountVolume(1000);
    OfferEvaluator offerEvaluator = getOfferEvaluator(expectedResource);
    List<Offer> offers = getOffers(expectedResource);

    List<OfferRecommendation> recommendations = offerEvaluator.evaluate(offers);
    Assert.assertEquals(1, recommendations.size());

    Operation launchOperation = recommendations.get(0).getOperation();
    Resource launchResource =
      launchOperation
      .getLaunch()
      .getTaskInfosList()
      .get(0)
      .getResourcesList()
      .get(0);

    Assert.assertEquals(Operation.Type.LAUNCH, launchOperation.getType());
    Assert.assertEquals(1000, launchResource.getScalar().getValue(), 0.0);
    Assert.assertEquals(ResourceTestUtils.testRole, launchResource.getRole());
    Assert.assertEquals(ResourceTestUtils.testMountRoot, launchResource.getDisk().getSource().getMount().getRoot());
    Assert.assertEquals(ResourceTestUtils.testPersistenceId, launchResource.getDisk().getPersistence().getId());
    Assert.assertEquals(ResourceTestUtils.testPrincipal, launchResource.getDisk().getPersistence().getPrincipal());
    Assert.assertEquals(ResourceTestUtils.testPrincipal, launchResource.getReservation().getPrincipal());
    Assert.assertEquals(ResourceRequirement.RESOURCE_ID_KEY, launchResource.getReservation().getLabels().getLabelsList().get(0).getKey());
    Assert.assertEquals(ResourceTestUtils.testResourceId, launchResource.getReservation().getLabels().getLabelsList().get(0).getValue());
  }

  @Test
  public void testExpectedRootVolume() throws InvalidTaskRequirementException {
    Resource expectedResource = ResourceTestUtils.getExpectedRootVolume(1000);
    OfferEvaluator offerEvaluator = getOfferEvaluator(expectedResource);
    List<Offer> offers = getOffers(expectedResource);

    List<OfferRecommendation> recommendations = offerEvaluator.evaluate(offers);
    Assert.assertEquals(1, recommendations.size());

    Operation launchOperation = recommendations.get(0).getOperation();
    Resource launchResource =
      launchOperation
      .getLaunch()
      .getTaskInfosList()
      .get(0)
      .getResourcesList()
      .get(0);

    Assert.assertEquals(Operation.Type.LAUNCH, launchOperation.getType());
    Assert.assertEquals(1000, launchResource.getScalar().getValue(), 0.0);
    Assert.assertEquals(ResourceTestUtils.testRole, launchResource.getRole());
    Assert.assertEquals(ResourceTestUtils.testPersistenceId, launchResource.getDisk().getPersistence().getId());
    Assert.assertEquals(ResourceTestUtils.testPrincipal, launchResource.getDisk().getPersistence().getPrincipal());
    Assert.assertEquals(ResourceTestUtils.testPrincipal, launchResource.getReservation().getPrincipal());
    Assert.assertEquals(ResourceRequirement.RESOURCE_ID_KEY, launchResource.getReservation().getLabels().getLabelsList().get(0).getKey());
    Assert.assertEquals(ResourceTestUtils.testResourceId, launchResource.getReservation().getLabels().getLabelsList().get(0).getValue());
  }

  @Test
  public void testReserveLaunchScalar() throws InvalidTaskRequirementException {
    Resource desiredResource = ResourceUtils.getDesiredScalar(
        ResourceTestUtils.testRole,
        ResourceTestUtils.testPrincipal,
        "cpus",
        1.0);
    Resource offeredResource = ResourceTestUtils.getOfferedUnreservedScalar("cpus", 2.0);
    OfferEvaluator offerEvaluator = getOfferEvaluator(desiredResource);
    List<Offer> offers = getOffers(offeredResource);

    List<OfferRecommendation> recommendations = offerEvaluator.evaluate(offers);
    Assert.assertEquals(2, recommendations.size());

    // Validate RESERVE Operation
    Operation reserveOperation = recommendations.get(0).getOperation();
    Resource reserveResource =
      reserveOperation
      .getReserve()
      .getResourcesList()
      .get(0);

    Assert.assertEquals(Operation.Type.RESERVE, reserveOperation.getType());
    Assert.assertEquals(1.0, reserveResource.getScalar().getValue(), 0.0);
    Assert.assertEquals(ResourceTestUtils.testRole, reserveResource.getRole());
    Assert.assertEquals(ResourceTestUtils.testPrincipal, reserveResource.getReservation().getPrincipal());
    Assert.assertEquals(ResourceRequirement.RESOURCE_ID_KEY, reserveResource.getReservation().getLabels().getLabelsList().get(0).getKey());
    Assert.assertEquals(36, reserveResource.getReservation().getLabels().getLabelsList().get(0).getValue().length());
    Assert.assertFalse(reserveResource.hasDisk());

    // Validate LAUNCH Operation
    String resourceId = reserveResource.getReservation().getLabels().getLabelsList().get(0).getValue();
    Operation launchOperation = recommendations.get(1).getOperation();
    Resource launchResource =
      launchOperation
      .getLaunch()
      .getTaskInfosList()
      .get(0)
      .getResourcesList()
      .get(0);

    Assert.assertEquals(Operation.Type.LAUNCH, launchOperation.getType());
    Assert.assertEquals(resourceId, launchResource.getReservation().getLabels().getLabelsList().get(0).getValue());
  }

  @Test
  public void testCustomExecutorReserveLaunchScalar() throws InvalidTaskRequirementException {
    Resource desiredTaskResource = ResourceUtils.getDesiredScalar(
        ResourceTestUtils.testRole,
        ResourceTestUtils.testPrincipal,
        "cpus",
        1.0);
    Resource desiredExecutorResource = ResourceUtils.getDesiredScalar(
        ResourceTestUtils.testRole,
        ResourceTestUtils.testPrincipal,
        "mem",
        2.0);

    Resource offeredTaskResource = ResourceTestUtils.getOfferedUnreservedScalar("cpus", 2.0);
    Resource offeredExecutorResource = ResourceTestUtils.getOfferedUnreservedScalar("mem", 2.0);

    TaskInfo taskInfo = getTaskInfo(desiredTaskResource);
    ExecutorInfo execInfo = getExecutorInfo(desiredExecutorResource);

    OfferRequirement offerRequirement = new OfferRequirement(Arrays.asList(taskInfo), execInfo);
    OfferEvaluator offerEvaluator = new OfferEvaluator(offerRequirement);
    List<Offer> offers = getOffers(Arrays.asList(offeredTaskResource, offeredExecutorResource));

    List<OfferRecommendation> recommendations = offerEvaluator.evaluate(offers);
    Assert.assertEquals(3, recommendations.size());

    // Validate Executor RESERVE Operation
    Operation reserveOperation = recommendations.get(0).getOperation();
    Resource reserveResource =
      reserveOperation
      .getReserve()
      .getResourcesList()
      .get(0);

    Assert.assertEquals(Operation.Type.RESERVE, reserveOperation.getType());
    Assert.assertEquals("mem", reserveResource.getName());
    Assert.assertEquals(2.0, reserveResource.getScalar().getValue(), 0.0);
    Assert.assertEquals(ResourceTestUtils.testRole, reserveResource.getRole());
    Assert.assertEquals(ResourceTestUtils.testPrincipal, reserveResource.getReservation().getPrincipal());
    Assert.assertEquals(ResourceRequirement.RESOURCE_ID_KEY, reserveResource.getReservation().getLabels().getLabelsList().get(0).getKey());
    Assert.assertEquals(36, reserveResource.getReservation().getLabels().getLabelsList().get(0).getValue().length());
    String executorResourceId = reserveResource.getReservation().getLabels().getLabelsList().get(0).getValue();
    Assert.assertFalse(reserveResource.hasDisk());

    // Validate Task RESERVE Operation
    reserveOperation = recommendations.get(1).getOperation();
    reserveResource =
      reserveOperation
      .getReserve()
      .getResourcesList()
      .get(0);

    Assert.assertEquals(Operation.Type.RESERVE, reserveOperation.getType());
    Assert.assertEquals("cpus", reserveResource.getName());
    Assert.assertEquals(1.0, reserveResource.getScalar().getValue(), 0.0);
    Assert.assertEquals(ResourceTestUtils.testRole, reserveResource.getRole());
    Assert.assertEquals(ResourceTestUtils.testPrincipal, reserveResource.getReservation().getPrincipal());
    Assert.assertEquals(ResourceRequirement.RESOURCE_ID_KEY, reserveResource.getReservation().getLabels().getLabelsList().get(0).getKey());
    Assert.assertEquals(36, reserveResource.getReservation().getLabels().getLabelsList().get(0).getValue().length());
    Assert.assertFalse(reserveResource.hasDisk());

    // Validate LAUNCH Operation
    String taskResourceId = reserveResource.getReservation().getLabels().getLabelsList().get(0).getValue();
    Operation launchOperation = recommendations.get(2).getOperation();
    TaskInfo outTaskInfo =
      launchOperation
      .getLaunch()
      .getTaskInfosList()
      .get(0);

    Assert.assertTrue(outTaskInfo.hasExecutor());
    ExecutorInfo outExecInfo = outTaskInfo.getExecutor();
    Assert.assertEquals(executorResourceId, outExecInfo.getResourcesList().get(0).getReservation().getLabels().getLabelsList().get(0).getValue());

    Resource launchResource = outTaskInfo.getResourcesList().get(0);
    Assert.assertEquals(Operation.Type.LAUNCH, launchOperation.getType());
    Assert.assertEquals(taskResourceId, launchResource.getReservation().getLabels().getLabelsList().get(0).getValue());
  }

  @Test
  public void testReuseCustomExecutorReserveLaunchScalar() throws InvalidTaskRequirementException {
    Resource desiredTaskResource = ResourceUtils.getDesiredScalar(
            ResourceTestUtils.testRole,
            ResourceTestUtils.testPrincipal,
            "cpus",
            1.0);
    Resource desiredExecutorResource = ResourceTestUtils.getExpectedScalar("mem", 2.0);

    Resource offeredTaskResource = ResourceTestUtils.getOfferedUnreservedScalar("cpus", 2.0);
    Resource offeredExecutorResource = ResourceTestUtils.getExpectedScalar("mem", 2.0);

    TaskInfo taskInfo = getTaskInfo(desiredTaskResource);
    ExecutorInfo execInfo = getExecutorInfo(desiredExecutorResource);

    OfferRequirement offerRequirement = new OfferRequirement(Arrays.asList(taskInfo), execInfo);
    OfferEvaluator offerEvaluator = new OfferEvaluator(offerRequirement);
    List<Offer> offers = getOffers(Arrays.asList(offeredTaskResource, offeredExecutorResource));

    List<OfferRecommendation> recommendations = offerEvaluator.evaluate(offers);
    Assert.assertEquals(2, recommendations.size());

    // Validate Task RESERVE Operation
    Operation reserveOperation = recommendations.get(0).getOperation();
    Resource reserveResource =
            reserveOperation
                    .getReserve()
                    .getResourcesList()
                    .get(0);

    Assert.assertEquals(Operation.Type.RESERVE, reserveOperation.getType());
    Assert.assertEquals("cpus", reserveResource.getName());
    Assert.assertEquals(1.0, reserveResource.getScalar().getValue(), 0.0);
    Assert.assertEquals(ResourceTestUtils.testRole, reserveResource.getRole());
    Assert.assertEquals(ResourceTestUtils.testPrincipal, reserveResource.getReservation().getPrincipal());
    Assert.assertEquals(ResourceRequirement.RESOURCE_ID_KEY, reserveResource.getReservation().getLabels().getLabelsList().get(0).getKey());
    Assert.assertEquals(36, reserveResource.getReservation().getLabels().getLabelsList().get(0).getValue().length());
    Assert.assertFalse(reserveResource.hasDisk());

    // Validate LAUNCH Operation
    String taskResourceId = reserveResource.getReservation().getLabels().getLabelsList().get(0).getValue();
    Operation launchOperation = recommendations.get(1).getOperation();
    TaskInfo outTaskInfo =
            launchOperation
                    .getLaunch()
                    .getTaskInfosList()
                    .get(0);

    Assert.assertTrue(outTaskInfo.hasExecutor());
    Resource launchResource = outTaskInfo.getResourcesList().get(0);
    Assert.assertEquals(Operation.Type.LAUNCH, launchOperation.getType());
    Assert.assertEquals(taskResourceId, launchResource.getReservation().getLabels().getLabelsList().get(0).getValue());
  }

  @Test
  public void testLaunchExpectedScalar() throws InvalidTaskRequirementException {
    Resource desiredResource = ResourceTestUtils.getExpectedScalar(
        "cpus",
        1.0);

    OfferEvaluator offerEvaluator = getOfferEvaluator(desiredResource);
    List<Offer> offers = getOffers(desiredResource);

    List<OfferRecommendation> recommendations = offerEvaluator.evaluate(offers);
    Assert.assertEquals(1, recommendations.size());

    // Validate LAUNCH Operation
    Operation launchOperation = recommendations.get(0).getOperation();
    Resource launchResource =
      launchOperation
      .getLaunch()
      .getTaskInfosList()
      .get(0)
      .getResourcesList()
      .get(0);

    Assert.assertEquals(Operation.Type.LAUNCH, launchOperation.getType());
    Assert.assertEquals(ResourceTestUtils.testResourceId, launchResource.getReservation().getLabels().getLabelsList().get(0).getValue());
  }

  @Test
  public void testReserveLaunchExpectedScalar() throws InvalidTaskRequirementException {
    Resource desiredResource = ResourceTestUtils.getExpectedScalar("cpus", 2.0);
    Resource offeredResource = ResourceTestUtils.getExpectedScalar("cpus", 1.0);
    Resource unreservedResource = ResourceBuilder.cpus(1.0);

    OfferEvaluator offerEvaluator = getOfferEvaluator(desiredResource);
    List<Offer> offers = getOffers(Arrays.asList(offeredResource, unreservedResource));

    List<OfferRecommendation> recommendations = offerEvaluator.evaluate(offers);
    Assert.assertEquals(2, recommendations.size());

    // Validate RESERVE Operation
    Operation reserveOperation = recommendations.get(0).getOperation();
    Resource reserveResource =
      reserveOperation
      .getReserve()
      .getResourcesList()
      .get(0);

    Assert.assertEquals(Operation.Type.RESERVE, reserveOperation.getType());
    Assert.assertEquals(1.0, reserveResource.getScalar().getValue(), 0.0);
    Assert.assertEquals(ResourceTestUtils.testRole, reserveResource.getRole());
    Assert.assertEquals(ResourceTestUtils.testPrincipal, reserveResource.getReservation().getPrincipal());
    Assert.assertEquals(ResourceRequirement.RESOURCE_ID_KEY, reserveResource.getReservation().getLabels().getLabelsList().get(0).getKey());
    Assert.assertEquals(ResourceTestUtils.testResourceId, reserveResource.getReservation().getLabels().getLabelsList().get(0).getValue());

    // Validate LAUNCH Operation
    Operation launchOperation = recommendations.get(1).getOperation();
    Resource launchResource =
      launchOperation
      .getLaunch()
      .getTaskInfosList()
      .get(0)
      .getResourcesList()
      .get(0);

    Assert.assertEquals(Operation.Type.LAUNCH, launchOperation.getType());
    Assert.assertEquals(ResourceTestUtils.testResourceId, launchResource.getReservation().getLabels().getLabelsList().get(0).getValue());
    Assert.assertEquals(2.0, launchResource.getScalar().getValue(), 0.0);
  }

  @Test
  public void testFailReserveLaunchExpectedScalar() throws InvalidTaskRequirementException {
    Resource desiredResource = ResourceTestUtils.getExpectedScalar("cpus", 2.0);
    Resource offeredResource = ResourceTestUtils.getExpectedScalar("cpus", 1.0);

    OfferEvaluator offerEvaluator = getOfferEvaluator(desiredResource);
    List<Offer> offers = getOffers(offeredResource);

    List<OfferRecommendation> recommendations = offerEvaluator.evaluate(offers);
    Assert.assertEquals(0, recommendations.size());
  }

  @Test
  public void testUnreserveLaunchExpectedScalar() throws InvalidTaskRequirementException {
    Resource desiredResource = ResourceTestUtils.getExpectedScalar("cpus", 1.0);
    Resource offeredResource = ResourceTestUtils.getExpectedScalar("cpus", 2.0);

    OfferEvaluator offerEvaluator = getOfferEvaluator(desiredResource);
    List<Offer> offers = getOffers(offeredResource);

    List<OfferRecommendation> recommendations = offerEvaluator.evaluate(offers);
    Assert.assertEquals(2, recommendations.size());

    // Validate UNRESERVE Operation
    Operation unreserveOperation = recommendations.get(0).getOperation();
    Resource unreserveResource =
      unreserveOperation
      .getUnreserve()
      .getResourcesList()
      .get(0);

    Assert.assertEquals(Operation.Type.UNRESERVE, unreserveOperation.getType());
    Assert.assertEquals(1.0, unreserveResource.getScalar().getValue(), 0.0);
    Assert.assertEquals(ResourceTestUtils.testRole, unreserveResource.getRole());
    Assert.assertEquals(ResourceTestUtils.testPrincipal, unreserveResource.getReservation().getPrincipal());
    Assert.assertEquals(ResourceRequirement.RESOURCE_ID_KEY, unreserveResource.getReservation().getLabels().getLabelsList().get(0).getKey());
    Assert.assertEquals(ResourceTestUtils.testResourceId, unreserveResource.getReservation().getLabels().getLabelsList().get(0).getValue());

    // Validate LAUNCH Operation
    Operation launchOperation = recommendations.get(1).getOperation();
    Resource launchResource =
      launchOperation
      .getLaunch()
      .getTaskInfosList()
      .get(0)
      .getResourcesList()
      .get(0);

    Assert.assertEquals(Operation.Type.LAUNCH, launchOperation.getType());
    Assert.assertEquals(ResourceTestUtils.testResourceId, launchResource.getReservation().getLabels().getLabelsList().get(0).getValue());
    Assert.assertEquals(1.0, launchResource.getScalar().getValue(), 0.0);
  }

  private OfferEvaluator getOfferEvaluator(Resource resource) throws InvalidTaskRequirementException {
    return getOfferEvaluator(Arrays.asList(resource));
  }

  private OfferEvaluator getOfferEvaluator(List<Resource> resources) throws InvalidTaskRequirementException {
    OfferRequirement offerRequirement = getOfferRequirement(resources);
    return new OfferEvaluator(offerRequirement);
  }

  private List<Offer> getOffers(List<Resource> resources) {
    OfferBuilder builder = new OfferBuilder(
        ResourceTestUtils.testOfferId,
        ResourceTestUtils.testFrameworkId,
        ResourceTestUtils.testSlaveId,
        ResourceTestUtils.testHostname);
    builder.addAllResources(resources);
    return Arrays.asList(builder.build());
  }

  private List<Offer> getOffers(Resource resource) {
    return getOffers(Arrays.asList(resource));
  }

  private OfferRequirement getOfferRequirement(List<Resource> resources) throws InvalidTaskRequirementException {
    return new OfferRequirement(Arrays.asList(getTaskInfo(resources)));
  }

  private TaskInfo getTaskInfo(Resource resource) {
    return getTaskInfo(Arrays.asList(resource));
  }

  private TaskInfo getTaskInfo(List<Resource> resources) {
    TaskInfoBuilder builder = new TaskInfoBuilder(
        ResourceTestUtils.testTaskId,
        ResourceTestUtils.testTaskName,
        ResourceTestUtils.testSlaveId);

    return builder.addAllResources(resources).build();
  }

  private ExecutorInfo getExecutorInfo(Resource resource) {
    return getExecutorInfo(Arrays.asList(resource));
  }

  private ExecutorInfo getExecutorInfo(List<Resource> resources) {
    CommandInfo cmd = CommandInfo.newBuilder().build();
    ExecutorInfoBuilder builder = new ExecutorInfoBuilder(ResourceTestUtils.testExecutorId, cmd);
    return builder.addAllResources(resources).build();
  }
}
