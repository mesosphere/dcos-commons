package com.mesosphere.sdk.offer.evaluate;

import com.mesosphere.sdk.offer.*;
import com.mesosphere.sdk.offer.evaluate.placement.PlacementUtils;
import com.mesosphere.sdk.offer.taskdata.EnvUtils;
import com.mesosphere.sdk.offer.taskdata.SchedulerLabelReader;
import com.mesosphere.sdk.offer.taskdata.TaskPackingUtils;
import com.mesosphere.sdk.scheduler.plan.*;
import com.mesosphere.sdk.scheduler.plan.Status;
import com.mesosphere.sdk.scheduler.recovery.FailureUtils;
import com.mesosphere.sdk.scheduler.recovery.RecoveryType;
import com.mesosphere.sdk.specification.DefaultServiceSpec;
import com.mesosphere.sdk.specification.PodInstance;
import com.mesosphere.sdk.specification.PodSpec;
import com.mesosphere.sdk.specification.ServiceSpec;
import com.mesosphere.sdk.specification.yaml.RawServiceSpec;
import com.mesosphere.sdk.specification.yaml.YAMLServiceSpecFactory;
import com.mesosphere.sdk.state.PersistentLaunchRecorder;
import com.mesosphere.sdk.testutils.OfferRequirementTestUtils;
import com.mesosphere.sdk.testutils.OfferTestUtils;
import com.mesosphere.sdk.testutils.ResourceTestUtils;
import com.mesosphere.sdk.testutils.TestConstants;
import org.apache.mesos.Protos;
import org.apache.mesos.Protos.*;
import org.apache.mesos.Protos.Offer.Operation;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mock;

import java.io.File;
import java.util.*;
import java.util.stream.Collectors;

public class OfferEvaluatorTest extends OfferEvaluatorTestBase {
    @Mock ServiceSpec serviceSpec;

    @Test
    public void testReserveLaunchScalar() throws Exception {
        PodInstanceRequirement podInstanceRequirement = PodInstanceRequirementTestUtils.getCpuRequirement(1.0);
        Resource offeredResource = ResourceUtils.getUnreservedScalar("cpus", 2.0);

        List<OfferRecommendation> recommendations = evaluator.evaluate(
                podInstanceRequirement,
                Arrays.asList(OfferTestUtils.getOffer(offeredResource)));
        Assert.assertEquals(2, recommendations.size());

        // Validate RESERVE Operation
        Operation reserveOperation = recommendations.get(0).getOperation();
        Resource reserveResource = reserveOperation.getReserve().getResources(0);

        Resource.ReservationInfo reservation = reserveResource.getReservation();
        Assert.assertEquals(Operation.Type.RESERVE, reserveOperation.getType());
        Assert.assertEquals(1.0, reserveResource.getScalar().getValue(), 0.0);
        Assert.assertEquals(TestConstants.ROLE, reserveResource.getRole());
        Assert.assertEquals(TestConstants.PRINCIPAL, reservation.getPrincipal());
        Assert.assertEquals(36, getResourceId(reserveResource).length());
        Assert.assertFalse(reserveResource.hasDisk());

        // Validate LAUNCH Operation
        Operation launchOperation = recommendations.get(1).getOperation();
        Resource launchResource = launchOperation.getLaunch().getTaskInfos(0).getResources(0);

        Assert.assertEquals(Operation.Type.LAUNCH, launchOperation.getType());
        Assert.assertEquals(getResourceId(reserveResource), getResourceId(launchResource));
    }

