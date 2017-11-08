package com.mesosphere.sdk.offer.evaluate.placement;

import com.mesosphere.sdk.config.SerializationUtils;
import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;

/**
 * This class tests the {@link RoundRobinByZoneRule} class.
 */
public class RoundRobinByZoneRuleTest {
    @Test
    public void testSerializeDeserialize() throws IOException {
        PlacementRule rule = new RoundRobinByZoneRule(2);
        Assert.assertEquals(rule, SerializationUtils.fromString(
                SerializationUtils.toJsonString(rule), PlacementRule.class, TestPlacementUtils.OBJECT_MAPPER));
    }
}
