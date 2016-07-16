package org.apache.mesos.offer;

import java.util.*;

import org.apache.mesos.Protos;
import org.apache.mesos.executor.ExecutorUtils;
import org.apache.mesos.protobuf.ExecutorInfoBuilder;
import org.apache.mesos.protobuf.OfferBuilder;
import org.apache.mesos.protobuf.ResourceBuilder;
import org.apache.mesos.protobuf.TaskInfoBuilder;

import org.apache.mesos.Protos.CommandInfo;
import org.apache.mesos.Protos.ExecutorInfo;
import org.apache.mesos.Protos.Label;
import org.apache.mesos.Protos.Offer;
import org.apache.mesos.Protos.Offer.Operation;
import org.apache.mesos.Protos.Resource;
import org.apache.mesos.Protos.TaskInfo;

import org.junit.Assert;
import org.junit.Test;

public class OfferEvaluatorTest {

  private static final OfferEvaluator evaluator = new OfferEvaluator();

  @Test
  public void testReserveTaskExecutorInsufficient() throws InvalidRequirementException {
    Resource desiredTaskCpu = ResourceUtils.getDesiredScalar(
            ResourceTestUtils.testRole, ResourceTestUtils.testPrincipal, "cpus", 1.0);
    Resource desiredExecutorCpu = desiredTaskCpu;
    Resource insufficientOfferedResource =
            ResourceUtils.getUnreservedScalar("cpus", 1.0);

    OfferRequirement offerReq = new OfferRequirement(
            Arrays.asList(getTaskInfo(desiredTaskCpu)),
            getExecutorInfo(desiredExecutorCpu),
            null,
            null);
    List<Offer> offers = Arrays.asList(getOffer(insufficientOfferedResource));

    List<OfferRecommendation> recommendations = evaluator.evaluate(offerReq, offers);
    Assert.assertEquals(0, recommendations.size());
  }

  @Test
  public void testReserveCreateLaunchMountVolume() throws InvalidRequirementException {
    Resource desiredResource = ResourceUtils.getDesiredMountVolume(
        ResourceTestUtils.testRole,
        ResourceTestUtils.testPrincipal,
        1000,
        ResourceTestUtils.testContainerPath);
    Resource offeredResource = ResourceTestUtils.getOfferedUnreservedMountVolume(2000);

    List<OfferRecommendation> recommendations = evaluator.evaluate(
            getOfferRequirement(desiredResource),
            Arrays.asList(getOffer(offeredResource)));
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
    Assert.assertEquals(ResourceRequirement.RESOURCE_ID_KEY, getFirstLabel(reserveResource).getKey());
    Assert.assertEquals(36, getFirstLabel(reserveResource).getValue().length());

    // Validate CREATE Operation
    String resourceId = getFirstLabel(reserveResource).getValue();
    Operation createOperation = recommendations.get(1).getOperation();
    Resource createResource =
      createOperation
      .getCreate()
      .getVolumesList()
      .get(0);

    Assert.assertEquals(resourceId, getFirstLabel(createResource).getValue());
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
    Assert.assertEquals(resourceId, getFirstLabel(launchResource).getValue());
    Assert.assertEquals(persistenceId, launchResource.getDisk().getPersistence().getId());
    Assert.assertEquals(ResourceTestUtils.testMountRoot, launchResource.getDisk().getSource().getMount().getRoot());
    Assert.assertEquals(ResourceTestUtils.testPrincipal, launchResource.getDisk().getPersistence().getPrincipal());
    Assert.assertEquals(2000, launchResource.getScalar().getValue(), 0.0);
  }

  @Test
  public void testUpdateMountVolumeSuccess() throws Exception {
    String resourceId = UUID.randomUUID().toString();
    Resource updatedResource = ResourceTestUtils.getExpectedMountVolume(1500, resourceId);
    Resource offeredResource = ResourceTestUtils.getExpectedMountVolume(2000, resourceId);

    List<OfferRecommendation> recommendations = evaluator.evaluate(
            getOfferRequirement(updatedResource),
            Arrays.asList(getOffer(offeredResource)));
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
    Assert.assertEquals(getFirstLabel(updatedResource).getValue(), getFirstLabel(launchResource).getValue());
    Assert.assertEquals(updatedResource.getDisk().getPersistence().getId(), launchResource.getDisk().getPersistence().getId());
    Assert.assertEquals(ResourceTestUtils.testMountRoot, launchResource.getDisk().getSource().getMount().getRoot());
    Assert.assertEquals(ResourceTestUtils.testPrincipal, launchResource.getDisk().getPersistence().getPrincipal());
    Assert.assertEquals(2000, launchResource.getScalar().getValue(), 0.0);
  }

