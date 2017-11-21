package com.mesosphere.sdk.offer.evaluate.placement;

import com.mesosphere.sdk.config.SerializationUtils;
import com.mesosphere.sdk.scheduler.plan.DefaultPodInstance;
import com.mesosphere.sdk.specification.DefaultPodSpec;
import com.mesosphere.sdk.specification.PodInstance;
import com.mesosphere.sdk.specification.PodSpec;
import com.mesosphere.sdk.testing.TestPodFactory;
import com.mesosphere.sdk.testutils.OfferTestUtils;
import org.apache.mesos.Protos.Offer;
import org.apache.mesos.Protos.Resource;
import org.junit.Test;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;

import static org.junit.Assert.*;

/**
 * Tests for {@link OrRule}.
 */
public class OrRuleTest {
    private static final PodSpec podSpec = DefaultPodSpec.newBuilder("executor-uri")
            .type("type")
            .count(1)
            .tasks(Arrays.asList(TestPodFactory.getTaskSpec()))
            .build();
    private static final PodInstance POD_INSTANCE = new DefaultPodInstance(podSpec, 0);

    @Test
    public void testEmpty() {
        assertFalse(new OrRule()
                .filter(offerWith(TestPlacementUtils.RESOURCES), POD_INSTANCE, Collections.emptyList()).isPassing());
    }

    @Test
    public void testAllPass() {
        assertTrue(new OrRule(TestPlacementUtils.PASS)
                .filter(offerWith(TestPlacementUtils.RESOURCES), POD_INSTANCE, Collections.emptyList()).isPassing());

        assertTrue(new OrRule(TestPlacementUtils.PASS, TestPlacementUtils.PASS, TestPlacementUtils.PASS)
                .filter(offerWith(TestPlacementUtils.RESOURCES), POD_INSTANCE, Collections.emptyList()).isPassing());
    }

    @Test
    public void testAllFail() {
        assertFalse(new OrRule(TestPlacementUtils.FAIL)
                .filter(offerWith(TestPlacementUtils.RESOURCES), POD_INSTANCE, Collections.emptyList()).isPassing());

        assertTrue(new OrRule(TestPlacementUtils.FAIL, TestPlacementUtils.PASS)
                .filter(offerWith(TestPlacementUtils.RESOURCES), POD_INSTANCE, Collections.emptyList()).isPassing());

        assertTrue(new OrRule(TestPlacementUtils.PASS, TestPlacementUtils.FAIL)
                .filter(offerWith(TestPlacementUtils.RESOURCES), POD_INSTANCE, Collections.emptyList()).isPassing());

        assertFalse(new OrRule(TestPlacementUtils.FAIL, TestPlacementUtils.FAIL)
                .filter(offerWith(TestPlacementUtils.RESOURCES), POD_INSTANCE, Collections.emptyList()).isPassing());

        assertTrue(new OrRule(TestPlacementUtils.FAIL, TestPlacementUtils.PASS, TestPlacementUtils.FAIL)
                .filter(offerWith(TestPlacementUtils.RESOURCES), POD_INSTANCE, Collections.emptyList()).isPassing());

        assertTrue(new OrRule(TestPlacementUtils.PASS, TestPlacementUtils.FAIL, TestPlacementUtils.PASS)
                .filter(offerWith(TestPlacementUtils.RESOURCES), POD_INSTANCE, Collections.emptyList()).isPassing());

        assertFalse(new OrRule(TestPlacementUtils.FAIL, TestPlacementUtils.FAIL, TestPlacementUtils.FAIL)
                .filter(offerWith(TestPlacementUtils.RESOURCES), POD_INSTANCE, Collections.emptyList()).isPassing());
    }

    @Test
    public void testSerializeDeserialize() throws IOException {
        PlacementRule rule = new OrRule(TestPlacementUtils.FAIL, TestPlacementUtils.PASS, TestPlacementUtils.PASS);
        assertEquals(rule, SerializationUtils.fromString(
                SerializationUtils.toJsonString(rule), PlacementRule.class, TestPlacementUtils.OBJECT_MAPPER));
    }

    private static Offer offerWith(Collection<Resource> resources) {
        Offer.Builder o = OfferTestUtils.getEmptyOfferBuilder();
        for (Resource r : resources) {
            o.addResources(r);
        }
        return o.build();
    }
}
