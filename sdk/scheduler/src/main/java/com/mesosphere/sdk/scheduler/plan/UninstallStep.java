package com.mesosphere.sdk.scheduler.plan;

import com.google.protobuf.TextFormat;
import com.mesosphere.sdk.offer.OfferRecommendation;
import com.mesosphere.sdk.offer.ResourceUtils;
import com.mesosphere.sdk.offer.UninstallRecommendation;
import org.apache.mesos.Protos;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * Step which implements the uninstalling of a particular persistent volume or resource.
 */
public class UninstallStep extends AbstractStep {

    /**
     * Creates a new instance with the provided {@code resourceId} and initial {@code status}.
     */
    public UninstallStep(String resourceId, Status status) {
        super(resourceId, status);
    }

    @Override
    public Optional<PodInstanceRequirement> start() {
        if (getStatus().equals(Status.PENDING)) {
            logger.info("Prepared UninstallStep for resource {}", getName());
            setStatus(Status.PREPARED);
        }
        return Optional.empty();
    }

    @Override
    public void updateOfferStatus(Collection<OfferRecommendation> recommendations) {
        // Expecting a singleton UninstallRecommendation attached to a resource with a resource ID. If it matches
        // the resource ID for this step, we mark the step as COMPLETE.
        boolean is_matched = recommendations.stream()
                .filter(offerRecommendation -> offerRecommendation instanceof UninstallRecommendation)
                .map(offerRecommendation -> (UninstallRecommendation) offerRecommendation)
                .map(UninstallRecommendation::getResource)
                .map(ResourceUtils::getResourceId)
                .anyMatch(uninstallResourceId -> getName().equals(uninstallResourceId));
        if (is_matched) {
            logger.info("Completed uninstall step for resource {}", getName());
            setStatus(Status.COMPLETE);
        }
    }

    @Override
    public Optional<String> getAsset() {
        return Optional.empty();
    }

    @Override
    public List<String> getErrors() {
        return Collections.emptyList();
    }

    @Override
    public void update(Protos.TaskStatus status) {
        logger.info("Step {} received status: {}", getName(), TextFormat.shortDebugString(status));
        logger.debug("Step {} ignoring irrelevant TaskStatus: {}", getName(), TextFormat.shortDebugString(status));
    }

}