  @Test
  public void testUpdateMountVolumeFailure() throws Exception {
    String resourceId = UUID.randomUUID().toString();
    Resource updatedResource = ResourceTestUtils.getExpectedMountVolume(2500, resourceId);
    Resource offeredResource = ResourceTestUtils.getExpectedMountVolume(2000, resourceId);

    List<OfferRecommendation> recommendations = evaluator.evaluate(
            getOfferRequirement(updatedResource),
            Arrays.asList(getOffer(offeredResource)));
    Assert.assertEquals(0, recommendations.size());
  }

  @Test
  public void testFailToCreateVolumeWithWrongResource() throws InvalidRequirementException {
    Resource desiredResource = ResourceUtils.getDesiredRootVolume(
        ResourceTestUtils.testRole,
        ResourceTestUtils.testPrincipal,
        1000,
        ResourceTestUtils.testContainerPath);
    Resource wrongOfferedResource = ResourceTestUtils.getOfferedUnreservedMountVolume(2000);

    List<OfferRecommendation> recommendations = evaluator.evaluate(
            getOfferRequirement(desiredResource),
            Arrays.asList(getOffer(wrongOfferedResource)));
    Assert.assertEquals(0, recommendations.size());
  }

  @Test
  public void testReserveCreateLaunchRootVolume() throws InvalidRequirementException {
    Resource desiredResource = ResourceUtils.getDesiredRootVolume(
        ResourceTestUtils.testRole,
        ResourceTestUtils.testPrincipal,
        1500,
        ResourceTestUtils.testContainerPath);
    Resource offeredResource = ResourceUtils.getUnreservedRootVolume(2000);

    List<OfferRecommendation> recommendations = evaluator.evaluate(
            getOfferRequirement(desiredResource),
            Arrays.asList(getOffer(offeredResource)));
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
    Assert.assertEquals(ResourceRequirement.RESOURCE_ID_KEY, getFirstLabel(reserveResource).getKey());
    Assert.assertEquals(36, getFirstLabel(reserveResource).getValue().length());

    // Validate CREATE Operation
    String resourceId = getFirstLabel(reserveResource).getValue();
    Operation createOperation = recommendations.get(1).getOperation();
    Resource createResource =
      createOperation
      .getCreate()
      .getVolumesList()
      .get(0);

    Assert.assertEquals(resourceId, getFirstLabel(createResource).getValue());
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
    Assert.assertEquals(resourceId, getFirstLabel(launchResource).getValue());
    Assert.assertEquals(persistenceId, launchResource.getDisk().getPersistence().getId());
    Assert.assertEquals(ResourceTestUtils.testPrincipal, launchResource.getDisk().getPersistence().getPrincipal());
  }

  @Test
  public void testFailCreateRootVolume() throws InvalidRequirementException {
    Resource desiredResource = ResourceUtils.getDesiredRootVolume(
        ResourceTestUtils.testRole,
        ResourceTestUtils.testPrincipal,
        2000,
        ResourceTestUtils.testContainerPath);
    Resource offeredResource = ResourceUtils.getUnreservedRootVolume(1000);

    List<OfferRecommendation> recommendations = evaluator.evaluate(
            getOfferRequirement(desiredResource),
            Arrays.asList(getOffer(offeredResource)));
    Assert.assertEquals(0, recommendations.size());
  }

  @Test
  public void testExpectedMountVolume() throws InvalidRequirementException {
    String resourceId = UUID.randomUUID().toString();
    Resource expectedResource = ResourceTestUtils.getExpectedMountVolume(1000, resourceId);

    List<OfferRecommendation> recommendations = evaluator.evaluate(
            getOfferRequirement(expectedResource),
            Arrays.asList(getOffer(expectedResource)));
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
    Assert.assertEquals(ResourceRequirement.RESOURCE_ID_KEY, getFirstLabel(launchResource).getKey());
    Assert.assertEquals(resourceId, getFirstLabel(launchResource).getValue());
  }

