package com.mesosphere.sdk.offer.evaluate;

import com.mesosphere.sdk.offer.InvalidRequirementException;
import com.mesosphere.sdk.offer.MesosResourcePool;
import com.mesosphere.sdk.offer.OfferRecommendation;
import com.mesosphere.sdk.offer.OfferRequirement;
import com.mesosphere.sdk.testutils.OfferRequirementTestUtils;
import com.mesosphere.sdk.testutils.OfferTestUtils;
import com.mesosphere.sdk.testutils.ResourceTestUtils;
import com.mesosphere.sdk.testutils.TestConstants;
import org.apache.mesos.Protos;
import org.junit.Assert;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

public class PortsEvaluationStageTest {

    @Test
    public void testPortRecommendationsAreAggregated() throws InvalidRequirementException {
        List<Protos.Resource> desiredPorts = Arrays.asList(
                ResourceTestUtils.getDesiredRanges("ports", 10000, 10000),
                ResourceTestUtils.getDesiredRanges("ports", 11000, 11000));
        Protos.Resource offeredPorts = ResourceTestUtils.getUnreservedPorts(10000, 11000);
        Protos.Offer offer = OfferTestUtils.getOffer(offeredPorts);

        OfferRequirement offerRequirement = OfferRequirementTestUtils.getOfferRequirement(desiredPorts, false);
        PodInfoBuilder podInfoBuilder = new PodInfoBuilder(offerRequirement);

        Collection<OfferEvaluationStage> evaluationStages = new ArrayList<>();
        for (int i = 0; i < desiredPorts.size(); ++i) {
            evaluationStages.add(new PortEvaluationStage(
                    desiredPorts.get(i),
                    TestConstants.TASK_NAME,
                    "test-port" + i,
                    (int) desiredPorts.get(i).getRanges().getRange(0).getBegin()));
        }

        PortsEvaluationStage portEvaluationStage = new PortsEvaluationStage(evaluationStages);
        EvaluationOutcome outcome = portEvaluationStage.evaluate(new MesosResourcePool(offer), podInfoBuilder);
        Assert.assertTrue(outcome.isPassing());

        Assert.assertEquals(1, outcome.getOfferRecommendations().size());

        OfferRecommendation recommendation = outcome.getOfferRecommendations().iterator().next();
        Assert.assertEquals(Protos.Offer.Operation.Type.RESERVE, recommendation.getOperation().getType());

        Protos.Resource resource = recommendation.getOperation().getReserve().getResources(0);
        Assert.assertEquals(
                10000, resource.getRanges().getRange(0).getBegin(), resource.getRanges().getRange(0).getEnd());
        Assert.assertEquals(
                11000, resource.getRanges().getRange(1).getBegin(), resource.getRanges().getRange(1).getEnd());

        Protos.TaskInfo.Builder taskBuilder = podInfoBuilder.getTaskBuilder(TestConstants.TASK_NAME);
        Protos.Environment.Variable variable = taskBuilder.getCommand().getEnvironment().getVariables(0);
        Assert.assertEquals(variable.getName(), "TEST_PORT0");
        Assert.assertEquals(variable.getValue(), "10000");
        variable = taskBuilder.getCommand().getEnvironment().getVariables(1);
        Assert.assertEquals(variable.getName(), "TEST_PORT1");
        Assert.assertEquals(variable.getValue(), "11000");
    }
}
