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
 * This class tests the {@link RegionRule} class.
 */
public class RegionRuleTest {
    @Test
    public void testSerializeDeserialize() throws IOException {
        PlacementRule rule = new RegionRule(ExactMatcher.create(TestConstants.LOCAL_REGION));
        Assert.assertEquals(
                rule,
                SerializationUtils.fromString(
                        SerializationUtils.toJsonString(rule),
                        PlacementRule.class,
                        TestPlacementUtils.OBJECT_MAPPER));
    }

    @Test
    public void getEmptyKeys() {
        RegionRule rule = new RegionRule(ExactMatcher.create(TestConstants.LOCAL_REGION));
        Assert.assertTrue(rule.getKeys(OfferTestUtils.getEmptyOfferBuilder().build()).isEmpty());
    }

    @Test
    public void getKeys() {
        RegionRule rule = new RegionRule(ExactMatcher.create(TestConstants.LOCAL_REGION));
        Protos.Offer offer = OfferTestUtils.getEmptyOfferBuilder()
                .setDomain(TestConstants.LOCAL_DOMAIN_INFO)
                .build();
        Collection<String> keys = rule.getKeys(offer);
        Assert.assertEquals(1, keys.size());
        Assert.assertEquals(TestConstants.LOCAL_REGION, keys.stream().findFirst().get());
    }
}
