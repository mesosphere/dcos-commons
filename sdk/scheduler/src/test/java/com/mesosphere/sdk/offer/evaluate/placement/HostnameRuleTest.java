package com.mesosphere.sdk.offer.evaluate.placement;

import com.mesosphere.sdk.config.SerializationUtils;
import com.mesosphere.sdk.scheduler.plan.DefaultPodInstance;
import com.mesosphere.sdk.specification.DefaultPodSpec;
import com.mesosphere.sdk.specification.PodInstance;
import com.mesosphere.sdk.specification.PodSpec;
import com.mesosphere.sdk.testutils.OfferTestUtils;
import com.mesosphere.sdk.testutils.TestPodFactory;
import org.apache.mesos.Protos.Offer;
import org.junit.Test;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.*;

/**
 * Tests for {@link HostnameRule}.
 */
public class HostnameRuleTest {

    private static final PodSpec podSpec = DefaultPodSpec.newBuilder("executor-uri")
            .type("type")
            .count(1)
            .tasks(Arrays.asList(TestPodFactory.getTaskSpec()))
            .build();
    private static final PodInstance POD_INSTANCE = new DefaultPodInstance(podSpec, 0);
    private static final String HOST_1 = "host-1-uuid";
    private static final String HOST_2 = "host-2-uuid";
    private static final String HOST_3 = "host-3-uuid";
    private static final StringMatcher HOST_MATCHER_1 = ExactMatcher.create(HOST_1);
    private static final StringMatcher HOST_MATCHER_2 = ExactMatcher.create(HOST_2);
    private static final StringMatcher HOST_MATCHER_3 = ExactMatcher.create(HOST_3);

    @Test
    public void testRequireHostname() {
        PlacementRule rule = HostnameRuleFactory.getInstance().require(HOST_MATCHER_1);
        assertTrue(rule.filter(offerWithHost(HOST_1), POD_INSTANCE, Collections.emptyList()).isPassing());
        assertFalse(rule.filter(offerWithHost(HOST_2), POD_INSTANCE, Collections.emptyList()).isPassing());
        assertFalse(rule.filter(offerWithHost(HOST_3), POD_INSTANCE, Collections.emptyList()).isPassing());

        rule = HostnameRuleFactory.getInstance().require(HOST_MATCHER_2);
        assertFalse(rule.filter(offerWithHost(HOST_1), POD_INSTANCE, Collections.emptyList()).isPassing());
        assertTrue(rule.filter(offerWithHost(HOST_2), POD_INSTANCE, Collections.emptyList()).isPassing());
        assertFalse(rule.filter(offerWithHost(HOST_3), POD_INSTANCE, Collections.emptyList()).isPassing());
    }

    @Test
    public void testAvoidHostname() {
        PlacementRule rule = HostnameRuleFactory.getInstance().avoid(HOST_MATCHER_1);
        assertFalse(rule.filter(offerWithHost(HOST_1), POD_INSTANCE, Collections.emptyList()).isPassing());
        assertTrue(rule.filter(offerWithHost(HOST_2), POD_INSTANCE, Collections.emptyList()).isPassing());
        assertTrue(rule.filter(offerWithHost(HOST_3), POD_INSTANCE, Collections.emptyList()).isPassing());

        rule = HostnameRuleFactory.getInstance().avoid(HOST_MATCHER_2);
        assertTrue(rule.filter(offerWithHost(HOST_1), POD_INSTANCE, Collections.emptyList()).isPassing());
        assertFalse(rule.filter(offerWithHost(HOST_2), POD_INSTANCE, Collections.emptyList()).isPassing());
        assertTrue(rule.filter(offerWithHost(HOST_3), POD_INSTANCE, Collections.emptyList()).isPassing());
    }

    @Test
    public void testRequireHostnames() {
        PlacementRule rule = HostnameRuleFactory.getInstance().require(HOST_MATCHER_1, HOST_MATCHER_3);
        assertTrue(rule.filter(offerWithHost(HOST_1), POD_INSTANCE, Collections.emptyList()).isPassing());
        assertFalse(rule.filter(offerWithHost(HOST_2), POD_INSTANCE, Collections.emptyList()).isPassing());
        assertTrue(rule.filter(offerWithHost(HOST_3), POD_INSTANCE, Collections.emptyList()).isPassing());

        rule = HostnameRuleFactory.getInstance().require(HOST_MATCHER_2, HOST_MATCHER_3);
        assertFalse(rule.filter(offerWithHost(HOST_1), POD_INSTANCE, Collections.emptyList()).isPassing());
        assertTrue(rule.filter(offerWithHost(HOST_2), POD_INSTANCE, Collections.emptyList()).isPassing());
        assertTrue(rule.filter(offerWithHost(HOST_3), POD_INSTANCE, Collections.emptyList()).isPassing());
    }

    @Test
    public void testAvoidHostnames() {
        PlacementRule rule = HostnameRuleFactory.getInstance().avoid(HOST_MATCHER_1, HOST_MATCHER_3);
        assertFalse(rule.filter(offerWithHost(HOST_1), POD_INSTANCE, Collections.emptyList()).isPassing());
        assertTrue(rule.filter(offerWithHost(HOST_2), POD_INSTANCE, Collections.emptyList()).isPassing());
        assertFalse(rule.filter(offerWithHost(HOST_3), POD_INSTANCE, Collections.emptyList()).isPassing());

        rule = HostnameRuleFactory.getInstance().avoid(HOST_MATCHER_2, HOST_MATCHER_3);
        assertTrue(rule.filter(offerWithHost(HOST_1), POD_INSTANCE, Collections.emptyList()).isPassing());
        assertFalse(rule.filter(offerWithHost(HOST_2), POD_INSTANCE, Collections.emptyList()).isPassing());
        assertFalse(rule.filter(offerWithHost(HOST_3), POD_INSTANCE, Collections.emptyList()).isPassing());
    }

    @Test
    public void testSerializeDeserialize() throws IOException {
        PlacementRule rule = HostnameRuleFactory.getInstance().avoid(HOST_MATCHER_1, HOST_MATCHER_3);
        assertEquals(rule, SerializationUtils.fromString(
                SerializationUtils.toJsonString(rule), PlacementRule.class, TestPlacementUtils.OBJECT_MAPPER));

        rule = HostnameRuleFactory.getInstance().require(HOST_MATCHER_1, HOST_MATCHER_3);
        assertEquals(rule, SerializationUtils.fromString(
                SerializationUtils.toJsonString(rule), PlacementRule.class, TestPlacementUtils.OBJECT_MAPPER));

        rule = HostnameRuleFactory.getInstance().avoid(HOST_MATCHER_1);
        assertEquals(rule, SerializationUtils.fromString(
                SerializationUtils.toJsonString(rule), PlacementRule.class, TestPlacementUtils.OBJECT_MAPPER));

        rule = HostnameRuleFactory.getInstance().require(HOST_MATCHER_3);
        assertEquals(rule, SerializationUtils.fromString(
                SerializationUtils.toJsonString(rule), PlacementRule.class, TestPlacementUtils.OBJECT_MAPPER));
    }

    private static Offer offerWithHost(String host) {
        Offer.Builder o = OfferTestUtils.getEmptyOfferBuilder().setHostname(host);
        OfferTestUtils.addResource(o, "a");
        OfferTestUtils.addResource(o, "b");
        OfferTestUtils.addResource(o, "c");
        return o.build();
    }
}
