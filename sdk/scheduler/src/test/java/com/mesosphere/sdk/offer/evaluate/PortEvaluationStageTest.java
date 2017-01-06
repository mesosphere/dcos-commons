package com.mesosphere.sdk.offer.evaluate;

import com.mesosphere.sdk.offer.*;
import com.mesosphere.sdk.testutils.OfferRequirementTestUtils;
import com.mesosphere.sdk.testutils.OfferTestUtils;
import com.mesosphere.sdk.testutils.ResourceTestUtils;
import com.mesosphere.sdk.testutils.TestConstants;
import org.apache.mesos.Protos;
import org.junit.Assert;
import org.junit.Test;

import java.util.UUID;

public class PortEvaluationStageTest {
    @Test
    public void testReserveDynamicPort() throws Exception {
        Protos.Resource desiredPorts = ResourceTestUtils.getDesiredRanges("ports", 0, 0)
                .toBuilder().clearRanges().build();
        Protos.Resource offeredPorts = ResourceTestUtils.getUnreservedPorts(10000, 10000);
        Protos.Offer offer = OfferTestUtils.getOffer(offeredPorts);

        OfferRequirement offerRequirement = OfferRequirementTestUtils.getOfferRequirement(desiredPorts);
        OfferRecommendationSlate offerRecommendationSlate = new OfferRecommendationSlate();

        PortEvaluationStage portEvaluationStage = new PortEvaluationStage(
                desiredPorts, TestConstants.TASK_NAME, "test-port", 0);
        portEvaluationStage.evaluate(new MesosResourcePool(offer), offerRequirement, offerRecommendationSlate);

        Assert.assertEquals(1, offerRecommendationSlate.getRecommendations().size());

        OfferRecommendation recommendation = offerRecommendationSlate.getRecommendations().get(0);
        Assert.assertEquals(Protos.Offer.Operation.Type.RESERVE, recommendation.getOperation().getType());

        Protos.Resource resource = recommendation.getOperation().getReserve().getResources(0);
        Assert.assertEquals(
                10000, resource.getRanges().getRange(0).getBegin(), resource.getRanges().getRange(0).getEnd());

        Protos.TaskInfo taskInfo = offerRequirement.getTaskRequirement(TestConstants.TASK_NAME).getTaskInfo();
        Protos.Environment.Variable variable = taskInfo.getCommand().getEnvironment().getVariables(0);
        Assert.assertEquals(variable.getName(), "PORT_test-port");
        Assert.assertEquals(variable.getValue(), "10000");
    }

    @Test
    public void testReserveKnownPort() throws Exception {
        Protos.Resource desiredPorts = ResourceTestUtils.getDesiredRanges("ports", 0, 0)
                .toBuilder().clearRanges().build();
        Protos.Resource offeredPorts = ResourceTestUtils.getUnreservedPorts(10000, 10000);
        Protos.Offer offer = OfferTestUtils.getOffer(offeredPorts);

        OfferRequirement offerRequirement = OfferRequirementTestUtils.getOfferRequirement(desiredPorts);
        OfferRecommendationSlate offerRecommendationSlate = new OfferRecommendationSlate();

        PortEvaluationStage portEvaluationStage = new PortEvaluationStage(
                desiredPorts, TestConstants.TASK_NAME, "test-port", 10000);
        portEvaluationStage.evaluate(new MesosResourcePool(offer), offerRequirement, offerRecommendationSlate);

        Assert.assertEquals(1, offerRecommendationSlate.getRecommendations().size());

        OfferRecommendation recommendation = offerRecommendationSlate.getRecommendations().get(0);
        Assert.assertEquals(Protos.Offer.Operation.Type.RESERVE, recommendation.getOperation().getType());

        Protos.Resource resource = recommendation.getOperation().getReserve().getResources(0);
        Assert.assertEquals(
                10000, resource.getRanges().getRange(0).getBegin(), resource.getRanges().getRange(0).getEnd());

        Protos.TaskInfo taskInfo = offerRequirement.getTaskRequirement(TestConstants.TASK_NAME).getTaskInfo();
        Protos.Environment.Variable variable = taskInfo.getCommand().getEnvironment().getVariables(0);
        Assert.assertEquals(variable.getName(), "PORT_test-port");
        Assert.assertEquals(variable.getValue(), "10000");
    }

    @Test
    public void testReserveKnownPortFails() throws Exception {
        Protos.Resource desiredPorts = ResourceTestUtils.getDesiredRanges("ports", 0, 0)
                .toBuilder().clearRanges().build();
        Protos.Resource offeredPorts = ResourceTestUtils.getUnreservedPorts(10000, 10000);
        Protos.Offer offer = OfferTestUtils.getOffer(offeredPorts);

        OfferRequirement offerRequirement = OfferRequirementTestUtils.getOfferRequirement(desiredPorts);
        OfferRecommendationSlate offerRecommendationSlate = new OfferRecommendationSlate();

        PortEvaluationStage portEvaluationStage = new PortEvaluationStage(
                desiredPorts, TestConstants.TASK_NAME, "test-port", 10001);
        try {
            portEvaluationStage.evaluate(new MesosResourcePool(offer), offerRequirement, offerRecommendationSlate);
        } catch (OfferEvaluationException e) {
            // Expected.
        }

        Assert.assertEquals(0, offerRecommendationSlate.getRecommendations().size());
    }

    @Test
    public void testGetClaimedDynamicPort() throws Exception {
        String resourceId = UUID.randomUUID().toString();
        Protos.Resource expectedPorts = ResourceTestUtils.getExpectedRanges("ports", 0, 0, resourceId)
                .toBuilder().clearRanges().build();
        Protos.Resource offeredPorts = ResourceTestUtils.getExpectedRanges("ports", 10000, 10000, resourceId);
        Protos.Offer offer = OfferTestUtils.getOffer(offeredPorts);

        MesosResourcePool mesosResourcePool = new MesosResourcePool(offer);
        OfferRequirement offerRequirement = OfferRequirementTestUtils.getOfferRequirement(expectedPorts);
        OfferRecommendationSlate offerRecommendationSlate = new OfferRecommendationSlate();

        Protos.TaskInfo.Builder builder = offerRequirement.getTaskRequirement(TestConstants.TASK_NAME)
                .getTaskInfo().toBuilder();
        builder.getCommandBuilder().getEnvironmentBuilder().addVariablesBuilder()
                .setName("PORT_test-port")
                .setValue("10000");
        offerRequirement.updateTaskRequirement(TestConstants.TASK_NAME, builder.build());

        PortEvaluationStage portEvaluationStage = new PortEvaluationStage(
                expectedPorts, TestConstants.TASK_NAME, "test-port", 0);
        portEvaluationStage.evaluate(mesosResourcePool, offerRequirement, offerRecommendationSlate);

        Assert.assertEquals(0, offerRecommendationSlate.getRecommendations().size());
        Assert.assertEquals(0, mesosResourcePool.getReservedPool().size());
    }
}
