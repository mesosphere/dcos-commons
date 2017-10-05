package com.mesosphere.sdk.offer.evaluate;

import com.mesosphere.sdk.offer.OfferRecommendation;
import com.mesosphere.sdk.offer.evaluate.placement.PlacementRule;
import com.mesosphere.sdk.offer.evaluate.placement.PlacementUtils;
import com.mesosphere.sdk.scheduler.plan.DefaultPodInstance;
import com.mesosphere.sdk.scheduler.plan.PodInstanceRequirement;
import com.mesosphere.sdk.scheduler.plan.PodInstanceRequirementTestUtils;
import com.mesosphere.sdk.specification.DefaultPodSpec;
import com.mesosphere.sdk.specification.PodInstance;
import com.mesosphere.sdk.specification.PodSpec;
import com.mesosphere.sdk.testutils.OfferTestUtils;
import com.mesosphere.sdk.testutils.ResourceTestUtils;
import com.mesosphere.sdk.testutils.TestConstants;
import org.apache.mesos.Protos;
import org.junit.Assert;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Placement-related tests for {@link OfferEvaluator}.
 */
public class OfferEvaluatorPlacementTest extends OfferEvaluatorTestBase {
    @Test
    public void testAvoidAgents() throws Exception {
        Protos.Resource offeredCpu = ResourceTestUtils.getUnreservedCpus(2.0);

        // Don't launch
        PlacementRule placementRule = PlacementUtils.getAgentPlacementRule(
                Arrays.asList(TestConstants.AGENT_ID.getValue()),
                Collections.emptyList())
                .get();

        PodSpec podSpec = PodInstanceRequirementTestUtils.getCpuRequirement(1.0).getPodInstance().getPod();
        podSpec = DefaultPodSpec.newBuilder(podSpec)
                .placementRule(placementRule)
                .build();
        PodInstance podInstance = new DefaultPodInstance(podSpec, 0);
        PodInstanceRequirement podInstanceRequirement = PodInstanceRequirement.newBuilder(
                podInstance,
                Arrays.asList(TestConstants.TASK_NAME))
                .build();

        List<OfferRecommendation> recommendations = evaluator.evaluate(
                podInstanceRequirement,
                Arrays.asList(OfferTestUtils.getCompleteOffer(offeredCpu)));
        Assert.assertEquals(0, recommendations.size());

        // Launch
        placementRule = PlacementUtils.getAgentPlacementRule(
                Arrays.asList("some-random-agent"),
                Collections.emptyList()).get();

        podSpec = DefaultPodSpec.newBuilder(podSpec)
                .placementRule(placementRule)
                .build();
        podInstance = new DefaultPodInstance(podSpec, 0);
        podInstanceRequirement = PodInstanceRequirement.newBuilder(
                podInstance,
                Arrays.asList(TestConstants.TASK_NAME))
                .build();

        recommendations = evaluator.evaluate(
                podInstanceRequirement,
                Arrays.asList(OfferTestUtils.getCompleteOffer(offeredCpu)));

        Assert.assertEquals(5, recommendations.size());
    }

    @Test
    public void testAvoidAgentsCustomExecutor() throws Exception {
        useCustomExecutor();
        Protos.Resource offeredCpu = ResourceTestUtils.getUnreservedCpus(2.0);

        // Don't launch
        PlacementRule placementRule = PlacementUtils.getAgentPlacementRule(
                Arrays.asList(TestConstants.AGENT_ID.getValue()),
                Collections.emptyList())
                .get();

        PodSpec podSpec = PodInstanceRequirementTestUtils.getCpuRequirement(1.0).getPodInstance().getPod();
        podSpec = DefaultPodSpec.newBuilder(podSpec)
                .placementRule(placementRule)
                .build();
        PodInstance podInstance = new DefaultPodInstance(podSpec, 0);
        PodInstanceRequirement podInstanceRequirement = PodInstanceRequirement.newBuilder(
                podInstance,
                Arrays.asList(TestConstants.TASK_NAME))
                .build();

        List<OfferRecommendation> recommendations = evaluator.evaluate(
                podInstanceRequirement,
                Arrays.asList(OfferTestUtils.getOffer(offeredCpu)));
        Assert.assertEquals(0, recommendations.size());

        // Launch
        placementRule = PlacementUtils.getAgentPlacementRule(
                Arrays.asList("some-random-agent"),
                Collections.emptyList()).get();

        podSpec = DefaultPodSpec.newBuilder(podSpec)
                .placementRule(placementRule)
                .build();
        podInstance = new DefaultPodInstance(podSpec, 0);
        podInstanceRequirement = PodInstanceRequirement.newBuilder(
                podInstance,
                Arrays.asList(TestConstants.TASK_NAME))
                .build();

        recommendations = evaluator.evaluate(
                podInstanceRequirement,
                Arrays.asList(OfferTestUtils.getCompleteOffer(offeredCpu)));

        Assert.assertEquals(2, recommendations.size());
    }