  @Test
  public void testExpectedRootVolume() throws InvalidRequirementException {
    String resourceId = UUID.randomUUID().toString();
    Resource expectedResource = ResourceTestUtils.getExpectedRootVolume(1000, resourceId);

    List<OfferRecommendation> recommendations = evaluator.evaluate(
            getOfferRequirement(expectedResource),
            Arrays.asList(getOffer(expectedResource)));
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
    Assert.assertEquals(ResourceRequirement.RESOURCE_ID_KEY, getFirstLabel(launchResource).getKey());
    Assert.assertEquals(resourceId, getFirstLabel(launchResource).getValue());
  }

  @Test
  public void testReserveLaunchScalar() throws InvalidRequirementException {
    Resource desiredResource = ResourceUtils.getDesiredScalar(
        ResourceTestUtils.testRole,
        ResourceTestUtils.testPrincipal,
        "cpus",
        1.0);
    Resource offeredResource = ResourceUtils.getUnreservedScalar("cpus", 2.0);

    List<OfferRecommendation> recommendations = evaluator.evaluate(
            getOfferRequirement(desiredResource),
            Arrays.asList(getOffer(offeredResource)));
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
    Assert.assertEquals(ResourceRequirement.RESOURCE_ID_KEY, getFirstLabel(reserveResource).getKey());
    Assert.assertEquals(36, getFirstLabel(reserveResource).getValue().length());
    Assert.assertFalse(reserveResource.hasDisk());

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
    Assert.assertEquals(getFirstLabel(reserveResource).getValue(), getFirstLabel(launchResource).getValue());
  }

  @Test
  public void testCustomExecutorReserveLaunchScalar() throws InvalidRequirementException {
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

    Resource offeredTaskResource = ResourceUtils.getUnreservedScalar("cpus", 2.0);
    Resource offeredExecutorResource = ResourceUtils.getUnreservedScalar("mem", 2.0);

    TaskInfo taskInfo = getTaskInfo(desiredTaskResource);
    ExecutorInfo execInfo = getExecutorInfo(desiredExecutorResource);

    List<OfferRecommendation> recommendations = evaluator.evaluate(
            new OfferRequirement(Arrays.asList(taskInfo), execInfo),
            Arrays.asList(getOffer(offeredTaskResource, offeredExecutorResource)));
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
    String executorResourceId = getFirstLabel(reserveResource).getValue();
    Assert.assertEquals(ResourceRequirement.RESOURCE_ID_KEY, getFirstLabel(reserveResource).getKey());
    Assert.assertEquals(36, executorResourceId.length());
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
    Assert.assertEquals(ResourceRequirement.RESOURCE_ID_KEY, getFirstLabel(reserveResource).getKey());
    Assert.assertEquals(36, getFirstLabel(reserveResource).getValue().length());
    Assert.assertFalse(reserveResource.hasDisk());

    // Validate LAUNCH Operation
    Operation launchOperation = recommendations.get(2).getOperation();
    TaskInfo outTaskInfo =
      launchOperation
      .getLaunch()
      .getTaskInfosList()
      .get(0);

    Assert.assertTrue(outTaskInfo.hasExecutor());
    ExecutorInfo outExecInfo = outTaskInfo.getExecutor();
    Assert.assertEquals(executorResourceId, getFirstLabel(outExecInfo.getResourcesList().get(0)).getValue());

    Resource launchResource = outTaskInfo.getResourcesList().get(0);
    Assert.assertEquals(Operation.Type.LAUNCH, launchOperation.getType());
    Assert.assertEquals(getFirstLabel(reserveResource).getValue(), getFirstLabel(launchResource).getValue());
  }

