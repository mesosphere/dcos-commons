package com.mesosphere.sdk.offer.evaluate.placement;

import com.mesosphere.sdk.config.SerializationUtils;
import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;

/**
 * This class tests the {@link RoundRobinByRegionRule} class.
 */
public class RoundRobinByRegionRuleTest {
    @Test
    public void testSerializeDeserialize() throws IOException {
        PlacementRule rule = new RoundRobinByRegionRule(2);
        Assert.assertEquals(rule, SerializationUtils.fromString(
                SerializationUtils.toJsonString(rule), PlacementRule.class, TestPlacementUtils.OBJECT_MAPPER));
    }
}
