package com.mesosphere.sdk.offer.evaluate;

import com.mesosphere.sdk.offer.MesosResourcePool;
import com.mesosphere.sdk.offer.TaskUtils;
import com.mesosphere.sdk.offer.evaluate.placement.AgentRule;
import com.mesosphere.sdk.offer.evaluate.placement.PlacementRule;
import com.mesosphere.sdk.scheduler.plan.DefaultPodInstance;
import com.mesosphere.sdk.scheduler.plan.PodInstanceRequirement;
import com.mesosphere.sdk.scheduler.plan.PodInstanceRequirementTestUtils;
import com.mesosphere.sdk.specification.DefaultPodSpec;
import com.mesosphere.sdk.specification.PodInstance;
import com.mesosphere.sdk.specification.PodSpec;
import com.mesosphere.sdk.testutils.OfferRequirementTestUtils;
import com.mesosphere.sdk.testutils.OfferTestUtils;
import com.mesosphere.sdk.testutils.ResourceTestUtils;
import com.mesosphere.sdk.testutils.TestConstants;
import org.apache.mesos.Protos;
import org.junit.Assert;
import org.junit.Test;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class PlacementRuleEvaluationStageTest {
    @Test
    public void testOfferPassesPlacementRule() throws Exception {
        String agent = "test-agent";
        Protos.Resource offered = ResourceTestUtils.getUnreservedCpu(1.0);
        PlacementRule rule = AgentRule.require(agent);
        Protos.Offer offer = offerWithAgent(agent, offered);

        MesosResourcePool mesosResourcePool = new MesosResourcePool(offer);

        PodSpec podSpec = PodInstanceRequirementTestUtils.getCpuRequirement(1.0).getPodInstance().getPod();
        DefaultPodSpec.newBuilder(podSpec)
                .placementRule(rule);
        PodInstance podInstance = new DefaultPodInstance(podSpec, 0);
        List<String> taskNames = TaskUtils.getTaskNames(podInstance);
        PodInstanceRequirement podInstanceRequirement = PodInstanceRequirement
                .newBuilder(podInstance, taskNames)
                .build();

        PlacementRuleEvaluationStage placementRuleEvaluationStage = new PlacementRuleEvaluationStage(
                Collections.emptyList(), rule);
        EvaluationOutcome outcome = placementRuleEvaluationStage.evaluate(
                mesosResourcePool,
                new PodInfoBuilder(
                        podInstanceRequirement,
                        TestConstants.SERVICE_NAME,
                        UUID.randomUUID(),
                        OfferRequirementTestUtils.getTestSchedulerFlags(),
                        Collections.emptyList(),
                        Optional.empty()));
        Assert.assertTrue(outcome.isPassing());

        Assert.assertEquals(1, mesosResourcePool.getUnreservedMergedPool().size());
        Assert.assertTrue(mesosResourcePool.getUnreservedMergedPool().get("cpus").getScalar().getValue() == 1.0);
    }

    @Test
    public void testOfferFailsPlacementRule() throws Exception {
        String agent = "test-agent";
        Protos.Resource offered = ResourceTestUtils.getUnreservedCpu(1.0);
        PlacementRule rule = AgentRule.require(agent);
        Protos.Offer offer = offerWithAgent("other-agent", offered);

        MesosResourcePool mesosResourcePool = new MesosResourcePool(offer);
        PodSpec podSpec = PodInstanceRequirementTestUtils.getCpuRequirement(1.0).getPodInstance().getPod();
        DefaultPodSpec.newBuilder(podSpec)
                .placementRule(rule);
        PodInstance podInstance = new DefaultPodInstance(podSpec, 0);
        List<String> taskNames = TaskUtils.getTaskNames(podInstance);
        PodInstanceRequirement podInstanceRequirement = PodInstanceRequirement
                .newBuilder(podInstance, taskNames)
                .build();

        PlacementRuleEvaluationStage placementRuleEvaluationStage =
                new PlacementRuleEvaluationStage(Collections.emptyList(), rule);
        EvaluationOutcome outcome = placementRuleEvaluationStage.evaluate(
                mesosResourcePool,
                new PodInfoBuilder(
                        podInstanceRequirement,
                        TestConstants.SERVICE_NAME,
                        UUID.randomUUID(),
                        OfferRequirementTestUtils.getTestSchedulerFlags(),
                        Collections.emptyList(),
                        Optional.empty()));

        Assert.assertFalse(outcome.isPassing());
        Assert.assertEquals(1, mesosResourcePool.getUnreservedMergedPool().size());
        Assert.assertTrue(mesosResourcePool.getUnreservedMergedPool().get("cpus").getScalar().getValue() == 1.0);
    }

    private static Protos.Offer offerWithAgent(String agentId, Protos.Resource resource) {
        Protos.Offer.Builder o = OfferTestUtils.getOffer(resource).toBuilder();
        o.getSlaveIdBuilder().setValue(agentId);

        return o.build();
    }
}
