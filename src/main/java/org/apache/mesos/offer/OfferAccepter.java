package org.apache.mesos.offer;

import com.google.inject.Inject;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.mesos.Protos.Filters;
import org.apache.mesos.Protos.Offer.Operation;
import org.apache.mesos.Protos.OfferID;
import org.apache.mesos.SchedulerDriver;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The OfferAccepter accepts OfferRecommendations.
 * This means that it extracts the Mesos Operations encapsulated by the OfferRecommendations
 * and accepts Offers with thos Operations
 */
public class OfferAccepter {
  private final Log log = LogFactory.getLog(OfferAccepter.class);
  private Collection<OperationRecorder> recorders;

  public OfferAccepter(OperationRecorder recorder) {
    this.recorders = Arrays.asList(recorder);
  }

  public OfferAccepter(List<OperationRecorder> recorders) {
    this.recorders = recorders;
  }

  @Inject
  public OfferAccepter(Set<OperationRecorder> recorders) {
    this.recorders = recorders;
  }

  public List<OfferID> accept(
    SchedulerDriver driver,
    List<OfferRecommendation> recommendations) {

    if (recommendations.size() <= 0) {
      log.warn("No recommendations, nothing to do");
      return new ArrayList<OfferID>();
    }

    List<OfferID> offerIds = getOfferIds(recommendations);
    List<Operation> operations = getOperations(recommendations);
    Filters filters = getFilters();

    logOperations(operations);

    try {
      record(recommendations);
    } catch (Exception ex) {
      log.error("Failed to record Operations so not launching Task", ex);
      return new ArrayList<OfferID>();
    }

    if (operations.size() > 0) {
      driver.acceptOffers(offerIds, operations, filters);
    } else {
      log.warn("No Operations to perform.");
    }

    return offerIds;
  }

  private void record(List<OfferRecommendation> recommendations) throws Exception {
    for (OfferRecommendation recommendation : recommendations) {
      for (OperationRecorder recorder : recorders) {
        recorder.record(recommendation.getOperation(), recommendation.getOffer());
      }
    }
  }

  private List<Operation> getOperations(List<OfferRecommendation> recommendations) {
    List<Operation> operations = new ArrayList<Operation>();

    for (OfferRecommendation recommendation : recommendations) {
      if (recommendation instanceof LaunchOfferRecommendation && 
          ((LaunchOfferRecommendation) recommendation).isTransient()) {
        log.info("Skipping launch of transient Operation: " + recommendation.getOperation());
      } else {
        operations.add(recommendation.getOperation());
      }
    }

    return operations;
  }

  private List<OfferID> getOfferIds(List<OfferRecommendation> recommendations) {
    Set<OfferID> offerIdSet = new HashSet<OfferID>();

    for (OfferRecommendation recommendation : recommendations) {
      offerIdSet.add(recommendation.getOffer().getId());
    }

    return new ArrayList<OfferID>(offerIdSet);
  }

  private void logOperations(List<Operation> operations) {
    for (Operation op : operations) {
      log.info("Performing Operation: " + op);
    }
  }

  private Filters getFilters() {
    return Filters.newBuilder().setRefuseSeconds(1).build();
  }
}
