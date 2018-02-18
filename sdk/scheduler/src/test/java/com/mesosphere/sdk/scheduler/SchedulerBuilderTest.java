package com.mesosphere.sdk.scheduler;

import com.mesosphere.sdk.dcos.Capabilities;
import com.mesosphere.sdk.offer.evaluate.placement.*;
import com.mesosphere.sdk.specification.DefaultPodSpec;
import com.mesosphere.sdk.specification.DefaultServiceSpec;
import com.mesosphere.sdk.specification.PodSpec;
import com.mesosphere.sdk.storage.MemPersister;
import com.mesosphere.sdk.storage.Persister;
import com.mesosphere.sdk.testutils.SchedulerConfigTestUtils;
import com.mesosphere.sdk.testutils.TestConstants;
import com.mesosphere.sdk.testutils.TestPodFactory;
import org.junit.Assert;
import org.junit.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.File;
import java.nio.charset.StandardCharsets;

/**
 * This class tests the {@link SchedulerBuilder}.
 */
public class SchedulerBuilderTest {

    private static final SchedulerConfig SCHEDULER_CONFIG = SchedulerConfigTestUtils.getTestSchedulerConfig();

    @Test
    public void checkSchemaVersion() throws Exception {
        ClassLoader classLoader = getClass().getClassLoader();
        File file = new File(classLoader.getResource("valid-minimal.yml").getFile());
        DefaultServiceSpec serviceSpec = DefaultServiceSpec.newGenerator(file, SCHEDULER_CONFIG).build();

        Persister persister = new MemPersister();

        // Constructing the builder must not touch the persister:
        SchedulerBuilder builder = new SchedulerBuilder(serviceSpec, SCHEDULER_CONFIG, persister);
        Assert.assertEquals(0, persister.getChildren("").size());

        // Once builder.build() is invoked, the schema version should be checked/updated:
        builder.build();
        Assert.assertEquals("1", new String(persister.get("SchemaVersion"), StandardCharsets.UTF_8));

        // Set the schema version to a bad value, and check that version validation now fails:
        persister.set("SchemaVersion", "123".getBytes(StandardCharsets.UTF_8));
        try {
            builder.build();
            Assert.fail("Expected exception due to bad schema version");
        } catch (IllegalStateException e) {
            Assert.assertEquals(
                    "Storage schema version 123 is not supported by this software (expected: 1)", e.getMessage());
        }
    }

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
