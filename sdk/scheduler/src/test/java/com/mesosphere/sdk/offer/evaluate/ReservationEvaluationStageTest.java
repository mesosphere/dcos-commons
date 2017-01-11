package com.mesosphere.sdk.offer.evaluate;

import com.mesosphere.sdk.offer.MesosResourcePool;
import com.mesosphere.sdk.offer.OfferRecommendation;
import com.mesosphere.sdk.offer.OfferRecommendationSlate;
import com.mesosphere.sdk.testutils.OfferTestUtils;
import com.mesosphere.sdk.testutils.ResourceTestUtils;
import org.apache.mesos.Protos;
import org.junit.Assert;
import org.junit.Test;

import java.util.Arrays;
import java.util.UUID;

public class ReservationEvaluationStageTest {
    @Test
    public void testRemainingResourcesAreUnreserved() {
        String resourceId = UUID.randomUUID().toString();
        Protos.Resource expectedResource = ResourceTestUtils.getExpectedScalar("cpus", 1.0, resourceId);
        Protos.Offer offer = OfferTestUtils.getOffer(expectedResource);

        ReservationEvaluationStage reservationEvaluationStage = new ReservationEvaluationStage(
                Arrays.asList(resourceId));
        OfferRecommendationSlate offerRecommendationSlate = new OfferRecommendationSlate();

        reservationEvaluationStage.evaluate(new MesosResourcePool(offer), null, offerRecommendationSlate);

        Assert.assertEquals(1, offerRecommendationSlate.getRecommendations().size());

        OfferRecommendation recommendation = offerRecommendationSlate.getRecommendations().get(0);
        Assert.assertEquals(Protos.Offer.Operation.Type.UNRESERVE, recommendation.getOperation().getType());
        Assert.assertEquals(expectedResource, recommendation.getOperation().getUnreserve().getResources(0));
    }
}
