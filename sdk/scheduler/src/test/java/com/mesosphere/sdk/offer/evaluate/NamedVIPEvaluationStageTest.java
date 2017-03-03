package com.mesosphere.sdk.offer.evaluate;

import com.mesosphere.sdk.offer.InvalidRequirementException;
import com.mesosphere.sdk.offer.MesosResourcePool;
import com.mesosphere.sdk.offer.OfferRequirement;
import com.mesosphere.sdk.offer.ResourceUtils;
import com.mesosphere.sdk.testutils.OfferRequirementTestUtils;
import com.mesosphere.sdk.testutils.OfferTestUtils;
import com.mesosphere.sdk.testutils.ResourceTestUtils;
import com.mesosphere.sdk.testutils.TestConstants;
import org.apache.mesos.Protos;
import org.apache.mesos.Protos.DiscoveryInfo;
import org.junit.Assert;
import org.junit.Test;

import java.util.UUID;

public class NamedVIPEvaluationStageTest {
    @Test
    public void testDiscoveryInfoPopulated() throws Exception {
        Protos.Resource desiredPorts = ResourceTestUtils.getDesiredRanges("ports", 0, 0);
        Protos.Resource offeredPorts = ResourceTestUtils.getUnreservedPorts(10000, 10000);
        Protos.Offer offer = OfferTestUtils.getOffer(offeredPorts);

        OfferRequirement offerRequirement = OfferRequirementTestUtils.getOfferRequirement(desiredPorts);
        PodInfoBuilder podInfoBuilder = new PodInfoBuilder(offerRequirement);

        PortEvaluationStage portEvaluationStage = new NamedVIPEvaluationStage(
                desiredPorts,
                TestConstants.TASK_NAME,
                "test-port",
                10000,
                "sctp",
                DiscoveryInfo.Visibility.CLUSTER,
                "test-vip",
                80);
        EvaluationOutcome outcome = portEvaluationStage.evaluate(new MesosResourcePool(offer), podInfoBuilder);
        Assert.assertTrue(outcome.isPassing());

        Protos.DiscoveryInfo discoveryInfo = podInfoBuilder.getTaskBuilder(TestConstants.TASK_NAME).getDiscovery();
        Assert.assertEquals(DiscoveryInfo.Visibility.CLUSTER, discoveryInfo.getVisibility());

        Protos.Port port = discoveryInfo.getPorts().getPorts(0);
        Assert.assertEquals(port.getNumber(), 10000);
        Assert.assertEquals(port.getProtocol(), "sctp");

        Protos.Label vipLabel = port.getLabels().getLabels(0);
        Assert.assertEquals(discoveryInfo.getName(), TestConstants.TASK_NAME);
        Assert.assertTrue(vipLabel.getKey().startsWith("VIP_"));
        Assert.assertEquals(vipLabel.getValue(), "test-vip:80");
    }

    @Test
    public void testVIPIsReused() throws InvalidRequirementException {
        String resourceId = UUID.randomUUID().toString();
        Protos.Resource expectedPorts = ResourceUtils.setLabel(
                ResourceTestUtils.getExpectedRanges("ports", 10000, 10000, resourceId),
                TestConstants.HAS_VIP_LABEL,
                "test-vip:80");
        Protos.Resource offeredResource = ResourceTestUtils.getExpectedRanges("ports", 10000, 10000, resourceId);
        Protos.Offer offer = OfferTestUtils.getOffer(offeredResource);

        OfferRequirement offerRequirement = OfferRequirementTestUtils.getOfferRequirement(expectedPorts);
        PodInfoBuilder podInfoBuilder = new PodInfoBuilder(offerRequirement);

        PortEvaluationStage portEvaluationStage = new NamedVIPEvaluationStage(
                expectedPorts,
                TestConstants.TASK_NAME,
                "test-port",
                10000,
                "sctp",
                DiscoveryInfo.Visibility.CLUSTER,
                "test-vip",
                80);

        EvaluationOutcome outcome = portEvaluationStage.evaluate(new MesosResourcePool(offer), podInfoBuilder);
        Assert.assertTrue(outcome.isPassing());

        Protos.DiscoveryInfo discoveryInfo = podInfoBuilder.getTaskBuilder(TestConstants.TASK_NAME).getDiscovery();
        Assert.assertEquals(1, discoveryInfo.getPorts().getPortsList().size());
        Assert.assertEquals(1, discoveryInfo.getPorts().getPorts(0).getLabels().getLabelsList().size());

        String portVIPLabel = discoveryInfo.getPorts().getPorts(0).getLabels().getLabels(0).getKey();
        String taskVIPLabel = offerRequirement.getTaskRequirements().iterator().next()
                .getTaskInfo().getDiscovery().getPorts().getPorts(0).getLabels().getLabels(0).getKey();
        Assert.assertEquals(portVIPLabel, taskVIPLabel);
    }
}
