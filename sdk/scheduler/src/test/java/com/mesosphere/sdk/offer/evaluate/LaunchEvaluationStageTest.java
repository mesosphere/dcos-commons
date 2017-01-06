package com.mesosphere.sdk.offer.evaluate;

import com.mesosphere.sdk.offer.*;
import com.mesosphere.sdk.testutils.OfferRequirementTestUtils;
import com.mesosphere.sdk.testutils.OfferTestUtils;
import com.mesosphere.sdk.testutils.ResourceTestUtils;
import com.mesosphere.sdk.testutils.TestConstants;
import org.apache.mesos.Protos;
import org.junit.Assert;
import org.junit.Test;

public class LaunchEvaluationStageTest {
    @Test
    public void testTaskInfoIsModifiedCorrectly() throws Exception {
        Protos.Resource desiredResource = ResourceTestUtils.getDesiredCpu(1.0);
        Protos.Resource offeredResource = ResourceUtils.getUnreservedScalar("cpus", 2.0);

        LaunchEvaluationStage evaluationStage = new LaunchEvaluationStage(TestConstants.TASK_NAME);
        Protos.Offer offer = OfferTestUtils.getOffer(offeredResource);
        OfferRequirement offerRequirement = OfferRequirementTestUtils.getOfferRequirement(desiredResource);

        evaluationStage.evaluate(new MesosResourcePool(offer), offerRequirement, new OfferRecommendationSlate());
        Protos.TaskInfo taskInfo = offerRequirement.getTaskRequirement(TestConstants.TASK_NAME).getTaskInfo();

        Protos.Label label = taskInfo.getLabels().getLabels(0);
        Assert.assertEquals(label.getKey(), "offer_attributes");
        Assert.assertEquals(label.getValue(), "");

        label = taskInfo.getLabels().getLabels(1);
        Assert.assertEquals(label.getKey(), "task_type");
        Assert.assertEquals(label.getValue(), TestConstants.TASK_TYPE);

        label = taskInfo.getLabels().getLabels(2);
        Assert.assertEquals(label.getKey(), "index");
        Assert.assertEquals(label.getValue(), Integer.toString(TestConstants.TASK_INDEX));

        label = taskInfo.getLabels().getLabels(3);
        Assert.assertEquals(label.getKey(), "offer_hostname");
        Assert.assertEquals(label.getValue(), TestConstants.HOSTNAME);
    }
}
