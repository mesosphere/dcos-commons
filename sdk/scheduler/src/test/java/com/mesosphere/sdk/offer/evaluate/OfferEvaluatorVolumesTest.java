package com.mesosphere.sdk.offer.evaluate;

import com.mesosphere.sdk.dcos.Capabilities;
import com.mesosphere.sdk.dcos.ResourceRefinementCapabilityContext;
import com.mesosphere.sdk.offer.Constants;
import com.mesosphere.sdk.offer.OfferRecommendation;
import com.mesosphere.sdk.offer.ResourceUtils;
import com.mesosphere.sdk.scheduler.plan.PodInstanceRequirement;
import com.mesosphere.sdk.scheduler.plan.PodInstanceRequirementTestUtils;
import com.mesosphere.sdk.specification.DefaultResourceSet;
import com.mesosphere.sdk.specification.DefaultVolumeSpec;
import com.mesosphere.sdk.specification.ResourceSet;
import com.mesosphere.sdk.specification.VolumeSpec;
import com.mesosphere.sdk.testutils.OfferTestUtils;
import com.mesosphere.sdk.testutils.ResourceTestUtils;
import com.mesosphere.sdk.testutils.TestConstants;
import org.apache.mesos.Protos;
import org.apache.mesos.Protos.Offer.Operation;
import org.apache.mesos.Protos.Resource;
import org.junit.Assert;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Offer evaluation tests concerning volumes.
 */
public class OfferEvaluatorVolumesTest extends OfferEvaluatorTestBase {

    @Test
    public void testReserveCreateLaunchRootVolume() throws Exception {
        Resource offeredCpuResource = ResourceTestUtils.getUnreservedScalar("cpus", 1.0);
        Resource offeredDiskResource = ResourceTestUtils.getUnreservedRootVolume(2000);

        List<OfferRecommendation> recommendations = evaluator.evaluate(
                PodInstanceRequirementTestUtils.getRootVolumeRequirement(1.0, 1500),
                Arrays.asList(OfferTestUtils.getCompleteOffer(Arrays.asList(offeredDiskResource, offeredCpuResource))));
        Assert.assertEquals(7, recommendations.size()); // RESERVE, RESERVE, CREATE, RESERVE, RESERVE, RESERVE, LAUNCH

        // Validate CPU RESERVE Operation
        Operation reserveOperation = recommendations.get(0).getOperation();
        Resource reserveResource = reserveOperation.getReserve().getResources(0);

        Resource.ReservationInfo reservation = ResourceUtils.getReservation(reserveResource).get();
        Assert.assertEquals(Operation.Type.RESERVE, reserveOperation.getType());
        Assert.assertEquals(1.0, reserveResource.getScalar().getValue(), 0.0);
        OfferEvaluatorTest.validateRole(reserveResource);
        Assert.assertEquals(TestConstants.ROLE, ResourceUtils.getRole(reserveResource));
        Assert.assertEquals(TestConstants.PRINCIPAL, reservation.getPrincipal());
        Assert.assertEquals(36, getResourceId(reserveResource).length());
        Assert.assertFalse(reserveResource.hasDisk());

        // Validate DISK RESERVE Operation
        reserveOperation = recommendations.get(1).getOperation();
        reserveResource = reserveOperation.getReserve().getResources(0);

        reservation = ResourceUtils.getReservation(reserveResource).get();
        Assert.assertEquals(Operation.Type.RESERVE, reserveOperation.getType());
        Assert.assertEquals(1500, reserveResource.getScalar().getValue(), 0.0);
        OfferEvaluatorTest.validateRole(reserveResource);
        Assert.assertEquals(TestConstants.ROLE, ResourceUtils.getRole(reserveResource));
        Assert.assertEquals(TestConstants.PRINCIPAL, reservation.getPrincipal());
        Assert.assertEquals(36, getResourceId(reserveResource).length());
        Assert.assertFalse(reserveResource.hasDisk());

        // Validate CREATE Operation
        String resourceId = getResourceId(reserveResource);
        Operation createOperation = recommendations.get(2).getOperation();
        Resource createResource = createOperation.getCreate().getVolumes(0);

        Assert.assertEquals(resourceId, getResourceId(createResource));
        Assert.assertEquals(36, createResource.getDisk().getPersistence().getId().length());
        Assert.assertEquals(TestConstants.PRINCIPAL, createResource.getDisk().getPersistence().getPrincipal());
        Assert.assertTrue(createResource.getDisk().hasVolume());

        // Validate LAUNCH Operation
        String persistenceId = createResource.getDisk().getPersistence().getId();
        Operation launchOperation = recommendations.get(6).getOperation();
        Resource launchResource = launchOperation.getLaunchGroup().getTaskGroup().getTasks(0).getResources(1);

        Assert.assertEquals(Operation.Type.LAUNCH_GROUP, launchOperation.getType());
        Assert.assertEquals(resourceId, getResourceId(launchResource));
        Assert.assertEquals(persistenceId, launchResource.getDisk().getPersistence().getId());
        Assert.assertEquals(TestConstants.PRINCIPAL, launchResource.getDisk().getPersistence().getPrincipal());
    }

