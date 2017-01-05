package com.mesosphere.sdk.offer.evaluate;

import com.mesosphere.sdk.offer.MesosResourcePool;
import com.mesosphere.sdk.offer.OfferRecommendationSlate;
import com.mesosphere.sdk.offer.OfferRequirement;
import com.mesosphere.sdk.offer.constrain.AgentRule;
import com.mesosphere.sdk.offer.constrain.PlacementRule;
import com.mesosphere.sdk.testutils.OfferRequirementTestUtils;
import com.mesosphere.sdk.testutils.OfferTestUtils;
import com.mesosphere.sdk.testutils.ResourceTestUtils;
import org.apache.mesos.Protos;
import org.junit.Assert;
import org.junit.Test;

import java.util.Collections;

public class PlacementRuleEvaluationStageTest {
    @Test
    public void testOfferPassesPlacementRule() throws Exception {
        String agent = "test-agent";
        Protos.Resource desired = ResourceTestUtils.getDesiredCpu(1.0);
        Protos.Resource offered = ResourceTestUtils.getUnreservedCpu(1.0);
        PlacementRule rule = AgentRule.require(agent);
        Protos.Offer offer = offerWithAgent(agent, offered);

        MesosResourcePool mesosResourcePool = new MesosResourcePool(offer);
        OfferRequirement offerRequirement = OfferRequirementTestUtils.getOfferRequirement(desired, rule);

        PlacementRuleEvaluationStage placementRuleEvaluationStage = new PlacementRuleEvaluationStage(
                Collections.emptyList());
        placementRuleEvaluationStage.evaluate(mesosResourcePool, offerRequirement, new OfferRecommendationSlate());

        Assert.assertEquals(1, mesosResourcePool.getUnreservedMergedPool().size());
        Assert.assertTrue(mesosResourcePool.getUnreservedMergedPool().get("cpus").getScalar().getValue() == 1.0);
    }

    @Test
    public void testOfferFailsPlacementRule() throws Exception {
        String agent = "test-agent";
        Protos.Resource desired = ResourceTestUtils.getDesiredCpu(1.0);
        Protos.Resource offered = ResourceTestUtils.getUnreservedCpu(1.0);
        PlacementRule rule = AgentRule.require(agent);
        Protos.Offer offer = offerWithAgent("other-agent", offered);

        MesosResourcePool mesosResourcePool = new MesosResourcePool(offer);
        OfferRequirement offerRequirement = OfferRequirementTestUtils.getOfferRequirement(desired, rule);

        PlacementRuleEvaluationStage placementRuleEvaluationStage = new PlacementRuleEvaluationStage(
                Collections.emptyList());
        boolean failed = false;
        try {
            placementRuleEvaluationStage.evaluate(mesosResourcePool, offerRequirement, new OfferRecommendationSlate());
        } catch (OfferEvaluationException e) {
            failed = true;
        }

        Assert.assertTrue(failed);
        Assert.assertEquals(0, mesosResourcePool.getUnreservedMergedPool().size());
    }

    private static Protos.Offer offerWithAgent(String agentId, Protos.Resource resource) {
        Protos.Offer.Builder o = OfferTestUtils.getOffer(resource).toBuilder();
        o.getSlaveIdBuilder().setValue(agentId);

        return o.build();
    }
}
