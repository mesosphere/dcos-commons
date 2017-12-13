package com.mesosphere.sdk.offer.evaluate.placement;

import com.mesosphere.sdk.config.SerializationUtils;
import com.mesosphere.sdk.offer.taskdata.TaskLabelWriter;
import com.mesosphere.sdk.testutils.OfferTestUtils;
import com.mesosphere.sdk.testutils.TestConstants;
import org.apache.mesos.Protos;
import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;
import java.util.Collection;

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

    @Test
    public void getEmptyOfferKeys() {
        MaxPerRegionRule rule = new MaxPerRegionRule(2);
        Protos.Offer offer = OfferTestUtils.getEmptyOfferBuilder().build();
        Assert.assertTrue(rule.getKeys(offer).isEmpty());
    }

    @Test
    public void getRegionOfferKeys() {
        MaxPerRegionRule rule = new MaxPerRegionRule(2);
        Protos.Offer offer = OfferTestUtils.getEmptyOfferBuilder()
                .setDomain(TestConstants.LOCAL_DOMAIN_INFO)
                .build();
        Collection<String> keys = rule.getKeys(offer);
        Assert.assertEquals(1, keys.size());
        Assert.assertEquals(
                TestConstants.LOCAL_DOMAIN_INFO.getFaultDomain().getRegion().getName(),
                keys.stream().findFirst().get());
    }

    @Test
    public void getEmptyTaskKeys() {
        MaxPerRegionRule rule = new MaxPerRegionRule(2);
        Assert.assertTrue(rule.getKeys(TestConstants.TASK_INFO).isEmpty());
    }

    @Test
    public void getRegionTaskKeys() {
        MaxPerRegionRule rule = new MaxPerRegionRule(2);
        Protos.TaskInfo taskInfo = TestConstants.TASK_INFO.toBuilder()
                .setLabels(
                        new TaskLabelWriter(TestConstants.TASK_INFO)
                                .setRegion(TestConstants.LOCAL_DOMAIN_INFO.getFaultDomain().getRegion())
                                .toProto())
                .build();
        Collection<String> keys = rule.getKeys(taskInfo);
        Assert.assertEquals(1, keys.size());
        Assert.assertEquals(
                TestConstants.LOCAL_DOMAIN_INFO.getFaultDomain().getRegion().getName(),
                keys.stream().findFirst().get());
    }
}
