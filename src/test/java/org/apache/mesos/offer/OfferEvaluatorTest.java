package org.apache.mesos.offer;

import org.apache.mesos.Protos;
import org.apache.mesos.Protos.*;
import org.apache.mesos.Protos.Offer.Operation;
import org.apache.mesos.executor.ExecutorUtils;
import org.apache.mesos.testutils.*;
import org.junit.Assert;
import org.junit.Test;

import java.util.*;

public class OfferEvaluatorTest {

    private static final OfferEvaluator evaluator = new OfferEvaluator();

    @Test
    public void testReserveTaskExecutorInsufficient() throws InvalidRequirementException {
        Resource desiredTaskCpu = ResourceTestUtils.getDesiredCpu(1.0);
        Resource desiredExecutorCpu = desiredTaskCpu;
        Resource insufficientOfferedResource =
                        ResourceUtils.getUnreservedScalar("cpus", 1.0);

        OfferRequirement offerReq = new OfferRequirement(
                        Arrays.asList(TaskTestUtils.getTaskInfo(desiredTaskCpu)),
                        Optional.of(TaskTestUtils.getExecutorInfo(desiredExecutorCpu)),
                        null,
                        null);
        List<Offer> offers = Arrays.asList(OfferTestUtils.getOffer(insufficientOfferedResource));

        List<OfferRecommendation> recommendations = evaluator.evaluate(offerReq, offers);
        Assert.assertEquals(0, recommendations.size());
    }

    @Test
    public void testReserveCreateLaunchMountVolume() throws InvalidRequirementException {
        Resource desiredResource = ResourceTestUtils.getDesiredMountVolume(1000);
        Resource offeredResource = ResourceTestUtils.getUnreservedMountVolume(2000);

        List<OfferRecommendation> recommendations = evaluator.evaluate(
                        OfferRequirementTestUtils.getOfferRequirement(desiredResource),
                        Arrays.asList(OfferTestUtils.getOffer(offeredResource)));
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
        Assert.assertEquals(TestConstants.role, reserveResource.getRole());
        Assert.assertEquals(TestConstants.mountRoot, reserveResource.getDisk().getSource().getMount().getRoot());
        Assert.assertEquals(TestConstants.principal, reserveResource.getReservation().getPrincipal());
        Assert.assertEquals(MesosResource.RESOURCE_ID_KEY, getFirstLabel(reserveResource).getKey());
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
        Assert.assertEquals(TestConstants.mountRoot, createResource.getDisk().getSource().getMount().getRoot());
        Assert.assertEquals(TestConstants.principal, createResource.getDisk().getPersistence().getPrincipal());
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
        Assert.assertEquals(TestConstants.mountRoot, launchResource.getDisk().getSource().getMount().getRoot());
        Assert.assertEquals(TestConstants.principal, launchResource.getDisk().getPersistence().getPrincipal());
        Assert.assertEquals(2000, launchResource.getScalar().getValue(), 0.0);
    }

    @Test
    public void testUpdateMountVolumeSuccess() throws Exception {
        String resourceId = UUID.randomUUID().toString();
        Resource updatedResource = ResourceTestUtils.getExpectedMountVolume(1500, resourceId);
        Resource offeredResource = ResourceTestUtils.getExpectedMountVolume(2000, resourceId);

        List<OfferRecommendation> recommendations = evaluator.evaluate(
                        OfferRequirementTestUtils.getOfferRequirement(updatedResource),
                        Arrays.asList(OfferTestUtils.getOffer(offeredResource)));
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
        Assert.assertEquals(TestConstants.mountRoot, launchResource.getDisk().getSource().getMount().getRoot());
        Assert.assertEquals(TestConstants.principal, launchResource.getDisk().getPersistence().getPrincipal());
        Assert.assertEquals(2000, launchResource.getScalar().getValue(), 0.0);
    }

    @Test
    public void testUpdateMountVolumeFailure() throws Exception {
        String resourceId = UUID.randomUUID().toString();
        Resource updatedResource = ResourceTestUtils.getExpectedMountVolume(2500, resourceId);
        Resource offeredResource = ResourceTestUtils.getExpectedMountVolume(2000, resourceId);

        List<OfferRecommendation> recommendations = evaluator.evaluate(
                        OfferRequirementTestUtils.getOfferRequirement(updatedResource),
                        Arrays.asList(OfferTestUtils.getOffer(offeredResource)));
        Assert.assertEquals(0, recommendations.size());
    }

