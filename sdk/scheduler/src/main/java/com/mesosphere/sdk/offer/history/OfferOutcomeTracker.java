package com.mesosphere.sdk.offer.history;

import com.google.common.collect.EvictingQueue;
import static j2html.TagCreator.*;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class OfferOutcomeTracker {
    private final EvictingQueue<OfferOutcome> outcomes;
    private static final int DEFAULT_CAPACITY = 100;

    public OfferOutcomeTracker() {
        this.outcomes = EvictingQueue.create(DEFAULT_CAPACITY);
    }

    public OfferOutcomeTracker(int capacity) {
        this.outcomes = EvictingQueue.create(capacity);
    }

    public void track(OfferOutcome outcome) {
        outcomes.add(outcome);
    }

    public void track(OfferOutcome... outcomes) {
        Arrays.stream(outcomes).forEach(this::track);
    }

    public String toHtml() {
        // Read the current outcomes into a list, reversing the list
        // as callers want to see the most recent offer outcomes first.
        List<OfferOutcome> recentFirst = outcomes.stream().collect(Collectors.toList());
        Collections.reverse(recentFirst);

        // Construct a table of the current outcomes.
        return html(
                body(
                        table(
                                tr(
                                        th("Pod Instance"), th("Outcome"), th("Explanation"), th("Offer")
                                ),
                                each(recentFirst, offerOutcome ->
                                    tr(
                                            td(offerOutcome.getPodInstanceName()),
                                            td(offerOutcome.pass() ? "PASS" : "FAIL"),
                                            td(offerOutcome.getOutcomeDetails()),
                                            td(offerOutcome.getOffer().toString())
                                    )
                                )
                        )
                )
        ).render();
    }
}
