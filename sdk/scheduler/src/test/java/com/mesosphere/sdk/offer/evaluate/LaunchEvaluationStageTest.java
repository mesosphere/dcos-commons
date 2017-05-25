package com.mesosphere.sdk.offer.evaluate;

import com.mesosphere.sdk.offer.MesosResourcePool;
import com.mesosphere.sdk.offer.ResourceUtils;
import com.mesosphere.sdk.scheduler.plan.PodInstanceRequirement;
import com.mesosphere.sdk.scheduler.plan.PodInstanceRequirementTestUtils;
import com.mesosphere.sdk.specification.GoalState;
import com.mesosphere.sdk.testutils.OfferRequirementTestUtils;
import com.mesosphere.sdk.testutils.OfferTestUtils;
import com.mesosphere.sdk.testutils.ResourceTestUtils;
import com.mesosphere.sdk.testutils.TestConstants;
import org.apache.mesos.Protos;
import org.junit.Assert;
import org.junit.Test;

import java.util.Collections;
import java.util.UUID;

public class LaunchEvaluationStageTest {
    @Test
    public void testTaskInfoIsModifiedCorrectly() throws Exception {
        Protos.Resource offeredResource = ResourceUtils.getUnreservedScalar("cpus", 2.0);

        LaunchEvaluationStage evaluationStage = new LaunchEvaluationStage(TestConstants.TASK_NAME);
        Protos.Offer offer = OfferTestUtils.getOffer(offeredResource);
        PodInstanceRequirement podInstanceRequirement = PodInstanceRequirementTestUtils.getCpuRequirement(1.0);
        PodInfoBuilder podInfoBuilder = new PodInfoBuilder(
                podInstanceRequirement,
                TestConstants.SERVICE_NAME,
                UUID.randomUUID(),
                OfferRequirementTestUtils.getTestSchedulerFlags(),
                Collections.emptyList());

        EvaluationOutcome outcome = evaluationStage.evaluate(new MesosResourcePool(offer), podInfoBuilder);
        Assert.assertTrue(outcome.isPassing());
        Protos.TaskInfo.Builder taskBuilder = podInfoBuilder.getTaskBuilder(TestConstants.TASK_NAME);

        // labels are sorted alphabetically (see LabelUtils):
        Protos.Label label = taskBuilder.getLabels().getLabels(0);
        Assert.assertEquals("goal_state", label.getKey());
        Assert.assertEquals(GoalState.RUNNING.name(), label.getValue());

        label = taskBuilder.getLabels().getLabels(1);
        Assert.assertEquals("index", label.getKey());
        Assert.assertEquals(Integer.toString(TestConstants.TASK_INDEX), label.getValue());

        label = taskBuilder.getLabels().getLabels(2);
        Assert.assertEquals("offer_attributes", label.getKey());
        Assert.assertEquals("", label.getValue());

        label = taskBuilder.getLabels().getLabels(3);
        Assert.assertEquals("offer_hostname", label.getKey());
        Assert.assertEquals(TestConstants.HOSTNAME, label.getValue());

        label = taskBuilder.getLabels().getLabels(4);
        Assert.assertEquals("target_configuration", label.getKey());
        Assert.assertEquals(36, label.getValue().length());

        label = taskBuilder.getLabels().getLabels(5);
        Assert.assertEquals(label.getKey(), "task_type");
        Assert.assertEquals(TestConstants.POD_TYPE, label.getValue());
    }
}
