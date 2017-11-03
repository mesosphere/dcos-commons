package com.mesosphere.sdk.offer.evaluate;

import com.mesosphere.sdk.offer.*;
import com.mesosphere.sdk.scheduler.plan.PodInstanceRequirement;
import com.mesosphere.sdk.scheduler.plan.PodInstanceRequirementTestUtils;
import com.mesosphere.sdk.specification.PodInstance;
import com.mesosphere.sdk.specification.VolumeSpec;
import com.mesosphere.sdk.testutils.*;
import org.apache.mesos.Protos;
import org.junit.Assert;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class VolumeEvaluationStageTest extends DefaultCapabilitiesTestSuite {
    @Test
    public void testCreateSucceeds() throws Exception {
        Protos.Resource offeredResource = ResourceTestUtils.getUnreservedMountVolume(2000);
        Protos.Offer offer = OfferTestUtils.getCompleteOffer(offeredResource);

        MesosResourcePool mesosResourcePool = new MesosResourcePool(offer, Optional.of(Constants.ANY_ROLE));
        PodInstanceRequirement podInstanceRequirement =
                PodInstanceRequirementTestUtils.getMountVolumeRequirement(1.0, 1000);

        VolumeEvaluationStage volumeEvaluationStage = new VolumeEvaluationStage(
                getVolumeSpec(podInstanceRequirement.getPodInstance()),
                getTaskName(podInstanceRequirement.getPodInstance()),
                Optional.empty(),
                Optional.empty(),
                true);
        EvaluationOutcome outcome =
                volumeEvaluationStage.evaluate(
                        mesosResourcePool,
                        new PodInfoBuilder(
                                podInstanceRequirement,
                                TestConstants.SERVICE_NAME,
                                UUID.randomUUID(),
                                SchedulerConfigTestUtils.getTestSchedulerConfig(),
                                Collections.emptyList(),
                                TestConstants.FRAMEWORK_ID,
                                true,
                                Collections.emptyMap()));
        Assert.assertTrue(outcome.isPassing());

        List<OfferRecommendation> recommendations = new ArrayList<>(outcome.getOfferRecommendations());
        Assert.assertEquals(2, outcome.getOfferRecommendations().size());

        OfferRecommendation reserveRecommendation = recommendations.get(0);
        Assert.assertEquals(Protos.Offer.Operation.Type.RESERVE, reserveRecommendation.getOperation().getType());

        Protos.Resource resource = reserveRecommendation.getOperation().getReserve().getResources(0);
        Assert.assertEquals("disk", resource.getName());
        Assert.assertEquals(resource.getScalar(), offeredResource.getScalar());
        Protos.Resource.ReservationInfo reservationInfo = ResourceUtils.getReservation(resource).get();
        Protos.Label reservationLabel = reservationInfo.getLabels().getLabels(0);
        Assert.assertEquals(reservationLabel.getKey(), "resource_id");
        Assert.assertNotEquals(reservationLabel.getValue(), "");

        OfferRecommendation createRecommendation = recommendations.get(1);
        resource = createRecommendation.getOperation().getCreate().getVolumes(0);
        Assert.assertEquals(Protos.Offer.Operation.Type.CREATE, createRecommendation.getOperation().getType());
        Assert.assertEquals("disk", resource.getName());
        Assert.assertEquals(resource.getScalar(), offeredResource.getScalar());
        reservationInfo = ResourceUtils.getReservation(resource).get();
        reservationLabel = reservationInfo.getLabels().getLabels(0);
        Assert.assertEquals(reservationLabel.getKey(), "resource_id");
        Assert.assertNotEquals(reservationLabel.getValue(), "");
        Assert.assertNotEquals(resource.getDisk().getPersistence().getId(), "");
    }

    @Test
    public void testCreateFails() throws Exception {
        Protos.Resource offeredResource = ResourceTestUtils.getUnreservedMountVolume(1000);
        Protos.Offer offer = OfferTestUtils.getCompleteOffer(offeredResource);

        MesosResourcePool mesosResourcePool = new MesosResourcePool(offer, Optional.of(Constants.ANY_ROLE));
        PodInstanceRequirement podInstanceRequirement =
                PodInstanceRequirementTestUtils.getMountVolumeRequirement(1.0, 2000);

        VolumeEvaluationStage volumeEvaluationStage = new VolumeEvaluationStage(
                getVolumeSpec(podInstanceRequirement.getPodInstance()),
                getTaskName(podInstanceRequirement.getPodInstance()),
                Optional.empty(),
                Optional.empty(),
                true);
        EvaluationOutcome outcome =
                volumeEvaluationStage.evaluate(
                        mesosResourcePool,
                        new PodInfoBuilder(
                                podInstanceRequirement,
                                TestConstants.SERVICE_NAME,
                                UUID.randomUUID(),
                                SchedulerConfigTestUtils.getTestSchedulerConfig(),
                                Collections.emptyList(),
                                TestConstants.FRAMEWORK_ID,
                                true,
                                Collections.emptyMap()));
        Assert.assertFalse(outcome.isPassing());
        Assert.assertEquals(0, outcome.getOfferRecommendations().size());
    }

    private VolumeSpec getVolumeSpec(PodInstance podInstance) {
        return podInstance.getPod().getTasks().get(0).getResourceSet().getVolumes().stream().findFirst().get();
    }

    private String getTaskName(PodInstance podInstance) {
        return podInstance.getPod().getTasks().get(0).getName();
    }
}
