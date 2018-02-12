package com.mesosphere.sdk.offer.evaluate.placement;

import com.mesosphere.sdk.config.SerializationUtils;
import org.junit.Test;

import java.io.IOException;

import static org.junit.Assert.assertEquals;

public class InvalidPlacementRuleTest {

    @Test
    public void testSerializeDeserialize() throws IOException {
        PlacementRule rule = new InvalidPlacementRule("constraint", "exception");
        assertEquals(rule, SerializationUtils.fromString(
                SerializationUtils.toJsonString(rule), PlacementRule.class, TestPlacementUtils.OBJECT_MAPPER));
    }

}
