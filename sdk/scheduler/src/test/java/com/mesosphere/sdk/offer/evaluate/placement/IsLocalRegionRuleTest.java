package com.mesosphere.sdk.offer.evaluate.placement;

import com.mesosphere.sdk.config.SerializationUtils;
import com.mesosphere.sdk.offer.evaluate.EvaluationOutcome;
import com.mesosphere.sdk.testutils.TestConstants;
import org.apache.mesos.Protos;
import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;

/**
 * This class tests the {@link IsLocalRegionRule}.
 */
public class IsLocalRegionRuleTest {
    private static final PlacementRule rule = new IsLocalRegionRule();

    @Test
    public void testSerializeDeserialize() throws IOException {
        PlacementRule rule = new IsLocalRegionRule();
        Assert.assertEquals(rule, SerializationUtils.fromString(
                SerializationUtils.toJsonString(rule), PlacementRule.class, TestPlacementUtils.OBJECT_MAPPER));
    }

    @Test
    public void offerLacksDomain() {
        EvaluationOutcome outcome = rule.filter(getOffer(), null, null);
        Assert.assertTrue(outcome.isPassing());
    }

    @Test
    public void offerHasDomainLocalDomainUnset() {
        EvaluationOutcome outcome = rule.filter(getOffer(TestConstants.LOCAL_DOMAIN_INFO), null, null);
        Assert.assertTrue(outcome.isPassing());
    }

    @Test
    public void offerMatchesLocalDomain() {
        IsLocalRegionRule.setLocalDomain(TestConstants.LOCAL_DOMAIN_INFO);
        EvaluationOutcome outcome = rule.filter(getOffer(TestConstants.LOCAL_DOMAIN_INFO), null, null);
        Assert.assertTrue(outcome.isPassing());
    }

    @Test
    public void offerMismatchesLocalDomain() {
        IsLocalRegionRule.setLocalDomain(TestConstants.LOCAL_DOMAIN_INFO);
        EvaluationOutcome outcome = rule.filter(getOffer(TestConstants.REMOTE_DOMAIN_INFO), null, null);
        Assert.assertFalse(outcome.isPassing());
    }

    private Protos.Offer getOffer() {
        return Protos.Offer.newBuilder()
                .setId(TestConstants.OFFER_ID)
                .setFrameworkId(TestConstants.FRAMEWORK_ID)
                .setSlaveId(TestConstants.AGENT_ID)
                .setHostname(TestConstants.HOSTNAME)
                .build();
    }

    private Protos.Offer getOffer(Protos.DomainInfo domainInfo) {
        return getOffer().toBuilder()
                .setDomain(domainInfo)
                .build();
    }
}
