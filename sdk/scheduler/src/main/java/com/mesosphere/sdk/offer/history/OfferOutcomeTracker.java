package com.mesosphere.sdk.offer.history;

import com.google.common.collect.EvictingQueue;
import j2html.tags.DomContent;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

// CHECKSTYLE:OFF AvoidStaticImport
// CHECKSTYLE:OFF AvoidStarImport

import static j2html.TagCreator.*;


/**
 * Tracks the outcome of offers as they are evaluated against a PodInstanceRequirement.
 */
public class OfferOutcomeTracker {
  static final String OUTCOMES_FIELD = "outcomes";

  static final String TIMESTAMP_FIELD = "timestamp";

  static final String POD_INSTANCE_NAME_FIELD = "pod-instance-name";

  static final String OUTCOME_FIELD = "outcome";

  static final String EXPLANATION_FIELD = "explanation";

  static final String OFFER_FIELD = "offer";

  private static final int DEFAULT_CAPACITY = 100;

  private final EvictingQueue<OfferOutcome> outcomes;

  public OfferOutcomeTracker() {
    this.outcomes = EvictingQueue.create(DEFAULT_CAPACITY);
  }

  public OfferOutcomeTracker(int capacity) {
    this.outcomes = EvictingQueue.create(capacity);
  }

  public void track(OfferOutcome outcome) {
    outcomes.add(outcome);
  }

  @SuppressWarnings("checkstyle:HiddenField")
  public void track(OfferOutcome... outcomes) {
    this.outcomes.addAll(Arrays.asList(outcomes));
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

  @SuppressWarnings("checkstyle:HiddenField")
  public JSONObject toJson() {
    JSONArray outcomes = new JSONArray();
    reverseList().forEach(offerOutcome -> {
      JSONObject outcome = new JSONObject();
      outcome.put(TIMESTAMP_FIELD, offerOutcome.getTimestamp())
          .put(POD_INSTANCE_NAME_FIELD, offerOutcome.getPodInstanceName())
          .put(OUTCOME_FIELD, offerOutcome.pass() ? "pass" : "fail")
          .put(EXPLANATION_FIELD, offerOutcome.getOutcomeDetails())
          .put(OFFER_FIELD, offerOutcome.getOffer().toString());
      outcomes.put(outcome);
    });

    return new JSONObject().put(OUTCOMES_FIELD, outcomes);
  }

  @SuppressWarnings("checkstyle:MultipleStringLiterals")
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
                        td(new Date(offerOutcome.getTimestamp()).toString())
                            .withStyle("white-space: nowrap"),
                        td(offerOutcome.getPodInstanceName()).withStyle("white-space: nowrap"),
                        td(offerOutcome.pass() ? "PASS" : "FAIL"),
                        td(newLineToHtmlBreak(offerOutcome.getOutcomeDetails()))
                            .withStyle("width: 500px"),
                        td(offerOutcome.getOffer().toString()).withStyle("width: 500px")
                    )
                )
            ).withStyle("border: 1px solid black")
        )
    ).render();
  }
}
// CHECKSTYLE:ON