    @Test
    public void testFailToCreateVolumeWithWrongResource() throws InvalidRequirementException {
        Resource desiredResource = ResourceTestUtils.getDesiredRootVolume(1000);
        Resource wrongOfferedResource = ResourceTestUtils.getUnreservedMountVolume(2000);

        List<OfferRecommendation> recommendations = evaluator.evaluate(
                        OfferRequirementTestUtils.getOfferRequirement(desiredResource),
                        Arrays.asList(OfferTestUtils.getOffer(wrongOfferedResource)));
        Assert.assertEquals(0, recommendations.size());
    }

    @Test
    public void testReserveCreateLaunchRootVolume() throws InvalidRequirementException {
        Resource desiredResource = ResourceTestUtils.getDesiredRootVolume(1500);
        Resource offeredResource = ResourceUtils.getUnreservedRootVolume(2000);

        List<OfferRecommendation> recommendations = evaluator.evaluate(
                        OfferRequirementTestUtils.getOfferRequirement(desiredResource),
                        Arrays.asList(OfferTestUtils.getOffer(offeredResource)));
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
        Assert.assertEquals(TestConstants.role, reserveResource.getRole());
        Assert.assertEquals(TestConstants.principal, reserveResource.getReservation().getPrincipal());
        Assert.assertEquals(MesosResource.RESOURCE_ID_KEY, getFirstLabel(reserveResource).getKey());
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
        Assert.assertEquals(TestConstants.principal, createResource.getDisk().getPersistence().getPrincipal());
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
        Assert.assertEquals(TestConstants.principal, launchResource.getDisk().getPersistence().getPrincipal());
    }

    @Test
    public void testFailCreateRootVolume() throws InvalidRequirementException {
        Resource desiredResource = ResourceTestUtils.getDesiredRootVolume(1000 * 2);
        Resource offeredResource = ResourceUtils.getUnreservedRootVolume(1000);

        List<OfferRecommendation> recommendations = evaluator.evaluate(
                        OfferRequirementTestUtils.getOfferRequirement(desiredResource),
                        Arrays.asList(OfferTestUtils.getOffer(offeredResource)));
        Assert.assertEquals(0, recommendations.size());
    }

    @Test
    public void testExpectedMountVolume() throws InvalidRequirementException {
        String resourceId = UUID.randomUUID().toString();
        Resource expectedResource = ResourceTestUtils.getExpectedMountVolume(1000, resourceId);

        List<OfferRecommendation> recommendations = evaluator.evaluate(
                        OfferRequirementTestUtils.getOfferRequirement(expectedResource),
                        Arrays.asList(OfferTestUtils.getOffer(expectedResource)));
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
        Assert.assertEquals(TestConstants.role, launchResource.getRole());
        Assert.assertEquals(TestConstants.mountRoot, launchResource.getDisk().getSource().getMount().getRoot());
        Assert.assertEquals(TestConstants.persistenceId, launchResource.getDisk().getPersistence().getId());
        Assert.assertEquals(TestConstants.principal, launchResource.getDisk().getPersistence().getPrincipal());
        Assert.assertEquals(TestConstants.principal, launchResource.getReservation().getPrincipal());
        Assert.assertEquals(MesosResource.RESOURCE_ID_KEY, getFirstLabel(launchResource).getKey());
        Assert.assertEquals(resourceId, getFirstLabel(launchResource).getValue());
    }

    @Test
    public void testExpectedRootVolume() throws InvalidRequirementException {
        String resourceId = UUID.randomUUID().toString();
        Resource expectedResource = ResourceTestUtils.getExpectedRootVolume(1000, resourceId);

        List<OfferRecommendation> recommendations = evaluator.evaluate(
                        OfferRequirementTestUtils.getOfferRequirement(expectedResource),
                        Arrays.asList(OfferTestUtils.getOffer(expectedResource)));
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
        Assert.assertEquals(TestConstants.role, launchResource.getRole());
        Assert.assertEquals(TestConstants.persistenceId, launchResource.getDisk().getPersistence().getId());
        Assert.assertEquals(TestConstants.principal, launchResource.getDisk().getPersistence().getPrincipal());
        Assert.assertEquals(TestConstants.principal, launchResource.getReservation().getPrincipal());
        Assert.assertEquals(MesosResource.RESOURCE_ID_KEY, getFirstLabel(launchResource).getKey());
        Assert.assertEquals(resourceId, getFirstLabel(launchResource).getValue());
    }

