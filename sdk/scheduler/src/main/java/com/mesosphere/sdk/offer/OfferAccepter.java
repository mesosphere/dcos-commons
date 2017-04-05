package com.mesosphere.sdk.offer;

import com.google.inject.Inject;
import com.google.protobuf.TextFormat;

import org.apache.commons.collections.CollectionUtils;
import org.apache.mesos.Protos.Filters;
import org.apache.mesos.Protos.Offer.Operation;
import org.apache.mesos.Protos.OfferID;
import org.apache.mesos.SchedulerDriver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The OfferAccepter extracts the Mesos Operations encapsulated by the OfferRecommendation and accepts Offers with those
 * Operations.
 */
public class OfferAccepter {
    private static final Logger logger = LoggerFactory.getLogger(OfferAccepter.class);

    private Collection<OperationRecorder> recorders;

    public OfferAccepter(OperationRecorder recorder) {
        this(Arrays.asList(recorder));
    }

    public OfferAccepter(List<OperationRecorder> recorders) {
        this.recorders = recorders;
    }

    @Inject
    public OfferAccepter(Set<OperationRecorder> recorders) {
        this.recorders = recorders;
    }

    public List<OfferID> accept(SchedulerDriver driver, List<OfferRecommendation> recommendations) {
        return accept(driver, recommendations, getFilters());
    }

    public List<OfferID> accept(SchedulerDriver driver, List<OfferRecommendation> recommendations, Filters filters) {
        if (CollectionUtils.isEmpty(recommendations)) {
            logger.warn("No recommendations, nothing to do");
            return new ArrayList<>();
        }

        List<OfferID> offerIds = getOfferIds(recommendations);
        List<Operation> operations = getOperations(recommendations);

        logOperations(operations);

        try {
            record(recommendations);
        } catch (Exception ex) {
            logger.error("Failed to record Operations so not launching Task", ex);
            return new ArrayList<>();
        }

        if (CollectionUtils.isNotEmpty(operations)) {
            driver.acceptOffers(offerIds, operations, filters);
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

    private static List<Operation> getOperations(List<OfferRecommendation> recommendations) {
        List<Operation> operations = new ArrayList<>();

        for (OfferRecommendation recommendation : recommendations) {
            if (recommendation instanceof LaunchOfferRecommendation &&
                    ((LaunchOfferRecommendation) recommendation).isTransient()) {
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

    private static void logOperations(List<Operation> operations) {
        logger.info("Performing {} operations:", operations.size());
        for (Operation op : operations) {
            logger.info("  {}", TextFormat.shortDebugString(op));
        }
    }

    private static Filters getFilters() {
        return Filters.newBuilder().setRefuseSeconds(1).build();
    }
}
