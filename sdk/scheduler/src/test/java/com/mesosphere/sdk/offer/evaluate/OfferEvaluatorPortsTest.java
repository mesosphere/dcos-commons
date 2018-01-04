package com.mesosphere.sdk.offer.evaluate;

import com.mesosphere.sdk.offer.OfferRecommendation;
import com.mesosphere.sdk.offer.ResourceUtils;
import com.mesosphere.sdk.offer.taskdata.EnvUtils;
import com.mesosphere.sdk.offer.taskdata.TaskPackingUtils;
import com.mesosphere.sdk.scheduler.plan.PodInstanceRequirement;
import com.mesosphere.sdk.scheduler.plan.PodInstanceRequirementTestUtils;
import com.mesosphere.sdk.testutils.OfferTestUtils;
import com.mesosphere.sdk.testutils.ResourceTestUtils;
import com.mesosphere.sdk.testutils.TestConstants;
import org.apache.mesos.Protos;
import org.apache.mesos.Protos.DiscoveryInfo;
import org.apache.mesos.Protos.Label;
import org.apache.mesos.Protos.Port;
import org.apache.mesos.Protos.Resource;
import org.apache.mesos.Protos.TaskInfo;
import org.apache.mesos.Protos.Offer.Operation;
import org.junit.Assert;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Offer evaluation tests concerning ports.
 */
public class OfferEvaluatorPortsTest extends OfferEvaluatorTestBase {
    @Test
    public void testReserveStaticPort() throws Exception {
        PodInstanceRequirement podInstanceRequirement = PodInstanceRequirementTestUtils.getPortRequirement(555);
        Protos.Resource offeredPorts = ResourceTestUtils.getUnreservedPorts(555, 555);

        List<OfferRecommendation> recommendations =
                evaluator.evaluate(podInstanceRequirement, OfferTestUtils.getCompleteOffers(offeredPorts));

        Assert.assertEquals(5, recommendations.size());

        Protos.Offer.Operation launchOperation = recommendations.get(4).getOperation();
        Protos.TaskInfo taskInfo = launchOperation.getLaunchGroup().getTaskGroup().getTasks(0);
        Protos.Resource fulfilledPortResource = taskInfo.getResources(0);
        Assert.assertFalse(getResourceId(fulfilledPortResource).isEmpty());

        Protos.CommandInfo command = TaskPackingUtils.unpack(taskInfo).getCommand();
        Map<String, String> envvars = EnvUtils.toMap(command.getEnvironment());
        Assert.assertEquals(String.valueOf(555), envvars.get(TestConstants.PORT_ENV_NAME + "_555"));
    }

    @Test
    public void testReserveStaticPortCustomExecutor() throws Exception {
        useCustomExecutor();
        PodInstanceRequirement podInstanceRequirement = PodInstanceRequirementTestUtils.getPortRequirement(555);
        Protos.Resource offeredPorts = ResourceTestUtils.getUnreservedPorts(555, 555);

        List<OfferRecommendation> recommendations =
                evaluator.evaluate(podInstanceRequirement, OfferTestUtils.getOffers(offeredPorts));

        Assert.assertEquals(2, recommendations.size());

        Protos.Offer.Operation launchOperation = recommendations.get(1).getOperation();
        Protos.TaskInfo taskInfo = launchOperation.getLaunch().getTaskInfos(0);
        Protos.Resource fulfilledPortResource = taskInfo.getResources(0);
        Assert.assertFalse(getResourceId(fulfilledPortResource).isEmpty());

        Protos.CommandInfo command = TaskPackingUtils.unpack(taskInfo).getCommand();
        Map<String, String> envvars = EnvUtils.toMap(command.getEnvironment());
        Assert.assertEquals(String.valueOf(555), envvars.get(TestConstants.PORT_ENV_NAME + "_555"));
    }

    @Test
    public void testReserveStaticPortFailure() throws Exception {
        PodInstanceRequirement podInstanceRequirement = PodInstanceRequirementTestUtils.getPortRequirement(555);
        Protos.Resource offeredPorts = ResourceTestUtils.getUnreservedPorts(666, 666);

        List<OfferRecommendation> recommendations =
                evaluator.evaluate(podInstanceRequirement, OfferTestUtils.getCompleteOffers(offeredPorts));
        Assert.assertEquals(0, recommendations.size());
    }

