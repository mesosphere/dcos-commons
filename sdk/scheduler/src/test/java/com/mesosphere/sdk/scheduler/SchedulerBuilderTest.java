package com.mesosphere.sdk.scheduler;

import com.mesosphere.sdk.dcos.Capabilities;
import com.mesosphere.sdk.offer.Constants;
import com.mesosphere.sdk.offer.evaluate.placement.*;
import com.mesosphere.sdk.scheduler.plan.DefaultPlan;
import com.mesosphere.sdk.scheduler.plan.Phase;
import com.mesosphere.sdk.scheduler.plan.Plan;
import com.mesosphere.sdk.specification.DefaultPodSpec;
import com.mesosphere.sdk.specification.DefaultServiceSpec;
import com.mesosphere.sdk.specification.PodSpec;
import com.mesosphere.sdk.specification.ServiceSpec;
import com.mesosphere.sdk.storage.MemPersister;
import com.mesosphere.sdk.storage.Persister;
import com.mesosphere.sdk.storage.PersisterException;
import com.mesosphere.sdk.testutils.SchedulerConfigTestUtils;
import com.mesosphere.sdk.testutils.TestConstants;
import com.mesosphere.sdk.testutils.TestPodFactory;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import static org.mockito.Mockito.*;

import java.io.File;
import java.util.Arrays;
import java.util.Collection;
import java.util.Optional;

/**
 * This class tests the {@link SchedulerBuilder}.
 */
public class SchedulerBuilderTest {

    private static ServiceSpec minimalServiceSpec;

    private SchedulerConfig mockSchedulerConfig;
    @Mock private Capabilities mockCapabilities;

    @BeforeClass
    public static void beforeAll() throws Exception {
        ClassLoader classLoader = SchedulerBuilderTest.class.getClassLoader();
        File file = new File(classLoader.getResource("valid-minimal.yml").getFile());
        minimalServiceSpec =
                DefaultServiceSpec.newGenerator(file, SchedulerConfigTestUtils.getTestSchedulerConfig()).build();
    }

    @Before
    public void beforeEach() {
        MockitoAnnotations.initMocks(this);
        mockSchedulerConfig = SchedulerConfigTestUtils.getTestSchedulerConfig();
        when(mockSchedulerConfig.getSchedulerRegion()).thenReturn(Optional.empty());
        when(mockCapabilities.supportsDomains()).thenReturn(true);
        Capabilities.overrideCapabilities(mockCapabilities);
    }

    @Test
    public void testExistingRegionRuleUnmodified() throws PersisterException {
        PlacementRule remoteRegionRule = RegionRuleFactory.getInstance().require(ExactMatcher.create(TestConstants.REMOTE_REGION));
        PlacementRule localRegionRule = new IsLocalRegionRule();
        ServiceSpec serviceSpec = DefaultServiceSpec.newBuilder(minimalServiceSpec)
                .addPod(DefaultPodSpec.newBuilder(getPodSpec("foo"))
                        .placementRule(remoteRegionRule)
                        .build())
                .addPod(DefaultPodSpec.newBuilder(getPodSpec("bar"))
                        .placementRule(localRegionRule)
                        .build())
                .build();

        ServiceSpec updatedServiceSpec = DefaultScheduler.newBuilder(
                serviceSpec, mockSchedulerConfig, new MemPersister())
                .withSingleRegionConstraint()
                .build()
                .getServiceSpec();

        // Pod without placement rules from valid-minimal.yml had a rule added:
        Assert.assertTrue(updatedServiceSpec.getPods().get(0).getPlacementRule().get() instanceof IsLocalRegionRule);
        // Pods with region placement rules were left as-is:
        Assert.assertSame(remoteRegionRule, updatedServiceSpec.getPods().get(1).getPlacementRule().get());
        Assert.assertSame(localRegionRule, updatedServiceSpec.getPods().get(2).getPlacementRule().get());
    }

    @Test
    public void testRegionAwarenessEnabledJavaWithSchedulerRegion() throws PersisterException {
        when(mockSchedulerConfig.getSchedulerRegion()).thenReturn(Optional.of(TestConstants.REMOTE_REGION));

        ServiceSpec updatedServiceSpec = DefaultScheduler.newBuilder(
                minimalServiceSpec, mockSchedulerConfig, new MemPersister())
                .withSingleRegionConstraint()
                .build()
                .getServiceSpec();

        Optional<PlacementRule> rule = updatedServiceSpec.getPods().get(0).getPlacementRule();
        // Pod updated with exact region rule:
        Assert.assertTrue(rule.isPresent());
        Assert.assertTrue(rule.get() instanceof RegionRule);

        // Not hit since enabled directly in java:
        verify(mockSchedulerConfig, never()).isRegionAwarenessEnabled();
    }

    @Test
    public void testRegionAwarenessEnabledEnvWithSchedulerRegion() throws PersisterException {
        when(mockSchedulerConfig.getSchedulerRegion()).thenReturn(Optional.of(TestConstants.REMOTE_REGION));
        when(mockSchedulerConfig.isRegionAwarenessEnabled()).thenReturn(true);

        ServiceSpec updatedServiceSpec = DefaultScheduler.newBuilder(
                minimalServiceSpec, mockSchedulerConfig, new MemPersister())
                .build()
                .getServiceSpec();

        Optional<PlacementRule> rule = updatedServiceSpec.getPods().get(0).getPlacementRule();
        // Pod updated with exact region rule:
        Assert.assertTrue(rule.isPresent());
        Assert.assertTrue(rule.get() instanceof RegionRule);

        verify(mockSchedulerConfig).isRegionAwarenessEnabled();
    }

