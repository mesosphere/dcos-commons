package com.mesosphere.sdk.offer.history;

import org.apache.mesos.Protos;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Assert;
import org.junit.Test;

public class OfferOutcomeTrackerTest {

    @Test
    public void verifyJson() {
        OfferOutcomeTracker tracker = new OfferOutcomeTracker();
        tracker.track(
                generateTestOutcome(true),
                generateTestOutcome(false),
                generateTestOutcome(true),
                generateTestOutcome(false),
                generateTestOutcome(false)
        );

        // Expected results are in the opposite of insertion order.
        verifyJson(tracker.toJson(), "fail", "fail", "pass", "fail", "pass");
    }

    private void verifyJson(JSONObject output, String... expectedResults) {
        JSONArray outcomes = output.getJSONArray(OfferOutcomeTracker.OUTCOMES_FIELD);
        for (int i = 0; i < outcomes.length(); i++) {
            Object object = outcomes.get(i);
            JSONObject outcome = (JSONObject) object;

            Assert.assertNotNull(outcome.get(OfferOutcomeTracker.TIMESTAMP_FIELD));
            Assert.assertNotNull(outcome.get(OfferOutcomeTracker.POD_INSTANCE_NAME_FIELD));
            Assert.assertNotNull(outcome.get(OfferOutcomeTracker.OUTCOME_FIELD));
            Assert.assertNotNull(outcome.get(OfferOutcomeTracker.EXPLANATION_FIELD));
            Assert.assertNotNull(outcome.get(OfferOutcomeTracker.OFFER_FIELD));

            Assert.assertEquals(
                    expectedResults[i],
                    outcome.getString(OfferOutcomeTracker.OUTCOME_FIELD));
        }
    }

    @Test
    public void verifyHtml() {

    }

    @Test
    public void verifyEviction() {
        // Once capacity is reached, previous outcomes should be ejected.
        OfferOutcomeTracker tracker = new OfferOutcomeTracker(2);

        tracker.track(generateTestOutcome(true),
                generateTestOutcome(false));

        // Expected results are in the opposite of insertion order.
        verifyJson(tracker.toJson(), "fail", "pass");

        tracker.track(generateTestOutcome(true));
        verifyJson(tracker.toJson(), "pass", "fail");
    }

    private OfferOutcome generateTestOutcome(boolean pass) {
        return new OfferOutcome("instance-name",
                pass,
                Protos.Offer.getDefaultInstance(),
                "an outcome");
    }
}
