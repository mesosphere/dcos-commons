package com.mesosphere.sdk.offer.history;

import com.google.common.collect.EvictingQueue;

import java.util.Arrays;

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


}
