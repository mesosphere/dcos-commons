package com.mesosphere.sdk.offer.evaluate.placement;

import com.mesosphere.sdk.config.SerializationUtils;
import com.mesosphere.sdk.testutils.OfferTestUtils;
import com.mesosphere.sdk.testutils.TestConstants;
import org.apache.mesos.Protos;
import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;
import java.util.Collection;

/**
 * This class tests the {@link ZoneRule} class.
 */
public class ZoneRuleTest {
    @Test
    public void testSerializeDeserialize() throws IOException {
        PlacementRule rule = new ZoneRule(ExactMatcher.create(TestConstants.ZONE));
        Assert.assertEquals(
                rule,
                SerializationUtils.fromString(
                        SerializationUtils.toJsonString(rule),
                        PlacementRule.class,
                        TestPlacementUtils.OBJECT_MAPPER));
    }

    @Test
    public void getEmptyKeys() {
        ZoneRule rule = new ZoneRule(ExactMatcher.create(TestConstants.ZONE));
        Assert.assertTrue(rule.getKeys(OfferTestUtils.getEmptyOfferBuilder().build()).isEmpty());
    }

    @Test
    public void getKeys() {
        ZoneRule rule = new ZoneRule(ExactMatcher.create(TestConstants.ZONE));
        Protos.Offer offer = OfferTestUtils.getEmptyOfferBuilder()
                .setDomain(TestConstants.LOCAL_DOMAIN_INFO)
                .build();
        Collection<String> keys = rule.getKeys(offer);
        Assert.assertEquals(1, keys.size());
        Assert.assertEquals(TestConstants.ZONE, keys.stream().findFirst().get());
    }
}