  @Test
  public void testReuseCustomExecutorReserveLaunchScalar() throws InvalidRequirementException {
    String resourceId = UUID.randomUUID().toString();
    Resource desiredTaskResource = ResourceUtils.getDesiredScalar(
            ResourceTestUtils.testRole,
            ResourceTestUtils.testPrincipal,
            "cpus",
            1.0);
    Resource offeredTaskResource = ResourceUtils.getUnreservedScalar("cpus", 2.0);

    Resource desiredExecutorResource = ResourceTestUtils.getExpectedScalar("mem", 2.0, resourceId);
    Resource offeredExecutorResource = desiredExecutorResource;

    TaskInfo taskInfo = getTaskInfo(desiredTaskResource);
    ExecutorInfo execInfo = getExecutorInfo(desiredExecutorResource, true);

    List<OfferRecommendation> recommendations = evaluator.evaluate(
            new OfferRequirement(Arrays.asList(taskInfo), execInfo),
            Arrays.asList(getOffer(offeredTaskResource, offeredExecutorResource)));
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
    Assert.assertEquals(ResourceRequirement.RESOURCE_ID_KEY, getFirstLabel(reserveResource).getKey());
    Assert.assertEquals(36, getFirstLabel(reserveResource).getValue().length());
    Assert.assertFalse(reserveResource.hasDisk());

    // Validate LAUNCH Operation
    Operation launchOperation = recommendations.get(1).getOperation();
    TaskInfo outTaskInfo =
            launchOperation
                    .getLaunch()
                    .getTaskInfosList()
                    .get(0);

    Assert.assertTrue(outTaskInfo.hasExecutor());
    Resource launchResource = outTaskInfo.getResourcesList().get(0);
    Assert.assertEquals(Operation.Type.LAUNCH, launchOperation.getType());
    Assert.assertEquals(getFirstLabel(reserveResource).getValue(), getFirstLabel(launchResource).getValue());
  }

  @Test
  public void testLaunchExpectedScalar() throws InvalidRequirementException {
    String resourceId = UUID.randomUUID().toString();
    Resource desiredResource = ResourceTestUtils.getExpectedScalar("cpus", 1.0, resourceId);

    List<OfferRecommendation> recommendations = evaluator.evaluate(
            getOfferRequirement(desiredResource),
            Arrays.asList(getOffer(desiredResource)));
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
    Assert.assertEquals(resourceId, getFirstLabel(launchResource).getValue());
  }

  @Test
  public void testReserveLaunchExpectedScalar() throws InvalidRequirementException {
    String resourceId = UUID.randomUUID().toString();
    Resource desiredResource = ResourceTestUtils.getExpectedScalar("cpus", 2.0, resourceId);
    Resource offeredResource = ResourceTestUtils.getExpectedScalar("cpus", 1.0, resourceId);
    Resource unreservedResource = ResourceBuilder.cpus(1.0);

    List<OfferRecommendation> recommendations = evaluator.evaluate(
            getOfferRequirement(desiredResource),
            Arrays.asList(getOffer(offeredResource, unreservedResource)));
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
    Assert.assertEquals(ResourceRequirement.RESOURCE_ID_KEY, getFirstLabel(reserveResource).getKey());
    Assert.assertEquals(resourceId, getFirstLabel(reserveResource).getValue());

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
    Assert.assertEquals(resourceId, getFirstLabel(launchResource).getValue());
    Assert.assertEquals(2.0, launchResource.getScalar().getValue(), 0.0);
  }

  @Test
  public void testFailReserveLaunchExpectedScalar() throws InvalidRequirementException {
    String resourceId = UUID.randomUUID().toString();
    Resource desiredResource = ResourceTestUtils.getExpectedScalar("cpus", 2.0, resourceId);
    Resource offeredResource = ResourceTestUtils.getExpectedScalar("cpus", 1.0, resourceId);

    List<OfferRecommendation> recommendations = evaluator.evaluate(
            getOfferRequirement(desiredResource),
            Arrays.asList(getOffer(offeredResource)));
    Assert.assertEquals(0, recommendations.size());
  }

