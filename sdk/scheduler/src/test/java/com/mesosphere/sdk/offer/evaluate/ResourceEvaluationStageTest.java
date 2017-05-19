package com.mesosphere.sdk.offer.evaluate;

import com.mesosphere.sdk.offer.*;
import com.mesosphere.sdk.testutils.OfferRequirementTestUtils;
import com.mesosphere.sdk.testutils.OfferTestUtils;
import com.mesosphere.sdk.testutils.ResourceTestUtils;
import com.mesosphere.sdk.testutils.TestConstants;
import org.apache.mesos.Protos;
import org.junit.Assert;
import org.junit.Test;

import java.util.Arrays;

public class ResourceEvaluationStageTest {
    @Test
    public void testReserveResource() throws Exception {
        Protos.Resource desiredResource = ResourceTestUtils.getDesiredCpu(1.0);
        Protos.Resource offeredResource = ResourceTestUtils.getUnreservedScalar("cpus", 2.0);
        Protos.Offer offer = OfferTestUtils.getOffer(offeredResource);

        MesosResourcePool mesosResourcePool = new MesosResourcePool(offer);
        OfferRequirement offerRequirement = OfferRequirementTestUtils.getOfferRequirement(desiredResource);

        ResourceEvaluationStage resourceEvaluationStage =
                new ResourceEvaluationStage(desiredResource, TestConstants.TASK_NAME);
        EvaluationOutcome outcome =
                resourceEvaluationStage.evaluate(mesosResourcePool, new PodInfoBuilder(offerRequirement));
        Assert.assertTrue(outcome.isPassing());

        Protos.Value remaining = mesosResourcePool.getUnreservedMergedPool().get("cpus");
        Assert.assertTrue(1.0 == remaining.getScalar().getValue());

        OfferRecommendation recommendation = outcome.getOfferRecommendations().iterator().next();
        Assert.assertEquals(Protos.Offer.Operation.Type.RESERVE, recommendation.getOperation().getType());

        Protos.Resource resource = recommendation.getOperation().getReserve().getResources(0);
        Assert.assertEquals("cpus", resource.getName());
        Assert.assertEquals(resource.getScalar(), desiredResource.getScalar());
        Protos.Label reservationLabel = resource.getReservation().getLabels().getLabels(0);
        Assert.assertEquals(reservationLabel.getKey(), "resource_id");
        Assert.assertTrue(reservationLabel.getValue() != "");
    }

    @Test
    public void testIncreaseResource() throws Exception {
        Protos.Resource expectedResource = ResourceTestUtils.getExpectedCpu(2.0);
        Protos.Resource reservedResource = ResourceTestUtils.getExpectedCpu(1.0);
        Protos.Resource offeredResource = ResourceTestUtils.getUnreservedCpu(1.0);
        Protos.Offer offer = OfferTestUtils.getOffer(Arrays.asList(reservedResource, offeredResource));

        MesosResourcePool mesosResourcePool = new MesosResourcePool(offer);
        OfferRequirement offerRequirement = OfferRequirementTestUtils.getOfferRequirement(expectedResource);

        ResourceEvaluationStage resourceEvaluationStage =
                new ResourceEvaluationStage(expectedResource, TestConstants.TASK_NAME);
        EvaluationOutcome outcome =
                resourceEvaluationStage.evaluate(mesosResourcePool, new PodInfoBuilder(offerRequirement));
        Assert.assertTrue(outcome.toString(), outcome.isPassing());

        Assert.assertEquals(0, mesosResourcePool.getReservedPool().size());
        Assert.assertEquals(1, outcome.getOfferRecommendations().size());

        OfferRecommendation recommendation = outcome.getOfferRecommendations().iterator().next();
        Assert.assertEquals(Protos.Offer.Operation.Type.RESERVE, recommendation.getOperation().getType());

        Protos.Resource resource = recommendation.getOperation().getReserve().getResources(0);
        Assert.assertEquals("cpus", resource.getName());
        Assert.assertEquals(resource.getScalar(), offeredResource.getScalar());
        Protos.Label reservationLabel = resource.getReservation().getLabels().getLabels(0);
        Assert.assertEquals(reservationLabel.getKey(), "resource_id");
        Assert.assertTrue(reservationLabel.getValue() != "");
    }
    @Test
    public void testCannotReserveResource() throws Exception {
        Protos.Resource desiredResource = ResourceTestUtils.getDesiredCpu(1.0);
        Protos.Resource offeredResource = ResourceTestUtils.getUnreservedScalar("cpus", 0.5);
        Protos.Offer offer = OfferTestUtils.getOffer(offeredResource);

        MesosResourcePool mesosResourcePool = new MesosResourcePool(offer);
        OfferRequirement offerRequirement = OfferRequirementTestUtils.getOfferRequirement(desiredResource);

        ResourceEvaluationStage resourceEvaluationStage = new ResourceEvaluationStage(
                desiredResource, TestConstants.TASK_NAME);
        EvaluationOutcome outcome =
                resourceEvaluationStage.evaluate(mesosResourcePool, new PodInfoBuilder(offerRequirement));
        Assert.assertFalse(outcome.isPassing());
        Assert.assertEquals(0, outcome.getOfferRecommendations().size());
    }
}
