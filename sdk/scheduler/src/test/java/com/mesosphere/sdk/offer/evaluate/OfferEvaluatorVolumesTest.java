package com.mesosphere.sdk.offer.evaluate;

import com.mesosphere.sdk.offer.MesosResource;
import com.mesosphere.sdk.offer.OfferRecommendation;
import com.mesosphere.sdk.offer.ResourceUtils;
import com.mesosphere.sdk.scheduler.plan.PodInstanceRequirement;
import com.mesosphere.sdk.scheduler.plan.PodInstanceRequirementTestUtils;
import com.mesosphere.sdk.testutils.OfferTestUtils;
import com.mesosphere.sdk.testutils.ResourceTestUtils;
import com.mesosphere.sdk.testutils.TestConstants;
import org.apache.mesos.Protos.Offer.Operation;
import org.apache.mesos.Protos.Resource;
import org.junit.Assert;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;

/**
 * Offer evaluation tests concerning volumes.
 */
public class OfferEvaluatorVolumesTest extends OfferEvaluatorTestBase {

    @Test
    public void testReserveCreateLaunchRootVolume() throws Exception {
        Resource offeredCpuResource = ResourceUtils.getUnreservedScalar("cpus", 1.0);
        Resource offeredDiskResource = ResourceUtils.getUnreservedRootVolume(2000);

        List<OfferRecommendation> recommendations = evaluator.evaluate(
                PodInstanceRequirementTestUtils.getRootVolumeRequirement(1.0, 1500),
                Arrays.asList(OfferTestUtils.getOffer(Arrays.asList(offeredDiskResource, offeredCpuResource))));
        Assert.assertEquals(4, recommendations.size()); // RESERVE, RESERVE, CREATE, LAUNCH

        // Validate CPU RESERVE Operation
        Operation reserveOperation = recommendations.get(0).getOperation();
        Resource reserveResource =
                reserveOperation
                        .getReserve()
                        .getResourcesList()
                        .get(0);

        Assert.assertEquals(Operation.Type.RESERVE, reserveOperation.getType());
        Assert.assertEquals(1.0, reserveResource.getScalar().getValue(), 0.0);
        Assert.assertEquals(TestConstants.ROLE, reserveResource.getRole());
        Assert.assertEquals(TestConstants.PRINCIPAL, reserveResource.getReservation().getPrincipal());
        Assert.assertEquals(MesosResource.RESOURCE_ID_KEY, getFirstLabel(reserveResource).getKey());
        Assert.assertEquals(36, getFirstLabel(reserveResource).getValue().length());
        Assert.assertFalse(reserveResource.hasDisk());

        // Validate DISK RESERVE Operation
        reserveOperation = recommendations.get(1).getOperation();
        reserveResource =
                reserveOperation
                        .getReserve()
                        .getResourcesList()
                        .get(0);

        Assert.assertEquals(Operation.Type.RESERVE, reserveOperation.getType());
        Assert.assertEquals(1500, reserveResource.getScalar().getValue(), 0.0);
        Assert.assertEquals(TestConstants.ROLE, reserveResource.getRole());
        Assert.assertEquals(TestConstants.PRINCIPAL, reserveResource.getReservation().getPrincipal());
        Assert.assertEquals(MesosResource.RESOURCE_ID_KEY, getFirstLabel(reserveResource).getKey());
        Assert.assertEquals(36, getFirstLabel(reserveResource).getValue().length());

        // Validate CREATE Operation
        String resourceId = getFirstLabel(reserveResource).getValue();
        Operation createOperation = recommendations.get(2).getOperation();
        Resource createResource =
                createOperation
                        .getCreate()
                        .getVolumesList()
                        .get(0);

        Assert.assertEquals(resourceId, getFirstLabel(createResource).getValue());
        Assert.assertEquals(36, createResource.getDisk().getPersistence().getId().length());
        Assert.assertEquals(TestConstants.PRINCIPAL, createResource.getDisk().getPersistence().getPrincipal());
        Assert.assertTrue(createResource.getDisk().hasVolume());

        // Validate LAUNCH Operation
        String persistenceId = createResource.getDisk().getPersistence().getId();
        Operation launchOperation = recommendations.get(3).getOperation();
        Resource launchResource =
                launchOperation
                        .getLaunch()
                        .getTaskInfosList()
                        .get(0)
                        .getResourcesList()
                        .get(1);

        Assert.assertEquals(Operation.Type.LAUNCH, launchOperation.getType());
        Assert.assertEquals(resourceId, getFirstLabel(launchResource).getValue());
        Assert.assertEquals(persistenceId, launchResource.getDisk().getPersistence().getId());
        Assert.assertEquals(TestConstants.PRINCIPAL, launchResource.getDisk().getPersistence().getPrincipal());
    }