    @Test
    public void testLaunchExpectedStaticPort() throws Exception {
        // Launch for the first time: get port 555
        PodInstanceRequirement podInstanceRequirement = PodInstanceRequirementTestUtils.getPortRequirement(555);
        Resource reserveResource = recordLaunchWithCompleteOfferedResources(
                podInstanceRequirement,
                ResourceTestUtils.getUnreservedPorts(555, 555)).get(0);
        String resourceId = getResourceId(reserveResource);
        Collection<Resource> expectedResources = getExpectedExecutorResources(
                stateStore.fetchTasks().iterator().next().getExecutor());
        expectedResources.add(ResourceTestUtils.getReservedPorts(555, 555, resourceId));

        // Launch on previously reserved resources
        List<OfferRecommendation> recommendations = evaluator.evaluate(
                podInstanceRequirement,
                Arrays.asList(OfferTestUtils.getOffer(expectedResources)));
        Assert.assertEquals(1, recommendations.size());

        // Validate LAUNCH Operation
        Protos.Offer.Operation launchOperation = recommendations.get(0).getOperation();
        Assert.assertEquals(Operation.Type.LAUNCH_GROUP, launchOperation.getType());

        Protos.Resource launchResource = launchOperation.getLaunchGroup().getTaskGroup().getTasks(0).getResources(0);
        Assert.assertEquals(resourceId, getResourceId(launchResource));
    }

    @Test
    public void testLaunchExpectedStaticPortCustomExecutor() throws Exception {
        useCustomExecutor();
        // Launch for the first time: get port 555
        PodInstanceRequirement podInstanceRequirement = PodInstanceRequirementTestUtils.getPortRequirement(555);
        Resource reserveResource = recordLaunchWithOfferedResources(
                podInstanceRequirement,
                ResourceTestUtils.getUnreservedPorts(555, 555)).get(0);
        String resourceId = getResourceId(reserveResource);

        // Launch on previously reserved resources
        List<OfferRecommendation> recommendations = evaluator.evaluate(
                podInstanceRequirement,
                OfferTestUtils.getOffers(ResourceTestUtils.getReservedPorts(555, 555, resourceId)));
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
        Resource reserveResource = recordLaunchWithCompleteOfferedResources(
                podInstanceRequirement, ResourceTestUtils.getUnreservedPorts(10000, 10000)).get(0);
        String resourceId = getResourceId(reserveResource);
        Collection<Resource> expectedResources = getExpectedExecutorResources(
                stateStore.fetchTasks().iterator().next().getExecutor());
        expectedResources.add(ResourceTestUtils.getReservedPorts(10000, 10000, resourceId));


        // Relaunch: detect (from envvar) and reuse previously reserved dynamic port 10000
        List<OfferRecommendation> recommendations = evaluator.evaluate(
                podInstanceRequirement,
                Arrays.asList(OfferTestUtils.getOffer(expectedResources)));
        Assert.assertEquals(1, recommendations.size());

        // Validate LAUNCH Operation
        Operation launchOperation = recommendations.get(0).getOperation();
        Assert.assertEquals(Operation.Type.LAUNCH_GROUP, launchOperation.getType());

        Resource launchResource = launchOperation.getLaunchGroup().getTaskGroup().getTasks(0).getResources(0);
        Assert.assertEquals(resourceId, getResourceId(launchResource));
    }


