package com.mesosphere.sdk.debug;

import com.mesosphere.sdk.http.ResponseUtils;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.fasterxml.jackson.datatype.jsonorg.JsonOrgModule;
import com.google.common.collect.EvictingQueue;
import org.apache.commons.collections.map.HashedMap;
import org.json.JSONArray;
import org.json.JSONObject;

import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Response;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * OfferOutcomeTrackerV2 is the backend of DebugOffersResource.
 * It aggregates  a summary of offerOutcome information
 */
public class OfferOutcomeTrackerV2 implements DebugEndpoint {

  private OfferOutcomeSummary summary = new OfferOutcomeSummary();

  public OfferOutcomeSummary getSummary() {
    return this.summary;
  }

  public Response getJson(
      @QueryParam("plan") String plan,
      @QueryParam("phase") String phase,
      @QueryParam("step") String step,
      @QueryParam("sync") boolean sync)
  {
    ObjectMapper jsonMapper = new ObjectMapper();
    jsonMapper.registerModule(new Jdk8Module());
    jsonMapper.registerModule(new JsonOrgModule());

    JSONObject response = jsonMapper.convertValue(this.getSummary(), JSONObject.class);
    response.put("offers", this.getSummary().toJson());

    return ResponseUtils.jsonOkResponse(response);
  }

  /**
   * Encapsulates the outcome of an summary of current offer evaluation.
   */
  public static class OfferOutcomeSummary {

    private static final int DEFAULT_CAPACITY = 100;

    private int acceptedCount;

    private int rejectedCount;

    @JsonIgnore
    private EvictingQueue<OfferOutcomeV2> outcomes;

    private Map<String, Integer> failureReasons;

    private Map<String, Integer> rejectedAgents;

    public OfferOutcomeSummary() {
      this.acceptedCount = 0;
      this.rejectedCount = 0;
      this.outcomes = EvictingQueue.create(DEFAULT_CAPACITY);
      this.failureReasons = new HashMap<>();
      this.rejectedAgents = new HashedMap();
    }

    public void addOffer(OfferOutcomeV2 offerOutcome) {
      this.outcomes.add(offerOutcome);
      if (offerOutcome.pass()) {
        this.acceptedCount++;
      } else {
        this.rejectedCount++;
      }
    }

    public int getAcceptedCount() {
      return this.acceptedCount;
    }

    public int getRejectedCount() {
      return this.rejectedCount;
    }

    public Map<String, Integer> getFailureReasons() {
      return this.failureReasons;
    }

    public Map<String, Integer> getRejectedAgents() {
      return this.rejectedAgents;
    }

    public void addFailureReason(String failureReason) {
      failureReasons.compute(failureReason, (k, v) -> v == null ? 1 : v + 1);
    }

    public void addFailureAgent(String agentId) {
      rejectedAgents.compute(agentId, (k, v) -> v == null ? 1 : v + 1);
    }

    public JSONArray toJson() {
      JSONArray displayOutcomes = new JSONArray();
      this.outcomes.forEach(offerOutcome -> {
        JSONObject outcome = new JSONObject();
        outcome.put("timestamp", offerOutcome.getTimestamp())
            .put("pod-instance-name", offerOutcome.getPodInstanceName())
            .put("outcome", offerOutcome.pass() ? "pass" : "fail")
            .put("explanation", offerOutcome.getOutcomeDetails())
            .put("offer", offerOutcome.getOffer());
        displayOutcomes.put(outcome);
      });
      return displayOutcomes;
    }
  }

  /**
   * Encapsulates the outcome of an offer evaluation in OfferEvaluator.
   */
  public static class OfferOutcomeV2 {
    private final long timestamp;

    private final String podInstanceName;

    private final boolean pass;

    private final String offer;

    private final List<String> outcomeDetails;

    public OfferOutcomeV2(
        String podInstanceName,
        boolean pass,
        String offer,
        List<String> outcomeDetails)
    {
      this.timestamp = System.currentTimeMillis();
      this.podInstanceName = podInstanceName;
      this.pass = pass;
      this.offer = offer;
      this.outcomeDetails = outcomeDetails;
    }

    public String getPodInstanceName() {
      return podInstanceName;
    }

    public boolean pass() {
      return pass;
    }

    public String getOffer() {
      return offer;
    }

    public List<String> getOutcomeDetails() {
      return outcomeDetails;
    }

    public long getTimestamp() {
      return timestamp;
    }
  }
}
