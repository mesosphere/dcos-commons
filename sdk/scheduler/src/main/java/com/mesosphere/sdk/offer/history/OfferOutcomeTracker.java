package com.mesosphere.sdk.offer.history;

import com.google.common.collect.EvictingQueue;
import j2html.tags.DomContent;
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

    private DomContent newLineToHtmlBreak(String newLined) {
        return each(Arrays.asList(newLined.split("\\n")), line ->
            div(
                    div(line),
                    br()
            )
        );
    }

    public JSONObject toJson() {
        JSONArray outcomes = new JSONArray();
        reverseList().stream().forEach(offerOutcome -> {
            JSONObject outcome = new JSONObject();
            outcome.append("timestamp", offerOutcome.getTimestamp())
                    .append("pod-instance-name", offerOutcome.getPodInstanceName())
                    .append("outcome", offerOutcome.pass() ? "pass" : "fail")
                    .append("explanation", offerOutcome.getOutcomeDetails())
                    .append("offer", offerOutcome.getOffer().toString());
            outcomes.put(outcome);
        });

        return new JSONObject().append("outcomes", outcomes);
    }

    public String toHtml() {
        // Construct a table of the current outcomes.
        return html(
                style("table, th, td { border: 1px solid black; }" +
                        "\ntbody tr:nth-child(odd) { background-color: #E8E8E8 }" +
                        "\nth, td { padding: 10px }"),
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
                                            td(new Date(offerOutcome.getTimestamp()).toString()).withStyle("white-space: nowrap"),
                                            td(offerOutcome.getPodInstanceName()).withStyle("white-space: nowrap"),
                                            td(offerOutcome.pass() ? "PASS" : "FAIL"),
                                            td(newLineToHtmlBreak(offerOutcome.getOutcomeDetails())).withStyle("width: 500px"),
                                            td(offerOutcome.getOffer().toString()).withStyle("width: 500px")
                                    )
                                )
                        ).withStyle("border: 1px solid black")
                )
        ).render();
    }
}
