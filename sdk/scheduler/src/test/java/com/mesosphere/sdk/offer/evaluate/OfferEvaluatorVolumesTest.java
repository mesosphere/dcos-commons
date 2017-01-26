package com.mesosphere.sdk.offer.evaluate;

import com.mesosphere.sdk.offer.MesosResource;
import com.mesosphere.sdk.offer.OfferRecommendation;
import com.mesosphere.sdk.offer.ResourceUtils;
import com.mesosphere.sdk.scheduler.plan.DefaultPodInstance;
import com.mesosphere.sdk.scheduler.plan.PodInstanceRequirement;
import com.mesosphere.sdk.specification.*;
import com.mesosphere.sdk.specification.yaml.*;
import com.mesosphere.sdk.testutils.ResourceTestUtils;
import com.mesosphere.sdk.testutils.TestConstants;
import org.apache.mesos.Protos.Label;
import org.apache.mesos.Protos.Offer;
import org.apache.mesos.Protos.Offer.Operation;
import org.apache.mesos.Protos.Resource;
import org.apache.mesos.Protos.TaskInfo;
import org.junit.Assert;
import org.junit.Test;

import java.io.File;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static com.mesosphere.sdk.testutils.OfferTestUtils.getOffer;

/**
 * Offer evaluation tests concerning volumes.
 */
public class OfferEvaluatorVolumesTest extends OfferEvaluatorTestBase {

