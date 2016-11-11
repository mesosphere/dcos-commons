package org.apache.mesos.offer.constrain;

import java.io.IOException;
import org.apache.mesos.config.SerializationUtils;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * Tests for {@link PassthroughRule}.
 */
public class PassthroughRuleTest {

    @Test
    public void testSerializeDeserialize() throws IOException {
        PlacementRule rule = new PassthroughRule();
        assertEquals(rule, SerializationUtils.fromString(
                SerializationUtils.toJsonString(rule), PlacementRule.class, TestPlacementUtils.OBJECT_MAPPER));
    }
}