    @Test
    public void testReserveCreateLaunchRootVolumeRefined() throws Exception {
        ResourceRefinementCapabilityContext context = new ResourceRefinementCapabilityContext(Capabilities.getInstance());
        try {
            testReserveCreateLaunchRootVolume();
        } finally {
            context.reset();
        }
    }

    @Test
    public void testExpectedRootVolume() throws Exception {
        // Launch for the first time.
        Resource offeredCpuResource = ResourceTestUtils.getUnreservedScalar("cpus", 1.0);
        Resource offeredDiskResource = ResourceTestUtils.getUnreservedRootVolume(2000);

        PodInstanceRequirement podInstanceRequirement =
                PodInstanceRequirementTestUtils.getRootVolumeRequirement(1.0, 1500);
        List<OfferRecommendation> recommendations = evaluator.evaluate(
                podInstanceRequirement,
                Arrays.asList(OfferTestUtils.getCompleteOffer(Arrays.asList(offeredDiskResource, offeredCpuResource))));

        String cpuResourceId = ResourceTestUtils.getResourceId(
                recommendations.get(0).getOperation().getReserve().getResources(0));

        Operation createOperation = recommendations.get(2).getOperation();
        Resource createResource = createOperation.getCreate().getVolumes(0);
        String diskResourceId = ResourceTestUtils.getResourceId(createResource);
        String persistenceId = ResourceTestUtils.getPersistenceId(createResource);

        String executorCpuResourceId = ResourceTestUtils.getResourceId(
                recommendations.get(3).getOperation().getReserve().getResources(0));
        String executorMemResourceId = ResourceTestUtils.getResourceId(
                recommendations.get(4).getOperation().getReserve().getResources(0));
        String executorDiskResourceId = ResourceTestUtils.getResourceId(
                recommendations.get(5).getOperation().getReserve().getResources(0));

        Operation launchOperation = recommendations.get(recommendations.size()-1).getOperation();
        Protos.ExecutorInfo executorInfo = launchOperation.getLaunchGroup().getExecutor();
        Collection<Protos.TaskInfo> taskInfos = launchOperation.getLaunchGroup().getTaskGroup().getTasksList().stream()
                .map(t -> t.toBuilder().setExecutor(executorInfo).build())
                .collect(Collectors.toList());
        stateStore.storeTasks(taskInfos);


        // Launch again on expected resources.
        Resource expectedCpu = ResourceTestUtils.getExpectedScalar("cpus", 1.0, cpuResourceId);
        Resource expectedDisk = ResourceTestUtils.getExpectedRootVolume(1500, diskResourceId, persistenceId);
        Resource expectedExecutorCpu = ResourceTestUtils.getExpectedScalar("cpus", 0.1, executorCpuResourceId);
        Resource expectedExecutorMem = ResourceTestUtils.getExpectedScalar("mem", 32, executorMemResourceId);
        Resource expectedExecutorDisk = ResourceTestUtils.getExpectedScalar("disk", 256, executorDiskResourceId);
        recommendations = evaluator.evaluate(
                podInstanceRequirement,
                Arrays.asList(OfferTestUtils.getOffer(Arrays.asList(
                        expectedCpu, expectedDisk, expectedExecutorCpu, expectedExecutorMem, expectedExecutorDisk))));
        Assert.assertEquals(1, recommendations.size());

        launchOperation = recommendations.get(0).getOperation();
        Protos.TaskInfo launchTask = launchOperation.getLaunchGroup().getTaskGroup().getTasks(0);
        Assert.assertEquals(recommendations.toString(), 2, launchTask.getResourcesCount());
        Resource launchResource = launchTask.getResources(1);

        Resource.ReservationInfo reservation = ResourceUtils.getReservation(launchResource).get();
        Assert.assertEquals(Operation.Type.LAUNCH_GROUP, launchOperation.getType());

        Assert.assertEquals(1500, launchResource.getScalar().getValue(), 0.0);
        OfferEvaluatorTest.validateRole(launchResource);
        Assert.assertEquals(TestConstants.ROLE, ResourceUtils.getRole(launchResource));
        Assert.assertEquals(persistenceId, launchResource.getDisk().getPersistence().getId());
        Assert.assertEquals(TestConstants.PRINCIPAL, launchResource.getDisk().getPersistence().getPrincipal());
        Assert.assertEquals(TestConstants.PRINCIPAL, reservation.getPrincipal());
        Assert.assertEquals(diskResourceId, getResourceId(launchResource));
    }

