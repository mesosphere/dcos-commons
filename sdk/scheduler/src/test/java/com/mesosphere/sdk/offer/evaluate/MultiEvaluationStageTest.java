package com.mesosphere.sdk.offer.evaluate;

import com.mesosphere.sdk.offer.InvalidRequirementException;
import com.mesosphere.sdk.offer.MesosResourcePool;
import com.mesosphere.sdk.offer.OfferRecommendation;
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
import java.util.Optional;

public class MultiEvaluationStageTest {
    /*

    @Test
    public void testPortRecommendationsAreAggregated() throws InvalidRequirementException {
        List<Protos.Resource> desiredPorts = Arrays.asList(
                ResourceTestUtils.getDesiredRanges("ports", 10000, 10000),
                ResourceTestUtils.getDesiredRanges("ports", 11000, 11000));
        Protos.Offer offer = OfferTestUtils.getOffer(ResourceTestUtils.getUnreservedPorts(10000, 11000));

        Collection<OfferEvaluationStage> evaluationStages = new ArrayList<>();
        for (int i = 0; i < desiredPorts.size(); ++i) {
            Protos.Resource desiredPort = desiredPorts.get(i);
            Protos.Value.Range desiredValue = desiredPort.getRanges().getRange(0);
            Optional<String> envKey = (i % 2 == 0) ? Optional.empty() : Optional.of(TestConstants.PORT_ENV_NAME + i);
            evaluationStages.add(new PortEvaluationStage(
                    desiredPort,
                    TestConstants.TASK_NAME,
                    "test-port" + i,
                    (int) desiredValue.getBegin(),
                    envKey));
        }

        MultiEvaluationStage multiEvaluationStage = new MultiEvaluationStage(evaluationStages);
        PodInfoBuilder podInfoBuilder =
                new PodInfoBuilder(OfferRequirementTestUtils.getOfferRequirement(desiredPorts, false));
        EvaluationOutcome outcome = multiEvaluationStage.evaluate(new MesosResourcePool(offer), podInfoBuilder);
        Assert.assertTrue(outcome.isPassing());

        Assert.assertEquals(outcome.getOfferRecommendations().toString(), 1, outcome.getOfferRecommendations().size());

        OfferRecommendation recommendation = outcome.getOfferRecommendations().iterator().next();
        Assert.assertEquals(Protos.Offer.Operation.Type.RESERVE, recommendation.getOperation().getType());

        Protos.Resource resource = recommendation.getOperation().getReserve().getResources(0);
        Assert.assertEquals(
                10000, resource.getRanges().getRange(0).getBegin(), resource.getRanges().getRange(0).getEnd());
        Assert.assertEquals(
                11000, resource.getRanges().getRange(1).getBegin(), resource.getRanges().getRange(1).getEnd());

        Protos.TaskInfo.Builder taskBuilder = podInfoBuilder.getTaskBuilder(TestConstants.TASK_NAME);
        Protos.Environment.Variable variable = taskBuilder.getCommand().getEnvironment().getVariables(0);
        Assert.assertEquals("PORT_TEST_PORT0", variable.getName()); // default value based on port name
        Assert.assertEquals("10000", variable.getValue());
        variable = taskBuilder.getCommand().getEnvironment().getVariables(1);
        Assert.assertEquals("TEST_PORT_NAME1", variable.getName()); // custom value provided
        Assert.assertEquals("11000", variable.getValue());
    }
    */
}
