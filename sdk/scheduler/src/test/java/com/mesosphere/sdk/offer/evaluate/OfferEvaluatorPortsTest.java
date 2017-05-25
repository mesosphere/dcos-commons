package com.mesosphere.sdk.offer.evaluate;

import com.mesosphere.sdk.offer.OfferRecommendation;
import com.mesosphere.sdk.offer.taskdata.EnvUtils;
import com.mesosphere.sdk.offer.taskdata.TaskPackingUtils;
import com.mesosphere.sdk.scheduler.plan.PodInstanceRequirement;
import com.mesosphere.sdk.scheduler.plan.PodInstanceRequirementTestUtils;
import com.mesosphere.sdk.testutils.OfferTestUtils;
import com.mesosphere.sdk.testutils.ResourceTestUtils;
import com.mesosphere.sdk.testutils.TestConstants;
import org.apache.mesos.Protos;
import org.apache.mesos.Protos.CommandInfo;
import org.apache.mesos.Protos.DiscoveryInfo;
import org.apache.mesos.Protos.Label;
import org.apache.mesos.Protos.Port;
import org.apache.mesos.Protos.Resource;
import org.apache.mesos.Protos.TaskInfo;
import org.apache.mesos.Protos.Offer.Operation;
import org.junit.Assert;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Offer evaluation tests concerning ports.
 */
public class OfferEvaluatorPortsTest extends OfferEvaluatorTestBase {
    @Test
    public void testReserveTaskStaticPort() throws Exception {
        PodInstanceRequirement podInstanceRequirement = PodInstanceRequirementTestUtils.getPortRequirement(555);
        Protos.Resource offeredPorts = ResourceTestUtils.getUnreservedPorts(555, 555);

        List<OfferRecommendation> recommendations = evaluator.evaluate(
                podInstanceRequirement,
                Arrays.asList(OfferTestUtils.getOffer(offeredPorts)));

        Assert.assertEquals(2, recommendations.size());

        Protos.Offer.Operation launchOperation = recommendations.get(1).getOperation();
        Protos.TaskInfo taskInfo = launchOperation.getLaunch().getTaskInfos(0);
        Protos.Resource fulfilledPortResource = taskInfo.getResources(0);
        Assert.assertFalse(getResourceId(fulfilledPortResource).isEmpty());

        Protos.CommandInfo command = TaskPackingUtils.unpack(taskInfo).getCommand();
        Map<String, String> envvars = EnvUtils.fromEnvironmentToMap(command.getEnvironment());
        Assert.assertEquals(String.valueOf(555), envvars.get(TestConstants.PORT_ENV_NAME + "_555"));
    }

    @Test
    public void testLaunchExpectedStaticPort() throws Exception {
        // Launch for the first time: get port 555
        PodInstanceRequirement podInstanceRequirement = PodInstanceRequirementTestUtils.getPortRequirement(555);
        Resource reserveResource = recordLaunchWithOfferedResources(
                podInstanceRequirement,
                ResourceTestUtils.getUnreservedPorts(555, 555)).get(0);
        String resourceId = getResourceId(reserveResource);

        // Launch on previously reserved resources
        List<OfferRecommendation> recommendations = evaluator.evaluate(
                podInstanceRequirement,
                Arrays.asList(OfferTestUtils.getOffer(
                        ResourceTestUtils.getExpectedRanges("ports", 555, 555, resourceId))));
        Assert.assertEquals(1, recommendations.size());

        // Validate LAUNCH Operation
        Protos.Offer.Operation launchOperation = recommendations.get(0).getOperation();
        Assert.assertEquals(Protos.Offer.Operation.Type.LAUNCH, launchOperation.getType());

        Protos.Resource launchResource = launchOperation.getLaunch().getTaskInfos(0).getResources(0);
        Assert.assertEquals(resourceId, getResourceId(launchResource));
    }