    @Test
    public void testExpectedRootVolume() throws Exception {
        // Launch for the first time.
        Resource offeredCpuResource = ResourceUtils.getUnreservedScalar("cpus", 1.0);
        Resource offeredDiskResource = ResourceUtils.getUnreservedRootVolume(2000);

        PodInstanceRequirement podInstanceRequirement =
                PodInstanceRequirementTestUtils.getRootVolumeRequirement(1.0, 1500);
        List<OfferRecommendation> recommendations = evaluator.evaluate(
                podInstanceRequirement,
                Arrays.asList(OfferTestUtils.getOffer(Arrays.asList(offeredDiskResource, offeredCpuResource))));

        String cpuResourceId = ResourceUtils.getResourceId(
                recommendations.get(0).getOperation()
                        .getReserve()
                        .getResources(0));

        Operation createOperation = recommendations.get(2).getOperation();
        Resource createResource =
                createOperation
                        .getCreate()
                        .getVolumesList()
                        .get(0);
        String diskResourceId = ResourceUtils.getResourceId(createResource);
        String persistenceId = ResourceUtils.getPersistenceId(createResource);

        Operation launchOperation = recommendations.get(recommendations.size()-1).getOperation();
        stateStore.storeTasks(launchOperation.getLaunch().getTaskInfosList());


        // Launch again on expected resources.
        Resource expectedCpu = ResourceTestUtils.getExpectedScalar("cpus", 1.0, cpuResourceId);
        Resource expectedDisk = ResourceTestUtils.getExpectedRootVolume(1500, diskResourceId, persistenceId);
        recommendations = evaluator.evaluate(
                podInstanceRequirement,
                Arrays.asList(OfferTestUtils.getOffer(Arrays.asList(expectedCpu, expectedDisk))));
        Assert.assertEquals(1, recommendations.size());

        launchOperation = recommendations.get(0).getOperation();
        Resource launchResource =
                launchOperation
                        .getLaunch()
                        .getTaskInfosList()
                        .get(0)
                        .getResourcesList()
                        .get(1);

        Assert.assertEquals(Operation.Type.LAUNCH, launchOperation.getType());
        Assert.assertEquals(1500, launchResource.getScalar().getValue(), 0.0);
        Assert.assertEquals(TestConstants.ROLE, launchResource.getRole());
        Assert.assertEquals(persistenceId, launchResource.getDisk().getPersistence().getId());
        Assert.assertEquals(TestConstants.PRINCIPAL, launchResource.getDisk().getPersistence().getPrincipal());
        Assert.assertEquals(TestConstants.PRINCIPAL, launchResource.getReservation().getPrincipal());
        Assert.assertEquals(MesosResource.RESOURCE_ID_KEY, getFirstLabel(launchResource).getKey());
        Assert.assertEquals(diskResourceId, getFirstLabel(launchResource).getValue());
    }

