package com.mesosphere.sdk.config.validate;

import com.mesosphere.sdk.dcos.Capabilities;
import com.mesosphere.sdk.dcos.DcosVersion;
import com.mesosphere.sdk.offer.evaluate.placement.*;
import com.mesosphere.sdk.specification.PodSpec;
import com.mesosphere.sdk.specification.ServiceSpec;
import com.mesosphere.sdk.testutils.TestConstants;
import org.junit.*;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.io.IOException;
import java.util.Arrays;
import java.util.Optional;

import static org.mockito.Mockito.when;

/**
 * This class tests the {@link DomainCapabilityValidator} class.
 */
public class DomainCapabilityValidatorTest {
    @Mock private ServiceSpec serviceSpec;
    @Mock private PodSpec podSpec;

    private static final PlacementRule REGION_RULE = RegionRuleFactory.getInstance()
            .require(ExactMatcher.create(TestConstants.REMOTE_REGION));
    private static final PlacementRule ZONE_RULE = ZoneRuleFactory.getInstance()
            .require(ExactMatcher.create(TestConstants.ZONE));
    private static final PlacementRule ATTRIBUTE_RULE = AttributeRuleFactory.getInstance()
            .require(ExactMatcher.create("attribute"));

    private static final DomainCapabilityValidator validator = new DomainCapabilityValidator();

    @Before
    public void beforeEach() {
        MockitoAnnotations.initMocks(this);
        when(serviceSpec.getPods()).thenReturn(Arrays.asList(podSpec));
        when(podSpec.getType()).thenReturn(TestConstants.POD_TYPE);
    }

    @Test
    public void noRulePassesOnOldCluster() throws IOException {
        setOldCluster();
        when(podSpec.getPlacementRule()).thenReturn(Optional.empty());
        Assert.assertTrue(validator.validate(Optional.empty(), serviceSpec).isEmpty());
    }

    @Test
    public void noRulePassesOnNewCluster() throws IOException {
        setNewCluster();
        when(podSpec.getPlacementRule()).thenReturn(Optional.empty());
        Assert.assertTrue(validator.validate(Optional.empty(), serviceSpec).isEmpty());
    }

    @Test
    public void nonDomainRulePassesOnOldCluster() throws IOException {
        setOldCluster();
        when(podSpec.getPlacementRule()).thenReturn(Optional.empty());
        when(podSpec.getPlacementRule()).thenReturn(Optional.of(ATTRIBUTE_RULE));
        Assert.assertTrue(validator.validate(Optional.empty(), serviceSpec).isEmpty());
    }

    @Test
    public void nonDomainRulePassesOnNewCluster() throws IOException {
        setNewCluster();
        when(podSpec.getPlacementRule()).thenReturn(Optional.of(ATTRIBUTE_RULE));
        Assert.assertTrue(validator.validate(Optional.empty(), serviceSpec).isEmpty());
    }

    @Test
    public void regionRuleFailsOnOldCluster() throws IOException {
        setOldCluster();
        when(podSpec.getPlacementRule()).thenReturn(Optional.of(REGION_RULE));
        Assert.assertEquals(1, validator.validate(Optional.empty(), serviceSpec).size());
    }

    @Test
    public void regionRulePassesOnNewCluster() throws IOException {
        setNewCluster();
        when(podSpec.getPlacementRule()).thenReturn(Optional.of(REGION_RULE));
        Assert.assertTrue(validator.validate(Optional.empty(), serviceSpec).isEmpty());
    }

    @Test
    public void zoneRuleFailsOnOldCluster() throws IOException {
        setOldCluster();
        when(podSpec.getPlacementRule()).thenReturn(Optional.of(ZONE_RULE));
        Assert.assertEquals(1, validator.validate(Optional.empty(), serviceSpec).size());
    }

    @Test
    public void zoneRulePassesOnNewCluster() throws IOException {
        setNewCluster();
        when(podSpec.getPlacementRule()).thenReturn(Optional.of(ZONE_RULE));
        Assert.assertTrue(validator.validate(Optional.empty(), serviceSpec).isEmpty());
    }

    @Test
    public void regionAndZoneRuleFailsOnOldCluster() throws IOException {
        setOldCluster();
        when(podSpec.getPlacementRule()).thenReturn(Optional.of(new AndRule(ZONE_RULE, REGION_RULE)));
        Assert.assertEquals(2, validator.validate(Optional.empty(), serviceSpec).size());
    }

    @Test
    public void regionAndZoneRulePassesOnNewCluster() throws IOException {
        setNewCluster();
        when(podSpec.getPlacementRule()).thenReturn(Optional.of(new AndRule(ZONE_RULE, REGION_RULE)));
        Assert.assertTrue(validator.validate(Optional.empty(), serviceSpec).isEmpty());
    }

    private void setOldCluster() throws IOException {
        setVersion("0.9.0");
    }

    private void setNewCluster() throws IOException {
        setVersion("1.11");
    }
    private void setVersion(String version) throws IOException {
        Capabilities.overrideCapabilities(new Capabilities(new DcosVersion(version)));
    }
}