    @Test
    public void testLaunchExpectedDynamicPort() throws Exception {
        // Launch for the first time: get port 10000
        PodInstanceRequirement podInstanceRequirement = PodInstanceRequirementTestUtils.getPortRequirement(0);
        Resource reserveResource = recordLaunchWithOfferedResources(
                podInstanceRequirement, ResourceTestUtils.getUnreservedPorts(10000, 10000)).get(0);
        String resourceId = getResourceId(reserveResource);

        // Relaunch: detect (from envvar) and reuse previously reserved dynamic port 10000
        List<OfferRecommendation> recommendations = evaluator.evaluate(
                podInstanceRequirement,
                Arrays.asList(OfferTestUtils.getOffer(
                        ResourceTestUtils.getExpectedRanges("ports", 10000, 10000, resourceId))));
        Assert.assertEquals(1, recommendations.size());

        // Validate LAUNCH Operation
        Operation launchOperation = recommendations.get(0).getOperation();
        Assert.assertEquals(Operation.Type.LAUNCH, launchOperation.getType());

        Resource launchResource = launchOperation.getLaunch().getTaskInfos(0).getResources(0);
        Assert.assertEquals(resourceId, getResourceId(launchResource));
    }

    @Test
    public void testReserveTaskDynamicPort() throws Exception {
        Protos.Resource offeredPorts = ResourceTestUtils.getUnreservedPorts(10000, 10000);
        PodInstanceRequirement podInstanceRequirement = PodInstanceRequirementTestUtils.getPortRequirement(0);

        List<OfferRecommendation> recommendations = evaluator.evaluate(
                podInstanceRequirement,
                Arrays.asList(OfferTestUtils.getOffer(offeredPorts)));

        Assert.assertEquals(2, recommendations.size());

        Protos.Offer.Operation launchOperation = recommendations.get(1).getOperation();
        Protos.TaskInfo taskInfo = launchOperation.getLaunch().getTaskInfos(0);
        Protos.Resource fulfilledPortResource = taskInfo.getResources(0);
        Assert.assertFalse(getResourceId(fulfilledPortResource).isEmpty());

        Map<String, String> envvars = EnvUtils.fromEnvironmentToMap(
                TaskPackingUtils.unpack(taskInfo).getCommand().getEnvironment());
        Assert.assertEquals(envvars.toString(),
                String.valueOf(10000), envvars.get(TestConstants.PORT_ENV_NAME + "_0"));
    }
/*
    @Test
    public void testUpdateStaticToStaticPort() throws Exception {
        // Launch for the first time: get port 555
        Resource reserveResource = recordLaunchWithOfferedResources(
                PodInstanceRequirementTestUtils.getPortRequirement(555),
                ResourceTestUtils.getUnreservedPorts(555, 555)).get(0);
        String resourceId = getResourceId(reserveResource);

        // Now lets move to port 666:
        List<OfferRecommendation> recommendations = evaluator.evaluate(
                PodInstanceRequirementTestUtils.getPortRequirement(666),
                Arrays.asList(OfferTestUtils.getOffer(Arrays.asList(
                        ResourceTestUtils.getExpectedRanges("ports", 555, 555, resourceId),
                        ResourceTestUtils.getUnreservedPorts(666, 666)))));

        // UNRESERVE, RESERVE, LAUNCH
        Assert.assertEquals(recommendations.toString(), 3, recommendations.size());
        Assert.assertEquals(Operation.Type.RESERVE, recommendations.get(0).getOperation().getType());
        Assert.assertEquals(Operation.Type.LAUNCH, recommendations.get(1).getOperation().getType());
        Assert.assertEquals(Operation.Type.UNRESERVE, recommendations.get(2).getOperation().getType());

        Operation launchOperation = recommendations.get(1).getOperation();
        TaskInfo taskInfo = launchOperation.getLaunch().getTaskInfos(0);
        Resource fulfilledPortResource = taskInfo.getResources(0);
        Assert.assertEquals(resourceId, getResourceId(fulfilledPortResource));

        Map<String, String> envvars = EnvUtils.fromEnvironmentToMap(
                TaskPackingUtils.unpack(taskInfo).getCommand().getEnvironment());
        Assert.assertEquals(envvars.toString(), 1, envvars.size());
        Assert.assertEquals(String.valueOf(666), envvars.get(TestConstants.PORT_ENV_NAME + "_666"));
    }

    @Test
    public void testUpdateDynamicToStaticPort() throws Exception {
        String resourceId = UUID.randomUUID().toString();
        Resource offeredReservedResource = ResourceTestUtils.getExpectedRanges("ports", 555, 555, resourceId);
        Resource offeredUnreservedResource = ResourceTestUtils.getUnreservedPorts(666, 666);

        List<OfferRecommendation> recommendations = evaluator.evaluate(
                PodInstanceRequirementTestUtils.getPortRequirement(0),
                Arrays.asList(OfferTestUtils.getOffer(Arrays.asList(
                        offeredReservedResource, offeredUnreservedResource))));

        // RESERVE, UNRESERVE, LAUNCH
        Assert.assertEquals(recommendations.toString(), 3, recommendations.size());
        Assert.assertEquals(Operation.Type.RESERVE, recommendations.get(0).getOperation().getType());
        Assert.assertEquals(Operation.Type.LAUNCH, recommendations.get(1).getOperation().getType());
        Assert.assertEquals(Operation.Type.UNRESERVE, recommendations.get(2).getOperation().getType());

        Operation launchOperation = recommendations.get(1).getOperation();
        TaskInfo taskInfo = launchOperation.getLaunch().getTaskInfos(0);
        Resource fulfilledPortResource = taskInfo.getResources(0);
        Assert.assertEquals(resourceId, getResourceId(fulfilledPortResource));

        Map<String, String> envvars = EnvUtils.fromEnvironmentToMap(
                TaskPackingUtils.unpack(taskInfo).getCommand().getEnvironment());
        Assert.assertEquals(envvars.toString(), 1, envvars.size());
        Assert.assertEquals(String.valueOf(666), envvars.get(TestConstants.PORT_ENV_NAME + "_666"));
    }

    @Test
    public void testLaunchExpectedMultiplePorts() throws Exception {
        String resourceId = UUID.randomUUID().toString();
        Resource offeredResource = ResourceTestUtils.getExpectedRanges("ports", 10000, 10001, resourceId);

        List<OfferRecommendation> recommendations = evaluator.evaluate(
                PodInstanceRequirementTestUtils.getPortsRequirement(Arrays.asList(10000, 10001)),
                Arrays.asList(OfferTestUtils.getOffer(offeredResource)));
        Assert.assertEquals(1, recommendations.size());

        // Validate LAUNCH Operation
        Operation launchOperation = recommendations.get(0).getOperation();
        Assert.assertEquals(Operation.Type.LAUNCH, launchOperation.getType());

        Resource launchResource = launchOperation.getLaunch().getTaskInfos(0).getResources(0);
        Assert.assertEquals(resourceId, getResourceId(launchResource));
    }

    @Test
    public void testReserveTaskMultipleDynamicPorts() throws Exception {
        List<OfferRecommendation> recommendations = evaluator.evaluate(
                PodInstanceRequirementTestUtils.getPortsRequirement(Arrays.asList(0, 0)),
                Arrays.asList(OfferTestUtils.getOffer(ResourceTestUtils.getUnreservedPorts(10000, 10001))));

        // TODO(nickbp): we now produce two separate ports RESERVE operations instead of one combined operation.
        //               does mesos allow this?
        Assert.assertEquals(recommendations.toString(), 3, recommendations.size());

        Operation reserveOperation = recommendations.get(0).getOperation();
        Resource fulfilledPortResource1 = reserveOperation.getReserve().getResources(0);
        Assert.assertEquals(10000, fulfilledPortResource1.getRanges().getRange(0).getBegin());
        Assert.assertEquals(10000, fulfilledPortResource1.getRanges().getRange(0).getEnd());

        reserveOperation = recommendations.get(1).getOperation();
        Resource fulfilledPortResource2 = reserveOperation.getReserve().getResources(0);
        Assert.assertEquals(10001, fulfilledPortResource2.getRanges().getRange(0).getBegin());
        Assert.assertEquals(10001, fulfilledPortResource2.getRanges().getRange(0).getEnd());

        Operation launchOperation = recommendations.get(2).getOperation();
        TaskInfo taskInfo = launchOperation.getLaunch().getTaskInfos(0);
        Assert.assertEquals(getResourceId(taskInfo.getResources(0)), getResourceId(fulfilledPortResource1));
        Assert.assertEquals(getResourceId(taskInfo.getResources(1)), getResourceId(fulfilledPortResource2));

        Map<String, String> envvars = EnvUtils.fromEnvironmentToMap(
                TaskPackingUtils.unpack(taskInfo).getCommand().getEnvironment());
        Assert.assertEquals(envvars.toString(), 2, envvars.size());
        Assert.assertEquals(String.valueOf(10000), envvars.get(TestConstants.PORT_ENV_NAME + "_10000"));
        Assert.assertEquals(String.valueOf(10001), envvars.get(TestConstants.PORT_ENV_NAME + "_10001"));

        Assert.assertEquals(10000, taskInfo.getResources(0).getRanges().getRange(0).getBegin());
        Assert.assertEquals(10000, taskInfo.getResources(0).getRanges().getRange(0).getEnd());
        Assert.assertEquals(10001, taskInfo.getResources(1).getRanges().getRange(0).getBegin());
        Assert.assertEquals(10001, taskInfo.getResources(1).getRanges().getRange(0).getEnd());
    }
    */

