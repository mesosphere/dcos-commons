package com.mesosphere.sdk.offer.evaluate;

import com.mesosphere.sdk.offer.OfferRecommendation;
import com.mesosphere.sdk.offer.taskdata.EnvUtils;
import com.mesosphere.sdk.offer.taskdata.TaskPackingUtils;
import com.mesosphere.sdk.scheduler.plan.PodInstanceRequirement;
import com.mesosphere.sdk.scheduler.plan.PodInstanceRequirementTestUtils;
import com.mesosphere.sdk.testutils.OfferRequirementTestUtils;
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
        Protos.Label resourceIdLabel = fulfilledPortResource.getReservation().getLabels().getLabels(0);
        Assert.assertEquals("resource_id", resourceIdLabel.getKey());

        Protos.CommandInfo command = TaskPackingUtils.unpack(taskInfo).getCommand();
        Map<String, String> envvars = EnvUtils.fromEnvironmentToMap(command.getEnvironment());
        Assert.assertEquals(String.valueOf(555), envvars.get(TestConstants.PORT_ENV_NAME));
    }

}
