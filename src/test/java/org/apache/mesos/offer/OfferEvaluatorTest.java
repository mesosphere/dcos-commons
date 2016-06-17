package org.apache.mesos.offer;

import java.util.*;

import org.apache.mesos.Protos;
import org.apache.mesos.offer.TaskRequirement.InvalidTaskRequirementException;
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
  public void testReserveTaskExecutorInsufficient() throws InvalidTaskRequirementException {
    Resource desiredTaskCpu = ResourceUtils.getDesiredScalar(
            ResourceTestUtils.testRole, ResourceTestUtils.testPrincipal, "cpus", 1.0);
    Resource desiredExecutorCpu = desiredTaskCpu;
    Resource insufficientOfferedResource =
            ResourceTestUtils.getOfferedUnreservedScalar("cpus", 1.0);

    OfferRequirement offerReq = new OfferRequirement(
            Arrays.asList(getTaskInfo(desiredTaskCpu)),
            getExecutorInfo(desiredExecutorCpu),
            null,
            null);
    List<Offer> offers = getOffers(insufficientOfferedResource);

    List<OfferRecommendation> recommendations = evaluator.evaluate(offerReq, offers);
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

    List<OfferRecommendation> recommendations = evaluator.evaluate(
            getOfferRequirement(desiredResource), getOffers(offeredResource));
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
    Resource updatedResource = ResourceTestUtils.getExpectedMountVolume(1500);
    Resource offeredResource = ResourceTestUtils.getExpectedMountVolume(2000);

    List<OfferRecommendation> recommendations = evaluator.evaluate(
            getOfferRequirement(updatedResource), getOffers(offeredResource));
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
    Resource updatedResource = ResourceTestUtils.getExpectedMountVolume(2500);
    Resource offeredResource = ResourceTestUtils.getExpectedMountVolume(2000);

    List<OfferRecommendation> recommendations = evaluator.evaluate(
            getOfferRequirement(updatedResource), getOffers(offeredResource));
    Assert.assertEquals(0, recommendations.size());
  }

  @Test
  public void testFailToCreateVolumeWithWrongResource() throws InvalidTaskRequirementException {
    Resource desiredResource = ResourceUtils.getDesiredRootVolume(
        ResourceTestUtils.testRole,
        ResourceTestUtils.testPrincipal,
        1000,
        ResourceTestUtils.testContainerPath);
    Resource wrongOfferedResource = ResourceTestUtils.getOfferedUnreservedMountVolume(2000);

    List<OfferRecommendation> recommendations = evaluator.evaluate(
            getOfferRequirement(desiredResource), getOffers(wrongOfferedResource));
    Assert.assertEquals(0, recommendations.size());
  }

  @Test
  public void testReserveCreateLaunchRootVolume() throws InvalidTaskRequirementException {
    Resource desiredResource = ResourceUtils.getDesiredRootVolume(
        ResourceTestUtils.testRole,
        ResourceTestUtils.testPrincipal,
        1500,
        ResourceTestUtils.testContainerPath);
    Resource offeredResource = ResourceTestUtils.getOfferedUnreservedRootVolume(2000);

    List<OfferRecommendation> recommendations = evaluator.evaluate(
            getOfferRequirement(desiredResource), getOffers(offeredResource));
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
  public void testFailCreateRootVolume() throws InvalidTaskRequirementException {
    Resource desiredResource = ResourceUtils.getDesiredRootVolume(
        ResourceTestUtils.testRole,
        ResourceTestUtils.testPrincipal,
        2000,
        ResourceTestUtils.testContainerPath);
    Resource offeredResource = ResourceTestUtils.getOfferedUnreservedRootVolume(1000);

    List<OfferRecommendation> recommendations = evaluator.evaluate(
            getOfferRequirement(desiredResource), getOffers(offeredResource));
    Assert.assertEquals(0, recommendations.size());
  }

  @Test
  public void testExpectedMountVolume() throws InvalidTaskRequirementException {
    Resource expectedResource = ResourceTestUtils.getExpectedMountVolume(1000);

    List<OfferRecommendation> recommendations = evaluator.evaluate(
            getOfferRequirement(expectedResource), getOffers(expectedResource));
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
    Assert.assertEquals(ResourceTestUtils.testResourceId, getFirstLabel(launchResource).getValue());
  }

  @Test
  public void testExpectedRootVolume() throws InvalidTaskRequirementException {
    Resource expectedResource = ResourceTestUtils.getExpectedRootVolume(1000);

    List<OfferRecommendation> recommendations = evaluator.evaluate(
            getOfferRequirement(expectedResource), getOffers(expectedResource));
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
    Assert.assertEquals(ResourceTestUtils.testResourceId, getFirstLabel(launchResource).getValue());
  }

  @Test
  public void testReserveLaunchScalar() throws InvalidTaskRequirementException {
    Resource desiredResource = ResourceUtils.getDesiredScalar(
        ResourceTestUtils.testRole,
        ResourceTestUtils.testPrincipal,
        "cpus",
        1.0);
    Resource offeredResource = ResourceTestUtils.getOfferedUnreservedScalar("cpus", 2.0);

    List<OfferRecommendation> recommendations = evaluator.evaluate(
            getOfferRequirement(desiredResource), getOffers(offeredResource));
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

    List<OfferRecommendation> recommendations = evaluator.evaluate(
            new OfferRequirement(Arrays.asList(taskInfo), execInfo),
            getOffers(offeredTaskResource, offeredExecutorResource));
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

    List<OfferRecommendation> recommendations = evaluator.evaluate(
            new OfferRequirement(Arrays.asList(taskInfo), execInfo),
            getOffers(offeredTaskResource, offeredExecutorResource));
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
  public void testLaunchExpectedScalar() throws InvalidTaskRequirementException {
    Resource desiredResource = ResourceTestUtils.getExpectedScalar(
        "cpus",
        1.0);

    List<OfferRecommendation> recommendations = evaluator.evaluate(
            getOfferRequirement(desiredResource), getOffers(desiredResource));
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
    Assert.assertEquals(ResourceTestUtils.testResourceId, getFirstLabel(launchResource).getValue());
  }

  @Test
  public void testReserveLaunchExpectedScalar() throws InvalidTaskRequirementException {
    Resource desiredResource = ResourceTestUtils.getExpectedScalar("cpus", 2.0);
    Resource offeredResource = ResourceTestUtils.getExpectedScalar("cpus", 1.0);
    Resource unreservedResource = ResourceBuilder.cpus(1.0);

    List<OfferRecommendation> recommendations = evaluator.evaluate(
            getOfferRequirement(desiredResource), getOffers(offeredResource, unreservedResource));
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
    Assert.assertEquals(ResourceTestUtils.testResourceId, getFirstLabel(reserveResource).getValue());

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
    Assert.assertEquals(ResourceTestUtils.testResourceId, getFirstLabel(launchResource).getValue());
    Assert.assertEquals(2.0, launchResource.getScalar().getValue(), 0.0);
  }

  @Test
  public void testFailReserveLaunchExpectedScalar() throws InvalidTaskRequirementException {
    Resource desiredResource = ResourceTestUtils.getExpectedScalar("cpus", 2.0);
    Resource offeredResource = ResourceTestUtils.getExpectedScalar("cpus", 1.0);

    List<OfferRecommendation> recommendations = evaluator.evaluate(
            getOfferRequirement(desiredResource), getOffers(offeredResource));
    Assert.assertEquals(0, recommendations.size());
  }

  @Test
  public void testUnreserveLaunchExpectedScalar() throws InvalidTaskRequirementException {
    Resource desiredResource = ResourceTestUtils.getExpectedScalar("cpus", 1.0);
    Resource offeredResource = ResourceTestUtils.getExpectedScalar("cpus", 2.0);

    List<OfferRecommendation> recommendations = evaluator.evaluate(
            getOfferRequirement(desiredResource), getOffers(offeredResource));
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
    Assert.assertEquals(ResourceTestUtils.testResourceId, getFirstLabel(unreserveResource).getValue());

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
    Assert.assertEquals(ResourceTestUtils.testResourceId, getFirstLabel(launchResource).getValue());
    Assert.assertEquals(1.0, launchResource.getScalar().getValue(), 0.0);
  }

  @Test
  public void testAvoidAgents() throws Exception{
    Resource desiredCpu = ResourceUtils.getDesiredScalar(
            ResourceTestUtils.testRole, ResourceTestUtils.testPrincipal, "cpus", 1.0);
    Resource offeredCpu = ResourceTestUtils.getOfferedUnreservedScalar("cpus", 2.0);

    List<OfferRecommendation> recommendations = evaluator.evaluate(
            getOfferRequirement(
                    desiredCpu,
                    Arrays.asList(ResourceTestUtils.testSlaveId),
                    Collections.emptyList()),
            getOffers(offeredCpu));

    Assert.assertEquals(0, recommendations.size());

    recommendations = evaluator.evaluate(
            getOfferRequirement(
                    desiredCpu,
                    Arrays.asList("some-random-agent"),
                    Collections.emptyList()),
            getOffers(offeredCpu));

    Assert.assertEquals(2, recommendations.size());
  }

  @Test
  public void testCollocateAgents() throws Exception{
    Resource desiredCpu = ResourceUtils.getDesiredScalar(
            ResourceTestUtils.testRole, ResourceTestUtils.testPrincipal, "cpus", 1.0);
    Resource offeredCpu = ResourceTestUtils.getOfferedUnreservedScalar("cpus", 2.0);

    List<OfferRecommendation> recommendations = evaluator.evaluate(
            getOfferRequirement(
                    desiredCpu,
                    Collections.emptyList(),
                    Arrays.asList("some-random-agent")),
            getOffers(offeredCpu));

    Assert.assertEquals(0, recommendations.size());

    recommendations = evaluator.evaluate(
            getOfferRequirement(
                    desiredCpu,
                    Collections.emptyList(),
                    Arrays.asList(ResourceTestUtils.testSlaveId)),
            getOffers(offeredCpu));

    Assert.assertEquals(2, recommendations.size());
  }

  private static Label getFirstLabel(Resource resource) {
    return resource.getReservation().getLabels().getLabels(0);
  }

  private static List<Offer> getOffers(Resource... resources) {
    OfferBuilder builder = new OfferBuilder(
        ResourceTestUtils.testOfferId,
        ResourceTestUtils.testFrameworkId,
        ResourceTestUtils.testSlaveId,
        ResourceTestUtils.testHostname);
    return Arrays.asList(builder.addAllResources(Arrays.asList(resources)).build());
  }

  private static OfferRequirement getOfferRequirement(Resource resource)
          throws InvalidTaskRequirementException {
    return getOfferRequirement(resource, Collections.emptyList(), Collections.emptyList());
  }

  private static OfferRequirement getOfferRequirement(Resource resource, List<String> avoidAgents, List<String> collocateAgents)
    throws InvalidTaskRequirementException {
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
    CommandInfo cmd = CommandInfo.newBuilder().build();
    ExecutorInfoBuilder builder = new ExecutorInfoBuilder(ResourceTestUtils.testExecutorId, cmd);
    return builder.addResource(resource).build();
  }
}
