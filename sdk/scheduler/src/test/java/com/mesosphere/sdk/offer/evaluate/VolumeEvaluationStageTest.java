package com.mesosphere.sdk.offer.evaluate;

import com.mesosphere.sdk.offer.*;
import com.mesosphere.sdk.testutils.OfferRequirementTestUtils;
import com.mesosphere.sdk.testutils.OfferTestUtils;
import com.mesosphere.sdk.testutils.ResourceTestUtils;
import com.mesosphere.sdk.testutils.TestConstants;
import org.apache.mesos.Protos;
import org.junit.Assert;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

public class VolumeEvaluationStageTest {
    @Test
    public void testCreateSucceeds() throws Exception {
        Protos.Resource desiredResource = ResourceTestUtils.getDesiredMountVolume(1000);
        Protos.Resource offeredResource = ResourceTestUtils.getUnreservedMountVolume(2000);
        Protos.Offer offer = OfferTestUtils.getOffer(offeredResource);

        MesosResourcePool mesosResourcePool = new MesosResourcePool(offer);
        OfferRequirement offerRequirement = OfferRequirementTestUtils.getOfferRequirement(desiredResource);

        VolumeEvaluationStage volumeEvaluationStage = new VolumeEvaluationStage(
                desiredResource, TestConstants.TASK_NAME);
        EvaluationOutcome outcome =
                volumeEvaluationStage.evaluate(mesosResourcePool, new PodInfoBuilder(offerRequirement));
        Assert.assertTrue(outcome.isPassing());

        List<OfferRecommendation> recommendations = new ArrayList<>(outcome.getOfferRecommendations());
        Assert.assertEquals(2, outcome.getOfferRecommendations().size());

        OfferRecommendation reserveRecommendation = recommendations.get(0);
        Assert.assertEquals(Protos.Offer.Operation.Type.RESERVE, reserveRecommendation.getOperation().getType());

        Protos.Resource resource = reserveRecommendation.getOperation().getReserve().getResources(0);
        Assert.assertEquals("disk", resource.getName());
        Assert.assertEquals(resource.getScalar(), offeredResource.getScalar());
        Protos.Label reservationLabel = resource.getReservation().getLabels().getLabels(0);
        Assert.assertEquals(reservationLabel.getKey(), "resource_id");
        Assert.assertNotEquals(reservationLabel.getValue(), "");

        OfferRecommendation createRecommendation = recommendations.get(1);
        resource = createRecommendation.getOperation().getCreate().getVolumes(0);
        Assert.assertEquals(Protos.Offer.Operation.Type.CREATE, createRecommendation.getOperation().getType());
        Assert.assertEquals("disk", resource.getName());
        Assert.assertEquals(resource.getScalar(), offeredResource.getScalar());
        reservationLabel = resource.getReservation().getLabels().getLabels(0);
        Assert.assertEquals(reservationLabel.getKey(), "resource_id");
        Assert.assertNotEquals(reservationLabel.getValue(), "");
        Assert.assertNotEquals(resource.getDisk().getPersistence().getId(), "");
    }

    @Test
    public void testCreateFails() throws Exception {
        Protos.Resource desiredResource = ResourceTestUtils.getDesiredMountVolume(2000);
        Protos.Resource offeredResource = ResourceTestUtils.getUnreservedMountVolume(1000);
        Protos.Offer offer = OfferTestUtils.getOffer(offeredResource);

        MesosResourcePool mesosResourcePool = new MesosResourcePool(offer);
        OfferRequirement offerRequirement = OfferRequirementTestUtils.getOfferRequirement(desiredResource);

        VolumeEvaluationStage volumeEvaluationStage = new VolumeEvaluationStage(
                desiredResource, TestConstants.TASK_NAME);
        EvaluationOutcome outcome =
                volumeEvaluationStage.evaluate(mesosResourcePool, new PodInfoBuilder(offerRequirement));
        Assert.assertFalse(outcome.isPassing());
        Assert.assertEquals(0, outcome.getOfferRecommendations().size());
    }
}