    @Test
    public void testReserveCreateLaunchRootVolume() throws Exception {
        Resource desiredCpu = ResourceTestUtils.getUnreservedCpu(0.1);
        Resource desiredResource = ResourceTestUtils.getDesiredRootVolume(1500);
        Resource offeredResource = ResourceUtils.getUnreservedRootVolume(2000);

        List<OfferRecommendation> recommendations = evaluator.evaluate(
                getPodInstanceRequirement(desiredResource, true),
                Arrays.asList(getOffer(Arrays.asList(offeredResource, desiredCpu))));
        Assert.assertEquals(4, recommendations.size());

        // Validate RESERVE Operation
        Operation reserveOperation = recommendations.get(1).getOperation();
        Resource reserveResource =
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
    public void testReserveCreateLaunchMountVolume() throws Exception {
        Resource desiredCpu = ResourceTestUtils.getUnreservedCpu(0.1);
        Resource desiredResource = ResourceTestUtils.getDesiredMountVolume(1000);
        Resource offeredResource = ResourceTestUtils.getUnreservedMountVolume(2000);

        PodInstanceRequirement podInstanceRequirement = getPodInstanceRequirement(desiredResource, true);
        List<OfferRecommendation> recommendations = evaluator.evaluate(
                podInstanceRequirement,
                Arrays.asList(getOffer(Arrays.asList(offeredResource, desiredCpu))));
        Assert.assertEquals(4, recommendations.size());

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

    @Test
    public void testFailCreateRootVolume() throws Exception {
        Resource desiredResource = ResourceTestUtils.getDesiredRootVolume(1000 * 2);
        Resource offeredResource = ResourceUtils.getUnreservedRootVolume(1000);

        List<OfferRecommendation> recommendations = evaluator.evaluate(
                getPodInstanceRequirement(desiredResource, true),
                Arrays.asList(getOffer(offeredResource)));
        Assert.assertEquals(0, recommendations.size());
    }

    @Test
    public void testExpectedMountVolume() throws Exception {
        String resourceId = UUID.randomUUID().toString();
        Resource desiredCpu = ResourceTestUtils.getUnreservedCpu(0.1);
        Resource expectedResource = ResourceTestUtils.getExpectedMountVolume(1000, resourceId);

        List<OfferRecommendation> recommendations = evaluator.evaluate(
                getExistingPodInstanceRequirement(expectedResource, true),
                Arrays.asList(getOffer(Arrays.asList(expectedResource, desiredCpu))));
        Assert.assertEquals(2, recommendations.size());

        Operation launchOperation = recommendations.get(1).getOperation();
        Resource launchResource =
                launchOperation
                        .getLaunch()
                        .getTaskInfosList()
                        .get(0)
                        .getResourcesList()
                        .get(1);

        Assert.assertEquals(Operation.Type.LAUNCH, launchOperation.getType());
        Assert.assertEquals(1000, launchResource.getScalar().getValue(), 0.0);
        Assert.assertEquals(TestConstants.ROLE, launchResource.getRole());
        Assert.assertEquals(TestConstants.MOUNT_ROOT, launchResource.getDisk().getSource().getMount().getRoot());
        Assert.assertEquals(TestConstants.PERSISTENCE_ID, launchResource.getDisk().getPersistence().getId());
        Assert.assertEquals(TestConstants.PRINCIPAL, launchResource.getDisk().getPersistence().getPrincipal());
        Assert.assertEquals(TestConstants.PRINCIPAL, launchResource.getReservation().getPrincipal());
        Assert.assertEquals(MesosResource.RESOURCE_ID_KEY, getFirstLabel(launchResource).getKey());
        Assert.assertEquals(resourceId, getFirstLabel(launchResource).getValue());
    }

    @Test
    public void testExpectedRootVolume() throws Exception {
        String resourceId = UUID.randomUUID().toString();
        Resource desiredCpu = ResourceTestUtils.getUnreservedCpu(0.1);
        Resource expectedResource = ResourceTestUtils.getExpectedRootVolume(1000, resourceId);

        List<OfferRecommendation> recommendations = evaluator.evaluate(
                getExistingPodInstanceRequirement(expectedResource, true),
                Arrays.asList(getOffer(Arrays.asList(expectedResource, desiredCpu))));
        Assert.assertEquals(2, recommendations.size());

        Operation launchOperation = recommendations.get(1).getOperation();
        Resource launchResource =
                launchOperation
                        .getLaunch()
                        .getTaskInfosList()
                        .get(0)
                        .getResourcesList()
                        .get(1);

        Assert.assertEquals(Operation.Type.LAUNCH, launchOperation.getType());
        Assert.assertEquals(1000, launchResource.getScalar().getValue(), 0.0);
        Assert.assertEquals(TestConstants.ROLE, launchResource.getRole());
        Assert.assertEquals(TestConstants.PERSISTENCE_ID, launchResource.getDisk().getPersistence().getId());
        Assert.assertEquals(TestConstants.PRINCIPAL, launchResource.getDisk().getPersistence().getPrincipal());
        Assert.assertEquals(TestConstants.PRINCIPAL, launchResource.getReservation().getPrincipal());
        Assert.assertEquals(MesosResource.RESOURCE_ID_KEY, getFirstLabel(launchResource).getKey());
        Assert.assertEquals(resourceId, getFirstLabel(launchResource).getValue());
    }

    @Test
    public void testUpdateMountVolumeSuccess() throws Exception {
        String resourceId = UUID.randomUUID().toString();
        Resource desiredCpu = ResourceTestUtils.getUnreservedCpu(0.1);
        Resource updatedResource = ResourceTestUtils.getExpectedMountVolume(1500, resourceId);
        Resource offeredResource = ResourceTestUtils.getExpectedMountVolume(2000, resourceId);

        List<OfferRecommendation> recommendations = evaluator.evaluate(
                getExistingPodInstanceRequirement(updatedResource, true),
                Arrays.asList(getOffer(Arrays.asList(offeredResource, desiredCpu))));
        Assert.assertEquals(2, recommendations.size());

        Operation launchOperation = recommendations.get(1).getOperation();
        Resource launchResource = launchOperation
                .getLaunch()
                .getTaskInfosList()
                .get(0)
                .getResourcesList()
                .get(1);

        Assert.assertEquals(Operation.Type.LAUNCH, launchOperation.getType());
        Assert.assertEquals(getFirstLabel(updatedResource).getValue(), getFirstLabel(launchResource).getValue());
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
                getExistingPodInstanceRequirement(updatedResource, true),
                Arrays.asList(getOffer(offeredResource)));
        Assert.assertEquals(0, recommendations.size());
    }

    @Test
    public void testFailToCreateVolumeWithWrongResource() throws Exception {
        Resource desiredResource = ResourceTestUtils.getDesiredRootVolume(1000);
        Resource wrongOfferedResource = ResourceTestUtils.getUnreservedMountVolume(2000);

        List<OfferRecommendation> recommendations = evaluator.evaluate(
                getPodInstanceRequirement(desiredResource, true),
                Arrays.asList(getOffer(wrongOfferedResource)));
        Assert.assertEquals(0, recommendations.size());
        Assert.assertEquals(0, recommendations.size());
    }

    @Test
    public void testCreateMultipleVolumes() throws Exception {
        Resource desiredCpu = ResourceTestUtils.getUnreservedCpu(1);
        Resource desiredDisk = ResourceTestUtils.getUnreservedDisk(3);

        PodInstanceRequirement podInstanceRequirement = getMultiVolumePodInstanceRequirement();
        Offer offer = getOffer(Arrays.asList(desiredCpu, desiredDisk));

        List<OfferRecommendation> recommendations = evaluator.evaluate(
                podInstanceRequirement,
                Arrays.asList(offer));
        Assert.assertEquals(6, recommendations.size());

        Assert.assertEquals(Operation.Type.RESERVE, recommendations.get(0).getOperation().getType());
        Assert.assertEquals(Operation.Type.RESERVE, recommendations.get(1).getOperation().getType());
        Assert.assertEquals(Operation.Type.RESERVE, recommendations.get(2).getOperation().getType());
        Assert.assertEquals(Operation.Type.CREATE, recommendations.get(3).getOperation().getType());
        Assert.assertEquals(Operation.Type.CREATE, recommendations.get(4).getOperation().getType());
        Assert.assertEquals(Operation.Type.LAUNCH, recommendations.get(5).getOperation().getType());

        System.out.println(recommendations.get(5).getOperation());

        // Validate Create Operation
        Operation createOperation = recommendations.get(3).getOperation();
        Assert.assertEquals("pv0", createOperation.getCreate().getVolumes(0).getDisk().getVolume().getContainerPath());

        // Validate Create Operation
        createOperation = recommendations.get(4).getOperation();
        Assert.assertEquals("pv1", createOperation.getCreate().getVolumes(0).getDisk().getVolume().getContainerPath());

        // Validate Launch Operation
        Operation launchOperation = recommendations.get(5).getOperation();
        for (TaskInfo taskInfo : launchOperation.getLaunch().getTaskInfosList()) {
            for (Resource resource : taskInfo.getResourcesList()) {
                Label resourceIdLabel = getFirstLabel(resource);
                Assert.assertTrue(resourceIdLabel.getKey().equals("resource_id"));
                Assert.assertTrue(resourceIdLabel.getValue().length() > 0);
            }
        }
    }

    private PodInstanceRequirement getMultiVolumePodInstanceRequirement() throws Exception {
        CommandSpec cmd = DefaultCommandSpec.newBuilder("hello")
                .value("./cmd")
                .uris(Collections.emptyList())
                .build();
        VolumeSpec volume0 = new DefaultVolumeSpec(
                1,
                VolumeSpec.Type.ROOT,
                "pv0",
                TestConstants.ROLE,
                TestConstants.PRINCIPAL, "");
        VolumeSpec volume1 = new DefaultVolumeSpec(
                2,
                VolumeSpec.Type.ROOT,
                "pv1",
                TestConstants.ROLE,
                TestConstants.PRINCIPAL, "");
        ResourceSet resourceSet = DefaultResourceSet.newBuilder(TestConstants.ROLE, TestConstants.PRINCIPAL)
                .id("resource-set-id")
                .cpus(1.0)
                .volumes(Arrays.asList(volume0, volume1))
                .build();
        TaskSpec taskSpec = DefaultTaskSpec.newBuilder()
                .name("server")
                .goalState(GoalState.RUNNING)
                .commandSpec(cmd)
                .resourceSet(resourceSet)
                .configFiles(Collections.emptyList())
                .build();
        PodSpec podSpec = DefaultPodSpec.newBuilder()
                .type("hello")
                .count(1)
                .tasks(Arrays.asList(taskSpec))
                .build();

        PodInstance podInstance = new DefaultPodInstance(podSpec, 0);

        return PodInstanceRequirement.create(podInstance, Arrays.asList("server"));
    }

    private ServiceSpec getServiceSpec(String yamlFile) throws Exception {
        ClassLoader classLoader = getClass().getClassLoader();
        File file = new File(classLoader.getResource(yamlFile).getFile());
        RawServiceSpec rawServiceSpec = YAMLServiceSpecFactory.generateRawSpecFromYAML(file);
        return YAMLServiceSpecFactory.generateServiceSpec(rawServiceSpec);
    }
}