    @Test
    public void testLaunchExpectedScalar() throws Exception {
        PodInstanceRequirement podInstanceRequirement = PodInstanceRequirementTestUtils.getCpuRequirement(1.0);

        // Launch for the first time.
        String resourceId = getFirstResourceId(
                recordLaunchWithOfferedResources(
                        podInstanceRequirement,
                        ResourceUtils.getUnreservedScalar("cpus", 2.0)));

        // Launch again on expected resources.
        Resource expectedScalar = ResourceTestUtils.getExpectedScalar("cpus", 1.0, resourceId);

        List<OfferRecommendation> recommendations = evaluator.evaluate(
                podInstanceRequirement,
                Arrays.asList(OfferTestUtils.getOffer(Arrays.asList(expectedScalar))));
        Assert.assertEquals(1, recommendations.size());

        // Validate LAUNCH Operation
        Operation launchOperation = recommendations.get(0).getOperation();
        Resource launchResource = launchOperation.getLaunch().getTaskInfos(0).getResources(0);

        Assert.assertEquals(Operation.Type.LAUNCH, launchOperation.getType());
        Assert.assertEquals(resourceId, getResourceId(launchResource));
    }

    @Test
    public void testIncreaseReservationScalar() throws Exception {
        // Launch for the first time.
        String resourceId = getFirstResourceId(
                recordLaunchWithOfferedResources(
                        PodInstanceRequirementTestUtils.getCpuRequirement(1.0),
                        ResourceUtils.getUnreservedScalar("cpus", 2.0)));

        // Launch again with more resources.
        PodInstanceRequirement podInstanceRequirement = PodInstanceRequirementTestUtils.getCpuRequirement(2.0);
        Resource offeredResource = ResourceTestUtils.getExpectedScalar("cpus", 1.0, resourceId);
        Resource unreservedResource = ResourceTestUtils.getUnreservedCpu(1.0);

        List<OfferRecommendation> recommendations = evaluator.evaluate(
                podInstanceRequirement,
                Arrays.asList(OfferTestUtils.getOffer(Arrays.asList(offeredResource, unreservedResource))));
        Assert.assertEquals(2, recommendations.size());

        // Validate RESERVE Operation
        Operation reserveOperation = recommendations.get(0).getOperation();
        Resource reserveResource = reserveOperation.getReserve().getResources(0);

        Resource.ReservationInfo reservation = reserveResource.getReservation();
        Assert.assertEquals(Operation.Type.RESERVE, reserveOperation.getType());
        Assert.assertEquals(1.0, reserveResource.getScalar().getValue(), 0.0);
        Assert.assertEquals(TestConstants.ROLE, reserveResource.getRole());
        Assert.assertEquals(TestConstants.PRINCIPAL, reservation.getPrincipal());
        Assert.assertEquals(resourceId, getResourceId(reserveResource));

        // Validate LAUNCH Operation
        Operation launchOperation = recommendations.get(1).getOperation();
        Resource launchResource = launchOperation.getLaunch().getTaskInfos(0).getResources(0);

        Assert.assertEquals(Operation.Type.LAUNCH, launchOperation.getType());
        Assert.assertEquals(resourceId, getResourceId(launchResource));
        Assert.assertEquals(2.0, launchResource.getScalar().getValue(), 0.0);
    }

    @Test
    public void testDecreaseReservationScalar() throws Exception {
        // Launch for the first time.
        Resource reserveResource = recordLaunchWithOfferedResources(
                PodInstanceRequirementTestUtils.getCpuRequirement(2.0), ResourceUtils.getUnreservedScalar("cpus", 2.0))
                .get(0);
        String resourceId = getResourceId(reserveResource);

        // Launch again with fewer resources.
        PodInstanceRequirement podInstanceRequirement = PodInstanceRequirementTestUtils.getCpuRequirement(1.0);
        Resource offeredResource = ResourceTestUtils.getExpectedScalar("cpus", 2.0, resourceId);
        Resource unreservedResource = ResourceTestUtils.getUnreservedCpu(1.0);

        List<OfferRecommendation> recommendations = evaluator.evaluate(
                podInstanceRequirement,
                Arrays.asList(OfferTestUtils.getOffer(Arrays.asList(offeredResource, unreservedResource))));
        Assert.assertEquals(2, recommendations.size());

        // Validate UNRESERVE Operation
        Operation unreserveOperation = recommendations.get(0).getOperation();
        Resource unreserveResource = unreserveOperation.getUnreserve().getResources(0);

        Resource.ReservationInfo reservation = reserveResource.getReservation();
        Assert.assertEquals(Operation.Type.UNRESERVE, unreserveOperation.getType());
        Assert.assertEquals(1.0, unreserveResource.getScalar().getValue(), 0.0);
        Assert.assertEquals(TestConstants.ROLE, unreserveResource.getRole());
        Assert.assertEquals(TestConstants.PRINCIPAL, reservation.getPrincipal());
        Assert.assertEquals(resourceId, getResourceId(unreserveResource));

        // Validate LAUNCH Operation
        Operation launchOperation = recommendations.get(1).getOperation();
        Resource launchResource = launchOperation.getLaunch().getTaskInfos(0).getResources(0);

        Assert.assertEquals(Operation.Type.LAUNCH, launchOperation.getType());
        Assert.assertEquals(resourceId, getResourceId(launchResource));
        Assert.assertEquals(1.0, launchResource.getScalar().getValue(), 0.0);
    }

