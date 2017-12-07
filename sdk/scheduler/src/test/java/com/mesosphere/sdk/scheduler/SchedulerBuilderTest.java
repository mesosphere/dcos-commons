package com.mesosphere.sdk.scheduler;

import com.mesosphere.sdk.offer.evaluate.placement.*;
import com.mesosphere.sdk.specification.DefaultPodSpec;
import com.mesosphere.sdk.specification.PodSpec;
import com.mesosphere.sdk.testutils.TestConstants;
import com.mesosphere.sdk.testutils.TestPodFactory;
import org.junit.Assert;
import org.junit.Test;

/**
 * This class tests the {@link SchedulerBuilder}.
 */
public class SchedulerBuilderTest {
    @Test
    public void leaveRegionRuleUnmodified() {
        PodSpec originalPodSpec = DefaultPodSpec.newBuilder(getPodSpec())
                .placementRule(getRemoteRegionRule())
                .build();
        PodSpec updatedPodSpec = SchedulerBuilder.updatePodPlacement(originalPodSpec);

        Assert.assertEquals(originalPodSpec, updatedPodSpec);
    }

    @Test
    public void setLocalRegionRule() {
        PodSpec originalPodSpec = getPodSpec();
        PodSpec updatedPodSpec = SchedulerBuilder.updatePodPlacement(originalPodSpec);

        Assert.assertTrue(updatedPodSpec.getPlacementRule().isPresent());
        Assert.assertTrue(updatedPodSpec.getPlacementRule().get() instanceof IsLocalRegionRule);
    }

    @Test
    public void addLocalRegionRule() {
        PodSpec originalPodSpec = DefaultPodSpec.newBuilder(getPodSpec())
                .placementRule(ZoneRuleFactory.getInstance().require(ExactMatcher.create(TestConstants.ZONE)))
                .build();
        PodSpec updatedPodSpec = SchedulerBuilder.updatePodPlacement(originalPodSpec);

        Assert.assertTrue(updatedPodSpec.getPlacementRule().isPresent());
        Assert.assertTrue(updatedPodSpec.getPlacementRule().get() instanceof AndRule);
        Assert.assertTrue(PlacementUtils.placementRuleReferencesRegion(updatedPodSpec));
    }

    private PlacementRule getRemoteRegionRule() {
        return RegionRuleFactory.getInstance().require(ExactMatcher.create(TestConstants.REMOTE_REGION));
    }

    private static PodSpec getPodSpec() {
        return TestPodFactory.getPodSpec(
                TestConstants.POD_TYPE,
                TestConstants.RESOURCE_SET_ID,
                TestConstants.TASK_NAME,
                TestConstants.TASK_CMD,
                TestConstants.SERVICE_USER,
                1,
                1.0,
                256,
                4096);
    }
}