    @Test
    public void testReserveLaunchScalarRefined() throws Exception {
        ResourceRefinementCapabilityContext context = new ResourceRefinementCapabilityContext(Capabilities.getInstance());
        try {
            testExpectedRootVolume();
        } finally {
            context.reset();
        }
    }

    @Test
    public void testReserveCreateLaunchMountVolume() throws Exception {
        Resource offeredCpuResource = ResourceTestUtils.getUnreservedScalar("cpus", 1.0);
        Resource offeredDiskResource = ResourceTestUtils.getUnreservedMountVolume(2000);

        List<OfferRecommendation> recommendations = evaluator.evaluate(
                PodInstanceRequirementTestUtils.getMountVolumeRequirement(1.0, 1500),
                Arrays.asList(OfferTestUtils.getCompleteOffer(Arrays.asList(offeredCpuResource, offeredDiskResource))));
        Assert.assertEquals(7, recommendations.size()); // RESERVE, RESERVE, CREATE, RESERVE, RESERVE, RESERVE, LAUNCH

        // Validate RESERVE Operation
        Operation reserveOperation = recommendations.get(1).getOperation();
        Resource reserveResource = reserveOperation.getReserve().getResources(0);

        Resource.ReservationInfo reservation = ResourceUtils.getReservation(reserveResource).get();
        Assert.assertEquals(Operation.Type.RESERVE, reserveOperation.getType());
        Assert.assertEquals(2000, reserveResource.getScalar().getValue(), 0.0);
        Assert.assertEquals(TestConstants.MOUNT_ROOT, reserveResource.getDisk().getSource().getMount().getRoot());
        Assert.assertEquals(TestConstants.PRINCIPAL, reservation.getPrincipal());
        Assert.assertEquals(36, getResourceId(reserveResource).length());
        Assert.assertTrue(reserveResource.hasDisk());
        Assert.assertFalse(reserveResource.getDisk().hasPersistence());
        Assert.assertFalse(reserveResource.getDisk().hasVolume());

        // Validate CREATE Operation
        String resourceId = getResourceId(reserveResource);
        Operation createOperation = recommendations.get(2).getOperation();
        Resource createResource = createOperation.getCreate().getVolumes(0);

        Assert.assertEquals(resourceId, getResourceId(createResource));
        Assert.assertEquals(36, createResource.getDisk().getPersistence().getId().length());
        Assert.assertEquals(TestConstants.MOUNT_ROOT, createResource.getDisk().getSource().getMount().getRoot());
        Assert.assertEquals(TestConstants.PRINCIPAL, createResource.getDisk().getPersistence().getPrincipal());
        Assert.assertTrue(createResource.getDisk().hasVolume());

        // Validate LAUNCH Operation
        String persistenceId = createResource.getDisk().getPersistence().getId();
        Operation launchOperation = recommendations.get(6).getOperation();
        Resource launchResource = launchOperation.getLaunchGroup().getTaskGroup().getTasks(0).getResources(1);

        Assert.assertEquals(Operation.Type.LAUNCH_GROUP, launchOperation.getType());
        Assert.assertEquals(resourceId, getResourceId(launchResource));
        Assert.assertEquals(persistenceId, launchResource.getDisk().getPersistence().getId());
        Assert.assertEquals(TestConstants.MOUNT_ROOT, launchResource.getDisk().getSource().getMount().getRoot());
        Assert.assertEquals(TestConstants.PRINCIPAL, launchResource.getDisk().getPersistence().getPrincipal());
        Assert.assertEquals(2000, launchResource.getScalar().getValue(), 0.0);
    }

