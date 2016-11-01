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
        PlacementRule rule = new HostnameRule.RequireHostnameGenerator(HOST_1).generate(Collections.emptyList());
        Offer filtered = rule.filter(offerWithHost(HOST_1), REQ);
        assertEquals(3, filtered.getResourcesCount());
        filtered = rule.filter(offerWithHost(HOST_2), REQ);
        assertEquals(0, filtered.getResourcesCount());
        filtered = rule.filter(offerWithHost(HOST_3), REQ);
        assertEquals(0, filtered.getResourcesCount());

        rule = new HostnameRule.RequireHostnameGenerator(HOST_2).generate(Collections.emptyList());
        filtered = rule.filter(offerWithHost(HOST_1), REQ);
        assertEquals(0, filtered.getResourcesCount());
        filtered = rule.filter(offerWithHost(HOST_2), REQ);
        assertEquals(3, filtered.getResourcesCount());
        filtered = rule.filter(offerWithHost(HOST_3), REQ);
        assertEquals(0, filtered.getResourcesCount());
    }

    @Test
    public void testAvoidHostname() {
        PlacementRule rule = new HostnameRule.AvoidHostnameGenerator(HOST_1).generate(Collections.emptyList());
        Offer filtered = rule.filter(offerWithHost(HOST_1), REQ);
        assertEquals(0, filtered.getResourcesCount());
        filtered = rule.filter(offerWithHost(HOST_2), REQ);
        assertEquals(3, filtered.getResourcesCount());
        filtered = rule.filter(offerWithHost(HOST_3), REQ);
        assertEquals(3, filtered.getResourcesCount());

        rule = new HostnameRule.AvoidHostnameGenerator(HOST_2).generate(Collections.emptyList());
        filtered = rule.filter(offerWithHost(HOST_1), REQ);
        assertEquals(3, filtered.getResourcesCount());
        filtered = rule.filter(offerWithHost(HOST_2), REQ);
        assertEquals(0, filtered.getResourcesCount());
        filtered = rule.filter(offerWithHost(HOST_3), REQ);
        assertEquals(3, filtered.getResourcesCount());
    }

    @Test
    public void testRequireHostnames() {
        PlacementRule rule = new HostnameRule.RequireHostnamesGenerator(HOST_1, HOST_3).generate(Collections.emptyList());
        Offer filtered = rule.filter(offerWithHost(HOST_1), REQ);
        assertEquals(3, filtered.getResourcesCount());
        filtered = rule.filter(offerWithHost(HOST_2), REQ);
        assertEquals(0, filtered.getResourcesCount());
        filtered = rule.filter(offerWithHost(HOST_3), REQ);
        assertEquals(3, filtered.getResourcesCount());

        rule = new HostnameRule.RequireHostnamesGenerator(HOST_2, HOST_3).generate(Collections.emptyList());
        filtered = rule.filter(offerWithHost(HOST_1), REQ);
        assertEquals(0, filtered.getResourcesCount());
        filtered = rule.filter(offerWithHost(HOST_2), REQ);
        assertEquals(3, filtered.getResourcesCount());
        filtered = rule.filter(offerWithHost(HOST_3), REQ);
        assertEquals(3, filtered.getResourcesCount());
    }

    @Test
    public void testAvoidHostnames() {
        PlacementRule rule = new HostnameRule.AvoidHostnamesGenerator(HOST_1, HOST_3).generate(Collections.emptyList());
        Offer filtered = rule.filter(offerWithHost(HOST_1), REQ);
        assertEquals(0, filtered.getResourcesCount());
        filtered = rule.filter(offerWithHost(HOST_2), REQ);
        assertEquals(3, filtered.getResourcesCount());
        filtered = rule.filter(offerWithHost(HOST_3), REQ);
        assertEquals(0, filtered.getResourcesCount());

        rule = new HostnameRule.AvoidHostnamesGenerator(HOST_2, HOST_3).generate(Collections.emptyList());
        filtered = rule.filter(offerWithHost(HOST_1), REQ);
        assertEquals(3, filtered.getResourcesCount());
        filtered = rule.filter(offerWithHost(HOST_2), REQ);
        assertEquals(0, filtered.getResourcesCount());
        filtered = rule.filter(offerWithHost(HOST_3), REQ);
        assertEquals(0, filtered.getResourcesCount());
    }

    @Test
    public void testSerializeDeserialize() throws IOException {
        PlacementRuleGenerator generator = new HostnameRule.AvoidHostnamesGenerator(HOST_1, HOST_3);
        assertEquals(generator, SerializationUtils.fromJsonString(SerializationUtils.toJsonString(generator), PlacementRuleGenerator.class));

        generator = new HostnameRule.RequireHostnamesGenerator(HOST_1, HOST_3);
        assertEquals(generator, SerializationUtils.fromJsonString(SerializationUtils.toJsonString(generator), PlacementRuleGenerator.class));

        generator = new HostnameRule.AvoidHostnameGenerator(HOST_1);
        assertEquals(generator, SerializationUtils.fromJsonString(SerializationUtils.toJsonString(generator), PlacementRuleGenerator.class));

        generator = new HostnameRule.RequireHostnameGenerator(HOST_3);
        assertEquals(generator, SerializationUtils.fromJsonString(SerializationUtils.toJsonString(generator), PlacementRuleGenerator.class));
    }

    private static Offer offerWithHost(String host) {
        Offer.Builder o = OfferTestUtils.getEmptyOfferBuilder().setHostname(host);
        OfferTestUtils.addResource(o, "a");
        OfferTestUtils.addResource(o, "b");
        OfferTestUtils.addResource(o, "c");
        return o.build();
    }
}