  @Test
  public void testUnreserveLaunchExpectedScalar() throws InvalidRequirementException {
    String resourceId = UUID.randomUUID().toString();
    Resource desiredResource = ResourceTestUtils.getExpectedScalar("cpus", 1.0, resourceId);
    Resource offeredResource = ResourceTestUtils.getExpectedScalar("cpus", 2.0, resourceId);

    List<OfferRecommendation> recommendations = evaluator.evaluate(
            getOfferRequirement(desiredResource),
            Arrays.asList(getOffer(offeredResource)));
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
    Assert.assertEquals(ResourceRequirement.RESOURCE_ID_KEY, getFirstLabel(unreserveResource).getKey());
    Assert.assertEquals(resourceId, getFirstLabel(unreserveResource).getValue());

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
    Assert.assertEquals(resourceId, getFirstLabel(launchResource).getValue());
    Assert.assertEquals(1.0, launchResource.getScalar().getValue(), 0.0);
  }

  @Test
  public void testAvoidAgents() throws Exception{
    Resource desiredCpu = ResourceUtils.getDesiredScalar(
            ResourceTestUtils.testRole, ResourceTestUtils.testPrincipal, "cpus", 1.0);
    Resource offeredCpu = ResourceUtils.getUnreservedScalar("cpus", 2.0);

    List<OfferRecommendation> recommendations = evaluator.evaluate(
            getOfferRequirement(
                    desiredCpu,
                    Arrays.asList(ResourceTestUtils.testSlaveId),
                    Collections.emptyList()),
            Arrays.asList(getOffer(offeredCpu)));

    Assert.assertEquals(0, recommendations.size());

    recommendations = evaluator.evaluate(
            getOfferRequirement(
                    desiredCpu,
                    Arrays.asList("some-random-agent"),
                    Collections.emptyList()),
            Arrays.asList(getOffer(offeredCpu)));

    Assert.assertEquals(2, recommendations.size());
  }

  @Test
  public void testCollocateAgents() throws Exception{
    Resource desiredCpu = ResourceUtils.getDesiredScalar(
            ResourceTestUtils.testRole, ResourceTestUtils.testPrincipal, "cpus", 1.0);
    Resource offeredCpu = ResourceUtils.getUnreservedScalar("cpus", 2.0);

    List<OfferRecommendation> recommendations = evaluator.evaluate(
            getOfferRequirement(
                    desiredCpu,
                    Collections.emptyList(),
                    Arrays.asList("some-random-agent")),
            Arrays.asList(getOffer(offeredCpu)));

    Assert.assertEquals(0, recommendations.size());

    recommendations = evaluator.evaluate(
            getOfferRequirement(
                    desiredCpu,
                    Collections.emptyList(),
                    Arrays.asList(ResourceTestUtils.testSlaveId)),
            Arrays.asList(getOffer(offeredCpu)));

    Assert.assertEquals(2, recommendations.size());
  }

  @Test
  public void testRejectOfferWithoutExpectedExecutorId() throws Exception {
    String resourceId = UUID.randomUUID().toString();
    Resource expectedTaskCpu = ResourceTestUtils.getExpectedScalar("cpus", 1.0, resourceId);
    Resource expectedExecutorMem = ResourceTestUtils.getExpectedScalar("mem", 256, resourceId);

    TaskInfo taskInfo = getTaskInfo(expectedTaskCpu);
    ExecutorInfo execInfo = getExecutorInfo(expectedExecutorMem);

    // Set incorrect ExecutorID
    execInfo = ExecutorInfo.newBuilder(execInfo)
            .setExecutorId(ExecutorUtils.toExecutorId(execInfo.getName()))
            .build();

    OfferRequirement offerRequirement = new OfferRequirement(Arrays.asList(taskInfo), execInfo);

    List<OfferRecommendation> recommendations = evaluator.evaluate(
            offerRequirement,
            Arrays.asList(getOffer(expectedTaskCpu, expectedExecutorMem)));

    Assert.assertEquals(0, recommendations.size());
  }

