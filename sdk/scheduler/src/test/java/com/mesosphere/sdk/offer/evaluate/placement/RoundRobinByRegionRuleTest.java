package com.mesosphere.sdk.offer.evaluate.placement;

import com.mesosphere.sdk.config.SerializationUtils;
import com.mesosphere.sdk.offer.taskdata.TaskLabelWriter;
import com.mesosphere.sdk.testutils.OfferTestUtils;
import com.mesosphere.sdk.testutils.TestConstants;
import org.apache.mesos.Protos;
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
    
    @Test
    public void getNullOfferKey() {
        RoundRobinByRegionRule rule = new RoundRobinByRegionRule(2);
        Protos.Offer offer = OfferTestUtils.getEmptyOfferBuilder().build();
        Assert.assertNull(rule.getKey(offer));
    }

    @Test
    public void getRegionOfferKey() {
        RoundRobinByRegionRule rule = new RoundRobinByRegionRule(2);
        Protos.Offer offer = OfferTestUtils.getEmptyOfferBuilder()
                .setDomain(TestConstants.LOCAL_DOMAIN_INFO)
                .build();
        String key = rule.getKey(offer);
        Assert.assertNotNull(key);
        Assert.assertEquals(TestConstants.LOCAL_DOMAIN_INFO.getFaultDomain().getRegion().getName(), key);
    }

    @Test
    public void getNullTaskKey() {
        RoundRobinByRegionRule rule = new RoundRobinByRegionRule(2);
        Assert.assertNull(rule.getKey(TestConstants.TASK_INFO));
    }

    @Test
    public void getRegionTaskKey() {
        RoundRobinByRegionRule rule = new RoundRobinByRegionRule(2);
        Protos.TaskInfo taskInfo = TestConstants.TASK_INFO.toBuilder()
                .setLabels(
                        new TaskLabelWriter(TestConstants.TASK_INFO)
                                .setRegion(TestConstants.LOCAL_DOMAIN_INFO.getFaultDomain().getRegion())
                                .toProto())
                .build();
        String key = rule.getKey(taskInfo);
        Assert.assertNotNull(key);
        Assert.assertEquals(TestConstants.LOCAL_DOMAIN_INFO.getFaultDomain().getRegion().getName(), key);
    }
}
