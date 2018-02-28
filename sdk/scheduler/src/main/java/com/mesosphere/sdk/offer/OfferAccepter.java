package com.mesosphere.sdk.offer;

import com.google.protobuf.TextFormat;
import com.mesosphere.sdk.scheduler.Driver;
import org.apache.commons.collections.CollectionUtils;
import org.apache.mesos.Protos.Filters;
import org.apache.mesos.Protos.Offer.Operation;
import org.apache.mesos.Protos.OfferID;
import org.apache.mesos.SchedulerDriver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * The OfferAccepter extracts the Mesos Operations encapsulated by the OfferRecommendation and accepts Offers with those
 * Operations.
 */
public class OfferAccepter {
    private static final Logger LOGGER = LoggerFactory.getLogger(OfferAccepter.class);
    private static final Filters FILTERS = Filters.newBuilder().setRefuseSeconds(1).build();

    private final Collection<OperationRecorder> recorders = new ArrayList<>();

    public OfferAccepter(List<OperationRecorder> recorders) {
        this.recorders.addAll(recorders);
    }

    public List<OfferID> accept(List<OfferRecommendation> recommendations) {
        if (CollectionUtils.isEmpty(recommendations)) {
            LOGGER.warn("No recommendations, nothing to do");
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
            LOGGER.error("Failed to record Operations so not launching Task", ex);
            return Collections.emptyList();
        }

        if (CollectionUtils.isNotEmpty(operations)) {
            driver.get().acceptOffers(offerIds, operations, FILTERS);
        } else {
            LOGGER.warn("No Operations to perform.");
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

    private static List<Operation> getOperations(List<OfferRecommendation> recommendations) {
        List<Operation> operations = new ArrayList<>();

        for (OfferRecommendation recommendation : recommendations) {
            if (recommendation instanceof LaunchOfferRecommendation &&
                    !((LaunchOfferRecommendation) recommendation).shouldLaunch()) {
                LOGGER.info("Skipping launch of transient Operation: {}",
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

    private static void logOperations(List<Operation> operations) {
        LOGGER.info("Performing {} operations:", operations.size());
        for (Operation op : operations) {
            LOGGER.info("  {}", TextFormat.shortDebugString(op));
        }
    }
}