    @Test
    public void testReserveLaunchScalar() throws InvalidRequirementException {
        Resource desiredResource = ResourceTestUtils.getDesiredCpu(1.0);
        Resource offeredResource = ResourceUtils.getUnreservedScalar("cpus", 2.0);

        List<OfferRecommendation> recommendations = evaluator.evaluate(
                        OfferRequirementTestUtils.getOfferRequirement(desiredResource),
                        Arrays.asList(OfferTestUtils.getOffer(offeredResource)));
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
        Assert.assertEquals(TestConstants.role, reserveResource.getRole());
        Assert.assertEquals(TestConstants.principal, reserveResource.getReservation().getPrincipal());
        Assert.assertEquals(MesosResource.RESOURCE_ID_KEY, getFirstLabel(reserveResource).getKey());
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
        Resource desiredTaskResource = ResourceTestUtils.getDesiredCpu(1.0);
        Resource desiredExecutorResource = ResourceTestUtils.getDesiredMem(2.0);

        Resource offeredTaskResource = ResourceUtils.getUnreservedScalar("cpus", 2.0);
        Resource offeredExecutorResource = ResourceUtils.getUnreservedScalar("mem", 2.0);

        TaskInfo taskInfo = TaskTestUtils.getTaskInfo(desiredTaskResource);
        ExecutorInfo execInfo = TaskTestUtils.getExecutorInfo(desiredExecutorResource);

        List<OfferRecommendation> recommendations = evaluator.evaluate(
                        new OfferRequirement(Arrays.asList(taskInfo), Optional.of(execInfo)),
                        Arrays.asList(OfferTestUtils.getOffer(Arrays.asList(offeredTaskResource, offeredExecutorResource))));
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
        Assert.assertEquals(TestConstants.role, reserveResource.getRole());
        Assert.assertEquals(TestConstants.principal, reserveResource.getReservation().getPrincipal());
        String executorResourceId = getFirstLabel(reserveResource).getValue();
        Assert.assertEquals(MesosResource.RESOURCE_ID_KEY, getFirstLabel(reserveResource).getKey());
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
        Assert.assertEquals(TestConstants.role, reserveResource.getRole());
        Assert.assertEquals(TestConstants.principal, reserveResource.getReservation().getPrincipal());
        Assert.assertEquals(MesosResource.RESOURCE_ID_KEY, getFirstLabel(reserveResource).getKey());
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
        Resource desiredTaskResource = ResourceTestUtils.getDesiredCpu(1.0);
        Resource offeredTaskResource = ResourceUtils.getUnreservedScalar("cpus", 2.0);

        Resource desiredExecutorResource = ResourceTestUtils.getExpectedScalar("mem", 2.0, resourceId);
        Resource offeredExecutorResource = desiredExecutorResource;

        TaskInfo taskInfo = TaskTestUtils.getTaskInfo(desiredTaskResource);
        ExecutorInfo execInfo = TaskTestUtils.getExistingExecutorInfo(desiredExecutorResource);

        List<OfferRecommendation> recommendations = evaluator.evaluate(
                        new OfferRequirement(Arrays.asList(taskInfo), Optional.of(execInfo)),
                        Arrays.asList(
                                        OfferTestUtils.getOffer(
                                                        TestConstants.executorId,
                                                        Arrays.asList(offeredTaskResource, offeredExecutorResource))));
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
        Assert.assertEquals(TestConstants.role, reserveResource.getRole());
        Assert.assertEquals(TestConstants.principal, reserveResource.getReservation().getPrincipal());
        Assert.assertEquals(MesosResource.RESOURCE_ID_KEY, getFirstLabel(reserveResource).getKey());
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
                        OfferRequirementTestUtils.getOfferRequirement(desiredResource),
                        Arrays.asList(OfferTestUtils.getOffer(desiredResource)));
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
        Resource unreservedResource = ResourceTestUtils.getUnreservedCpu(1.0);