    @Test
    public void testExpectedMountVolume() throws Exception {
        // Launch for the first time.
        Resource offeredCpuResource = ResourceTestUtils.getUnreservedScalar("cpus", 1.0);
        Resource offeredDiskResource = ResourceTestUtils.getUnreservedMountVolume(2000);

        PodInstanceRequirement podInstanceRequirement =
                PodInstanceRequirementTestUtils.getMountVolumeRequirement(1.0, 1500);
        List<OfferRecommendation> recommendations = evaluator.evaluate(
                podInstanceRequirement,
                Arrays.asList(OfferTestUtils.getCompleteOffer(Arrays.asList(offeredDiskResource, offeredCpuResource))));

        String cpuResourceId = ResourceTestUtils.getResourceId(
                recommendations.get(0).getOperation().getReserve().getResources(0));
        String executorCpuResourceId = ResourceTestUtils.getResourceId(
                recommendations.get(3).getOperation().getReserve().getResources(0));
        String executorMemResourceId = ResourceTestUtils.getResourceId(
                recommendations.get(4).getOperation().getReserve().getResources(0));
        String executorDiskResourceId = ResourceTestUtils.getResourceId(
                recommendations.get(5).getOperation().getReserve().getResources(0));

        Operation createOperation = recommendations.get(2).getOperation();
        Resource createResource = createOperation.getCreate().getVolumes(0);
        String diskResourceId = ResourceTestUtils.getResourceId(createResource);
        String persistenceId = ResourceTestUtils.getPersistenceId(createResource);

        Operation launchOperation = recommendations.get(recommendations.size()-1).getOperation();
        Protos.ExecutorInfo executorInfo = launchOperation.getLaunchGroup().getExecutor();
        Collection<Protos.TaskInfo> taskInfos = launchOperation.getLaunchGroup().getTaskGroup().getTasksList().stream()
                .map(t -> t.toBuilder().setExecutor(executorInfo).build())
                .collect(Collectors.toList());
        stateStore.storeTasks(taskInfos);


        // Launch again on expected resources.
        Resource expectedCpu = ResourceTestUtils.getExpectedScalar("cpus", 1.0, cpuResourceId);
        Resource expectedDisk = ResourceTestUtils.getExpectedMountVolume(2000, diskResourceId, persistenceId);
        Resource expectedExecutorCpu = ResourceTestUtils.getExpectedScalar("cpus", 0.1, executorCpuResourceId);
        Resource expectedExecutorMem = ResourceTestUtils.getExpectedScalar("mem", 32, executorMemResourceId);
        Resource expectedExecutorDisk = ResourceTestUtils.getExpectedScalar("disk", 256, executorDiskResourceId);
        recommendations = evaluator.evaluate(
                podInstanceRequirement,
                Arrays.asList(OfferTestUtils.getCompleteOffer(Arrays.asList(
                        expectedCpu, expectedDisk, expectedExecutorCpu, expectedExecutorMem, expectedExecutorDisk))));
        Assert.assertEquals(1, recommendations.size());

        launchOperation = recommendations.get(0).getOperation();
        Protos.TaskInfo launchTask = launchOperation.getLaunchGroup().getTaskGroup().getTasks(0);
        Assert.assertEquals(recommendations.toString(), 2, launchTask.getResourcesCount());
        Resource launchResource = launchTask.getResources(1);

        Assert.assertEquals(Operation.Type.LAUNCH_GROUP, launchOperation.getType());
        Assert.assertEquals(2000, launchResource.getScalar().getValue(), 0.0);
        OfferEvaluatorTest.validateRole(launchResource);
        Assert.assertEquals(TestConstants.ROLE, ResourceUtils.getRole(launchResource));
        Assert.assertEquals(TestConstants.MOUNT_ROOT, launchResource.getDisk().getSource().getMount().getRoot());
        Assert.assertEquals(persistenceId, launchResource.getDisk().getPersistence().getId());
        Assert.assertEquals(TestConstants.PRINCIPAL, launchResource.getDisk().getPersistence().getPrincipal());
        validatePrincipal(launchResource);
        Assert.assertEquals(diskResourceId, getResourceId(launchResource));
    }