    @SuppressWarnings("PMD.AvoidUsingHardCodedIP")
    @Test
    public void testReserveTaskNamedVIPPort() throws Exception {
        List<OfferRecommendation> recommendations = evaluator.evaluate(
                PodInstanceRequirementTestUtils.getVIPRequirement(80, 10000),
                Arrays.asList(OfferTestUtils.getOffer(ResourceTestUtils.getUnreservedPorts(10000, 10000))));

        Assert.assertEquals(2, recommendations.size());

        Operation launchOperation = recommendations.get(1).getOperation();
        TaskInfo taskInfo = launchOperation.getLaunch().getTaskInfos(0);
        Resource fulfilledPortResource = taskInfo.getResources(0);
        Assert.assertEquals(10000, fulfilledPortResource.getRanges().getRange(0).getBegin());
        Assert.assertFalse(getResourceId(fulfilledPortResource).isEmpty());

        DiscoveryInfo discoveryInfo = taskInfo.getDiscovery();
        Assert.assertEquals(discoveryInfo.getName(), taskInfo.getName());
        Assert.assertEquals(discoveryInfo.getVisibility(), DiscoveryInfo.Visibility.CLUSTER);

        Port discoveryPort = discoveryInfo.getPorts().getPorts(0);
        Assert.assertEquals(discoveryPort.getProtocol(), "tcp");
        Assert.assertEquals(discoveryPort.getVisibility(), DiscoveryInfo.Visibility.EXTERNAL);
        Assert.assertEquals(discoveryPort.getNumber(), 10000);
        Label vipLabel = discoveryPort.getLabels().getLabels(0);
        Assert.assertTrue(vipLabel.getKey().startsWith("VIP_"));
        Assert.assertEquals(vipLabel.getValue(), TestConstants.VIP_NAME + "-10000:80");

        Map<String, String> envvars = EnvUtils.fromEnvironmentToMap(
                TaskPackingUtils.unpack(taskInfo).getCommand().getEnvironment());
        Assert.assertEquals(String.valueOf(10000), envvars.get(TestConstants.PORT_ENV_NAME + "_VIP_10000"));
    }

