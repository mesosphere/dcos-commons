package org.apache.mesos.offer;

import org.apache.mesos.Protos;
import org.apache.mesos.Protos.Offer;
import org.apache.mesos.Protos.OfferID;
import org.apache.mesos.SchedulerDriver;
import org.apache.mesos.scheduler.plan.*;

import java.util.*;

/**
 * This scheduler performs UNRESERVE and DESTROY operations on resources which are identified
 * as unexpected by the ResourceCleaner.
 */
public class ResourceCleanerScheduler implements PlanManager {
  private ResourceCleaner resourceCleaner;
  private OfferAccepter offerAccepter;

  public ResourceCleanerScheduler(
      ResourceCleaner resourceCleaner,
      OfferAccepter offerAccepter) {
    this.resourceCleaner = resourceCleaner;
    this.offerAccepter = offerAccepter;
  }

  public List<OfferID> resourceOffers(SchedulerDriver driver, List<Offer> offers) {
    final List<OfferRecommendation> recommendations = resourceCleaner.evaluate(offers);

    // Recommendations should be grouped by agent, as Mesos enforces processing of acceptOffers Operations
    // that belong to a single agent.
    final Map<Protos.SlaveID, List<OfferRecommendation>> recommendationsGroupedByAgents =
            groupRecommendationsByAgent(recommendations);

    final List<OfferID> processedOffers = new ArrayList<>(offers.size());
    for (Map.Entry<Protos.SlaveID, List<OfferRecommendation>> entry : recommendationsGroupedByAgents.entrySet()) {
      processedOffers.addAll(offerAccepter.accept(driver, recommendationsGroupedByAgents.get(entry.getKey())));
    }

    return processedOffers;
  }

  /**
   * Groups recommendations by agent.
   *
   * Visibility is protected to enable testing.
   */
  protected Map<Protos.SlaveID, List<OfferRecommendation>> groupRecommendationsByAgent(
          List<OfferRecommendation> recommendations) {
    final Map<Protos.SlaveID, List<OfferRecommendation>> recommendationsGroupedByAgents = new HashMap<>();

    for (OfferRecommendation recommendation : recommendations) {
      final Protos.SlaveID agentId = recommendation.getOffer().getSlaveId();

      if (!recommendationsGroupedByAgents.containsKey(agentId)) {
        recommendationsGroupedByAgents.put(agentId, new ArrayList<>());
      }

      final List<OfferRecommendation> agentRecommendations = recommendationsGroupedByAgents.get(agentId);
      agentRecommendations.add(recommendation);
      recommendationsGroupedByAgents.put(agentId, agentRecommendations);
    }

    return recommendationsGroupedByAgents;
  }

  @Override
  public Plan getPlan() {
    return null;
  }

  @Override
  public void setPlan(Plan plan) {

  }

  @Override
  public Optional<Phase> getCurrentPhase() {
    return Optional.empty();
  }

  @Override
  public Optional<Block> getCurrentBlock(List<Block> dirtiedAssets) {
    return Optional.empty();
  }

  @Override
  public boolean isComplete() {
    return false;
  }

  @Override
  public void proceed() {

  }

  @Override
  public void interrupt() {

  }

  @Override
  public boolean isInterrupted() {
    return false;
  }

  @Override
  public void restart(UUID phaseId, UUID blockId) {

  }

  @Override
  public void forceComplete(UUID phaseId, UUID blockId) {

  }

  @Override
  public void update(Protos.TaskStatus status) {

  }

  @Override
  public boolean hasDecisionPoint(Block block) {
    return false;
  }

  @Override
  public Status getStatus() {
    return null;
  }

  @Override
  public Status getPhaseStatus(UUID phaseId) {
    return null;
  }

  @Override
  public List<String> getErrors() {
    return null;
  }

  @Override
  public void update(Observable o, Object arg) {

  }
}