    @Test
    public void testLaunchExpectedDynamicPortCustomExecutor() throws Exception {
        useCustomExecutor();
        // Launch for the first time: get port 10000
        PodInstanceRequirement podInstanceRequirement = PodInstanceRequirementTestUtils.getPortRequirement(0);
        Resource reserveResource = recordLaunchWithOfferedResources(
                podInstanceRequirement, ResourceTestUtils.getUnreservedPorts(10000, 10000)).get(0);
        String resourceId = getResourceId(reserveResource);

        // Relaunch: detect (from envvar) and reuse previously reserved dynamic port 10000
        List<OfferRecommendation> recommendations = evaluator.evaluate(
                podInstanceRequirement,
                OfferTestUtils.getOffers(ResourceTestUtils.getReservedPorts(10000, 10000, resourceId)));
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

        List<OfferRecommendation> recommendations =
                evaluator.evaluate(podInstanceRequirement, OfferTestUtils.getCompleteOffers(offeredPorts));

        Assert.assertEquals(5, recommendations.size());

        Protos.Offer.Operation launchOperation = recommendations.get(4).getOperation();
        Protos.TaskInfo taskInfo = launchOperation.getLaunchGroup().getTaskGroup().getTasks(0);
        Protos.Resource fulfilledPortResource = taskInfo.getResources(0);
        Assert.assertFalse(getResourceId(fulfilledPortResource).isEmpty());

        Map<String, String> envvars = EnvUtils.toMap(
                TaskPackingUtils.unpack(taskInfo).getCommand().getEnvironment());
        Assert.assertEquals(envvars.toString(),
                String.valueOf(10000), envvars.get(TestConstants.PORT_ENV_NAME + "_0"));
    }

    @Test
    public void testReserveTaskDynamicPortCustomExecutor() throws Exception {
        useCustomExecutor();
        Protos.Resource offeredPorts = ResourceTestUtils.getUnreservedPorts(10000, 10000);
        PodInstanceRequirement podInstanceRequirement = PodInstanceRequirementTestUtils.getPortRequirement(0);

        List<OfferRecommendation> recommendations =
                evaluator.evaluate(podInstanceRequirement, OfferTestUtils.getOffers(offeredPorts));

        Assert.assertEquals(2, recommendations.size());

        Protos.Offer.Operation launchOperation = recommendations.get(1).getOperation();
        Protos.TaskInfo taskInfo = launchOperation.getLaunch().getTaskInfos(0);
        Protos.Resource fulfilledPortResource = taskInfo.getResources(0);
        Assert.assertFalse(getResourceId(fulfilledPortResource).isEmpty());

        Map<String, String> envvars = EnvUtils.toMap(
                TaskPackingUtils.unpack(taskInfo).getCommand().getEnvironment());
        Assert.assertEquals(envvars.toString(),
                String.valueOf(10000), envvars.get(TestConstants.PORT_ENV_NAME + "_0"));
    }

    @Test
    public void testUpdateStaticToStaticPort() throws Exception {
        // Launch for the first time: get port 555
        Resource reserveResource = recordLaunchWithCompleteOfferedResources(
                PodInstanceRequirementTestUtils.getPortRequirement(555),
                ResourceTestUtils.getUnreservedPorts(555, 555)).get(0);
        String resourceId = getResourceId(reserveResource);
        Collection<Resource> expectedResources = getExpectedExecutorResources(
                stateStore.fetchTasks().iterator().next().getExecutor());
        expectedResources.addAll(Arrays.asList(
                ResourceTestUtils.getReservedPorts(555, 555, resourceId),
                ResourceTestUtils.getUnreservedPorts(666, 666)));

        // Now lets move to port 666:
        List<OfferRecommendation> recommendations = evaluator.evaluate(
                PodInstanceRequirementTestUtils.getPortRequirement(666),
                Arrays.asList(OfferTestUtils.getOffer(expectedResources)));

        // UNRESERVE, RESERVE, LAUNCH
        Assert.assertEquals(recommendations.toString(), 3, recommendations.size());
        Assert.assertEquals(Operation.Type.UNRESERVE, recommendations.get(0).getOperation().getType());
        Assert.assertEquals(Operation.Type.RESERVE, recommendations.get(1).getOperation().getType());
        Assert.assertEquals(Operation.Type.LAUNCH_GROUP, recommendations.get(2).getOperation().getType());

        Operation launchOperation = recommendations.get(2).getOperation();
        TaskInfo taskInfo = launchOperation.getLaunchGroup().getTaskGroup().getTasks(0);

        Map<String, String> envvars = EnvUtils.toMap(
                TaskPackingUtils.unpack(taskInfo).getCommand().getEnvironment());
        Assert.assertEquals(String.valueOf(666), envvars.get(TestConstants.PORT_ENV_NAME + "_666"));
    }