        List<OfferRecommendation> recommendations = evaluator.evaluate(
                        OfferRequirementTestUtils.getOfferRequirement(desiredResource),
                        Arrays.asList(OfferTestUtils.getOffer(Arrays.asList(offeredResource, unreservedResource))));
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
        Assert.assertEquals(TestConstants.role, reserveResource.getRole());
        Assert.assertEquals(TestConstants.principal, reserveResource.getReservation().getPrincipal());
        Assert.assertEquals(MesosResource.RESOURCE_ID_KEY, getFirstLabel(reserveResource).getKey());
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
                        OfferRequirementTestUtils.getOfferRequirement(desiredResource),
                        Arrays.asList(OfferTestUtils.getOffer(offeredResource)));
        Assert.assertEquals(0, recommendations.size());
    }

    @Test
    public void testUnreserveLaunchExpectedScalar() throws InvalidRequirementException {
        String resourceId = UUID.randomUUID().toString();
        Resource desiredResource = ResourceTestUtils.getExpectedScalar("cpus", 1.0, resourceId);
        Resource offeredResource = ResourceTestUtils.getExpectedScalar("cpus", 2.0, resourceId);

        List<OfferRecommendation> recommendations = evaluator.evaluate(
                        OfferRequirementTestUtils.getOfferRequirement(desiredResource),
                        Arrays.asList(OfferTestUtils.getOffer(offeredResource)));
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
        Assert.assertEquals(TestConstants.role, unreserveResource.getRole());
        Assert.assertEquals(TestConstants.principal, unreserveResource.getReservation().getPrincipal());
        Assert.assertEquals(MesosResource.RESOURCE_ID_KEY, getFirstLabel(unreserveResource).getKey());
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
        Resource desiredCpu = ResourceTestUtils.getDesiredCpu(1.0);
        Resource offeredCpu = ResourceUtils.getUnreservedScalar("cpus", 2.0);

        List<OfferRecommendation> recommendations = evaluator.evaluate(
                        OfferRequirementTestUtils.getOfferRequirement(
                                        desiredCpu,
                                        Arrays.asList(TestConstants.agentId.getValue()),
                                        Collections.emptyList()),
                        Arrays.asList(OfferTestUtils.getOffer(offeredCpu)));

        Assert.assertEquals(0, recommendations.size());

        recommendations = evaluator.evaluate(
                        OfferRequirementTestUtils.getOfferRequirement(
                                        desiredCpu,
                                        Arrays.asList("some-random-agent"),
                                        Collections.emptyList()),
                        Arrays.asList(OfferTestUtils.getOffer(offeredCpu)));

        Assert.assertEquals(2, recommendations.size());
    }

    @Test
    public void testCollocateAgents() throws Exception{
        Resource desiredCpu = ResourceTestUtils.getDesiredCpu(1.0);
        Resource offeredCpu = ResourceUtils.getUnreservedScalar("cpus", 2.0);

        List<OfferRecommendation> recommendations = evaluator.evaluate(
                        OfferRequirementTestUtils.getOfferRequirement(
                                        desiredCpu,
                                        Collections.emptyList(),
                                        Arrays.asList("some-random-agent")),
                        Arrays.asList(OfferTestUtils.getOffer(offeredCpu)));

        Assert.assertEquals(0, recommendations.size());

        recommendations = evaluator.evaluate(
                        OfferRequirementTestUtils.getOfferRequirement(
                                        desiredCpu,
                                        Collections.emptyList(),
                                        Arrays.asList(TestConstants.agentId.getValue())),
                        Arrays.asList(OfferTestUtils.getOffer(offeredCpu)));

        Assert.assertEquals(2, recommendations.size());
    }

    @Test
    public void testRejectOfferWithoutExpectedExecutorId() throws Exception {
        String resourceId = UUID.randomUUID().toString();
        Resource expectedTaskCpu = ResourceTestUtils.getExpectedScalar("cpus", 1.0, resourceId);
        Resource expectedExecutorMem = ResourceTestUtils.getExpectedScalar("mem", 256, resourceId);

        TaskInfo taskInfo = TaskTestUtils.getTaskInfo(expectedTaskCpu);
        Optional<ExecutorInfo> execInfo = Optional.of(TaskTestUtils.getExecutorInfo(expectedExecutorMem));

        // Set incorrect ExecutorID
        execInfo = Optional.of(
                        ExecutorInfo.newBuilder(execInfo.get())
                                        .setExecutorId(ExecutorUtils.toExecutorId(execInfo.get().getName()))
                                        .build());

        OfferRequirement offerRequirement = new OfferRequirement(Arrays.asList(taskInfo), execInfo);

        List<OfferRecommendation> recommendations = evaluator.evaluate(
                        offerRequirement,
                        Arrays.asList(OfferTestUtils.getOffer(Arrays.asList(expectedTaskCpu, expectedExecutorMem))));

        Assert.assertEquals(0, recommendations.size());
    }

    @Test
    public void testAcceptOfferWithExpectedExecutorId() throws Exception {
        String taskResourceId = UUID.randomUUID().toString();
        String executorResourceId = UUID.randomUUID().toString();
        Resource expectedTaskCpu = ResourceTestUtils.getExpectedScalar("cpus", 1.0, taskResourceId);
        Resource expectedExecutorMem = ResourceTestUtils.getExpectedScalar("mem", 256, executorResourceId);

        TaskInfo taskInfo = TaskTestUtils.getTaskInfo(expectedTaskCpu);
        ExecutorInfo execInfo = TaskTestUtils.getExistingExecutorInfo(expectedExecutorMem);

        OfferRequirement offerRequirement = new OfferRequirement(Arrays.asList(taskInfo), Optional.of(execInfo));

        List<OfferRecommendation> recommendations = evaluator.evaluate(
                        offerRequirement,
                        Arrays.asList(
                                        OfferTestUtils.getOffer(
                                                        TestConstants.executorId,
                                                        Arrays.asList(expectedTaskCpu, expectedExecutorMem))));

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

        TaskInfo taskInfo = TaskTestUtils.getTaskInfo(expectedTaskCpu);
        ExecutorInfo execInfo = TaskTestUtils.getExecutorInfo(expectedExecutorMem);

        OfferRequirement offerRequirement = new OfferRequirement(Arrays.asList(taskInfo), Optional.of(execInfo));

        List<OfferRecommendation> recommendations = evaluator.evaluate(
                        offerRequirement,
                        Arrays.asList(OfferTestUtils.getOffer(null, Arrays.asList(expectedTaskCpu, expectedExecutorMem))));

        Assert.assertEquals(1, recommendations.size());
        Operation launchOperation = recommendations.get(0).getOperation();
        Assert.assertEquals(Operation.Type.LAUNCH, launchOperation.getType());
        TaskInfo launchedTaskInfo = launchOperation.getLaunch().getTaskInfosList().get(0);
        Assert.assertNotEquals("", launchedTaskInfo.getExecutor().getExecutorId().getValue());
    }

    @Test
    public void testLaunchMultipleTasksPerExecutor() throws Exception {
        Resource desiredTask0Cpu = ResourceTestUtils.getDesiredCpu(1.0);
        Resource desiredTask1Cpu = ResourceTestUtils.getDesiredCpu(2.0);
        Resource desiredExecutorCpu = ResourceTestUtils.getDesiredCpu(3.0);
        Resource offeredResource = ResourceUtils.getUnreservedScalar("cpus", 6.0);

        TaskInfo taskInfo0 = TaskTestUtils.getTaskInfo(desiredTask0Cpu);
        TaskInfo taskInfo1 = TaskTestUtils.getTaskInfo(desiredTask1Cpu);
        ExecutorInfo execInfo = TaskTestUtils.getExecutorInfo(desiredExecutorCpu);
        OfferRequirement offerRequirement = new OfferRequirement(
                        Arrays.asList(taskInfo0, taskInfo1),
                        Optional.of(execInfo));
        List<OfferRecommendation> recommendations = evaluator.evaluate(
                        offerRequirement,
                        Arrays.asList(OfferTestUtils.getOffer(null, Arrays.asList(offeredResource))));

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
        Resource desiredResource = ResourceTestUtils.getDesiredCpu(1.0);
        Resource insufficientOffer = ResourceUtils.getUnreservedScalar("mem", 2.0);
        Resource sufficientOffer = ResourceUtils.getUnreservedScalar("cpus", 2.0);

        List<OfferRecommendation> recommendations = evaluator.evaluate(
                        OfferRequirementTestUtils.getOfferRequirement(desiredResource),
                        Arrays.asList(
                                        OfferTestUtils.getOffer(insufficientOffer),
                                        OfferTestUtils.getOffer(sufficientOffer)));
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
}