    /*
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
        Resource launchResource = launchOperation.getLaunch().getTaskInfos(0).getResources(0);

        Assert.assertEquals(Operation.Type.LAUNCH, launchOperation.getType());
        Assert.assertEquals(getResourceId(updatedResource).getValue(), getResourceId(launchResource).getValue());
        Assert.assertEquals(updatedResource.getDisk().getPersistence().getId(), launchResource.getDisk().getPersistence().getId());
        Assert.assertEquals(TestConstants.MOUNT_ROOT, launchResource.getDisk().getSource().getMount().getRoot());
        Assert.assertEquals(TestConstants.PRINCIPAL, launchResource.getDisk().getPersistence().getPrincipal());
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
    public void testFailToCreateVolumeWithWrongResource() throws Exception {
        Resource desiredResource = ResourceTestUtils.getDesiredRootVolume(1000);
        Resource wrongOfferedResource = ResourceTestUtils.getUnreservedMountVolume(2000);

        List<OfferRecommendation> recommendations = evaluator.evaluate(
                OfferRequirementTestUtils.getOfferRequirement(desiredResource),
                Arrays.asList(OfferTestUtils.getOffer(wrongOfferedResource)));
        Assert.assertEquals(0, recommendations.size());
        Assert.assertEquals(0, recommendations.size());
    }


    @Test
    public void testFailCreateRootVolume() throws Exception {
        Resource desiredResource = ResourceTestUtils.getDesiredRootVolume(1000 * 2);
        Resource offeredResource = ResourceUtils.getUnreservedRootVolume(1000);

        List<OfferRecommendation> recommendations = evaluator.evaluate(
                        OfferRequirementTestUtils.getOfferRequirement(desiredResource),
                        Arrays.asList(OfferTestUtils.getOffer(offeredResource)));
        Assert.assertEquals(0, recommendations.size());
    }

    @Test
    public void testReserveExecutorVolume() throws Exception {
        Resource executorVolume = ResourceTestUtils.getDesiredMountVolume(1000);
        Resource taskCpu = ResourceTestUtils.getDesiredCpu(1.0);
        List<Resource> offeredResources = Arrays.asList(
                ResourceTestUtils.getUnreservedMountVolume(2000),
                ResourceTestUtils.getUnreservedCpu(1.0));

        Offer offer = OfferTestUtils.getOffer(offeredResources);
        OfferRequirement offerRequirement = OfferRequirementTestUtils.getOfferRequirement(taskCpu, executorVolume);

        List<OfferRecommendation> recommendations = evaluator.evaluate(offerRequirement, Arrays.asList(offer));
        Assert.assertEquals(4, recommendations.size());

        // Validate just the operations pertaining to the executor
        // Validate RESERVE Operation
        Operation reserveOperation = recommendations.get(0).getOperation();
        Resource reserveResource = reserveOperation.getReserve().getResources(0);

        Assert.assertEquals(Operation.Type.RESERVE, reserveOperation.getType());
        Assert.assertEquals(2000, reserveResource.getScalar().getValue(), 0.0);
        Assert.assertEquals(TestConstants.ROLE, reserveResource.getRole());
        Assert.assertEquals(TestConstants.MOUNT_ROOT, reserveResource.getDisk().getSource().getMount().getRoot());
        Assert.assertEquals(TestConstants.PRINCIPAL, reserveResource.getReservation().getPrincipal());
        Assert.assertEquals(MesosResource.RESOURCE_ID_KEY, getResourceId(reserveResource).getKey());
        Assert.assertEquals(36, getResourceId(reserveResource).getValue().length());

        // Validate CREATE Operation
        String resourceId = getResourceId(reserveResource).getValue();
        Operation createOperation = recommendations.get(1).getOperation();
        Resource createResource = createOperation.getCreate().getVolumes(0);

        Assert.assertEquals(resourceId, getResourceId(createResource).getValue());
        Assert.assertEquals(36, createResource.getDisk().getPersistence().getId().length());
        Assert.assertEquals(TestConstants.MOUNT_ROOT, createResource.getDisk().getSource().getMount().getRoot());
        Assert.assertEquals(TestConstants.PRINCIPAL, createResource.getDisk().getPersistence().getPrincipal());
        Assert.assertTrue(createResource.getDisk().hasVolume());
    }

    @Test
    public void testUpdateExecutorVolumeSuccess() throws Exception {
        String resourceId = UUID.randomUUID().toString();
        Resource executorVolume = ResourceTestUtils.getExpectedMountVolume(1500, resourceId);
        Resource taskCpu = ResourceTestUtils.getExpectedScalar("cpus", 1.0, resourceId);
        List<Resource> offeredResources = Arrays.asList(
                ResourceTestUtils.getExpectedMountVolume(2000, resourceId),
                ResourceTestUtils.getExpectedScalar("cpus", 1.0, resourceId));

        Offer offer = OfferTestUtils.getOffer(offeredResources);
        OfferRequirement offerRequirement = OfferRequirementTestUtils.getOfferRequirement(taskCpu, executorVolume);

        List<OfferRecommendation> recommendations = evaluator.evaluate(offerRequirement, Arrays.asList(offer));
        Assert.assertEquals(0, recommendations.size());
    }

    @Test
    public void testUpdateExecutorVolumeFailure() throws Exception {
        String resourceId = UUID.randomUUID().toString();
        Resource executorVolume = ResourceTestUtils.getExpectedMountVolume(2500, resourceId);
        Resource taskCpu = ResourceTestUtils.getExpectedScalar("cpus", 1.0, resourceId);
        List<Resource> offeredResources = Arrays.asList(
                ResourceTestUtils.getExpectedMountVolume(2000, resourceId),
                ResourceTestUtils.getExpectedScalar("cpus", 1.0, resourceId));

        Offer offer = OfferTestUtils.getOffer(offeredResources);
        OfferRequirement offerRequirement = OfferRequirementTestUtils.getOfferRequirement(taskCpu, executorVolume);

        List<OfferRecommendation> recommendations = evaluator.evaluate(offerRequirement, Arrays.asList(offer));
        Assert.assertEquals(0, recommendations.size());
    }

    /*
    @Test
    public void testLaunchAttributesEmbedded() throws Exception {
        String resourceId = UUID.randomUUID().toString();
        // TODO: Store expected TaskInfo in StateStore so OfferEvaluator generates right OfferRequirement internally
        Resource expectedResource = ResourceTestUtils.getExpectedScalar("cpus", 1.0, resourceId);
        PodInstanceRequirement podInstanceRequirement = PodInstanceRequirementTestUtils.getCpuRequirement(1.0);

        Offer.Builder offerBuilder = OfferTestUtils.getOffer(expectedResource).toBuilder();
        Attribute.Builder attrBuilder =
                offerBuilder.addAttributesBuilder().setName("rack").setType(Value.Type.TEXT);
        attrBuilder.getTextBuilder().setValue("foo");
        attrBuilder = offerBuilder.addAttributesBuilder().setName("diskspeed").setType(Value.Type.SCALAR);
        attrBuilder.getScalarBuilder().setValue(1234.5678);

        List<OfferRecommendation> recommendations = evaluator.evaluate(
                podInstanceRequirement,
                Arrays.asList(offerBuilder.build()));
        Assert.assertEquals(1, recommendations.size());

        // Validate LAUNCH Operation
        Operation launchOperation = recommendations.get(0).getOperation();
        Assert.assertEquals(Operation.Type.LAUNCH, launchOperation.getType());

        // Validate that TaskInfo has embedded the Attributes from the selected offer:
        TaskInfo launchTask = launchOperation.getLaunch().getTaskInfos(0);
        Assert.assertEquals(
                Arrays.asList("rack:foo", "diskspeed:1234.568"),
                new SchedulerLabelReader(launchTask).getOfferAttributeStrings());
        Resource launchResource = launchTask.getResources(0);
        Assert.assertEquals(resourceId, getResourceId(launchResource));
    }

    @Test
    public void testFailIncreaseReservationScalar() throws Exception {
        String resourceId = UUID.randomUUID().toString();
        PodInstanceRequirement podInstanceRequirement = PodInstanceRequirementTestUtils.getCpuRequirement(2.0);
        // TODO: Store expected TaskInfo in StateStore so OfferEvaluator generates right OfferRequirement internally
        Resource offeredResource = ResourceTestUtils.getExpectedScalar("cpus", 1.0, resourceId);

        List<OfferRecommendation> recommendations = evaluator.evaluate(
                podInstanceRequirement,
                Arrays.asList(OfferTestUtils.getOffer(offeredResource)));
        Assert.assertEquals(0, recommendations.size());
    }

    @Test
    public void testAvoidAgents() throws Exception {
        Resource desiredCpu = ResourceTestUtils.getDesiredCpu(1.0);
        Resource offeredCpu = ResourceUtils.getUnreservedScalar("cpus", 2.0);

        List<OfferRecommendation> recommendations = evaluator.evaluate(
                OfferRequirementTestUtils.getOfferRequirement(
                        desiredCpu,
                        PlacementUtils.getAgentPlacementRule(
                                Arrays.asList(TestConstants.AGENT_ID.getValue()), Collections.emptyList()).get()),
                Arrays.asList(OfferTestUtils.getOffer(offeredCpu)));

        Assert.assertEquals(0, recommendations.size());

        recommendations = evaluator.evaluate(
                OfferRequirementTestUtils.getOfferRequirement(
                        desiredCpu,
                        PlacementUtils.getAgentPlacementRule(
                                Arrays.asList("some-random-agent"), Collections.emptyList()).get()),
                Arrays.asList(OfferTestUtils.getOffer(offeredCpu)));

        Assert.assertEquals(2, recommendations.size());
    }

    @Test
    public void testColocateAgents() throws Exception {
        Resource desiredCpu = ResourceTestUtils.getDesiredCpu(1.0);
        Resource offeredCpu = ResourceUtils.getUnreservedScalar("cpus", 2.0);

        List<OfferRecommendation> recommendations = evaluator.evaluate(
                OfferRequirementTestUtils.getOfferRequirement(
                        desiredCpu,
                        PlacementUtils.getAgentPlacementRule(
                                Collections.emptyList(), Arrays.asList("some-random-agent")).get()),
                Arrays.asList(OfferTestUtils.getOffer(offeredCpu)));

        Assert.assertEquals(0, recommendations.size());

        recommendations = evaluator.evaluate(
                OfferRequirementTestUtils.getOfferRequirement(
                        desiredCpu,
                        PlacementUtils.getAgentPlacementRule(
                                Collections.emptyList(), Arrays.asList(TestConstants.AGENT_ID.getValue())).get()),
                Arrays.asList(OfferTestUtils.getOffer(offeredCpu)));

        Assert.assertEquals(2, recommendations.size());
    }

    @Test
    public void testLaunchMultipleTasksPerExecutor() throws Exception {
        Resource offeredResource = ResourceUtils.getUnreservedScalar("cpus", 3.0);
        List<Resource> desiredResources = Arrays.asList(
                ResourceTestUtils.getDesiredCpu(1.0),
                ResourceTestUtils.getDesiredCpu(2.0));

        List<OfferRecommendation> recommendations = evaluator.evaluate(
                OfferRequirementTestUtils.getOfferRequirement(desiredResources, true),
                Arrays.asList(OfferTestUtils.getOffer(null, Arrays.asList(offeredResource))));

        Assert.assertEquals(4, recommendations.size());
        Assert.assertEquals(Operation.Type.RESERVE, recommendations.get(0).getOperation().getType());
        Assert.assertEquals(Operation.Type.RESERVE, recommendations.get(2).getOperation().getType());
        Operation launchOp0 = recommendations.get(1).getOperation();
        Assert.assertEquals(Operation.Type.LAUNCH, launchOp0.getType());
        Operation launchOp1 = recommendations.get(3).getOperation();
        Assert.assertEquals(Operation.Type.LAUNCH, launchOp1.getType());
        Protos.ExecutorID launch0ExecutorId = launchOp0.getLaunch().getTaskInfos(0).getExecutor().getExecutorId();
        Protos.ExecutorID launch1ExecutorId = launchOp1.getLaunch().getTaskInfos(0).getExecutor().getExecutorId();
        Assert.assertEquals(launch0ExecutorId, launch1ExecutorId);
    }

    @Test
    public void testLaunchNotOnFirstOffer() throws Exception {
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

    @Test
    public void testLaunchSequencedTasksInPod() throws Exception {
        ClassLoader classLoader = getClass().getClassLoader();
        File file = new File(classLoader.getResource("resource-set-seq.yml").getFile());
        RawServiceSpec rawServiceSpec = YAMLServiceSpecFactory.generateRawSpecFromYAML(file);
        DefaultServiceSpec serviceSpec = YAMLServiceSpecFactory.generateServiceSpec(rawServiceSpec, flags);

        PodSpec podSpec = serviceSpec.getPods().get(0);
        PodInstance podInstance = new DefaultPodInstance(podSpec, 0);
        PodInstanceRequirement podInstanceRequirement =
                PodInstanceRequirement.newBuilder(podInstance, Arrays.asList("format")).build();

        Offer sufficientOffer = OfferTestUtils.getOffer(Arrays.asList(
                ResourceUtils.getUnreservedScalar("cpus", 3.0),
                ResourceUtils.getUnreservedScalar("disk", 500.0)));

        // Launch Task with FINISHED goal state, for first time.
        List<OfferRecommendation> recommendations = evaluator.evaluate(
                podInstanceRequirement,
                Arrays.asList(sufficientOffer));

        Assert.assertEquals(recommendations.toString(), 6, recommendations.size());

        // Validate format task operations
        Operation operation = recommendations.get(0).getOperation();
        Assert.assertEquals(Operation.Type.RESERVE, operation.getType());
        operation = recommendations.get(1).getOperation();
        Assert.assertEquals(Operation.Type.RESERVE, operation.getType());
        operation = recommendations.get(2).getOperation();
        Assert.assertEquals(Operation.Type.CREATE, operation.getType());
        operation = recommendations.get(3).getOperation();
        Assert.assertEquals(Operation.Type.LAUNCH, operation.getType());

        // Validate node task operations
        operation = recommendations.get(4).getOperation();
        Assert.assertEquals(Operation.Type.RESERVE, operation.getType());
        operation = recommendations.get(5).getOperation();
        Assert.assertEquals(Operation.Type.LAUNCH, operation.getType());

        recordOperations(recommendations);

        // Launch Task with RUNNING goal state, later.
        podInstanceRequirement = PodInstanceRequirement.newBuilder(podInstance, Arrays.asList("node")).build();
        recommendations = evaluator.evaluate(podInstanceRequirement, Arrays.asList(sufficientOffer));
        // Providing sufficient, but unreserved resources should result in no operations.
        Assert.assertEquals(0, recommendations.size());

        List<String> resourceIds = offerRequirementProvider.getExistingOfferRequirement(
                PodInstanceRequirement.newBuilder(podInstance, Arrays.asList("node")).build())
                .getTaskRequirements().stream()
                .flatMap(taskRequirement -> taskRequirement.getResourceRequirements().stream())
                .map(resourceRequirement -> resourceRequirement.getResourceId())
                .collect(Collectors.toList());
        Assert.assertEquals(resourceIds.toString(), 2, resourceIds.size());

        Offer expectedOffer = OfferTestUtils.getOffer(Arrays.asList(
                ResourceTestUtils.getExpectedScalar("cpus", 1.0, resourceIds.get(0)),
                ResourceTestUtils.getExpectedScalar("disk", 50.0, resourceIds.get(1))));
        recommendations = evaluator.evaluate(podInstanceRequirement, Arrays.asList(expectedOffer));
        // Providing the expected reserved resources should result in a LAUNCH operation.
        Assert.assertEquals(1, recommendations.size());
        operation = recommendations.get(0).getOperation();
        Assert.assertEquals(Operation.Type.LAUNCH, operation.getType());
    }

    @Test
    public void testRelaunchFailedPod() throws Exception {
        ClassLoader classLoader = getClass().getClassLoader();
        File file = new File(classLoader.getResource("resource-set-seq.yml").getFile());
        RawServiceSpec rawServiceSpec = YAMLServiceSpecFactory.generateRawSpecFromYAML(file);
        DefaultServiceSpec serviceSpec = YAMLServiceSpecFactory.generateServiceSpec(rawServiceSpec, flags);

        PodSpec podSpec = serviceSpec.getPods().get(0);
        PodInstance podInstance = new DefaultPodInstance(podSpec, 0);
        PodInstanceRequirement podInstanceRequirement =
                PodInstanceRequirement.newBuilder(podInstance, Arrays.asList("format")).build();

        Offer sufficientOffer = OfferTestUtils.getOffer(Arrays.asList(
                ResourceUtils.getUnreservedScalar("cpus", 3.0),
                ResourceUtils.getUnreservedScalar("disk", 500.0)));

        // Launch Task with FINISHED goal state, for first time.
        List<OfferRecommendation> recommendations = evaluator.evaluate(
                podInstanceRequirement,
                Arrays.asList(sufficientOffer));

        Assert.assertEquals(recommendations.toString(), 6, recommendations.size());

        // Validate format task operations
        Operation operation = recommendations.get(0).getOperation();
        Assert.assertEquals(Operation.Type.RESERVE, operation.getType());
        operation = recommendations.get(1).getOperation();
        Assert.assertEquals(Operation.Type.RESERVE, operation.getType());
        operation = recommendations.get(2).getOperation();
        Assert.assertEquals(Operation.Type.CREATE, operation.getType());
        operation = recommendations.get(3).getOperation();
        Assert.assertEquals(Operation.Type.LAUNCH, operation.getType());

        // Validate node task operations
        operation = recommendations.get(4).getOperation();
        Assert.assertEquals(Operation.Type.RESERVE, operation.getType());
        operation = recommendations.get(5).getOperation();
        Assert.assertEquals(Operation.Type.LAUNCH, operation.getType());

        recordOperations(recommendations);

        // Attempt to launch task again as non-failed.
        podInstanceRequirement = PodInstanceRequirement.newBuilder(podInstance, Arrays.asList("node")).build();
        recommendations = evaluator.evaluate(podInstanceRequirement, Arrays.asList(sufficientOffer));
        // The pod is running fine according to the state store, so no new deployment is issued.
        Assert.assertEquals(recommendations.toString(), 0, recommendations.size());

        // Now the same operation except with the task flagged as having permanently failed.
        podInstanceRequirement = PodInstanceRequirement.newBuilder(podInstance, Arrays.asList("node"))
                .recoveryType(RecoveryType.PERMANENT)
                .build();
        recommendations = evaluator.evaluate(podInstanceRequirement, Arrays.asList(sufficientOffer));
        // A new deployment replaces the prior one above.
        Assert.assertEquals(recommendations.toString(), 6, recommendations.size());

        // Validate format task operations
        operation = recommendations.get(0).getOperation();
        Assert.assertEquals(Operation.Type.RESERVE, operation.getType());
        operation = recommendations.get(1).getOperation();
        Assert.assertEquals(Operation.Type.RESERVE, operation.getType());
        operation = recommendations.get(2).getOperation();
        Assert.assertEquals(Operation.Type.CREATE, operation.getType());
        operation = recommendations.get(3).getOperation();
        Assert.assertEquals(Operation.Type.LAUNCH, operation.getType());

        // Validate node task operations
        operation = recommendations.get(4).getOperation();
        Assert.assertEquals(Operation.Type.RESERVE, operation.getType());
        operation = recommendations.get(5).getOperation();
        Assert.assertEquals(Operation.Type.LAUNCH, operation.getType());
    }

    @Test
    public void testReplaceDeployStep() throws Exception {
        ClassLoader classLoader = getClass().getClassLoader();
        File file = new File(classLoader.getResource("valid-minimal-volume.yml").getFile());
        RawServiceSpec rawServiceSpec = YAMLServiceSpecFactory.generateRawSpecFromYAML(file);
        DefaultServiceSpec serviceSpec = YAMLServiceSpecFactory.generateServiceSpec(rawServiceSpec, flags);

        PodSpec podSpec = serviceSpec.getPods().get(0);
        PodInstance podInstance = new DefaultPodInstance(podSpec, 0);
        PodInstanceRequirement podInstanceRequirement =
                PodInstanceRequirement.newBuilder(podInstance, Arrays.asList("task-name")).build();
        DeploymentStep deploymentStep = new DeploymentStep(
                "test-step",
                Status.PENDING,
                podInstanceRequirement,
                Collections.emptyList());

        Offer sufficientOffer = OfferTestUtils.getOffer(Arrays.asList(
                ResourceUtils.getUnreservedScalar("cpus", 3.0),
                ResourceUtils.getUnreservedScalar("mem", 1024),
                ResourceUtils.getUnreservedScalar("disk", 500.0)));

        List<OfferRecommendation> recommendations = evaluator.evaluate(
                deploymentStep.start().get(),
                Arrays.asList(sufficientOffer));

        Assert.assertEquals(recommendations.toString(), 5, recommendations.size());
        Operation launchOperation = recommendations.get(4).getOperation();
        TaskInfo taskInfo = launchOperation.getLaunch().getTaskInfos(0);
        recordOperations(recommendations);

        deploymentStep.updateOfferStatus(recommendations);
        Assert.assertEquals(Status.STARTING, deploymentStep.getStatus());

        // Simulate an initial failure to deploy.  Perhaps the CREATE operation failed
        deploymentStep.update(
                TaskStatus.newBuilder()
                        .setTaskId(taskInfo.getTaskId())
                        .setState(TaskState.TASK_ERROR)
                        .build());

        Assert.assertEquals(Status.PENDING, deploymentStep.getStatus());
        FailureUtils.markFailed(
                deploymentStep.getPodInstanceRequirement().get().getPodInstance(),
                stateStore);

        Assert.assertTrue(FailureUtils.isLabeledAsFailed(stateStore.fetchTask(taskInfo.getName()).get()));

        recommendations = evaluator.evaluate(
                deploymentStep.start().get(),
                Arrays.asList(sufficientOffer));
        Assert.assertEquals(recommendations.toString(), 5, recommendations.size());

        Operation operation = recommendations.get(0).getOperation();
        Assert.assertEquals(Operation.Type.RESERVE, operation.getType());
        operation = recommendations.get(1).getOperation();
        Assert.assertEquals(Operation.Type.RESERVE, operation.getType());
        operation = recommendations.get(2).getOperation();
        Assert.assertEquals(Operation.Type.RESERVE, operation.getType());
        operation = recommendations.get(3).getOperation();
        Assert.assertEquals(Operation.Type.CREATE, operation.getType());
        operation = recommendations.get(4).getOperation();
        Assert.assertEquals(Operation.Type.LAUNCH, operation.getType());

    }
    */

    private void recordOperations(List<OfferRecommendation> recommendations) throws Exception {
        OperationRecorder operationRecorder = new PersistentLaunchRecorder(stateStore, serviceSpec);
        for (OfferRecommendation recommendation : recommendations) {
            operationRecorder.record(recommendation);
        }
    }
}
