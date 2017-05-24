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
import org.junit.Assert;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * Offer evaluation tests concerning ports.
 */
public class OfferEvaluatorPortsTest extends OfferEvaluatorTestBase {
    @Test
    public void testReserveTaskStaticPort() throws Exception {
        PodInstanceRequirement podInstanceRequirement = PodInstanceRequirementTestUtils.getPortsRequirement(555, 555);
        Protos.Resource offeredPorts = ResourceTestUtils.getUnreservedPorts(555, 555);

        List<OfferRecommendation> recommendations = evaluator.evaluate(
                podInstanceRequirement,
                Arrays.asList(OfferTestUtils.getOffer(offeredPorts)));

        Assert.assertEquals(2, recommendations.size());

        Protos.Offer.Operation launchOperation = recommendations.get(1).getOperation();
        Protos.TaskInfo taskInfo = launchOperation.getLaunch().getTaskInfos(0);
        Protos.Resource fulfilledPortResource = taskInfo.getResources(0);
        Protos.Label resourceIdLabel = fulfilledPortResource.getReservations(0).getLabels().getLabels(0);
        Assert.assertEquals("resource_id", resourceIdLabel.getKey());

        Protos.CommandInfo command = TaskPackingUtils.unpack(taskInfo).getCommand();
        Map<String, String> envvars = EnvUtils.fromEnvironmentToMap(command.getEnvironment());
        Assert.assertEquals(String.valueOf(555), envvars.get(TestConstants.PORT_ENV_NAME));
    }

    @Test
    public void testLaunchExpectedPort() throws Exception {
        // Launch for the first time.
        PodInstanceRequirement podInstanceRequirement = PodInstanceRequirementTestUtils.getPortsRequirement(555, 555);
        Protos.Resource offeredPorts = ResourceTestUtils.getUnreservedPorts(555, 555);
        List<OfferRecommendation> recommendations = evaluator.evaluate(
                podInstanceRequirement,
                Arrays.asList(OfferTestUtils.getOffer(offeredPorts)));

        Protos.Offer.Operation launchOperation = recommendations.get(1).getOperation();
        Protos.TaskInfo taskInfo = launchOperation.getLaunch().getTaskInfos(0);
        String resourceId = ResourceUtils.getResourceId(taskInfo.getResources(0));
        stateStore.storeTasks(Arrays.asList(taskInfo));


        // Launch on previously reserved resources
        Protos.Resource desiredResource = ResourceTestUtils.getExpectedRanges("ports", 555, 555, resourceId);

        recommendations = evaluator.evaluate(
                podInstanceRequirement,
                Arrays.asList(OfferTestUtils.getOffer(Arrays.asList(desiredResource))));
        Assert.assertEquals(1, recommendations.size());

        // Validate LAUNCH Operation
        launchOperation = recommendations.get(0).getOperation();
        Protos.Resource launchResource =
                launchOperation.getLaunch().getTaskInfosList().get(0).getResourcesList().get(0);

        Assert.assertEquals(Protos.Offer.Operation.Type.LAUNCH, launchOperation.getType());
        Assert.assertEquals(resourceId, getFirstLabel(launchResource).getValue());
    }

    @Test
    public void testReserveTaskDynamicPort() throws Exception {
        Protos.Resource offeredPorts = ResourceTestUtils.getUnreservedPorts(10000, 10000);
        PodInstanceRequirement podInstanceRequirement = PodInstanceRequirementTestUtils.getPortsRequirement(0, 0);

        List<OfferRecommendation> recommendations = evaluator.evaluate(
                podInstanceRequirement,
                Arrays.asList(OfferTestUtils.getOffer(offeredPorts)));

        Assert.assertEquals(2, recommendations.size());

        Protos.Offer.Operation launchOperation = recommendations.get(1).getOperation();
        Protos.TaskInfo taskInfo = launchOperation.getLaunch().getTaskInfos(0);
        Protos.Resource fulfilledPortResource = taskInfo.getResources(0);
        Protos.Label resourceIdLabel = fulfilledPortResource.getReservations(0).getLabels().getLabels(0);
        Assert.assertEquals("resource_id", resourceIdLabel.getKey());

        Protos.CommandInfo command = TaskPackingUtils.unpack(taskInfo).getCommand();
        Map<String, String> envvars = EnvUtils.fromEnvironmentToMap(command.getEnvironment());
        Assert.assertEquals(String.valueOf(10000), envvars.get(TestConstants.PORT_ENV_NAME));
    }
}
