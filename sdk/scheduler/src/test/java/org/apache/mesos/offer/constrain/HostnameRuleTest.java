package org.apache.mesos.offer.constrain;

import org.apache.mesos.Protos.Offer;
import org.apache.mesos.config.SerializationUtils;
import org.apache.mesos.offer.OfferRequirement;
import org.apache.mesos.testutils.OfferRequirementTestUtils;
import org.apache.mesos.testutils.OfferTestUtils;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

import java.io.IOException;
import java.util.Collections;

/**
 * Tests for {@link HostnameRule}.
 */
public class HostnameRuleTest {

    private static final OfferRequirement REQ = OfferRequirementTestUtils.getOfferRequirement();
    private static final String HOST_1 = "host-1-uuid";
    private static final String HOST_2 = "host-2-uuid";
    private static final String HOST_3 = "host-3-uuid";

    @Test
    public void testRequireHostname() {
        PlacementRule rule = HostnameRule.require(HOST_1);
        Offer filtered = rule.filter(offerWithHost(HOST_1), REQ, Collections.emptyList());
        assertEquals(3, filtered.getResourcesCount());
        filtered = rule.filter(offerWithHost(HOST_2), REQ, Collections.emptyList());
        assertEquals(0, filtered.getResourcesCount());
        filtered = rule.filter(offerWithHost(HOST_3), REQ, Collections.emptyList());
        assertEquals(0, filtered.getResourcesCount());

        rule = HostnameRule.require(HOST_2);
        filtered = rule.filter(offerWithHost(HOST_1), REQ, Collections.emptyList());
        assertEquals(0, filtered.getResourcesCount());
        filtered = rule.filter(offerWithHost(HOST_2), REQ, Collections.emptyList());
        assertEquals(3, filtered.getResourcesCount());
        filtered = rule.filter(offerWithHost(HOST_3), REQ, Collections.emptyList());
        assertEquals(0, filtered.getResourcesCount());
    }

    @Test
    public void testAvoidHostname() {
        PlacementRule rule = HostnameRule.avoid(HOST_1);
        Offer filtered = rule.filter(offerWithHost(HOST_1), REQ, Collections.emptyList());
        assertEquals(0, filtered.getResourcesCount());
        filtered = rule.filter(offerWithHost(HOST_2), REQ, Collections.emptyList());
        assertEquals(3, filtered.getResourcesCount());
        filtered = rule.filter(offerWithHost(HOST_3), REQ, Collections.emptyList());
        assertEquals(3, filtered.getResourcesCount());

        rule = HostnameRule.avoid(HOST_2);
        filtered = rule.filter(offerWithHost(HOST_1), REQ, Collections.emptyList());
        assertEquals(3, filtered.getResourcesCount());
        filtered = rule.filter(offerWithHost(HOST_2), REQ, Collections.emptyList());
        assertEquals(0, filtered.getResourcesCount());
        filtered = rule.filter(offerWithHost(HOST_3), REQ, Collections.emptyList());
        assertEquals(3, filtered.getResourcesCount());
    }

    @Test
    public void testRequireHostnames() {
        PlacementRule rule = HostnameRule.require(HOST_1, HOST_3);
        Offer filtered = rule.filter(offerWithHost(HOST_1), REQ, Collections.emptyList());
        assertEquals(3, filtered.getResourcesCount());
        filtered = rule.filter(offerWithHost(HOST_2), REQ, Collections.emptyList());
        assertEquals(0, filtered.getResourcesCount());
        filtered = rule.filter(offerWithHost(HOST_3), REQ, Collections.emptyList());
        assertEquals(3, filtered.getResourcesCount());

        rule = HostnameRule.require(HOST_2, HOST_3);
        filtered = rule.filter(offerWithHost(HOST_1), REQ, Collections.emptyList());
        assertEquals(0, filtered.getResourcesCount());
        filtered = rule.filter(offerWithHost(HOST_2), REQ, Collections.emptyList());
        assertEquals(3, filtered.getResourcesCount());
        filtered = rule.filter(offerWithHost(HOST_3), REQ, Collections.emptyList());
        assertEquals(3, filtered.getResourcesCount());
    }

    @Test
    public void testAvoidHostnames() {
        PlacementRule rule = HostnameRule.avoid(HOST_1, HOST_3);
        Offer filtered = rule.filter(offerWithHost(HOST_1), REQ, Collections.emptyList());
        assertEquals(0, filtered.getResourcesCount());
        filtered = rule.filter(offerWithHost(HOST_2), REQ, Collections.emptyList());
        assertEquals(3, filtered.getResourcesCount());
        filtered = rule.filter(offerWithHost(HOST_3), REQ, Collections.emptyList());
        assertEquals(0, filtered.getResourcesCount());

        rule = HostnameRule.avoid(HOST_2, HOST_3);
        filtered = rule.filter(offerWithHost(HOST_1), REQ, Collections.emptyList());
        assertEquals(3, filtered.getResourcesCount());
        filtered = rule.filter(offerWithHost(HOST_2), REQ, Collections.emptyList());
        assertEquals(0, filtered.getResourcesCount());
        filtered = rule.filter(offerWithHost(HOST_3), REQ, Collections.emptyList());
        assertEquals(0, filtered.getResourcesCount());
    }

    @Test
    public void testSerializeDeserialize() throws IOException {
        PlacementRule rule = HostnameRule.avoid(HOST_1, HOST_3);
        assertEquals(rule, SerializationUtils.fromString(
                SerializationUtils.toJsonString(rule), PlacementRule.class, TestPlacementUtils.OBJECT_MAPPER));

        rule = HostnameRule.require(HOST_1, HOST_3);
        assertEquals(rule, SerializationUtils.fromString(
                SerializationUtils.toJsonString(rule), PlacementRule.class, TestPlacementUtils.OBJECT_MAPPER));

        rule = HostnameRule.avoid(HOST_1);
        assertEquals(rule, SerializationUtils.fromString(
                SerializationUtils.toJsonString(rule), PlacementRule.class, TestPlacementUtils.OBJECT_MAPPER));

        rule = HostnameRule.require(HOST_3);
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