  @Test
  public void testAcceptOfferWithExpectedExecutorId() throws Exception {
    String taskResourceId = UUID.randomUUID().toString();
    String executorResourceId = UUID.randomUUID().toString();
    Resource expectedTaskCpu = ResourceTestUtils.getExpectedScalar("cpus", 1.0, taskResourceId);
    Resource expectedExecutorMem = ResourceTestUtils.getExpectedScalar("mem", 256, executorResourceId);

    TaskInfo taskInfo = getTaskInfo(expectedTaskCpu);
    ExecutorInfo execInfo = getExecutorInfo(expectedExecutorMem, true);

    OfferRequirement offerRequirement = new OfferRequirement(Arrays.asList(taskInfo), execInfo);

    List<OfferRecommendation> recommendations = evaluator.evaluate(
            offerRequirement,
            Arrays.asList(getOffer(expectedTaskCpu, expectedExecutorMem)));

    Assert.assertEquals(1, recommendations.size());
    Operation launchOperation = recommendations.get(0).getOperation();
    Assert.assertEquals(Operation.Type.LAUNCH, launchOperation.getType());
  }

  @Test
  public void testRelaunchTaskWithCustomExecutor() throws Exception {
    String taskResourceId = UUID.randomUUID().toString();
    String executorResourceId = UUID.randomUUID().toString();
    Resource expectedTaskCpu = ResourceTestUtils.getExpectedScalar("cpus", 1.0, taskResourceId);
    Resource expectedExecutorMem = ResourceTestUtils.getExpectedScalar("mem", 256, executorResourceId);

    TaskInfo taskInfo = getTaskInfo(expectedTaskCpu);
    ExecutorInfo execInfo = getExecutorInfo(expectedExecutorMem, false);

    OfferRequirement offerRequirement = new OfferRequirement(Arrays.asList(taskInfo), execInfo);

    List<OfferRecommendation> recommendations = evaluator.evaluate(
            offerRequirement,
            Arrays.asList(getOffer(null, Arrays.asList(expectedTaskCpu, expectedExecutorMem))));

    Assert.assertEquals(1, recommendations.size());
    Operation launchOperation = recommendations.get(0).getOperation();
    Assert.assertEquals(Operation.Type.LAUNCH, launchOperation.getType());
    TaskInfo launchedTaskInfo = launchOperation.getLaunch().getTaskInfosList().get(0);
    Assert.assertNotEquals("", launchedTaskInfo.getExecutor().getExecutorId().getValue());
  }

  @Test
  public void testLaunchMultipleTasksPerExecutor() throws Exception {
    Resource desiredTask0Cpu = ResourceUtils.getDesiredScalar(
            ResourceTestUtils.testRole,
            ResourceTestUtils.testPrincipal,
            "cpus",
            1.0);
    Resource desiredTask1Cpu = ResourceUtils.getDesiredScalar(
            ResourceTestUtils.testRole,
            ResourceTestUtils.testPrincipal,
            "cpus",
            2.0);
    Resource desiredExecutorCpu = ResourceUtils.getDesiredScalar(
            ResourceTestUtils.testRole,
            ResourceTestUtils.testPrincipal,
            "cpus",
            3.0);
    Resource offeredResource = ResourceUtils.getUnreservedScalar("cpus", 6.0);

    TaskInfo taskInfo0 = getTaskInfo(desiredTask0Cpu);
    TaskInfo taskInfo1 = getTaskInfo(desiredTask1Cpu);
    ExecutorInfo execInfo = getExecutorInfo(desiredExecutorCpu, false);
    OfferRequirement offerRequirement = new OfferRequirement(Arrays.asList(taskInfo0, taskInfo1), execInfo);
    List<OfferRecommendation> recommendations = evaluator.evaluate(
            offerRequirement,
            Arrays.asList(getOffer(null, Arrays.asList(offeredResource))));

    Assert.assertEquals(5, recommendations.size());
    Assert.assertEquals(Operation.Type.RESERVE, recommendations.get(0).getOperation().getType());
    Assert.assertEquals(Operation.Type.RESERVE, recommendations.get(1).getOperation().getType());
    Assert.assertEquals(Operation.Type.RESERVE, recommendations.get(2).getOperation().getType());
    Operation launchOp0 = recommendations.get(3).getOperation();
    Assert.assertEquals(Operation.Type.LAUNCH, launchOp0.getType());
    Operation launchOp1 = recommendations.get(4).getOperation();
    Assert.assertEquals(Operation.Type.LAUNCH, launchOp1.getType());
    Protos.ExecutorID launch0ExecutorId = launchOp0.getLaunch().getTaskInfos(0).getExecutor().getExecutorId();
    Protos.ExecutorID launch1ExecutorId = launchOp1.getLaunch().getTaskInfos(0).getExecutor().getExecutorId();
    Assert.assertEquals(launch0ExecutorId, launch1ExecutorId);
  }

