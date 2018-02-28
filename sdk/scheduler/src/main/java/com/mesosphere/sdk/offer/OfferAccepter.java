package com.mesosphere.sdk.offer;

import com.google.protobuf.TextFormat;
import com.mesosphere.sdk.scheduler.Driver;
import org.apache.commons.collections.CollectionUtils;
import org.apache.mesos.Protos.Filters;
import org.apache.mesos.Protos.Offer.Operation;
import org.apache.mesos.Protos.OfferID;
import org.apache.mesos.SchedulerDriver;
import org.slf4j.Logger;

import java.util.*;

/**
 * The OfferAccepter extracts the Mesos Operations encapsulated by the OfferRecommendation and accepts Offers with those
 * Operations.
 */
public class OfferAccepter {
    private static final Filters FILTERS = Filters.newBuilder().setRefuseSeconds(1).build();

    private final Logger logger;
    private final Collection<OperationRecorder> recorders = new ArrayList<>();

    public OfferAccepter(String serviceName, List<OperationRecorder> recorders) {
        this.logger = LoggingUtils.getLogger(getClass(), serviceName);
        this.recorders.addAll(recorders);
    }

    public List<OfferID> accept(List<OfferRecommendation> recommendations) {
        if (CollectionUtils.isEmpty(recommendations)) {
            logger.warn("No recommendations, nothing to do");
            return Collections.emptyList();
        }

        Optional<SchedulerDriver> driver = Driver.getDriver();
        if (!driver.isPresent()) {
            throw new IllegalStateException("No driver present for accepting offers.  This should never happen.");
        }

        List<OfferID> offerIds = getOfferIds(recommendations);
        List<Operation> operations = getOperations(recommendations);

        logOperations(operations);

        try {
            record(recommendations);
        } catch (Exception ex) {
            logger.error("Failed to record Operations so not launching Task", ex);
            return Collections.emptyList();
        }

        if (CollectionUtils.isNotEmpty(operations)) {
            driver.get().acceptOffers(offerIds, operations, FILTERS);
        } else {
            logger.warn("No Operations to perform.");
        }

        return offerIds;
    }

    private void record(List<OfferRecommendation> recommendations) throws Exception {
        for (OfferRecommendation recommendation : recommendations) {
            for (OperationRecorder recorder : recorders) {
                recorder.record(recommendation);
            }
        }
    }

    private List<Operation> getOperations(List<OfferRecommendation> recommendations) {
        List<Operation> operations = new ArrayList<>();

        for (OfferRecommendation recommendation : recommendations) {
            if (recommendation instanceof LaunchOfferRecommendation &&
                    !((LaunchOfferRecommendation) recommendation).shouldLaunch()) {
                logger.info("Skipping launch of transient Operation: {}",
                        TextFormat.shortDebugString(recommendation.getOperation()));
            } else {
                operations.add(recommendation.getOperation());
            }
        }

        return operations;
    }

    private static List<OfferID> getOfferIds(List<OfferRecommendation> recommendations) {
        Set<OfferID> offerIdSet = new HashSet<>();

        for (OfferRecommendation recommendation : recommendations) {
            offerIdSet.add(recommendation.getOffer().getId());
        }

        return new ArrayList<>(offerIdSet);
    }

    private void logOperations(List<Operation> operations) {
        logger.info("Performing {} operations:", operations.size());
        for (Operation op : operations) {
            logger.info("  {}", TextFormat.shortDebugString(op));
        }
    }
}