    @Test
    public void testRegionAwarenessEnabledWithoutSchedulerRegion() throws PersisterException {
        ServiceSpec updatedServiceSpec = DefaultScheduler.newBuilder(
                minimalServiceSpec, mockSchedulerConfig, new MemPersister())
                .withSingleRegionConstraint()
                .build()
                .getServiceSpec();

        Optional<PlacementRule> rule = updatedServiceSpec.getPods().get(0).getPlacementRule();
        // Pod updated with local region rule:
        Assert.assertTrue(rule.isPresent());
        Assert.assertTrue(rule.get() instanceof IsLocalRegionRule);
    }

    @Test
    public void testRegionAwarenessDisabledWithSchedulerRegion() throws PersisterException {
        when(mockSchedulerConfig.getSchedulerRegion()).thenReturn(Optional.of(TestConstants.REMOTE_REGION));

        ServiceSpec updatedServiceSpec = DefaultScheduler.newBuilder(
                minimalServiceSpec, mockSchedulerConfig, new MemPersister())
                .build()
                .getServiceSpec();

        Optional<PlacementRule> rule = updatedServiceSpec.getPods().get(0).getPlacementRule();
        // Pod updated with local region rule:
        Assert.assertTrue(rule.isPresent());
        Assert.assertTrue(rule.get() instanceof IsLocalRegionRule);
    }

    @Test
    public void testRegionAwarenessDisabledWithoutSchedulerRegion() throws PersisterException {
        ServiceSpec updatedServiceSpec = DefaultScheduler.newBuilder(
                minimalServiceSpec, mockSchedulerConfig, new MemPersister())
                .build()
                .getServiceSpec();

        Optional<PlacementRule> rule = updatedServiceSpec.getPods().get(0).getPlacementRule();
        // Pod updated with local region rule:
        Assert.assertTrue(rule.isPresent());
        Assert.assertTrue(rule.get() instanceof IsLocalRegionRule);
    }

    @Test
    public void testDomainsNotSupportedByCluster() throws Exception {
        when(mockCapabilities.supportsDomains()).thenReturn(false);

        ServiceSpec updatedServiceSpec = DefaultScheduler.newBuilder(
                minimalServiceSpec, mockSchedulerConfig, new MemPersister())
                .build()
                .getServiceSpec();
        // No rule changes:
        Assert.assertFalse(updatedServiceSpec.getPods().get(0).getPlacementRule().isPresent());

        // Not hit since unsupported by cluster:
        verify(mockCapabilities, atLeastOnce()).supportsDomains();
        verify(mockSchedulerConfig, never()).isRegionAwarenessEnabled();
        verify(mockSchedulerConfig, never()).getSchedulerRegion();
    }

    @Test
    public void testDeployPlanOverriddenDuringUpdate() throws Exception {
        Persister persister = new MemPersister();
        SchedulerBuilder builder = DefaultScheduler.newBuilder(minimalServiceSpec, mockSchedulerConfig, persister);

        Collection<Plan> plans = builder.selectDeployPlan(getDeployUpdatePlans(), true);

        Assert.assertEquals(1, plans.size());
        Plan deployPlan = plans.stream()
                .filter(plan -> plan.isDeployPlan())
                .findFirst().get();

        Assert.assertEquals(1, deployPlan.getChildren().size());
    }

    @Test
    public void testDeployPlanPreservedDuringInstall() throws Exception {
        Persister persister = new MemPersister();
        SchedulerBuilder builder = DefaultScheduler.newBuilder(minimalServiceSpec, mockSchedulerConfig, persister);

        Collection<Plan> plans = builder.selectDeployPlan(getDeployUpdatePlans(), false);

        Assert.assertEquals(2, plans.size());
        Plan deployPlan = plans.stream()
                .filter(plan -> plan.isDeployPlan())
                .findFirst().get();

        Assert.assertEquals(2, deployPlan.getChildren().size());
    }

    private static PodSpec getPodSpec(String type) {
        return TestPodFactory.getPodSpec(
                type,
                TestConstants.RESOURCE_SET_ID,
                TestConstants.TASK_NAME,
                TestConstants.TASK_CMD,
                TestConstants.SERVICE_USER,
                1,
                1.0,
                256,
                4096);
    }

    // Deploy plan has 2 phases, update plan has 1 for distinguishing which was chosen.
    private static Collection<Plan> getDeployUpdatePlans() {
        Phase phase = mock(Phase.class);

        Plan deployPlan = new DefaultPlan(Constants.DEPLOY_PLAN_NAME, Arrays.asList(phase, phase));
        Assert.assertEquals(2, deployPlan.getChildren().size());

        Plan updatePlan = new DefaultPlan(Constants.UPDATE_PLAN_NAME, Arrays.asList(phase));
        Assert.assertEquals(1, updatePlan.getChildren().size());

        return Arrays.asList(deployPlan, updatePlan);
    }
}
