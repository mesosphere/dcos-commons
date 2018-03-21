package com.mesosphere.sdk.scheduler;

import com.mesosphere.sdk.dcos.Capabilities;
import com.mesosphere.sdk.offer.evaluate.placement.*;
import com.mesosphere.sdk.specification.DefaultPodSpec;
import com.mesosphere.sdk.specification.DefaultServiceSpec;
import com.mesosphere.sdk.specification.PodSpec;
import com.mesosphere.sdk.specification.ServiceSpec;
import com.mesosphere.sdk.storage.MemPersister;
import com.mesosphere.sdk.testutils.TestConstants;
import com.mesosphere.sdk.testutils.TestPodFactory;
import org.junit.Assert;
import org.junit.Test;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Optional;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

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
        Capabilities capabilities = mock(Capabilities.class);
        when(capabilities.supportsDomains()).thenReturn(true);
        Capabilities.overrideCapabilities(capabilities);

        PodSpec originalPodSpec = getPodSpec();
        PodSpec updatedPodSpec = SchedulerBuilder.updatePodPlacement(originalPodSpec);

        Assert.assertTrue(updatedPodSpec.getPlacementRule().isPresent());
        Assert.assertTrue(updatedPodSpec.getPlacementRule().get() instanceof IsLocalRegionRule);
    }

    @Test
    public void addLocalRegionRule() {
        Capabilities capabilities = mock(Capabilities.class);
        when(capabilities.supportsDomains()).thenReturn(true);
        Capabilities.overrideCapabilities(capabilities);

        PodSpec originalPodSpec = DefaultPodSpec.newBuilder(getPodSpec())
                .placementRule(ZoneRuleFactory.getInstance().require(ExactMatcher.create(TestConstants.ZONE)))
                .build();
        PodSpec updatedPodSpec = SchedulerBuilder.updatePodPlacement(originalPodSpec);

        Assert.assertTrue(updatedPodSpec.getPlacementRule().isPresent());
        Assert.assertTrue(updatedPodSpec.getPlacementRule().get() instanceof AndRule);
        Assert.assertTrue(PlacementUtils.placementRuleReferencesRegion(updatedPodSpec));
    }

    @Test
    public void doNotConstrainToRegionWhenNotSupported() throws Exception {
        Capabilities capabilities = mock(Capabilities.class);
        when(capabilities.supportsDomains()).thenReturn(false);
        Capabilities.overrideCapabilities(capabilities);

        ServiceSpec serviceSpec = DefaultServiceSpec.newBuilder()
                .name(TestConstants.SERVICE_NAME)
                .role(TestConstants.ROLE)
                .principal(TestConstants.PRINCIPAL)
                .zookeeperConnection("badhost-shouldbeignored:2181")
                .pods(Arrays.asList(getPodSpec()))
                .user(TestConstants.SERVICE_USER)
                .build();

        SchedulerBuilder builder = DefaultScheduler.newBuilder(
                serviceSpec, SchedulerConfig.fromMap(new HashMap<>()), new MemPersister());
        Optional<PlacementRule> placementRule = builder.getServiceSpec().getPods().get(0).getPlacementRule();

        assert !placementRule.isPresent();
    }

    @Test
    public void constrainToSingleRegion() {
        PlacementRule placementRule = SchedulerBuilder.getRegionRule(Optional.empty());
        Assert.assertTrue(placementRule instanceof IsLocalRegionRule);

        placementRule = SchedulerBuilder.getRegionRule(Optional.of("USA"));
        Assert.assertTrue(placementRule instanceof RegionRule);
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
