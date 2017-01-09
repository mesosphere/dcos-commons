package com.mesosphere.sdk.offer.evaluate;

import com.mesosphere.sdk.offer.OfferRecommendation;
import com.mesosphere.sdk.offer.ResourceUtils;
import com.mesosphere.sdk.testutils.OfferRequirementTestUtils;
import com.mesosphere.sdk.testutils.OfferTestUtils;
import com.mesosphere.sdk.testutils.ResourceTestUtils;
import com.mesosphere.sdk.testutils.TestConstants;
import org.apache.mesos.Protos.*;
import org.apache.mesos.Protos.Offer.Operation;
import org.junit.Assert;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;

/**
 * Offer evaluation tests concerning volumes.
 */
public class OfferEvaluatorVolumesTest extends OfferEvaluatorTestBase {
    @Test
    public void testCreateMultipleVolumes() throws Exception {
        Resource offeredResources = ResourceTestUtils.getUnreservedDisk(3);
        List<Resource> desiredResources = Arrays.asList(
                ResourceUtils.setLabel(
                        ResourceTestUtils.getDesiredRootVolume(1), TestConstants.CONTAINER_PATH_LABEL, "pv0"),
                ResourceUtils.setLabel(
                        ResourceTestUtils.getDesiredRootVolume(2), TestConstants.CONTAINER_PATH_LABEL, "pv1"));

        Offer offer = OfferTestUtils.getOffer(Arrays.asList(offeredResources));

        List<OfferRecommendation> recommendations = evaluator.evaluate(
                OfferRequirementTestUtils.getOfferRequirement(desiredResources, false),
                Arrays.asList(offer));
        Assert.assertEquals(5, recommendations.size());

        Assert.assertEquals(Operation.Type.RESERVE, recommendations.get(0).getOperation().getType());
        Assert.assertEquals(Operation.Type.CREATE, recommendations.get(1).getOperation().getType());
        Assert.assertEquals(Operation.Type.RESERVE, recommendations.get(2).getOperation().getType());
        Assert.assertEquals(Operation.Type.CREATE, recommendations.get(3).getOperation().getType());
        Assert.assertEquals(Operation.Type.LAUNCH, recommendations.get(4).getOperation().getType());

        System.out.println(recommendations.get(4).getOperation());

        // Validate Create Operation
        Operation createOperation = recommendations.get(1).getOperation();
        Assert.assertEquals("pv0", createOperation.getCreate().getVolumes(0).getDisk().getVolume().getContainerPath());

        // Validate Create Operation
        createOperation = recommendations.get(3).getOperation();
        Assert.assertEquals("pv1", createOperation.getCreate().getVolumes(0).getDisk().getVolume().getContainerPath());

        // Validate Launch Operation
        Operation launchOperation = recommendations.get(4).getOperation();
        for (TaskInfo taskInfo : launchOperation.getLaunch().getTaskInfosList()) {
            for (Resource resource : taskInfo.getResourcesList()) {
                Label resourceIdLabel = getFirstLabel(resource);
                Assert.assertTrue(resourceIdLabel.getKey().equals("resource_id"));
                Assert.assertTrue(resourceIdLabel.getValue().length() > 0);
            }
        }
    }
}