    @Test
    public void testReserveCreateLaunchMountVolume() throws Exception {
        Resource offeredCpuResource = ResourceUtils.getUnreservedScalar("cpus", 1.0);
        Resource offeredDiskResource = ResourceTestUtils.getUnreservedMountVolume(2000);

        List<OfferRecommendation> recommendations = evaluator.evaluate(
                PodInstanceRequirementTestUtils.getMountVolumeRequirement(1.0, 1500),
                Arrays.asList(OfferTestUtils.getOffer(Arrays.asList(offeredCpuResource, offeredDiskResource))));
        Assert.assertEquals(4, recommendations.size()); // RESERVE, RESERVE, CREATE, LAUNCH

        // Validate RESERVE Operation
        Operation reserveOperation = recommendations.get(1).getOperation();
        Resource reserveResource =
                reserveOperation
                        .getReserve()
                        .getResourcesList()
                        .get(0);

        Assert.assertEquals(Operation.Type.RESERVE, reserveOperation.getType());
        Assert.assertEquals(2000, reserveResource.getScalar().getValue(), 0.0);
        Assert.assertEquals(TestConstants.ROLE, reserveResource.getRole());
        Assert.assertEquals(TestConstants.MOUNT_ROOT, reserveResource.getDisk().getSource().getMount().getRoot());
        Assert.assertEquals(TestConstants.PRINCIPAL, reserveResource.getReservation().getPrincipal());
        Assert.assertEquals(MesosResource.RESOURCE_ID_KEY, getFirstLabel(reserveResource).getKey());
        Assert.assertEquals(36, getFirstLabel(reserveResource).getValue().length());

        // Validate CREATE Operation
        String resourceId = getFirstLabel(reserveResource).getValue();
        Operation createOperation = recommendations.get(2).getOperation();
        Resource createResource =
                createOperation
                        .getCreate()
                        .getVolumesList()
                        .get(0);

        Assert.assertEquals(resourceId, getFirstLabel(createResource).getValue());
        Assert.assertEquals(36, createResource.getDisk().getPersistence().getId().length());
        Assert.assertEquals(TestConstants.MOUNT_ROOT, createResource.getDisk().getSource().getMount().getRoot());
        Assert.assertEquals(TestConstants.PRINCIPAL, createResource.getDisk().getPersistence().getPrincipal());
        Assert.assertTrue(createResource.getDisk().hasVolume());

        // Validate LAUNCH Operation
        String persistenceId = createResource.getDisk().getPersistence().getId();
        Operation launchOperation = recommendations.get(3).getOperation();
        Resource launchResource =
                launchOperation
                        .getLaunch()
                        .getTaskInfosList()
                        .get(0)
                        .getResourcesList()
                        .get(1);

        Assert.assertEquals(Operation.Type.LAUNCH, launchOperation.getType());
        Assert.assertEquals(resourceId, getFirstLabel(launchResource).getValue());
        Assert.assertEquals(persistenceId, launchResource.getDisk().getPersistence().getId());
        Assert.assertEquals(TestConstants.MOUNT_ROOT, launchResource.getDisk().getSource().getMount().getRoot());
        Assert.assertEquals(TestConstants.PRINCIPAL, launchResource.getDisk().getPersistence().getPrincipal());
        Assert.assertEquals(2000, launchResource.getScalar().getValue(), 0.0);
    }

    /*
    @Test
    public void testCreateMultipleVolumes() throws Exception {
        Resource offeredResources = ResourceTestUtils.getUnreservedDisk(3);
        List<Resource> desiredResources = Arrays.asList(
                ResourceUtils.setLabel(
                        ResourceTestUtils.getDesiredRootVolume(1), TestConstants.CONTAINER_PATH_LABEL, "pv0"),
                ResourceUtils.setLabel(
                        ResourceTestUtils.getDesiredRootVolume(2), TestConstants.CONTAINER_PATH_LABEL, "pv1"));

        Offer offer = OfferTestUtils.getOffer(Arrays.asList(offeredResources));

        List<OfferRecommendation> recommendations = evaluator.evaluate(
                OfferRequirementTestUtils.getOfferRequirement(desiredResources, false),
                Arrays.asList(offer));
        Assert.assertEquals(5, recommendations.size());

        Assert.assertEquals(Operation.Type.RESERVE, recommendations.get(0).getOperation().getType());
        Assert.assertEquals(Operation.Type.CREATE, recommendations.get(1).getOperation().getType());
        Assert.assertEquals(Operation.Type.RESERVE, recommendations.get(2).getOperation().getType());
        Assert.assertEquals(Operation.Type.CREATE, recommendations.get(3).getOperation().getType());
        Assert.assertEquals(Operation.Type.LAUNCH, recommendations.get(4).getOperation().getType());

        // Validate Create Operation
        Operation createOperation = recommendations.get(1).getOperation();
        Assert.assertEquals("pv0", createOperation.getCreate().getVolumes(0).getDisk().getVolume().getContainerPath());

        // Validate Create Operation
        createOperation = recommendations.get(3).getOperation();
        Assert.assertEquals("pv1", createOperation.getCreate().getVolumes(0).getDisk().getVolume().getContainerPath());

        // Validate Launch Operation
        Operation launchOperation = recommendations.get(4).getOperation();
        for (TaskInfo taskInfo : launchOperation.getLaunch().getTaskInfosList()) {
            for (Resource resource : taskInfo.getResourcesList()) {
                Label resourceIdLabel = getFirstLabel(resource);
                Assert.assertTrue(resourceIdLabel.getKey().equals("resource_id"));
                Assert.assertTrue(resourceIdLabel.getValue().length() > 0);
            }
        }
    }
    */
}