    @Test
    public void testColocateAgents() throws Exception {
        Protos.Resource offeredCpu = ResourceTestUtils.getUnreservedCpus(2.0);

        // Don't launch
        PlacementRule placementRule = PlacementUtils.getAgentPlacementRule(
                Collections.emptyList(),
                Arrays.asList("some-random-agent")).get();

        PodSpec podSpec = PodInstanceRequirementTestUtils.getCpuRequirement(1.0).getPodInstance().getPod();
        podSpec = DefaultPodSpec.newBuilder(podSpec)
                .placementRule(placementRule)
                .build();
        PodInstance podInstance = new DefaultPodInstance(podSpec, 0);
        PodInstanceRequirement podInstanceRequirement = PodInstanceRequirement.newBuilder(
                podInstance,
                Arrays.asList(TestConstants.TASK_NAME))
                .build();

        List<OfferRecommendation> recommendations = evaluator.evaluate(
                podInstanceRequirement,
                Arrays.asList(OfferTestUtils.getCompleteOffer(offeredCpu)));
        Assert.assertEquals(0, recommendations.size());

        // Launch
        placementRule = PlacementUtils.getAgentPlacementRule(
                Collections.emptyList(),
                Arrays.asList(TestConstants.AGENT_ID.getValue())).get();

        podSpec = DefaultPodSpec.newBuilder(podSpec)
                .placementRule(placementRule)
                .build();
        podInstance = new DefaultPodInstance(podSpec, 0);
        podInstanceRequirement = PodInstanceRequirement.newBuilder(
                podInstance,
                Arrays.asList(TestConstants.TASK_NAME))
                .build();

        recommendations = evaluator.evaluate(
                podInstanceRequirement,
                Arrays.asList(OfferTestUtils.getCompleteOffer(offeredCpu)));

        Assert.assertEquals(5, recommendations.size());
    }

    @Test
    public void testColocateAgentsCustomExecutor() throws Exception {
        useCustomExecutor();
        Protos.Resource offeredCpu = ResourceTestUtils.getUnreservedCpus(2.0);

        // Don't launch
        PlacementRule placementRule = PlacementUtils.getAgentPlacementRule(
                Collections.emptyList(),
                Arrays.asList("some-random-agent")).get();

        PodSpec podSpec = PodInstanceRequirementTestUtils.getCpuRequirement(1.0).getPodInstance().getPod();
        podSpec = DefaultPodSpec.newBuilder(podSpec)
                .placementRule(placementRule)
                .build();
        PodInstance podInstance = new DefaultPodInstance(podSpec, 0);
        PodInstanceRequirement podInstanceRequirement = PodInstanceRequirement.newBuilder(
                podInstance,
                Arrays.asList(TestConstants.TASK_NAME))
                .build();

        List<OfferRecommendation> recommendations = evaluator.evaluate(
                podInstanceRequirement,
                Arrays.asList(OfferTestUtils.getOffer(offeredCpu)));
        Assert.assertEquals(0, recommendations.size());

        // Launch
        placementRule = PlacementUtils.getAgentPlacementRule(
                Collections.emptyList(),
                Arrays.asList(TestConstants.AGENT_ID.getValue())).get();

        podSpec = DefaultPodSpec.newBuilder(podSpec)
                .placementRule(placementRule)
                .build();
        podInstance = new DefaultPodInstance(podSpec, 0);
        podInstanceRequirement = PodInstanceRequirement.newBuilder(
                podInstance,
                Arrays.asList(TestConstants.TASK_NAME))
                .build();

        recommendations = evaluator.evaluate(
                podInstanceRequirement,
                Arrays.asList(OfferTestUtils.getCompleteOffer(offeredCpu)));

        Assert.assertEquals(2, recommendations.size());
    }
}
