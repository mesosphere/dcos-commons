package com.mesosphere.sdk.debug;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.fasterxml.jackson.datatype.jsonorg.JsonOrgModule;
import com.google.common.collect.EvictingQueue;
import com.mesosphere.sdk.http.ResponseUtils;
import org.apache.commons.collections.map.HashedMap;
import org.json.JSONArray;
import org.json.JSONObject;

import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Response;

import java.util.HashMap;
import java.util.Map;

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
  public class OfferOutcomeSummary {
    private int acceptedCount;

    private int rejectedCount;

    @JsonIgnore
    private static final int DEFAULT_CAPACITY = 100;

    @JsonIgnore
    private EvictingQueue<OfferOutcomeV2> outcomes;

    private Map<String, Integer> failureCounts;

    private Map<String, Integer> agentCounts;

    public OfferOutcomeSummary() {
      this.acceptedCount = 0;
      this.rejectedCount = 0;
      this.outcomes = EvictingQueue.create(DEFAULT_CAPACITY);
      this.failureCounts = new HashMap();
      this.agentCounts = new HashedMap();
    }

    public void addOffer(OfferOutcomeV2 offerOutcome) {
      this.outcomes.add(offerOutcome);
    }

    public int getAcceptedCount() {
      return this.acceptedCount;
    }

    public int getRejectedCount() {
      return this.rejectedCount;
    }

    public Map<String, Integer> getFailureMap() {
      return this.failureCounts;
    }

    public void incrementAcceptedCount() {
      this.acceptedCount++;
    }

    public void incrementFalureCount() {
      this.rejectedCount++;
    }

    public void addFailure(String failureReason, String agentId) {
      if (this.failureCounts.containsKey(failureReason)) {
        int oldVal = this.failureCounts.get(failureReason);
        this.failureCounts.put(failureReason, ++oldVal);
      } else {
        this.failureCounts.put(failureReason, 1);
      }

      if (this.agentCounts.containsKey(agentId)) {
        int oldVal = this.agentCounts.get(agentId);
        this.agentCounts.put(agentId, ++oldVal);
      } else {
        this.agentCounts.put(agentId, 1);
      }

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

    private final String outcomeDetails;

    public OfferOutcomeV2(
        String podInstanceName,
        boolean pass,
        String offer,
        String outcomeDetails)
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

    public String getOutcomeDetails() {
      return outcomeDetails;
    }

    public long getTimestamp() {
      return timestamp;
    }
  }
}
