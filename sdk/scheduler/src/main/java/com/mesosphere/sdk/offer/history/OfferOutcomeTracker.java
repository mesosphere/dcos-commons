package com.mesosphere.sdk.offer.history;

import com.google.common.collect.EvictingQueue;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.util.Strings;
import org.json.JSONArray;
import org.json.JSONObject;

import static j2html.TagCreator.*;

import java.sql.Timestamp;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
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

    private List<OfferOutcome> reverseList() {
        // Read the current outcomes into a list, reversing the list
        // as callers want to see the most recent offer outcomes first.
        List<OfferOutcome> recentFirst = outcomes.stream().collect(Collectors.toList());
        Collections.reverse(recentFirst);
        return recentFirst;
    }

    private String newLineToHtmlBreak(String newLined) {
        return StringUtils.join(newLined, "<br>");
    }

    public String toJson() {
        JSONArray outcomes = new JSONArray();
        reverseList().stream().forEach(offerOutcome -> {
            JSONObject outcome = new JSONObject();
            outcome.append("timestamp", offerOutcome.getTimestamp())
                    .append("pod-instance-name", offerOutcome.getPodInstanceName())
                    .append("outcome", offerOutcome.pass() ? "pass" : "fail")
                    .append("explanation", offerOutcome.getOutcomeDetails())
                    .append("offer", offerOutcome.getOffer().toString());
        });

        return new JSONObject().append("outcomes", outcomes).toString();
    }

    public String toHtml() {
        // Construct a table of the current outcomes.
        return html(
                body(
                        table(
                                tr(
                                        th("Time"),
                                        th("Pod Instance"),
                                        th("Outcome"),
                                        th("Explanation"),
                                        th("Offer")
                                ),
                                each(reverseList(), offerOutcome ->
                                    tr(
                                            td(new Date(offerOutcome.getTimestamp()).toString()),
                                            td(offerOutcome.getPodInstanceName()),
                                            td(offerOutcome.pass() ? "PASS" : "FAIL"),
                                            td(newLineToHtmlBreak(offerOutcome.getOutcomeDetails())),
                                            td(newLineToHtmlBreak(offerOutcome.getOffer().toString()))
                                    )
                                )
                        )
                )
        ).render();
    }
}
