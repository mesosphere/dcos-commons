package com.mesosphere.sdk.offer.evaluate;

import com.mesosphere.sdk.offer.*;
import com.mesosphere.sdk.offer.evaluate.placement.PlacementUtils;
import com.mesosphere.sdk.offer.taskdata.EnvUtils;
import com.mesosphere.sdk.offer.taskdata.SchedulerLabelReader;
import com.mesosphere.sdk.offer.taskdata.TaskPackingUtils;
import com.mesosphere.sdk.scheduler.plan.DefaultPodInstance;
import com.mesosphere.sdk.scheduler.plan.DeploymentStep;
import com.mesosphere.sdk.scheduler.plan.PodInstanceRequirement;
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
    public void testReserveTaskStaticPort() throws Exception {
        Resource desiredPorts = ResourceTestUtils.getDesiredRanges("ports", 555, 555);
        Resource offeredPorts = ResourceTestUtils.getUnreservedPorts(555, 555);

        List<OfferRecommendation> recommendations = evaluator.evaluate(
                OfferRequirementTestUtils.getOfferRequirement(desiredPorts),
                Arrays.asList(OfferTestUtils.getOffer(offeredPorts)));

        Assert.assertEquals(2, recommendations.size());

        Operation launchOperation = recommendations.get(1).getOperation();
        TaskInfo taskInfo = launchOperation.getLaunch().getTaskInfos(0);
        Resource fulfilledPortResource = taskInfo.getResources(0);
        Label resourceIdLabel = fulfilledPortResource.getReservation().getLabels().getLabels(0);
        Assert.assertEquals("resource_id", resourceIdLabel.getKey());

        CommandInfo command = TaskPackingUtils.unpack(taskInfo).getCommand();
        Map<String, String> envvars = EnvUtils.toMap(command.getEnvironment());
        Assert.assertEquals(envvars.toString(), 1, envvars.size());
        Assert.assertEquals(String.valueOf(555), envvars.get(TestConstants.PORT_ENV_NAME));
    }

    @Test
    public void testUpdateStaticToStaticPort() throws Exception {
        String resourceId = UUID.randomUUID().toString();
        Resource updatedResource = ResourceTestUtils.getExpectedRanges("ports", 666, 666, resourceId);
        Resource offeredReservedResource = ResourceTestUtils.getExpectedRanges("ports", 555, 555, resourceId);
        Resource offeredUnreservedResource = ResourceTestUtils.getUnreservedPorts(666, 666);

        List<Resource> offeredResources = Arrays.asList(offeredReservedResource, offeredUnreservedResource);
        List<OfferRecommendation> recommendations = evaluator.evaluate(
                OfferRequirementTestUtils.getOfferRequirement(updatedResource),
                Arrays.asList(OfferTestUtils.getOffer(offeredResources)));

        // UNRESERVE, RESERVE, LAUNCH
        Assert.assertEquals(3, recommendations.size());
        Assert.assertEquals(Operation.Type.RESERVE, recommendations.get(0).getOperation().getType());
        Assert.assertEquals(Operation.Type.LAUNCH, recommendations.get(1).getOperation().getType());
        Assert.assertEquals(Operation.Type.UNRESERVE, recommendations.get(2).getOperation().getType());

        Operation launchOperation = recommendations.get(1).getOperation();
        TaskInfo taskInfo = launchOperation.getLaunch().getTaskInfos(0);
        Resource fulfilledPortResource = taskInfo.getResources(0);
        Label resourceIdLabel = fulfilledPortResource.getReservation().getLabels().getLabels(0);
        Assert.assertEquals("resource_id", resourceIdLabel.getKey());

        CommandInfo command = TaskPackingUtils.unpack(taskInfo).getCommand();
        Map<String, String> envvars = EnvUtils.toMap(command.getEnvironment());
        Assert.assertEquals(envvars.toString(), 1, envvars.size());
        Assert.assertEquals(String.valueOf(666), envvars.get(TestConstants.PORT_ENV_NAME));
    }

    @Test
    public void testUpdateDynamicToStaticPort() throws Exception {
        String resourceId = UUID.randomUUID().toString();
        Resource updatedResource = ResourceTestUtils.setLabel(
                ResourceTestUtils.getExpectedRanges("ports", 0, 0, resourceId),
                TestConstants.HAS_DYNAMIC_PORT_ASSIGNMENT_LABEL,
                Integer.toString(666));
        Resource offeredReservedResource = ResourceTestUtils.getExpectedRanges("ports", 555, 555, resourceId);
        Resource offeredUnreservedResource = ResourceTestUtils.getUnreservedPorts(666, 666);

        List<OfferRecommendation> recommendations = evaluator.evaluate(
                OfferRequirementTestUtils.getOfferRequirement(updatedResource),
                Arrays.asList(OfferTestUtils.getOffer(Arrays.asList(
                        offeredReservedResource, offeredUnreservedResource))));

        // RESERVE, UNRESERVE, LAUNCH
        Assert.assertEquals(3, recommendations.size());
        Assert.assertEquals(Operation.Type.RESERVE, recommendations.get(0).getOperation().getType());
        Assert.assertEquals(Operation.Type.LAUNCH, recommendations.get(1).getOperation().getType());
        Assert.assertEquals(Operation.Type.UNRESERVE, recommendations.get(2).getOperation().getType());

        Operation launchOperation = recommendations.get(1).getOperation();
        TaskInfo taskInfo = launchOperation.getLaunch().getTaskInfos(0);
        Resource fulfilledPortResource = taskInfo.getResources(0);
        Label resourceIdLabel = fulfilledPortResource.getReservation().getLabels().getLabels(0);
        Assert.assertEquals("resource_id", resourceIdLabel.getKey());

        CommandInfo command = TaskPackingUtils.unpack(taskInfo).getCommand();
        Map<String, String> envvars = EnvUtils.toMap(command.getEnvironment());
        Assert.assertEquals(envvars.toString(), 1, envvars.size());
        Assert.assertEquals(String.valueOf(666), envvars.get(TestConstants.PORT_ENV_NAME));
    }

    @Test
    public void testReserveTaskDynamicPort() throws Exception {
        Resource offeredPorts = ResourceTestUtils.getUnreservedPorts(10000, 10000);
        Resource desiredPorts = ResourceTestUtils.getDesiredRanges("ports", 0, 0);

        List<OfferRecommendation> recommendations = evaluator.evaluate(
                OfferRequirementTestUtils.getOfferRequirement(desiredPorts),
                Arrays.asList(OfferTestUtils.getOffer(offeredPorts)));

        Assert.assertEquals(2, recommendations.size());

        Operation launchOperation = recommendations.get(1).getOperation();
        TaskInfo taskInfo = launchOperation.getLaunch().getTaskInfos(0);
        Resource fulfilledPortResource = taskInfo.getResources(0);
        Label resourceIdLabel = fulfilledPortResource.getReservation().getLabels().getLabels(0);
        Assert.assertEquals("resource_id", resourceIdLabel.getKey());

        CommandInfo command = TaskPackingUtils.unpack(taskInfo).getCommand();
        Map<String, String> envvars = EnvUtils.toMap(command.getEnvironment());
        Assert.assertEquals(envvars.toString(), 1, envvars.size());
        Assert.assertEquals(String.valueOf(10000), envvars.get(TestConstants.PORT_ENV_NAME));
    }

    @Test
    public void testLaunchExpectedPort() throws Exception {
        String resourceId = UUID.randomUUID().toString();
        Resource desiredResource = ResourceTestUtils.getExpectedRanges("ports", 10000, 10000, resourceId);

        List<OfferRecommendation> recommendations = evaluator.evaluate(
                OfferRequirementTestUtils.getOfferRequirement(desiredResource),
                Arrays.asList(OfferTestUtils.getOffer(Arrays.asList(desiredResource))));
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
    public void testLaunchExpectedDynamicPort() throws Exception {
        String resourceId = UUID.randomUUID().toString();
        Resource desiredResource = ResourceTestUtils.setLabel(
                ResourceTestUtils.getExpectedRanges("ports", 0, 0, resourceId),
                TestConstants.HAS_DYNAMIC_PORT_ASSIGNMENT_LABEL,
                Integer.toString(10000));
        Resource offeredResource = ResourceTestUtils.getExpectedRanges("ports", 10000, 10000, resourceId);

        List<OfferRecommendation> recommendations = evaluator.evaluate(
                OfferRequirementTestUtils.getOfferRequirement(desiredResource),
                Arrays.asList(OfferTestUtils.getOffer(Arrays.asList(offeredResource))));
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
    public void testLaunchExpectedMultiplePorts() throws Exception {
        String resourceId = UUID.randomUUID().toString();
        Resource offeredResource = ResourceTestUtils.getExpectedRanges("ports", 10000, 10001, resourceId);
        List<Resource> desiredResources = Arrays.asList(
                ResourceTestUtils.getExpectedRanges("ports", 10000, 10000, resourceId),
                ResourceTestUtils.getExpectedRanges("ports", 10001, 10001, resourceId));

        List<OfferRecommendation> recommendations = evaluator.evaluate(
                OfferRequirementTestUtils.getOfferRequirement(desiredResources, false),
                Arrays.asList(OfferTestUtils.getOffer(Arrays.asList(offeredResource))));
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
    public void testReserveTaskMultipleDynamicPorts() throws Exception {
        Resource offeredPorts = ResourceTestUtils.getUnreservedPorts(10000, 10001);
        List<Resource> desiredPorts = Arrays.asList(
                ResourceTestUtils.getDesiredRanges("ports", 0, 0),
                ResourceTestUtils.getDesiredRanges("ports", 0, 0));

        List<OfferRecommendation> recommendations = evaluator.evaluate(
                OfferRequirementTestUtils.getOfferRequirement(desiredPorts, false),
                Arrays.asList(OfferTestUtils.getOffer(offeredPorts)));

        Assert.assertEquals(2, recommendations.size());

        Operation reserveOperation = recommendations.get(0).getOperation();
        Resource fulfilledPortResource = reserveOperation.getReserve().getResources(0);
        Assert.assertEquals(10000, fulfilledPortResource.getRanges().getRange(0).getBegin());
        Assert.assertEquals(10001, fulfilledPortResource.getRanges().getRange(0).getEnd());

        Operation launchOperation = recommendations.get(1).getOperation();
        TaskInfo taskInfo = launchOperation.getLaunch().getTaskInfos(0);
        Resource taskPortResource = taskInfo.getResources(0);
        Label resourceIdLabel = taskPortResource.getReservation().getLabels().getLabels(0);
        Assert.assertEquals("resource_id", resourceIdLabel.getKey());
        Assert.assertEquals(
                resourceIdLabel.getValue(), fulfilledPortResource.getReservation().getLabels().getLabels(0).getValue());

        CommandInfo command = TaskPackingUtils.unpack(taskInfo).getCommand();
        Map<String, String> envvars = EnvUtils.toMap(command.getEnvironment());
        Assert.assertEquals(envvars.toString(), 2, envvars.size());
        Assert.assertEquals(String.valueOf(10000), envvars.get(TestConstants.PORT_ENV_NAME));
        Assert.assertEquals(String.valueOf(10001), envvars.get(TestConstants.PORT_ENV_NAME + "1"));

        Assert.assertEquals(10000, taskPortResource.getRanges().getRange(0).getBegin());
        Assert.assertEquals(10001, taskPortResource.getRanges().getRange(0).getEnd());
    }

    @SuppressWarnings("PMD.AvoidUsingHardCodedIP")
    @Test
    public void testReserveTaskNamedVIPPort() throws Exception {
        Resource offeredPorts = ResourceTestUtils.getUnreservedPorts(10000, 10000);
        Resource desiredPorts = ResourceTestUtils.setLabel(
                ResourceTestUtils.getDesiredRanges("ports", 10000, 10000),
                TestConstants.HAS_VIP_LABEL,
                "true");

        List<OfferRecommendation> recommendations = evaluator.evaluate(
                OfferRequirementTestUtils.getOfferRequirement(desiredPorts),
                Arrays.asList(OfferTestUtils.getOffer(offeredPorts)));

        Assert.assertEquals(2, recommendations.size());

        Operation launchOperation = recommendations.get(1).getOperation();
        TaskInfo taskInfo = launchOperation.getLaunch().getTaskInfos(0);
        Resource fulfilledPortResource = taskInfo.getResources(0);
        Assert.assertEquals(10000, fulfilledPortResource.getRanges().getRange(0).getBegin());

        Label resourceIdLabel = fulfilledPortResource.getReservation().getLabels().getLabels(0);
        Assert.assertEquals("resource_id", resourceIdLabel.getKey());

        DiscoveryInfo discoveryInfo = taskInfo.getDiscovery();
        Assert.assertEquals(discoveryInfo.getName(), taskInfo.getName());
        Assert.assertEquals(discoveryInfo.getVisibility(), DiscoveryInfo.Visibility.CLUSTER);

        Port discoveryPort = discoveryInfo.getPorts().getPorts(0);
        Assert.assertEquals(discoveryPort.getProtocol(), "tcp");
        Assert.assertEquals(discoveryPort.getVisibility(), DiscoveryInfo.Visibility.EXTERNAL);
        Assert.assertEquals(discoveryPort.getNumber(), 10000);
        Label vipLabel = discoveryPort.getLabels().getLabels(0);
        Assert.assertTrue(vipLabel.getKey().startsWith("VIP_"));
        Assert.assertEquals(vipLabel.getValue(), TestConstants.VIP_NAME + ":" + TestConstants.VIP_PORT);
    }

    @SuppressWarnings("PMD.AvoidUsingHardCodedIP")
    @Test
    public void testReserveTaskDynamicVIPPort() throws Exception {
        Resource offeredPorts = ResourceTestUtils.getUnreservedPorts(10000, 10000);
        Resource desiredPorts = ResourceTestUtils.setLabel(
                ResourceTestUtils.getDesiredRanges("ports", 0, 0),
                TestConstants.HAS_VIP_LABEL,
                "true");

        List<OfferRecommendation> recommendations = evaluator.evaluate(
                OfferRequirementTestUtils.getOfferRequirement(desiredPorts),
                Arrays.asList(OfferTestUtils.getOffer(offeredPorts)));

        Assert.assertEquals(2, recommendations.size());

        Operation launchOperation = recommendations.get(1).getOperation();
        TaskInfo taskInfo = launchOperation.getLaunch().getTaskInfos(0);
        Resource fulfilledPortResource = taskInfo.getResources(0);
        Assert.assertEquals(10000, fulfilledPortResource.getRanges().getRange(0).getBegin());

        Label resourceIdLabel = fulfilledPortResource.getReservation().getLabels().getLabels(0);
        Assert.assertEquals("resource_id", resourceIdLabel.getKey());

        DiscoveryInfo discoveryInfo = taskInfo.getDiscovery();
        Assert.assertEquals(discoveryInfo.getName(), taskInfo.getName());
        Assert.assertEquals(discoveryInfo.getVisibility(), DiscoveryInfo.Visibility.CLUSTER);

        Port discoveryPort = discoveryInfo.getPorts().getPorts(0);
        Assert.assertEquals(discoveryPort.getProtocol(), "tcp");
        Assert.assertEquals(discoveryPort.getVisibility(), DiscoveryInfo.Visibility.EXTERNAL);
        Assert.assertEquals(discoveryPort.getNumber(), 10000);
        Label vipLabel = discoveryPort.getLabels().getLabels(0);
        Assert.assertTrue(vipLabel.getKey().startsWith("VIP_"));
        Assert.assertEquals(vipLabel.getValue(), TestConstants.VIP_NAME + ":" + TestConstants.VIP_PORT);
    }

    @Test
    public void testConsumeMultipleMountVolumesSuccess() throws Exception {
        Resource desiredResourceA = ResourceTestUtils.getDesiredMountVolume(1000);
        Resource desiredResourceB = ResourceTestUtils.getDesiredMountVolume(1000);
        Resource offeredResourceA = ResourceTestUtils.getUnreservedMountVolume(2000);
        Resource offeredResourceB = ResourceTestUtils.getUnreservedMountVolume(3000);

        Offer offer = OfferTestUtils.getOffer(Arrays.asList(offeredResourceA, offeredResourceB));
        OfferRequirement offerRequirement = OfferRequirementTestUtils.getOfferRequirement(
                Arrays.asList(desiredResourceA, desiredResourceB), false);

        List<OfferRecommendation> recommendations = evaluator.evaluate(offerRequirement, Arrays.asList(offer));
        Assert.assertEquals(5, recommendations.size());
        Assert.assertEquals(Operation.Type.RESERVE, recommendations.get(0).getOperation().getType());
        Assert.assertEquals(Operation.Type.CREATE, recommendations.get(1).getOperation().getType());
        Assert.assertEquals(Operation.Type.RESERVE, recommendations.get(2).getOperation().getType());
        Assert.assertEquals(Operation.Type.CREATE, recommendations.get(3).getOperation().getType());
        Assert.assertEquals(Operation.Type.LAUNCH, recommendations.get(4).getOperation().getType());
    }

    @Test
    public void testConsumeMultipleMountVolumesFailure() throws Exception {
        Resource desiredResourceA = ResourceTestUtils.getDesiredMountVolume(1000);
        Resource desiredResourceB = ResourceTestUtils.getDesiredMountVolume(1000);
        Resource offeredResource = ResourceTestUtils.getUnreservedMountVolume(2000);

        Offer offer = OfferTestUtils.getOffer(Arrays.asList(offeredResource));
        OfferRequirement offerRequirement = OfferRequirementTestUtils.getOfferRequirement(
                Arrays.asList(desiredResourceA, desiredResourceB), false);

        List<OfferRecommendation> recommendations = evaluator.evaluate(offerRequirement, Arrays.asList(offer));
        Assert.assertEquals(0, recommendations.size());
    }

    @Test
    public void testReserveCreateLaunchMountVolume() throws Exception {
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
        Assert.assertEquals(TestConstants.ROLE, reserveResource.getRole());
        Assert.assertEquals(TestConstants.MOUNT_ROOT, reserveResource.getDisk().getSource().getMount().getRoot());
        Assert.assertEquals(TestConstants.PRINCIPAL, reserveResource.getReservation().getPrincipal());
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
        Assert.assertEquals(TestConstants.MOUNT_ROOT, createResource.getDisk().getSource().getMount().getRoot());
        Assert.assertEquals(TestConstants.PRINCIPAL, createResource.getDisk().getPersistence().getPrincipal());
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
        Assert.assertEquals(TestConstants.MOUNT_ROOT, launchResource.getDisk().getSource().getMount().getRoot());
        Assert.assertEquals(TestConstants.PRINCIPAL, launchResource.getDisk().getPersistence().getPrincipal());
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
        Resource launchResource = launchOperation
                .getLaunch()
                .getTaskInfosList()
                .get(0)
                .getResourcesList()
                .get(0);

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
    public void testReserveCreateLaunchRootVolume() throws Exception {
        Resource desiredResource = ResourceTestUtils.getDesiredRootVolume(1500);
        Resource offeredResource = ResourceTestUtils.getUnreservedRootVolume(2000);

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
        Assert.assertEquals(TestConstants.ROLE, reserveResource.getRole());
        Assert.assertEquals(TestConstants.PRINCIPAL, reserveResource.getReservation().getPrincipal());
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
        Assert.assertEquals(TestConstants.PRINCIPAL, createResource.getDisk().getPersistence().getPrincipal());
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
        Assert.assertEquals(TestConstants.PRINCIPAL, launchResource.getDisk().getPersistence().getPrincipal());
    }

    @Test
    public void testFailCreateRootVolume() throws Exception {
        Resource desiredResource = ResourceTestUtils.getDesiredRootVolume(1000 * 2);
        Resource offeredResource = ResourceTestUtils.getUnreservedRootVolume(1000);

        List<OfferRecommendation> recommendations = evaluator.evaluate(
                        OfferRequirementTestUtils.getOfferRequirement(desiredResource),
                        Arrays.asList(OfferTestUtils.getOffer(offeredResource)));
        Assert.assertEquals(0, recommendations.size());
    }

    @Test
    public void testExpectedMountVolume() throws Exception {
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
        Assert.assertEquals(TestConstants.ROLE, launchResource.getRole());
        Assert.assertEquals(TestConstants.PERSISTENCE_ID, launchResource.getDisk().getPersistence().getId());
        Assert.assertEquals(TestConstants.PRINCIPAL, launchResource.getDisk().getPersistence().getPrincipal());
        Assert.assertEquals(TestConstants.PRINCIPAL, launchResource.getReservation().getPrincipal());
        Assert.assertEquals(MesosResource.RESOURCE_ID_KEY, getFirstLabel(launchResource).getKey());
        Assert.assertEquals(resourceId, getFirstLabel(launchResource).getValue());
    }

    @Test
    public void testReserveExecutorVolume() throws Exception {
        Offer offer = OfferTestUtils.getOffer(Arrays.asList(
                ResourceTestUtils.getUnreservedMountVolume(2000),
                ResourceTestUtils.getUnreservedCpu(1.0)));
        OfferRequirement offerRequirement = OfferRequirementTestUtils.getOfferRequirement(
                ResourceTestUtils.getDesiredCpu(1.0),
                ResourceTestUtils.getDesiredMountVolume(1000));

        List<OfferRecommendation> recommendations = evaluator.evaluate(offerRequirement, Arrays.asList(offer));
        Assert.assertEquals(4, recommendations.size());

        // Validate just the operations pertaining to the executor
        // Validate RESERVE Operation
        Operation reserveOperation = recommendations.get(0).getOperation();
        Resource reserveResource = reserveOperation.getReserve().getResourcesList().get(0);

        Assert.assertEquals(Operation.Type.RESERVE, reserveOperation.getType());
        Assert.assertEquals(2000, reserveResource.getScalar().getValue(), 0.0);
        Assert.assertEquals(TestConstants.ROLE, reserveResource.getRole());
        Assert.assertEquals(TestConstants.MOUNT_ROOT, reserveResource.getDisk().getSource().getMount().getRoot());
        Assert.assertEquals(TestConstants.PRINCIPAL, reserveResource.getReservation().getPrincipal());
        Assert.assertEquals(MesosResource.RESOURCE_ID_KEY, getFirstLabel(reserveResource).getKey());
        Assert.assertEquals(36, getFirstLabel(reserveResource).getValue().length());

        // Validate CREATE Operation
        String resourceId = getFirstLabel(reserveResource).getValue();
        Operation createOperation = recommendations.get(1).getOperation();
        Resource createResource = createOperation.getCreate().getVolumesList().get(0);

        Assert.assertEquals(resourceId, getFirstLabel(createResource).getValue());
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

    @Test
    public void testReserveLaunchScalar() throws Exception {
        Resource desiredResource = ResourceTestUtils.getDesiredCpu(1.0);
        Resource offeredResource = ResourceTestUtils.getUnreservedScalar("cpus", 2.0);

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
        Assert.assertEquals(TestConstants.ROLE, reserveResource.getRole());
        Assert.assertEquals(TestConstants.PRINCIPAL, reserveResource.getReservation().getPrincipal());
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
    public void testLaunchExpectedScalar() throws Exception {
        String resourceId = UUID.randomUUID().toString();
        Resource desiredResource = ResourceTestUtils.getExpectedScalar("cpus", 1.0, resourceId);

        List<OfferRecommendation> recommendations = evaluator.evaluate(
                OfferRequirementTestUtils.getOfferRequirement(desiredResource),
                Arrays.asList(OfferTestUtils.getOffer(Arrays.asList(desiredResource))));
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
    public void testLaunchAttributesEmbedded() throws Exception {
        String resourceId = UUID.randomUUID().toString();
        Resource desiredResource = ResourceTestUtils.getExpectedScalar("cpus", 1.0, resourceId);
        Offer.Builder offerBuilder = OfferTestUtils.getOffer(desiredResource).toBuilder();
        Attribute.Builder attrBuilder =
                offerBuilder.addAttributesBuilder().setName("rack").setType(Value.Type.TEXT);
        attrBuilder.getTextBuilder().setValue("foo");
        attrBuilder = offerBuilder.addAttributesBuilder().setName("diskspeed").setType(Value.Type.SCALAR);
        attrBuilder.getScalarBuilder().setValue(1234.5678);

        List<OfferRecommendation> recommendations = evaluator.evaluate(
                OfferRequirementTestUtils.getOfferRequirement(desiredResource),
                Arrays.asList(offerBuilder.build()));
        Assert.assertEquals(1, recommendations.size());

        // Validate LAUNCH Operation
        Operation launchOperation = recommendations.get(0).getOperation();
        Assert.assertEquals(Operation.Type.LAUNCH, launchOperation.getType());

        // Validate that TaskInfo has embedded the Attributes from the selected offer:
        TaskInfo launchTask = launchOperation.getLaunch().getTaskInfosList().get(0);
        Assert.assertEquals(
                Arrays.asList("rack:foo", "diskspeed:1234.568"),
                new SchedulerLabelReader(launchTask).getOfferAttributeStrings());
        Resource launchResource = launchTask.getResourcesList().get(0);
        Assert.assertEquals(resourceId, getFirstLabel(launchResource).getValue());
    }

    @Test
    public void testReserveLaunchExpectedScalar() throws Exception {
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
        Assert.assertEquals(TestConstants.ROLE, reserveResource.getRole());
        Assert.assertEquals(TestConstants.PRINCIPAL, reserveResource.getReservation().getPrincipal());
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
    public void testFailReserveLaunchExpectedScalar() throws Exception {
        String resourceId = UUID.randomUUID().toString();
        Resource desiredResource = ResourceTestUtils.getExpectedScalar("cpus", 2.0, resourceId);
        Resource offeredResource = ResourceTestUtils.getExpectedScalar("cpus", 1.0, resourceId);

        List<OfferRecommendation> recommendations = evaluator.evaluate(
                OfferRequirementTestUtils.getOfferRequirement(desiredResource),
                Arrays.asList(OfferTestUtils.getOffer(offeredResource)));
        Assert.assertEquals(0, recommendations.size());
    }

    @Test
    public void testUnreserveLaunchExpectedScalar() throws Exception {
        String resourceId = UUID.randomUUID().toString();
        Resource desiredResource = ResourceTestUtils.getExpectedScalar("cpus", 1.0, resourceId);
        Resource offeredResource = ResourceTestUtils.getExpectedScalar("cpus", 2.0, resourceId);

        List<OfferRecommendation> recommendations = evaluator.evaluate(
                OfferRequirementTestUtils.getOfferRequirement(desiredResource),
                Arrays.asList(OfferTestUtils.getOffer(offeredResource)));
        Assert.assertEquals(2, recommendations.size());

        // Validate UNRESERVE Operation
        Operation unreserveOperation = recommendations.get(1).getOperation();
        Resource unreserveResource =
            unreserveOperation
            .getUnreserve()
            .getResourcesList()
            .get(0);

        Assert.assertEquals(Operation.Type.UNRESERVE, unreserveOperation.getType());
        Assert.assertEquals(1.0, unreserveResource.getScalar().getValue(), 0.0);
        Assert.assertEquals(TestConstants.ROLE, unreserveResource.getRole());
        Assert.assertEquals(TestConstants.PRINCIPAL, unreserveResource.getReservation().getPrincipal());
        Assert.assertEquals(MesosResource.RESOURCE_ID_KEY, getFirstLabel(unreserveResource).getKey());
        Assert.assertEquals(resourceId, getFirstLabel(unreserveResource).getValue());

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
        Assert.assertEquals(1.0, launchResource.getScalar().getValue(), 0.0);
    }

    @Test
    public void testAvoidAgents() throws Exception {
        Resource desiredCpu = ResourceTestUtils.getDesiredCpu(1.0);
        Resource offeredCpu = ResourceTestUtils.getUnreservedScalar("cpus", 2.0);

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
        Resource offeredCpu = ResourceTestUtils.getUnreservedScalar("cpus", 2.0);

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
        Resource offeredResource = ResourceTestUtils.getUnreservedScalar("cpus", 3.0);
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
        Resource insufficientOffer = ResourceTestUtils.getUnreservedScalar("mem", 2.0);
        Resource sufficientOffer = ResourceTestUtils.getUnreservedScalar("cpus", 2.0);

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
                ResourceTestUtils.getUnreservedScalar("cpus", 3.0),
                ResourceTestUtils.getUnreservedScalar("disk", 500.0)));

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

        List<ResourceRequirement> resourceRequirements = offerRequirementProvider.getExistingOfferRequirement(
                PodInstanceRequirement.newBuilder(podInstance, Arrays.asList("node")).build())
                .getTaskRequirements().stream()
                .flatMap(taskRequirement -> taskRequirement.getResourceRequirements().stream())
                .collect(Collectors.toList());
        Assert.assertEquals(resourceRequirements.toString(), 2, resourceRequirements.size());
        Assert.assertTrue(resourceRequirements.toString(), resourceRequirements.get(1).getResourceId() != null);

        Offer expectedOffer = OfferTestUtils.getOffer(Arrays.asList(
                ResourceTestUtils.getExpectedScalar("cpus", 1.0, resourceRequirements.get(0).getResourceId().get()),
                ResourceTestUtils.getExpectedScalar("disk", 50.0, resourceRequirements.get(1).getResourceId().get())));
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
                ResourceTestUtils.getUnreservedScalar("cpus", 3.0),
                ResourceTestUtils.getUnreservedScalar("disk", 500.0)));

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
                ResourceTestUtils.getUnreservedScalar("cpus", 3.0),
                ResourceTestUtils.getUnreservedScalar("mem", 1024),
                ResourceTestUtils.getUnreservedScalar("disk", 500.0)));

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

    private void recordOperations(List<OfferRecommendation> recommendations) throws Exception {
        OperationRecorder operationRecorder = new PersistentLaunchRecorder(stateStore, serviceSpec);
        for (OfferRecommendation recommendation : recommendations) {
            operationRecorder.record(recommendation);
        }
    }
}