    @Test
    public void testExpectedMountVolumeRefined() throws Exception {
        ResourceRefinementCapabilityContext context = new ResourceRefinementCapabilityContext(Capabilities.getInstance());
        try {
            testExpectedMountVolume();
        } finally {
            context.reset();
        }
    }

    @Test
    public void testCreateMultipleRootVolumes() throws Exception {
        ResourceSet resourceSet = DefaultResourceSet.newBuilder(TestConstants.ROLE, Constants.ANY_ROLE, TestConstants.PRINCIPAL)
                .id(TestConstants.RESOURCE_SET_ID)
                .cpus(1.0)
                .addVolume(
                        VolumeSpec.Type.ROOT.name(),
                        1.0,
                        TestConstants.CONTAINER_PATH + "-a")
                .addVolume(
                        VolumeSpec.Type.ROOT.name(),
                        2.0,
                        TestConstants.CONTAINER_PATH + "-b")
                .build();
        PodInstanceRequirement podInstanceRequirement = PodInstanceRequirementTestUtils.getRequirement(resourceSet, 0);

        Resource offeredDisk = ResourceTestUtils.getUnreservedDisk(3);
        Resource offeredCpu = ResourceTestUtils.getUnreservedScalar("cpus", 1.0);

        Protos.Offer offer = OfferTestUtils.getCompleteOffer(Arrays.asList(offeredCpu, offeredDisk));

        List<OfferRecommendation> recommendations = evaluator.evaluate(
                podInstanceRequirement,
                Arrays.asList(offer));
        Assert.assertEquals(9, recommendations.size());

        Assert.assertEquals(Operation.Type.RESERVE, recommendations.get(0).getOperation().getType());
        Assert.assertEquals(Operation.Type.RESERVE, recommendations.get(1).getOperation().getType());
        Assert.assertEquals(Operation.Type.CREATE, recommendations.get(2).getOperation().getType());
        Assert.assertEquals(Operation.Type.RESERVE, recommendations.get(3).getOperation().getType());
        Assert.assertEquals(Operation.Type.CREATE, recommendations.get(4).getOperation().getType());
        Assert.assertEquals(Operation.Type.LAUNCH_GROUP, recommendations.get(8).getOperation().getType());

        // Validate Create Operation
        Operation createOperation = recommendations.get(2).getOperation();
        Assert.assertEquals(
                TestConstants.CONTAINER_PATH + "-a",
                createOperation.getCreate().getVolumes(0).getDisk().getVolume().getContainerPath());

        // Validate Create Operation
        createOperation = recommendations.get(4).getOperation();
        Assert.assertEquals(
                TestConstants.CONTAINER_PATH + "-b",
                createOperation.getCreate().getVolumes(0).getDisk().getVolume().getContainerPath());

        // Validate Launch Operation
        Operation launchOperation = recommendations.get(8).getOperation();
        for (Protos.TaskInfo taskInfo : launchOperation.getLaunch().getTaskInfosList()) {
            for (Resource resource : taskInfo.getResourcesList()) {
                Assert.assertFalse(getResourceId(resource).isEmpty());
            }
        }
    }