  @Test
  public void testLaunchNotOnFirstOffer() throws InvalidRequirementException {
    Resource desiredResource = ResourceUtils.getDesiredScalar(
            ResourceTestUtils.testRole,
            ResourceTestUtils.testPrincipal,
            "cpus",
            1.0);

    Resource insufficientOffer = ResourceUtils.getUnreservedScalar("mem", 2.0);
    Resource sufficientOffer = ResourceUtils.getUnreservedScalar("cpus", 2.0);

    List<OfferRecommendation> recommendations = evaluator.evaluate(
            getOfferRequirement(desiredResource),
            Arrays.asList(
                    getOffer(insufficientOffer),
                    getOffer(sufficientOffer)));
    Assert.assertEquals(2, recommendations.size());

    // Validate RESERVE Operation
    Operation reserveOperation = recommendations.get(0).getOperation();
    Assert.assertEquals(Operation.Type.RESERVE, reserveOperation.getType());

    // Validate LAUNCH Operation
    Operation launchOperation = recommendations.get(1).getOperation();
    Assert.assertEquals(Operation.Type.LAUNCH, launchOperation.getType());
  }

  private static Label getFirstLabel(Resource resource) {
    return resource.getReservation().getLabels().getLabels(0);
  }

  private static Offer getOffer(Resource resource) {
    return getOffer(Arrays.asList(resource));
  }

  private static Offer getOffer(List<Resource> resources) {
    return getOffer(ResourceTestUtils.testExecutorId, resources);
  }

  private static Offer getOffer(Resource... resources) {
    return getOffer(Arrays.asList(resources));
  }

  private static Offer getOffer(String executorId, List<Resource> resources) {
    OfferBuilder builder = new OfferBuilder(
        ResourceTestUtils.testOfferId,
        ResourceTestUtils.testFrameworkId,
        ResourceTestUtils.testSlaveId,
        ResourceTestUtils.testHostname);

    if (executorId != null) {
      builder.addExecutorIds(Arrays.asList(executorId));
    }

    return builder.addAllResources(resources).build();
  }

  private static OfferRequirement getOfferRequirement(Resource resource)
          throws InvalidRequirementException {
    return getOfferRequirement(resource, Collections.emptyList(), Collections.emptyList());
  }

  private static OfferRequirement getOfferRequirement(
          Resource resource, List<String> avoidAgents, List<String> collocateAgents)
                  throws InvalidRequirementException {
    return new OfferRequirement(
            Arrays.asList(getTaskInfo(resource)),
            null,
            toSlaveIds(avoidAgents),
            toSlaveIds(collocateAgents));
  }

  private static Collection<Protos.SlaveID> toSlaveIds(List<String> ids) {
    List<Protos.SlaveID> slaveIds = new ArrayList<>();
    for (String id : ids) {
      slaveIds.add(Protos.SlaveID.newBuilder().setValue(id).build());
    }
    return slaveIds;
  }

  private static TaskInfo getTaskInfo(Resource resource) {
    TaskInfoBuilder builder = new TaskInfoBuilder(
        ResourceTestUtils.testTaskId,
        ResourceTestUtils.testTaskName,
        ResourceTestUtils.testSlaveId);
    return builder.addResource(resource).build();
  }

  private static ExecutorInfo getExecutorInfo(Resource resource) {
    return getExecutorInfo(resource, false);
  }

  private static ExecutorInfo getExecutorInfo(Resource resource, boolean existingExecutor) {
    if (existingExecutor) {
      return getExecutorInfo(resource, ResourceTestUtils.testExecutorId);
    } else {
      return getExecutorInfo(resource, "");
    }
  }

  private static ExecutorInfo getExecutorInfo(Resource resource, String executorID) {
    CommandInfo cmd = CommandInfo.newBuilder().build();
    ExecutorInfoBuilder builder = new ExecutorInfoBuilder(
            executorID, ResourceTestUtils.testExecutorName, cmd);
    return builder.addResource(resource).build();
  }
}
