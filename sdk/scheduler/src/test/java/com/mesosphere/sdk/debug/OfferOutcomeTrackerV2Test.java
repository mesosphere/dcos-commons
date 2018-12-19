package com.mesosphere.sdk.debug;

import org.apache.mesos.Protos;
import org.junit.Test;

public class OfferOutcomeTrackerV2Test {

  private OfferOutcomeTrackerV2.OfferOutcomeV2 generateTestOutcome(boolean pass) {
    return new OfferOutcomeTrackerV2.OfferOutcomeV2("instance-name",
        pass,
        Protos.Offer.getDefaultInstance().toString(),
        "an outcome");
  }

  @Test
  public void testSummaryCounts() {
    OfferOutcomeTrackerV2 tracker = new OfferOutcomeTrackerV2();

    tracker.getSummary().addOffer(
        generateTestOutcome(true)
    );
    tracker.getSummary().addOffer(
        generateTestOutcome(false)
    );
    tracker.getSummary().addOffer(
        generateTestOutcome(true)
    );
    tracker.getSummary().addOffer(
        generateTestOutcome(false)
    );
    tracker.getSummary().addOffer(
        generateTestOutcome(false)
    );
    assert tracker.getSummary().getAcceptedCount() == 2;
    assert tracker.getSummary().getRejectedCount() == 3;
  }

  @Test
  public void testRejectedAgents() {
    OfferOutcomeTrackerV2 tracker = new OfferOutcomeTrackerV2();
    tracker.getSummary().addFailureAgent("foo");
    tracker.getSummary().addFailureAgent("foo");
    tracker.getSummary().addFailureAgent("bar");

    assert tracker.getSummary().getRejectedAgents().get("foo") == 2;
    assert tracker.getSummary().getRejectedAgents().get("bar") == 1;
  }

  @Test
  public void testRejectedReasons() {
    OfferOutcomeTrackerV2 tracker = new OfferOutcomeTrackerV2();
    tracker.getSummary().addFailureReason("insufficientCpu");
    tracker.getSummary().addFailureReason("insufficientCpu");
    tracker.getSummary().addFailureReason("insufficientMem");

    assert tracker.getSummary().getFailureReasons().get("insufficientCpu") == 2;
    assert tracker.getSummary().getFailureReasons().get("insufficientMem") == 1;
  }

}