    @Test
    public void testUpdateStaticToStaticPortCustomExecutor() throws Exception {
        useCustomExecutor();
        // Launch for the first time: get port 555
        Resource reserveResource = recordLaunchWithOfferedResources(
                PodInstanceRequirementTestUtils.getPortRequirement(555),
                ResourceTestUtils.getUnreservedPorts(555, 555)).get(0);
        String resourceId = getResourceId(reserveResource);

        // Now lets move to port 666:
        List<OfferRecommendation> recommendations = evaluator.evaluate(
                PodInstanceRequirementTestUtils.getPortRequirement(666),
                OfferTestUtils.getOffers(Arrays.asList(
                        ResourceTestUtils.getReservedPorts(555, 555, resourceId),
                        ResourceTestUtils.getUnreservedPorts(666, 666))));

        // UNRESERVE, RESERVE, LAUNCH
        Assert.assertEquals(recommendations.toString(), 3, recommendations.size());
        Assert.assertEquals(Operation.Type.UNRESERVE, recommendations.get(0).getOperation().getType());
        Assert.assertEquals(Operation.Type.RESERVE, recommendations.get(1).getOperation().getType());
        Assert.assertEquals(Operation.Type.LAUNCH, recommendations.get(2).getOperation().getType());

        Operation launchOperation = recommendations.get(2).getOperation();
        TaskInfo taskInfo = launchOperation.getLaunch().getTaskInfos(0);

        Map<String, String> envvars = EnvUtils.toMap(
                TaskPackingUtils.unpack(taskInfo).getCommand().getEnvironment());
        Assert.assertEquals(String.valueOf(666), envvars.get(TestConstants.PORT_ENV_NAME + "_666"));
    }

    @Test
    public void testUpdateDynamicToStaticPort() throws Exception {
        // Launch for the first time: get port 555 from dynamic port
        Resource reserveResource = recordLaunchWithCompleteOfferedResources(
                PodInstanceRequirementTestUtils.getPortRequirement(0),
                ResourceTestUtils.getUnreservedPorts(555, 555)).get(0);
        String resourceId = getResourceId(reserveResource);
        Collection<Resource> expectedResources = getExpectedExecutorResources(
                stateStore.fetchTasks().iterator().next().getExecutor());
        expectedResources.addAll(Arrays.asList(
                ResourceTestUtils.getReservedPorts(555, 555, resourceId),
                ResourceTestUtils.getUnreservedPorts(666, 666)));

        // Now lets move to port 666:
        List<OfferRecommendation> recommendations = evaluator.evaluate(
                PodInstanceRequirementTestUtils.getPortRequirement(666),
                Arrays.asList(OfferTestUtils.getOffer(expectedResources)));

        // RESERVE, UNRESERVE, LAUNCH
        Assert.assertEquals(recommendations.toString(), 3, recommendations.size());
        Assert.assertEquals(Operation.Type.UNRESERVE, recommendations.get(0).getOperation().getType());
        Assert.assertEquals(Operation.Type.RESERVE, recommendations.get(1).getOperation().getType());
        Assert.assertEquals(Operation.Type.LAUNCH_GROUP, recommendations.get(2).getOperation().getType());

        Operation launchOperation = recommendations.get(2).getOperation();
        TaskInfo taskInfo = launchOperation.getLaunchGroup().getTaskGroup().getTasks(0);

        Map<String, String> envvars = EnvUtils.toMap(
                TaskPackingUtils.unpack(taskInfo).getCommand().getEnvironment());
        Assert.assertEquals(String.valueOf(666), envvars.get(TestConstants.PORT_ENV_NAME + "_666"));
    }