    @Test
    public void testConsumeMultipleMountVolumesFailure() throws Exception {
        Resource offeredResource = ResourceTestUtils.getUnreservedMountVolume(2000);
        ResourceSet volumeResourceSet = DefaultResourceSet.newBuilder(TestConstants.ROLE, Constants.ANY_ROLE, TestConstants.PRINCIPAL)
                .id(TestConstants.RESOURCE_SET_ID)
                .cpus(1.0)
                .addVolume(VolumeSpec.Type.MOUNT.name(), 1000.0, TestConstants.CONTAINER_PATH + "-A")
                .addVolume(VolumeSpec.Type.MOUNT.name(), 1000.0, TestConstants.CONTAINER_PATH + "-B")
                .build();
        PodInstanceRequirement podInstanceRequirement =
                PodInstanceRequirementTestUtils.getRequirement(volumeResourceSet, 0);
        Protos.Offer offer = OfferTestUtils.getCompleteOffer(Arrays.asList(offeredResource));

        List<OfferRecommendation> recommendations = evaluator.evaluate(podInstanceRequirement, Arrays.asList(offer));
        Assert.assertEquals(0, recommendations.size());
    }

    @Test
    public void testFailCreateRootVolume() throws Exception {
        Resource offeredResource = ResourceTestUtils.getUnreservedRootVolume(1000);

        List<OfferRecommendation> recommendations = evaluator.evaluate(
                PodInstanceRequirementTestUtils.getRootVolumeRequirement(1.0, 1500),
                Arrays.asList(OfferTestUtils.getCompleteOffer(offeredResource)));
        Assert.assertEquals(0, recommendations.size());
    }

    @Test
    public void testFailToCreateVolumeWithWrongResource() throws Exception {
        Resource wrongOfferedResource = ResourceTestUtils.getUnreservedMountVolume(2000);
        Resource offeredCpu = ResourceTestUtils.getUnreservedScalar("cpus", 1.0);

        List<OfferRecommendation> recommendations = evaluator.evaluate(
                PodInstanceRequirementTestUtils.getRootVolumeRequirement(1.0, 1500),
                Arrays.asList(OfferTestUtils.getCompleteOffer(Arrays.asList(offeredCpu, wrongOfferedResource))));
        Assert.assertEquals(0, recommendations.size());
        Assert.assertEquals(0, recommendations.size());
    }

