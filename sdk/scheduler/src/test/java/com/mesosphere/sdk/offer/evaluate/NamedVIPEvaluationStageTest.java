package com.mesosphere.sdk.offer.evaluate;

import com.mesosphere.sdk.offer.MesosResourcePool;
import com.mesosphere.sdk.offer.OfferRecommendationSlate;
import com.mesosphere.sdk.offer.OfferRequirement;
import com.mesosphere.sdk.testutils.OfferRequirementTestUtils;
import com.mesosphere.sdk.testutils.OfferTestUtils;
import com.mesosphere.sdk.testutils.ResourceTestUtils;
import com.mesosphere.sdk.testutils.TestConstants;
import org.apache.mesos.Protos;
import org.junit.Assert;
import org.junit.Test;

public class NamedVIPEvaluationStageTest {
    @Test
    public void testDiscoveryInfoPopulated() throws Exception {
        Protos.Resource desiredPorts = ResourceTestUtils.getDesiredRanges("ports", 0, 0)
                .toBuilder().clearRanges().build();
        Protos.Resource offeredPorts = ResourceTestUtils.getUnreservedPorts(10000, 10000);
        Protos.Offer offer = OfferTestUtils.getOffer(offeredPorts);

        OfferRequirement offerRequirement = OfferRequirementTestUtils.getOfferRequirement(desiredPorts);

        PortEvaluationStage portEvaluationStage = new NamedVIPEvaluationStage(
                desiredPorts, TestConstants.TASK_NAME, "test-port", 10000, "test-vip", 80);
        portEvaluationStage.evaluate(new MesosResourcePool(offer), offerRequirement, new OfferRecommendationSlate());

        Protos.DiscoveryInfo discoveryInfo = offerRequirement.getTaskRequirement(TestConstants.TASK_NAME)
                .getTaskInfo().getDiscovery();
        Protos.Port port = discoveryInfo.getPorts().getPorts(0);
        Assert.assertEquals(port.getNumber(), 10000);
        Assert.assertEquals(port.getProtocol(), "tcp");

        Protos.Label vipLabel = port.getLabels().getLabels(0);
        Assert.assertEquals(discoveryInfo.getName(), TestConstants.TASK_NAME);
        Assert.assertTrue(vipLabel.getKey().startsWith("VIP_"));
        Assert.assertEquals(vipLabel.getValue(), "test-vip:80");
    }
}
