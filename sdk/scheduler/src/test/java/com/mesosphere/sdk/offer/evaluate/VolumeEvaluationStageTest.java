package com.mesosphere.sdk.offer.evaluate;

import com.mesosphere.sdk.offer.*;
import com.mesosphere.sdk.testutils.OfferRequirementTestUtils;
import com.mesosphere.sdk.testutils.OfferTestUtils;
import com.mesosphere.sdk.testutils.ResourceTestUtils;
import com.mesosphere.sdk.testutils.TestConstants;
import org.apache.mesos.Protos;
import org.junit.Assert;
import org.junit.Test;

public class VolumeEvaluationStageTest {
    @Test
    public void testCreateSucceeds() throws Exception {
        Protos.Resource desiredResource = ResourceTestUtils.getDesiredMountVolume(1000);
        Protos.Resource offeredResource = ResourceTestUtils.getUnreservedMountVolume(2000);
        Protos.Offer offer = OfferTestUtils.getOffer(offeredResource);

        MesosResourcePool mesosResourcePool = new MesosResourcePool(offer);
        OfferRequirement offerRequirement = OfferRequirementTestUtils.getOfferRequirement(desiredResource);
        OfferRecommendationSlate offerRecommendationSlate = new OfferRecommendationSlate();

        VolumeEvaluationStage volumeEvaluationStage = new VolumeEvaluationStage(
                desiredResource, TestConstants.TASK_NAME);
        volumeEvaluationStage.evaluate(mesosResourcePool, offerRequirement, offerRecommendationSlate);

        Assert.assertEquals(2, offerRecommendationSlate.getRecommendations().size());

        OfferRecommendation reserveRecommendation = offerRecommendationSlate.getRecommendations().get(0);
        Assert.assertEquals(Protos.Offer.Operation.Type.RESERVE, reserveRecommendation.getOperation().getType());

        Protos.Resource resource = reserveRecommendation.getOperation().getReserve().getResources(0);
        Assert.assertEquals("disk", resource.getName());
        Assert.assertEquals(resource.getScalar(), offeredResource.getScalar());
        Protos.Label reservationLabel = resource.getReservation().getLabels().getLabels(0);
        Assert.assertEquals(reservationLabel.getKey(), "resource_id");
        Assert.assertNotEquals(reservationLabel.getValue(), "");

        OfferRecommendation createRecommendation = offerRecommendationSlate.getRecommendations().get(1);
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
        OfferRecommendationSlate offerRecommendationSlate = new OfferRecommendationSlate();

        VolumeEvaluationStage volumeEvaluationStage = new VolumeEvaluationStage(
                desiredResource, TestConstants.TASK_NAME);
        try {
            volumeEvaluationStage.evaluate(mesosResourcePool, offerRequirement, offerRecommendationSlate);
        } catch (OfferEvaluationException e) {
            // Expected.
        }

        Assert.assertEquals(0, offerRecommendationSlate.getRecommendations().size());
    }
}