    @Test
    public void testUpdateDynamicToStaticPortCustomExecutor() throws Exception {
        useCustomExecutor();
        // Launch for the first time: get port 555 from dynamic port
        Resource reserveResource = recordLaunchWithOfferedResources(
                PodInstanceRequirementTestUtils.getPortRequirement(0),
                ResourceTestUtils.getUnreservedPorts(555, 555)).get(0);
        String resourceId = getResourceId(reserveResource);

        // Now lets move to port 666:
        List<OfferRecommendation> recommendations = evaluator.evaluate(
                PodInstanceRequirementTestUtils.getPortRequirement(666),
                OfferTestUtils.getOffers(Arrays.asList(
                        ResourceTestUtils.getReservedPorts(555, 555, resourceId),
                        ResourceTestUtils.getUnreservedPorts(666, 666))));

        // RESERVE, UNRESERVE, LAUNCH
        Assert.assertEquals(recommendations.toString(), 3, recommendations.size());
        Assert.assertEquals(Operation.Type.UNRESERVE, recommendations.get(0).getOperation().getType());
        Assert.assertEquals(Operation.Type.RESERVE, recommendations.get(1).getOperation().getType());
        Assert.assertEquals(Operation.Type.LAUNCH, recommendations.get(2).getOperation().getType());

        Operation launchOperation = recommendations.get(2).getOperation();
        TaskInfo taskInfo = launchOperation.getLaunch().getTaskInfos(0);

        Map<String, String> envvars = EnvUtils.toMap(
                TaskPackingUtils.unpack(taskInfo).getCommand().getEnvironment());
        Assert.assertEquals(String.valueOf(666), envvars.get(TestConstants.PORT_ENV_NAME + "_666"));
    }

    @Test
    public void testLaunchExpectedMultiplePorts() throws Exception {
        // Launch for the first time: get ports 10000,10001
        PodInstanceRequirement podInstanceRequirement = PodInstanceRequirementTestUtils.getPortRequirement(10000, 10001);
        List<Resource> reserveResources = recordLaunchWithCompleteOfferedResources(
                podInstanceRequirement, ResourceTestUtils.getUnreservedPorts(10000, 10001));
        Assert.assertEquals(reserveResources.toString(), 5, reserveResources.size());
        String resourceId0 = getResourceId(reserveResources.get(0));
        String resourceId1 = getResourceId(reserveResources.get(1));
        Collection<Resource> expectedResources = getExpectedExecutorResources(
                stateStore.fetchTasks().iterator().next().getExecutor());
        expectedResources.addAll(Arrays.asList(
                ResourceTestUtils.getReservedPorts(10000, 10000, resourceId0),
                ResourceTestUtils.getReservedPorts(10001, 10001, resourceId1)));

        // Now try relaunch:
        List<OfferRecommendation> recommendations = evaluator.evaluate(
                PodInstanceRequirementTestUtils.getPortRequirement(10000, 10001),
                Arrays.asList(OfferTestUtils.getOffer(expectedResources)));
        Assert.assertEquals(1, recommendations.size());

        // Validate LAUNCH Operation
        Operation launchOperation = recommendations.get(0).getOperation();
        Assert.assertEquals(Operation.Type.LAUNCH_GROUP, launchOperation.getType());

        List<Resource> launchResources = launchOperation.getLaunchGroup().getTaskGroup().getTasks(0).getResourcesList();
        Assert.assertEquals(launchResources.toString(), 2, launchResources.size());

        Assert.assertEquals(resourceId0, getResourceId(launchResources.get(0)));
        Assert.assertEquals(resourceId1, getResourceId(launchResources.get(1)));
    }

    @Test
    public void testLaunchExpectedMultiplePortsCustomExecutor() throws Exception {
        useCustomExecutor();
        // Launch for the first time: get ports 10000,10001
        PodInstanceRequirement podInstanceRequirement = PodInstanceRequirementTestUtils.getPortRequirement(10000, 10001);
        List<Resource> reserveResources = recordLaunchWithOfferedResources(
                podInstanceRequirement, ResourceTestUtils.getUnreservedPorts(10000, 10001));
        Assert.assertEquals(reserveResources.toString(), 2, reserveResources.size());
        String resourceId0 = getResourceId(reserveResources.get(0));
        String resourceId1 = getResourceId(reserveResources.get(1));

        // Now try relaunch:
        List<OfferRecommendation> recommendations = evaluator.evaluate(
                PodInstanceRequirementTestUtils.getPortRequirement(10000, 10001),
                OfferTestUtils.getOffers(Arrays.asList(
                        ResourceTestUtils.getReservedPorts(10000, 10000, resourceId0),
                        ResourceTestUtils.getReservedPorts(10001, 10001, resourceId1))));
        Assert.assertEquals(1, recommendations.size());

        // Validate LAUNCH Operation
        Operation launchOperation = recommendations.get(0).getOperation();
        Assert.assertEquals(Operation.Type.LAUNCH, launchOperation.getType());

        List<Resource> launchResources = launchOperation.getLaunch().getTaskInfos(0).getResourcesList();
        Assert.assertEquals(launchResources.toString(), 2, launchResources.size());

        Assert.assertEquals(resourceId0, getResourceId(launchResources.get(0)));
        Assert.assertEquals(resourceId1, getResourceId(launchResources.get(1)));
    }