    @Test
    public void testReserveCreateExecutorVolume() throws Exception {
        List<Resource> offeredResources = Arrays.asList(
                ResourceTestUtils.getUnreservedMountVolume(2000),
                ResourceTestUtils.getUnreservedCpu(1.0));

        Protos.Offer offer = OfferTestUtils.getCompleteOffer(offeredResources);
        PodInstanceRequirement podInstanceRequirement = PodInstanceRequirementTestUtils.getExecutorRequirement(
                PodInstanceRequirementTestUtils.getCpuResourceSet(1.0),
                Arrays.asList(
                        new DefaultVolumeSpec(
                                1000,
                                VolumeSpec.Type.MOUNT,
                                TestConstants.CONTAINER_PATH,
                                TestConstants.ROLE,
                                Constants.ANY_ROLE,
                                TestConstants.PRINCIPAL)),
                TestConstants.POD_TYPE,
                0);

        List<OfferRecommendation> recommendations = evaluator.evaluate(podInstanceRequirement, Arrays.asList(offer));
        Assert.assertEquals(7, recommendations.size());

        // Validate just the operations pertaining to the executor
        // Validate RESERVE Operation
        Operation reserveOperation = recommendations.get(0).getOperation();
        Resource reserveResource = reserveOperation.getReserve().getResources(0);

        Assert.assertEquals(Operation.Type.RESERVE, reserveOperation.getType());
        Assert.assertEquals(2000, reserveResource.getScalar().getValue(), 0.0);
        OfferEvaluatorTest.validateRole(reserveResource);
        Assert.assertEquals(TestConstants.ROLE, ResourceUtils.getRole(reserveResource));
        Assert.assertEquals(TestConstants.MOUNT_ROOT, reserveResource.getDisk().getSource().getMount().getRoot());
        validatePrincipal(reserveResource);
        Assert.assertEquals(36, getResourceId(reserveResource).length());

        // Validate CREATE Operation
        String resourceId = getResourceId(reserveResource);
        Operation createOperation = recommendations.get(1).getOperation();
        Resource createResource = createOperation.getCreate().getVolumes(0);

        Assert.assertEquals(resourceId, getResourceId(createResource));
        Assert.assertEquals(36, createResource.getDisk().getPersistence().getId().length());
        Assert.assertEquals(TestConstants.MOUNT_ROOT, createResource.getDisk().getSource().getMount().getRoot());
        Assert.assertEquals(TestConstants.PRINCIPAL, createResource.getDisk().getPersistence().getPrincipal());
        Assert.assertTrue(createResource.getDisk().hasVolume());
    }

    @Test
    public void testReserveCreateExecutorVolumeRefined() throws Exception {
        ResourceRefinementCapabilityContext context = new ResourceRefinementCapabilityContext(Capabilities.getInstance());
        try {
            testReserveCreateExecutorVolume();
        } finally {
            context.reset();
        }
    }

    @Test
    public void testRelaunchExecutorVolumeFailure() throws Exception {
        // Create for the first time.
        List<Resource> offeredResources = Arrays.asList(
                ResourceTestUtils.getUnreservedMountVolume(2000),
                ResourceTestUtils.getUnreservedCpu(1.0));

        Protos.Offer offer = OfferTestUtils.getCompleteOffer(offeredResources);
        PodInstanceRequirement podInstanceRequirement = PodInstanceRequirementTestUtils.getExecutorRequirement(
                PodInstanceRequirementTestUtils.getCpuResourceSet(1.0),
                Arrays.asList(
                        new DefaultVolumeSpec(
                                1000,
                                VolumeSpec.Type.MOUNT,
                                TestConstants.CONTAINER_PATH,
                                TestConstants.ROLE,
                                Constants.ANY_ROLE,
                                TestConstants.PRINCIPAL)),
                TestConstants.POD_TYPE,
                0);

        List<OfferRecommendation> recommendations = evaluator.evaluate(podInstanceRequirement, Arrays.asList(offer));
        Assert.assertEquals(7, recommendations.size());

        // Validate RESERVE Operation
        Operation reserveOperation = recommendations.get(0).getOperation();
        Resource reserveResource = reserveOperation.getReserve().getResources(0);
        String resourceId = getResourceId(reserveResource);

        // Validate CREATE Operation
        Operation createOperation = recommendations.get(1).getOperation();
        Resource createResource = createOperation.getCreate().getVolumes(0);
        String persistenceId = createResource.getDisk().getPersistence().getId();


        // Evaluation for a second time
        offeredResources = Arrays.asList(
                ResourceTestUtils.getExpectedMountVolume(2000, resourceId, persistenceId),
                ResourceTestUtils.getExpectedScalar("cpus", 1.0, resourceId));

        offer = OfferTestUtils.getCompleteOffer(offeredResources);

        recommendations = evaluator.evaluate(podInstanceRequirement, Arrays.asList(offer));
        Assert.assertEquals(0, recommendations.size());
    }

    private static void validatePrincipal(Resource resource) {
        if (Capabilities.getInstance().supportsPreReservedResources()) {
            Assert.assertEquals(TestConstants.PRINCIPAL, resource.getReservations(0).getPrincipal());
        } else {
            Assert.assertEquals(TestConstants.PRINCIPAL, resource.getReservation().getPrincipal());
        }
    }
}
