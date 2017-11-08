package com.mesosphere.sdk.offer.evaluate.placement;

import com.mesosphere.sdk.config.SerializationUtils;
import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;

/**
 * This class tests the {@link MaxPerRegionRule} class.
 */
public class MaxPerRegionRuleTest {
    @Test
    public void testSerializeDeserialize() throws IOException {
        PlacementRule rule = new MaxPerRegionRule(2);
        Assert.assertEquals(rule, SerializationUtils.fromString(
                SerializationUtils.toJsonString(rule), PlacementRule.class, TestPlacementUtils.OBJECT_MAPPER));
    }

    @Test
    public void testDeserializeNoOptionalTaskFilter() throws IOException {
        String str = "{ '@type': 'MaxPerRegionRule', 'max': 2 }".replace('\'', '"');
        SerializationUtils.fromString(
                str,
                PlacementRule.class,
                TestPlacementUtils.OBJECT_MAPPER);
    }

    @Test
    public void testDeserializeAllParams() throws IOException {
        String str = "{ '@type': 'MaxPerRegionRule', 'max': 2, 'task-filter': { '@type': 'ExactMatcher', 'string': 'foo' } }".replace('\'', '"');
        SerializationUtils.fromString(
                str,
                PlacementRule.class,
                TestPlacementUtils.OBJECT_MAPPER);
    }
}