    @Test
    public void testReserveTaskMultipleDynamicPorts() throws Exception {
        String portenv0 = TestConstants.PORT_ENV_NAME + "_DYN_ZERO";
        String portenv1 = TestConstants.PORT_ENV_NAME + "_DYN_ONE";
        Map<String, Integer> ports = new HashMap<>();
        ports.put(portenv0, 0);
        ports.put(portenv1, 0);
        List<OfferRecommendation> recommendations = evaluator.evaluate(
                PodInstanceRequirementTestUtils.getPortRequirement(ports),
                OfferTestUtils.getCompleteOffers(ResourceTestUtils.getUnreservedPorts(10000, 10001)));

        Assert.assertEquals(recommendations.toString(), 6, recommendations.size());

        Operation reserveOperation = recommendations.get(0).getOperation();
        Resource fulfilledPortResource1 = reserveOperation.getReserve().getResources(0);
        Assert.assertEquals(10000, fulfilledPortResource1.getRanges().getRange(0).getBegin());
        Assert.assertEquals(10000, fulfilledPortResource1.getRanges().getRange(0).getEnd());

        reserveOperation = recommendations.get(1).getOperation();
        Resource fulfilledPortResource2 = reserveOperation.getReserve().getResources(0);
        Assert.assertEquals(10001, fulfilledPortResource2.getRanges().getRange(0).getBegin());
        Assert.assertEquals(10001, fulfilledPortResource2.getRanges().getRange(0).getEnd());

        Operation launchOperation = recommendations.get(5).getOperation();
        TaskInfo taskInfo = launchOperation.getLaunchGroup().getTaskGroup().getTasks(0);
        Assert.assertEquals(getResourceId(taskInfo.getResources(0)), getResourceId(fulfilledPortResource1));
        Assert.assertEquals(getResourceId(taskInfo.getResources(1)), getResourceId(fulfilledPortResource2));

        Map<String, String> envvars = EnvUtils.toMap(
                TaskPackingUtils.unpack(taskInfo).getCommand().getEnvironment());
        Assert.assertEquals(String.valueOf(10000), envvars.get(portenv0));
        Assert.assertEquals(String.valueOf(10001), envvars.get(portenv1));

        Assert.assertEquals(10000, taskInfo.getResources(0).getRanges().getRange(0).getBegin());
        Assert.assertEquals(10000, taskInfo.getResources(0).getRanges().getRange(0).getEnd());
        Assert.assertEquals(10001, taskInfo.getResources(1).getRanges().getRange(0).getBegin());
        Assert.assertEquals(10001, taskInfo.getResources(1).getRanges().getRange(0).getEnd());
    }

    @Test
    public void testReserveTaskMultipleDynamicPortsCustomExecutor() throws Exception {
        useCustomExecutor();
        String portenv0 = TestConstants.PORT_ENV_NAME + "_DYN_ZERO";
        String portenv1 = TestConstants.PORT_ENV_NAME + "_DYN_ONE";
        Map<String, Integer> ports = new HashMap<>();
        ports.put(portenv0, 0);
        ports.put(portenv1, 0);
        List<OfferRecommendation> recommendations = evaluator.evaluate(
                PodInstanceRequirementTestUtils.getPortRequirement(ports),
                OfferTestUtils.getOffers(ResourceTestUtils.getUnreservedPorts(10000, 10001)));

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

        Map<String, String> envvars = EnvUtils.toMap(
                TaskPackingUtils.unpack(taskInfo).getCommand().getEnvironment());
        Assert.assertEquals(String.valueOf(10000), envvars.get(portenv0));
        Assert.assertEquals(String.valueOf(10001), envvars.get(portenv1));

        Assert.assertEquals(10000, taskInfo.getResources(0).getRanges().getRange(0).getBegin());
        Assert.assertEquals(10000, taskInfo.getResources(0).getRanges().getRange(0).getEnd());
        Assert.assertEquals(10001, taskInfo.getResources(1).getRanges().getRange(0).getBegin());
        Assert.assertEquals(10001, taskInfo.getResources(1).getRanges().getRange(0).getEnd());
    }