    @SuppressWarnings("PMD.AvoidUsingHardCodedIP")
    @Test
    public void testReserveTaskDynamicVIPPort() throws Exception {
        List<OfferRecommendation> recommendations = evaluator.evaluate(
                PodInstanceRequirementTestUtils.getVIPRequirement(80, 0),
                Arrays.asList(OfferTestUtils.getOffer(ResourceTestUtils.getUnreservedPorts(10000, 10000))));

        Assert.assertEquals(2, recommendations.size());

        Operation launchOperation = recommendations.get(1).getOperation();
        TaskInfo taskInfo = launchOperation.getLaunch().getTaskInfos(0);
        Resource fulfilledPortResource = taskInfo.getResources(0);
        Assert.assertEquals(10000, fulfilledPortResource.getRanges().getRange(0).getBegin());
        Assert.assertFalse(getResourceId(fulfilledPortResource).isEmpty());

        DiscoveryInfo discoveryInfo = taskInfo.getDiscovery();
        Assert.assertEquals(discoveryInfo.getName(), taskInfo.getName());
        Assert.assertEquals(discoveryInfo.getVisibility(), DiscoveryInfo.Visibility.CLUSTER);

        Port discoveryPort = discoveryInfo.getPorts().getPorts(0);
        Assert.assertEquals(discoveryPort.getProtocol(), "tcp");
        Assert.assertEquals(discoveryPort.getVisibility(), DiscoveryInfo.Visibility.EXTERNAL);
        Assert.assertEquals(discoveryPort.getNumber(), 10000);
        Label vipLabel = discoveryPort.getLabels().getLabels(0);
        Assert.assertTrue(vipLabel.getKey().startsWith("VIP_"));
        Assert.assertEquals(vipLabel.getValue(), TestConstants.VIP_NAME + "-0:80");

        Map<String, String> envvars = EnvUtils.fromEnvironmentToMap(
                TaskPackingUtils.unpack(taskInfo).getCommand().getEnvironment());
        Assert.assertEquals(String.valueOf(10000), envvars.get(TestConstants.PORT_ENV_NAME + "_VIP_0"));
    }
}