    @SuppressWarnings("PMD.AvoidUsingHardCodedIP")
    @Test
    public void testReserveTaskNamedVIPPort() throws Exception {
        List<OfferRecommendation> recommendations = evaluator.evaluate(
                PodInstanceRequirementTestUtils.getVIPRequirement(80, 10000),
                OfferTestUtils.getCompleteOffers(ResourceTestUtils.getUnreservedPorts(10000, 10000)));

        Assert.assertEquals(5, recommendations.size());

        Operation launchOperation = recommendations.get(4).getOperation();
        TaskInfo taskInfo = launchOperation.getLaunchGroup().getTaskGroup().getTasks(0);
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

        Map<String, String> envvars = EnvUtils.toMap(
                TaskPackingUtils.unpack(taskInfo).getCommand().getEnvironment());
        Assert.assertEquals(String.valueOf(10000), envvars.get(TestConstants.PORT_ENV_NAME + "_VIP_10000"));
    }

    @SuppressWarnings("PMD.AvoidUsingHardCodedIP")
    @Test
    public void testReserveTaskDynamicVIPPort() throws Exception {
        List<OfferRecommendation> recommendations = evaluator.evaluate(
                PodInstanceRequirementTestUtils.getVIPRequirement(80, 0),
                OfferTestUtils.getCompleteOffers(ResourceTestUtils.getUnreservedPorts(10000, 10000)));

        Assert.assertEquals(5, recommendations.size());

        Operation launchOperation = recommendations.get(4).getOperation();
        TaskInfo taskInfo = launchOperation.getLaunchGroup().getTaskGroup().getTasks(0);
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

        Map<String, String> envvars = EnvUtils.toMap(
                TaskPackingUtils.unpack(taskInfo).getCommand().getEnvironment());
        Assert.assertEquals(String.valueOf(10000), envvars.get(TestConstants.PORT_ENV_NAME + "_VIP_0"));
    }

    private Collection<Resource> getExpectedExecutorResources(Protos.ExecutorInfo executorInfo) {
        String executorCpuId = executorInfo.getResourcesList().stream()
                .filter(r -> r.getName().equals("cpus"))
                .map(ResourceUtils::getResourceId)
                .filter(o -> o.isPresent())
                .map(o -> o.get())
                .findFirst()
                .get();
        String executorMemId = executorInfo.getResourcesList().stream()
                .filter(r -> r.getName().equals("mem"))
                .map(ResourceUtils::getResourceId)
                .filter(o -> o.isPresent())
                .map(o -> o.get())
                .findFirst()
                .get();
        String executorDiskId = executorInfo.getResourcesList().stream()
                .filter(r -> r.getName().equals("disk"))
                .map(ResourceUtils::getResourceId)
                .filter(o -> o.isPresent())
                .map(o -> o.get())
                .findFirst()
                .get();

        Resource expectedExecutorCpu = ResourceTestUtils.getReservedCpus(0.1, executorCpuId);
        Resource expectedExecutorMem = ResourceTestUtils.getReservedMem(32, executorMemId);
        Resource expectedExecutorDisk = ResourceTestUtils.getReservedDisk(256, executorDiskId);

        return new ArrayList<>(Arrays.asList(expectedExecutorCpu, expectedExecutorMem, expectedExecutorDisk));
    }

    @SuppressWarnings("PMD.AvoidUsingHardCodedIP")
    @Test
    public void testReserveTaskDynamicVIPPortCustomExecutor() throws Exception {
        useCustomExecutor();
        List<OfferRecommendation> recommendations = evaluator.evaluate(
                PodInstanceRequirementTestUtils.getVIPRequirement(80, 0),
                OfferTestUtils.getOffers(ResourceTestUtils.getUnreservedPorts(10000, 10000)));

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

        Map<String, String> envvars = EnvUtils.toMap(
                TaskPackingUtils.unpack(taskInfo).getCommand().getEnvironment());
        Assert.assertEquals(String.valueOf(10000), envvars.get(TestConstants.PORT_ENV_NAME + "_VIP_0"));
    }
}
